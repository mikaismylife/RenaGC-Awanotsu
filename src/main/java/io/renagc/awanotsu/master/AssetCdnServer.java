package io.renagc.awanotsu.master;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Static resource endpoint for remote asset bundles and the catalog hash gate.
 *
 * <p>Expected public path:
 * <pre>  GET /asset/{Platform}/{exactFileName}</pre>
 * where {@code {Platform}} is the Addressables {@code [BuildTarget]} token, and
 * {@code exactFileName} is either:
 * <ul>
 *   <li>{@code catalog_main.hash} — the 32-byte md5 version gate fetched on every launch
 *       ({@code TextDataProvider}); must match the running build's cached catalog hash.</li>
 *   <li>a content-addressed bundle, e.g.
 *       {@code character-live2d_..._everythinginitialdownload_<md5>.bundle}.</li>
 * </ul>
 *
 * <p>Bundles are served verbatim under their exact filenames: no rewriting, no signing.
 *
 * <p><b>Path mapping.</b> The request path {@code /asset/{Platform}/{file}} maps to
 * {@code <archiveRoot>/{Platform}/{file}}. A leading {@code asset/} segment is
 * stripped so {@code archiveRoot} points straight at the {@code asset} folder.
 * When a file is absent from the archive an optional second root is consulted,
 * then a 404 is returned and the missing path is logged at WARN.
 *
 * <p>Uses only the JDK's {@code com.sun.net.httpserver} (no extra dependency), mirroring
 * {@link MasterDataHttpServer}. Supports plain HTTP and, when an {@link SSLContext} is
 * supplied, HTTPS on the same self-signed cert.
 */
public final class AssetCdnServer {

    private static final Logger log = LoggerFactory.getLogger(AssetCdnServer.class);

    /** Unity Addressables remote-path build-target token = the platform sub-dir. */
    private static final String ASSET_PREFIX = "asset/";
    private static final int BUFFER = 1 << 16;

    private final Path archiveRoot;  // primary external resource root, may be null
    private final Path embeddedRoot; // optional fallback root, may be null
    private final int port;
    private final SSLContext sslContext; // null => plain HTTP
    private HttpServer http;

    public AssetCdnServer(String archiveDir, String embeddedDir, int port, SSLContext sslContext) {
        this.archiveRoot = toDir(archiveDir, "asset.dir");
        this.embeddedRoot = toDir(embeddedDir, "asset.embeddedDir");
        this.port = port;
        this.sslContext = sslContext;
    }

    static Path toDir(String dir, String label) {
        if (dir == null || dir.isBlank()) {
            return null;
        }
        Path p = Path.of(dir).normalize();
        if (!Files.isDirectory(p)) {
            log.warn("Asset CDN {} not a directory ({}) - ignoring.", label, p.toAbsolutePath());
            return null;
        }
        return p;
    }

    /** True when at least one serving root is present. */
    public boolean available() {
        return archiveRoot != null || embeddedRoot != null;
    }

