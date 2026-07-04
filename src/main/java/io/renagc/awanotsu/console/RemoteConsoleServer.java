package io.renagc.awanotsu.console;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.renagc.awanotsu.Config;
import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.command.Command;
import io.renagc.awanotsu.command.CommandHandler;
import io.renagc.awanotsu.command.CommandMap;
import io.renagc.awanotsu.command.commands.HelpCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Remote GC console over HTTP — goal [A.II]. Exposes the SAME {@link CommandMap}
 * the local {@link ConsoleManager} drives, so an operator can run
 * give/giveall/chart/help from a browser (or curl, or the proxy's in-app console)
 * WITHOUT shell access to the box.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET  /}             → a self-contained web console UI.</li>
 *   <li>{@code POST /api/exec}     → body = one command line; runs it; returns captured output.</li>
 *   <li>{@code GET  /api/commands} → the registered command list (discovery + the UI).</li>
 *   <li>{@code GET  /api/ping}     → {@code ok} health check.</li>
 * </ul>
 *
 * <p><b>Auth.</b> When {@link Config.Console#remoteToken} is non-blank, every
 * {@code /api/*} call must present it — header {@code X-Console-Token},
 * {@code Authorization: Bearer <t>}, or {@code ?token=<t>}. Blank token = OPEN
 * (the "non-authenticated" option the goal allows).
 *
 * <p><b>Termux-local.</b> Set {@link Config.Console#remoteHost}{@code =127.0.0.1}
 * to bind loopback only, for a RenaGC-on-Termux box where the console must not be
 * reachable off-device.
 */
public final class RemoteConsoleServer {

    private static final Logger log = LoggerFactory.getLogger(RemoteConsoleServer.class);

    private final CommandMap commandMap;
    private final Config.Console cfg;
    private HttpServer http;

    public RemoteConsoleServer(ServerContext ctx, Config.Console cfg) {
        this.cfg = cfg;
        this.commandMap = new CommandMap(ctx);
        HelpCommand.bind(commandMap);
    }

    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress(cfg.remoteHost, cfg.remotePort), 0);
        http.createContext("/", this::routeUi);
        http.createContext("/api/exec", this::routeExec);
        http.createContext("/api/commands", this::routeCommands);
        http.createContext("/api/ping", ex -> send(ex, 200, "text/plain", "ok"));
        http.setExecutor(null); // default executor; fine for an operator console
        http.start();
        boolean open = cfg.remoteToken == null || cfg.remoteToken.isBlank();
        log.info("Remote GC console on http://{}:{}/ (auth: {}).",
                cfg.remoteHost, cfg.remotePort, open ? "OPEN" : "token-required");
    }

    public void stop() {
        if (http != null) http.stop(0);
    }

    // ---- auth -------------------------------------------------------------

    private boolean authorized(HttpExchange ex) {
        String token = cfg.remoteToken;
        if (token == null || token.isBlank()) return true; // open mode
        String provided = ex.getRequestHeaders().getFirst("X-Console-Token");
        if (provided == null) {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) provided = auth.substring(7);
        }
        if (provided == null) {
            String q = ex.getRequestURI().getQuery();
            if (q != null) {
                for (String kv : q.split("&")) {
                    if (kv.startsWith("token=")) {
                        provided = URLDecoder.decode(kv.substring(6), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return token.equals(provided);
    }

    // ---- routes -----------------------------------------------------------

    private void routeExec(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, "text/plain", "POST the command line as the request body");
                return;
            }
            if (!authorized(ex)) {
                send(ex, 401, "text/plain", "unauthorized — supply X-Console-Token / Bearer / ?token=");
                return;
            }
            String line = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (line.isEmpty()) {
                send(ex, 400, "text/plain", "empty command");
                return;
            }
            StringBuilder sb = new StringBuilder();
            CommandHandler.Output out = m -> sb.append(m).append('\n');
            log.info("[remote-console] {}", line);
            commandMap.invoke(line, out);
            String body = sb.length() == 0 ? "(no output)" : sb.toString().stripTrailing();
            send(ex, 200, "text/plain; charset=utf-8", body);
        } catch (RuntimeException e) {
            log.warn("remote-console exec error", e);
            send(ex, 500, "text/plain", "error: " + e.getMessage());
        }
    }

    private void routeCommands(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            send(ex, 401, "text/plain", "unauthorized");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (CommandHandler h : commandMap.handlers()) {
            Command meta = h.meta();
            if (meta == null) continue;
            sb.append(meta.label());
            if (meta.aliases().length > 0) {
                sb.append(" (").append(String.join(",", meta.aliases())).append(')');
            }
            sb.append(" — ").append(meta.description()).append('\n');
        }
        send(ex, 200, "text/plain; charset=utf-8", sb.toString().stripTrailing());
    }

    private void routeUi(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            send(ex, 404, "text/plain", "not found");
            return;
        }
        send(ex, 200, "text/html; charset=utf-8", UI_HTML);
    }

    // ---- io ---------------------------------------------------------------

    private static void send(HttpExchange ex, int code, String contentType, String body) throws IOException {
        send(ex, code, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static final String UI_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>RenaGC — Remote GC Console</title>
            <style>
            body{background:#15151c;color:#d8d8e0;font:14px/1.5 ui-monospace,monospace;margin:0;padding:16px}
            h1{font-size:15px;color:#9aa0ff;margin:0 0 10px}
            #out{background:#0e0e14;border:1px solid #2a2a36;border-radius:8px;padding:10px;height:62vh;overflow:auto;white-space:pre-wrap;word-break:break-word}
            input{background:#0e0e14;color:#d8d8e0;border:1px solid #2a2a36;border-radius:8px;padding:9px;font:inherit}
            #cmd{flex:1}form{display:flex;gap:8px;margin-top:10px}
            button{background:#4a4ad0;color:#fff;border:0;border-radius:8px;padding:9px 16px;cursor:pointer}
            .tok{margin-bottom:8px;color:#7a7a8a}
            </style></head><body>
            <h1>RenaGC — Remote GC Console</h1>
            <div class="tok">token <input id="tok" type="password" placeholder="(blank if open)" size="22"></div>
            <div id="out"></div>
            <form id="f"><input id="cmd" placeholder="give 100001 x10    ·    help" autofocus autocomplete="off"><button>Run</button></form>
            <script>
            var out=document.getElementById('out'),cmd=document.getElementById('cmd'),tok=document.getElementById('tok');
            function log(s,c){var d=document.createElement('div');if(c)d.style.color=c;d.textContent=s;out.appendChild(d);out.scrollTop=out.scrollHeight;}
            document.getElementById('f').onsubmit=async function(e){e.preventDefault();var c=cmd.value.trim();if(!c)return;log('> '+c,'#9aa0ff');cmd.value='';
              try{var r=await fetch('/api/exec',{method:'POST',headers:{'X-Console-Token':tok.value},body:c});var t=await r.text();log(t,r.ok?null:'#ff7a7a');}
              catch(err){log('network error: '+err,'#ff7a7a');}};
            fetch('/api/ping').then(function(r){return r.text();}).then(function(t){log('console '+t+' — type help','#7a7a8a');});
            </script></body></html>
            """;
}
