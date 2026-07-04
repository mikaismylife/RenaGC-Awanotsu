package io.renagc.awanotsu.client;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.renagc.awanotsu.Config;
import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.master.MasterDataStore;
import io.renagc.awanotsu.proto.masterdata.MasterdataServiceGrpc;
import io.renagc.awanotsu.proto.masterdata.VersionRequest;
import io.renagc.awanotsu.service.MasterdataServiceImpl;
import io.renagc.awanotsu.tls.TlsSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TLS smoke test (no external server / fixed ports). Boots an in-process gRPC
 * server over a freshly generated self-signed cert, then connects a client that
 * trusts only that cert and verifies ALPN h2 + SAN matching.
 */
public final class TlsSmoke {

    private static final String AUTHORITY = "localhost";
    private static final String DUMMY_KEY =
            "0000000000000000000000000000000000000000000000000000000000000000";

    public static void main(String[] args) throws Exception {
        System.out.println("== RenaGC TLS smoke (self-signed, ALPN h2, SAN " + AUTHORITY + ") ==");

        Path tmp = Files.createTempDirectory("renagc-tls-smoke");
        String p12 = tmp.resolve("smoke.p12").toString();
        List<String> sans = List.of(AUTHORITY, "127.0.0.1");

        TlsSupport tls = TlsSupport.load(p12, "renagc", sans);
        X509Certificate cert = tls.certificate();
        System.out.println("cert subject: " + cert.getSubjectX500Principal());
        System.out.println("cert SANs:    " + cert.getSubjectAlternativeNames());

        MasterDataStore store = MasterDataStore.open(null, null, DUMMY_KEY, "tls-smoke-version");
        ServerContext ctx = new ServerContext(new Config(), null, null, store);
        SslContext serverSsl = tls.grpcServerSslContext();
        Server server = NettyServerBuilder.forPort(0)
                .sslContext(serverSsl)
                .addService(new MasterdataServiceImpl(ctx))
                .build()
                .start();
        int port = server.getPort();
        System.out.println("TLS gRPC server on 127.0.0.1:" + port);

        SslContext clientSsl = TlsSupport.grpcClientTrusting(cert);
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", port)
                .sslContext(clientSsl)
                .overrideAuthority(AUTHORITY)
                .build();

        boolean ok = false;
        try {
            String v = MasterdataServiceGrpc.newBlockingStub(channel)
                    .version(VersionRequest.getDefaultInstance()).getVersion();
            ok = v != null && !v.isEmpty();
            System.out.println("[OK] TLS handshake + ALPN h2 + SAN(" + AUTHORITY
                    + ") verified; Version -> \"" + v + "\"");
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }

        System.out.println("== TLS SMOKE " + (ok ? "PASS" : "FAIL") + " ==");
        if (!ok) {
            System.exit(2);
        }
    }
}