    /** Start the asset CDN (HTTP, or HTTPS when an SSLContext was supplied). No-op if no root. */
    public void start() throws IOException {
        if (!available()) {
            log.warn("Asset CDN not started: neither archive nor embedded dir available.");
            return;
        }
        String scheme;
        if (sslContext != null) {
            HttpsServer https = HttpsServer.create(new InetSocketAddress(port), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            http = https;
            scheme = "https";
        } else {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            scheme = "http";
        }
        http.createContext("/", this::handle);
        http.setExecutor(null); // default single-thread executor; fine for local dev
        http.start();
        log.info("Asset CDN listening on {}://0.0.0.0:{} - archive={} embedded={} "
                        + "(e.g. GET /asset/Android/catalog_main.hash)",
                scheme, port,
                archiveRoot == null ? "(none)" : archiveRoot.toAbsolutePath(),
                embeddedRoot == null ? "(none)" : embeddedRoot.toAbsolutePath());
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        String rawPath = ex.getRequestURI().getPath();
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String rel = relativeKey(rawPath);
            if (rel.isEmpty()) {
                send(ex, 404, "text/plain", ("not found: " + rawPath).getBytes(StandardCharsets.UTF_8));
                return;
            }

            Path file = resolveUnder(archiveRoot, rel);
            String source = "archive";
            if (file == null) {
                file = resolveUnder(embeddedRoot, rel);
                source = "embedded";
            }
            if (file == null) {
                log.warn("Asset CDN 404 - no archived/embedded file for {} (key={})", rawPath, rel);
                send(ex, 404, "text/plain", ("not found: " + rawPath).getBytes(StandardCharsets.UTF_8));
                return;
            }
            log.debug("Asset CDN 200 [{}] {} -> {}", source, rawPath, file);
            sendFile(ex, file);
        } catch (RuntimeException e) {
            log.warn("Asset CDN handler error for {}: {}", rawPath, e.toString());
            send(ex, 500, "text/plain", "internal error".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * {@code /asset/{Platform}/{file}} (or {@code /{Platform}/{file}}) to the relative
     * {@code {Platform}/{file}} key. Percent-decoded; a leading {@code asset/} segment is
     * stripped so a root pointed at the {@code asset} folder resolves directly.
     */
    static String relativeKey(String rawPath) {
        String p = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith(ASSET_PREFIX)) {
            p = p.substring(ASSET_PREFIX.length());
        }
        return p;
    }

    /** Resolve {@code rel} under {@code root}, defended against traversal; null if absent. */
    static Path resolveUnder(Path root, String rel) {
        if (root == null) {
            return null;
        }
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) {
            return null;
        }
        return p;
    }

    /**
     * Stream a file with the correct {@code Content-Type}/{@code Content-Length}, honouring a
     * single {@code Range: bytes=start-end} header. Everything is served as
     * {@code application/octet-stream}.
     */
    static void sendFile(HttpExchange ex, Path file) throws IOException {
        long size = Files.size(file);
        ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");

        long[] range = parseRange(ex.getRequestHeaders().getFirst("Range"), size);
        long start = range[0];
        long end = range[1]; // inclusive
        long length = end - start + 1;

        int code = 200;
        if (range[2] == 1) {
            code = 206;
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }
        ex.sendResponseHeaders(code, length == 0 ? -1 : length);
        try (InputStream in = Files.newInputStream(file);
             OutputStream os = ex.getResponseBody()) {
            if (start > 0) {
                long skipped = 0;
                while (skipped < start) {
                    long s = in.skip(start - skipped);
                    if (s <= 0) {
                        break;
                    }
                    skipped += s;
                }
            }
            byte[] buf = new byte[BUFFER];
            long remaining = length;
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                os.write(buf, 0, n);
                remaining -= n;
            }
        }
    }

    /**
     * Parse a single-range {@code bytes=start-end} header against {@code size}. Returns
     * {@code [start, endInclusive, isRange(0|1)]}. Unparseable / unsatisfiable ranges fall back
     * to the full body {@code [0, size-1, 0]} (200), keeping the server simple and lenient.
     */
    private static long[] parseRange(String header, long size) {
        long full = size == 0 ? 0 : size - 1;
        if (header == null || !header.startsWith("bytes=") || size == 0) {
            return new long[]{0, full, 0};
        }
        String spec = header.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return new long[]{0, full, 0};
        }
        try {
            String a = spec.substring(0, dash).trim();
            String b = spec.substring(dash + 1).trim();
            long start;
            long end;
            if (a.isEmpty()) {
                // suffix range: last N bytes
                long n = Long.parseLong(b);
                start = Math.max(0, size - n);
                end = full;
            } else {
                start = Long.parseLong(a);
                end = b.isEmpty() ? full : Long.parseLong(b);
            }
            if (start < 0 || start > full || end < start) {
                return new long[]{0, full, 0};
            }
            if (end > full) {
                end = full;
            }
            return new long[]{start, end, 1};
        } catch (NumberFormatException e) {
            return new long[]{0, full, 0};
        }
    }

    private static void send(HttpExchange ex, int code, String contentType, byte[] body)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
