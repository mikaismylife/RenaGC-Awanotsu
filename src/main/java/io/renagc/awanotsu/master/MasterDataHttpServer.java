package io.renagc.awanotsu.master;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Minimal HTTP endpoint that serves encrypted master-data resources.
 *
 * <p>Routes (all GET):
 * <ul>
 *   <li>{@code /MasterDataSystemVersion.txt} - the version string (hash).</li>
 *   <li>{@code /<TableName>.bin}             - the byte-exact encrypted table blob
 *       ({@code [salt][IV][Rijndael-256(gzip(JSON))]}) the client decrypts.</li>
 * </ul>
 *
 * <p>Uses only the JDK's {@code com.sun.net.httpserver}; no extra dependency.
 */
public final class MasterDataHttpServer {

    private static final Logger log = LoggerFactory.getLogger(MasterDataHttpServer.class);

    private final MasterDataStore store;
    private final int port;
    private final SSLContext sslContext; // null => plain HTTP
    // Unified resource roots: this endpoint can serve both masterdata and assets.
    private final Path assetArchiveRoot;
    private final Path assetEmbeddedRoot;
    private HttpServer http;

    public MasterDataHttpServer(MasterDataStore store, int port) {
        this(store, port, null, null, null);
    }

    public MasterDataHttpServer(MasterDataStore store, int port, SSLContext sslContext) {
        this(store, port, sslContext, null, null);
    }

    /**
     * @param sslContext       when non-null, serve HTTPS (same self-signed cert as the gRPC server).
     * @param assetArchiveDir  external asset root; null disables asset serving.
     * @param assetEmbeddedDir optional fallback asset root; null disables it.
     */
    public MasterDataHttpServer(MasterDataStore store, int port, SSLContext sslContext,
                                String assetArchiveDir, String assetEmbeddedDir) {
        this.store = store;
        this.port = port;
        this.sslContext = sslContext;
        this.assetArchiveRoot = AssetCdnServer.toDir(assetArchiveDir, "asset.dir");
        this.assetEmbeddedRoot = AssetCdnServer.toDir(assetEmbeddedDir, "asset.embeddedDir");
    }

    /** Start the CDN (HTTP, or HTTPS when an SSLContext was supplied). No-op if no master dir. */
    public void start() throws IOException {
        if (!store.available()) {
            log.warn("Master CDN not started: no master dir available.");
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
        log.info("Master-data CDN listening on {}://0.0.0.0:{} - version={} tables={} (e.g. GET /{}, GET /MasterBand.bin)",
                scheme, port, store.version(), store.tableFiles().size(), MasterDataStore.VERSION_FILENAME);
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        String fullPath = ex.getRequestURI().getPath();
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            log.info("[CDN] GET {}", fullPath);
            String name = filename(fullPath);

            // 1) master version gate
            if (MasterDataStore.VERSION_FILENAME.equals(name)) {
                send(ex, 200, "text/plain; charset=utf-8",
                        store.version().getBytes(StandardCharsets.UTF_8));
                return;
            }
            // 2) master table blob (NOTE: catalog_main.bin also ends in .bin but is an ASSET -> fall through)
            if (name.endsWith(".bin")) {
                byte[] blob = store.readEncrypted(name);
                if (blob != null) {
                    send(ex, 200, "application/octet-stream", blob);
                    return;
                }
            }
            // 3) Optional asset catalog/bundle serving from the same base.
            Path file = resolveAsset(fullPath, name);
            if (file != null) {
                log.info("[CDN] 200 asset {} -> {}", fullPath, file);
                AssetCdnServer.sendFile(ex, file);
                return;
            }
            log.warn("[CDN] 404 {}", fullPath);
            send(ex, 404, "text/plain", ("not found: " + fullPath).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.warn("Master CDN handler error for {}: {}", fullPath, e.toString());
            send(ex, 500, "text/plain", "internal error".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Resolve an asset path under the configured roots. */
    private Path resolveAsset(String fullPath, String name) {
        if (assetArchiveRoot == null && assetEmbeddedRoot == null) {
            return null;
        }
        String key = null;
        int i = fullPath.indexOf("/asset/");
        if (i >= 0) {
            // (a) everything after a "/asset/" segment = "{Platform}/{file}"
            key = fullPath.substring(i + "/asset/".length());
        } else {
            // (b) last two path components = "{Platform}/{file}"
            String s = fullPath;
            while (s.startsWith("/")) { s = s.substring(1); }
            String[] parts = s.split("/");
            if (parts.length >= 2) {
                key = parts[parts.length - 2] + "/" + parts[parts.length - 1];
            }
        }
        Path f = null;
        if (key != null) {
            f = AssetCdnServer.resolveUnder(assetArchiveRoot, key);
            if (f == null) { f = AssetCdnServer.resolveUnder(assetEmbeddedRoot, key); }
        }
        // (c) last resort: the bare filename under each known platform dir
        if (f == null) {
            for (String plat : new String[]{"Android", "iOS"}) {
                String pk = plat + "/" + name;
                f = AssetCdnServer.resolveUnder(assetArchiveRoot, pk);
                if (f == null) { f = AssetCdnServer.resolveUnder(assetEmbeddedRoot, pk); }
                if (f != null) { break; }
            }
        }
        return f;
    }

    private static String filename(String p) {
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    private static void send(HttpExchange ex, int code, String contentType, byte[] body)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
