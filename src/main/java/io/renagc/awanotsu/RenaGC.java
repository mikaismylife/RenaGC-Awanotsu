package io.renagc.awanotsu;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.renagc.awanotsu.auth.AuthInterceptor;
import io.renagc.awanotsu.console.ConsoleManager;
import io.renagc.awanotsu.console.RemoteConsoleServer;
import io.renagc.awanotsu.master.AssetCdnServer;
import io.renagc.awanotsu.master.MasterDataHttpServer;
import io.renagc.awanotsu.master.MasterDataStore;
import io.renagc.awanotsu.persistence.DatabaseManager;
import io.renagc.awanotsu.persistence.PlayerRepository;
import io.renagc.awanotsu.persistence.PlayerStore;
import io.renagc.awanotsu.service.ServiceRegistry;
import io.renagc.awanotsu.tls.TlsSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * RenaGC-Awanotsu entry point.
 *
 * <p>Boots a plaintext h2c gRPC server registering all generated services
 * (each *ServiceImpl + the {@link AuthInterceptor}). MongoDB is REQUIRED - the
 * server refuses to launch if the database is unreachable (no memory-only mode),
 * matching upstream Grasscutter behaviour so player progress is always durable.
 */
public final class RenaGC {

    private static final Logger log = LoggerFactory.getLogger(RenaGC.class);

    private RenaGC() {
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting RenaGC-Awanotsu (gRPC server)...");

        // 1. Config
        Path configPath = Paths.get(args.length > 0 ? args[0] : "config.json");
        Config config = Config.load(configPath);

        // 2. Persistence - MongoDB is REQUIRED. Like upstream Grasscutter, the server
        // refuses to launch unless the database is reachable (no memory-only fallback):
        // player progress must be durable.
        DatabaseManager database = new DatabaseManager();
        boolean mongo = database.tryConnect(config.mongo);
        if (!mongo) {
            log.error("Couldn't connect to MongoDB at '{}'.", config.mongo);
            log.error("MongoDB is REQUIRED - RenaGC will not start without a database. Make sure "
                    + "MongoDB is running (e.g. `mongod`) and the `mongo` URI in {} is correct, "
                    + "then restart RenaGC.", configPath);
            System.exit(1);
            return;
        }
        log.info("Persistence: MongoDB connected (required).");

        // 2b. Master-data store (version + encrypted resource tables). Built before
        // the player store, which derives the fresh-player template from it.
        MasterDataStore masterData = MasterDataStore.open(
                config.masterdata != null ? config.masterdata.dir : null,
                config.masterdata != null ? config.masterdata.overrideDir : null,
                config.masterdata != null ? config.masterdata.keyHex : null,
                config.masterVersion);

        // Player store: full player state, Mongo write-through (soft-fail to memory),
        // fresh accounts built from the master DB via FreshPlayerFactory.
        PlayerRepository playerRepo = new PlayerRepository(database);
        PlayerStore players = new PlayerStore(playerRepo,
                profileId -> io.renagc.awanotsu.flow.FreshPlayerFactory.build(masterData, profileId));

        ServerContext ctx = new ServerContext(config, players, database, masterData);

        // 2c. TLS material (self-signed; shared by the TLS gRPC listener + HTTPS CDN).
        TlsSupport tls = null;
        if (config.tls != null && config.tls.enabled) {
            try {
                tls = TlsSupport.load(config.tls.p12Path, config.tls.password, config.tls.sans);
            } catch (Exception e) {
                log.error("TLS enabled but keystore setup failed ({}); continuing h2c-only.", e.toString());
            }
        }

        // 2d. Master-data endpoint (bulk master is fetched over HTTP(S), not gRPC).
        // Always serve plain HTTP; additionally serve HTTPS when TLS is on.
        List<MasterDataHttpServer> cdns = new ArrayList<>();
        // Unified resource endpoint: the master endpoint can also serve asset roots.
        String assetDir = config.asset != null ? config.asset.dir : null;
        String assetEmb = config.asset != null ? config.asset.embeddedDir : null;
        if (config.masterdata != null && config.masterdata.serveHttp && masterData.available()) {
            startCdn(cdns, new MasterDataHttpServer(masterData, config.masterdata.httpPort, null, assetDir, assetEmb),
                    "http", config.masterdata.httpPort);
            if (tls != null) {
                try {
                    SSLContext sslCtx = tls.httpsServerSslContext();
                    startCdn(cdns, new MasterDataHttpServer(masterData, config.masterdata.httpsPort, sslCtx, assetDir, assetEmb),
                            "https", config.masterdata.httpsPort);
                } catch (Exception e) {
                    log.warn("HTTPS master CDN setup failed ({}); HTTP CDN still up.", e.toString());
                }
            }
        }

        // 2e. Asset endpoint for external bundles and catalog hashes. Plain HTTP always;
        // HTTPS additionally when TLS is on.
        List<AssetCdnServer> assetCdns = new ArrayList<>();
        if (config.asset != null && config.asset.serveHttp) {
            startAssetCdn(assetCdns, config, null, config.asset.httpPort, "http");
            if (tls != null) {
                try {
                    SSLContext sslCtx = tls.httpsServerSslContext();
                    startAssetCdn(assetCdns, config, sslCtx, config.asset.httpsPort, "https");
                } catch (Exception e) {
                    log.warn("HTTPS asset CDN setup failed ({}); HTTP asset CDN still up.", e.toString());
                }
            }
            if (!assetCdns.isEmpty()) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    for (AssetCdnServer a : assetCdns) {
                        a.stop();
                    }
                }, "renagc-asset-cdn-shutdown"));
            }
        }

        // 3. Build the gRPC servers: every generated *ServiceImpl + AuthInterceptor. The h2c
        // listener always runs (local SmokeClient/flowTest); a TLS listener is added
        // when TLS is enabled.
        AuthInterceptor authInterceptor = new AuthInterceptor();
        List<BindableService> services = ServiceRegistry.all(ctx);

        ServerBuilder<?> h2c = ServerBuilder.forPort(config.grpc.port);
        addServices(h2c, services, authInterceptor);
        List<Server> servers = new ArrayList<>();
        servers.add(h2c.build());

        if (tls != null) {
            try {
                SslContext grpcSsl = tls.grpcServerSslContext();
                NettyServerBuilder tlsBuilder =
                        NettyServerBuilder.forPort(config.tls.grpcPort).sslContext(grpcSsl);
                addServices(tlsBuilder, services, authInterceptor);
                servers.add(tlsBuilder.build());
            } catch (Exception e) {
                log.error("TLS gRPC listener setup failed ({}); h2c-only.", e.toString());
            }
        }

        startAndAwait(servers, config, services.size(), database, ctx, cdns);
    }

    private static void addServices(ServerBuilder<?> builder, List<BindableService> services,
                                    AuthInterceptor authInterceptor) {
        for (BindableService svc : services) {
            builder.addService(ServerInterceptors.intercept(svc, authInterceptor));
        }
        // Reflection makes grpcurl usable without local .proto files.
        builder.addService(ProtoReflectionService.newInstance());
    }

    private static void startCdn(List<MasterDataHttpServer> cdns, MasterDataHttpServer cdn,
                                 String scheme, int port) {
        try {
            cdn.start();
            cdns.add(cdn);
        } catch (IOException e) {
            log.warn("Master-data CDN ({}) failed to start on :{} ({}); continuing.",
                    scheme, port, e.toString());
        }
    }

    private static void startAssetCdn(List<AssetCdnServer> cdns, Config config,
                                      SSLContext sslCtx, int port, String scheme) {
        try {
            AssetCdnServer cdn = new AssetCdnServer(
                    config.asset.dir, config.asset.embeddedDir, port, sslCtx);
            if (!cdn.available()) {
                log.warn("Asset CDN ({}) not started: no archive/embedded dir.", scheme);
                return;
            }
            cdn.start();
            cdns.add(cdn);
        } catch (IOException e) {
            log.warn("Asset CDN ({}) failed to start on :{} ({}); continuing.",
                    scheme, port, e.toString());
        }
    }

    private static void startAndAwait(List<Server> servers, Config config, int serviceCount,
                                      DatabaseManager database, ServerContext ctx,
                                      List<MasterDataHttpServer> cdns)
            throws IOException, InterruptedException {
        // Primary (h2c) listener must come up; the local SmokeClient/flowTest depend on it.
        servers.get(0).start();
        log.info("gRPC h2c listening on {}:{} - {} services registered.",
                config.grpc.host, config.grpc.port, serviceCount);
        // Extra (TLS) listener is best-effort: a bind conflict must not take the server down.
        for (int i = 1; i < servers.size(); i++) {
            try {
                servers.get(i).start();
                log.info("gRPC TLS listening on 0.0.0.0:{} (self-signed, ALPN h2; SANs={}).",
                        config.tls.grpcPort, config.tls.sans);
            } catch (IOException e) {
                log.error("TLS gRPC listener failed to start on :{} ({}); h2c-only.",
                        config.tls.grpcPort, e.toString());
            }
        }
        log.info("Try: grpcurl -plaintext localhost:{} list", config.grpc.port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server(s)...");
            for (Server server : servers) {
                server.shutdown();
            }
            for (MasterDataHttpServer cdn : cdns) {
                cdn.stop();
            }
            database.close();
        }, "renagc-shutdown"));

        // Phase 2: Grasscutter-style operator console (give/giveall/chart/help).
        ConsoleManager console = new ConsoleManager(ctx);
        console.onStop(() -> {
            log.info("Console requested shutdown.");
            servers.forEach(Server::shutdown);
        });
        console.startAsync();

        // Optional remote GC console over HTTP, using the same CommandMap.
        if (config.console != null && config.console.remoteEnabled) {
            try {
                RemoteConsoleServer remote = new RemoteConsoleServer(ctx, config.console);
                remote.start();
                Runtime.getRuntime().addShutdownHook(
                        new Thread(remote::stop, "renagc-remote-console-shutdown"));
            } catch (IOException e) {
                log.warn("Remote GC console failed to start on :{} ({}); continuing.",
                        config.console.remotePort, e.toString());
            }
        }

        // Block on the primary (h2c) listener; the TLS listener shuts down via the hook.
        servers.get(0).awaitTermination();
    }
}
