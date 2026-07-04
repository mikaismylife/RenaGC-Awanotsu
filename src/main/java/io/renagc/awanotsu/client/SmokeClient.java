package io.renagc.awanotsu.client;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.renagc.awanotsu.auth.AuthInterceptor;
import io.renagc.awanotsu.proto.masterdata.MasterdataServiceGrpc;
import io.renagc.awanotsu.proto.masterdata.VersionRequest;
import io.renagc.awanotsu.proto.masterdata.VersionResponse;
import io.renagc.awanotsu.proto.player.PlayerServiceGrpc;
import io.renagc.awanotsu.proto.player.RegisterRequest;
import io.renagc.awanotsu.proto.player.RegisterResponse;
import io.renagc.awanotsu.proto.player.WhoamiRequest;
import io.renagc.awanotsu.proto.player.WhoamiResponse;

import java.util.concurrent.TimeUnit;

/**
 * Tiny plaintext h2c gRPC smoke-test client used in place of grpcurl.
 * Calls MasterdataService/Version, PlayerService/Register, then Whoami with the
 * issued credential as the {@code authkey} metadata, and prints the responses.
 *
 * Usage: gradlew smokeTest  (custom JavaExec task) or pass host/port as args.
 */
public final class SmokeClient {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 20000;

        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        try {
            System.out.println("== RenaGC gRPC smoke test against " + host + ":" + port + " ==");

            // 1. MasterdataService/Version
            VersionResponse ver = MasterdataServiceGrpc.newBlockingStub(channel)
                    .version(VersionRequest.getDefaultInstance());
            System.out.println("[1] app.masterdata.MasterdataService/Version -> version=\""
                    + ver.getVersion() + "\"");

            // 2. PlayerService/Register
            // RegisterResponse{ PlayerCredential credential = 1; int64 profile_id = 2; }
            // PlayerCredential{ string id; string credential; string device_id; }
            RegisterResponse reg = PlayerServiceGrpc.newBlockingStub(channel)
                    .register(RegisterRequest.getDefaultInstance());
            String credential = reg.getCredential().getCredential();
            System.out.println("[2] app.player.PlayerService/Register -> credential=\""
                    + credential + "\" profile_id=" + reg.getProfileId());

            // 3. PlayerService/Whoami WITH the issued credential as x-player-credential metadata
            // WhoamiResponse{ string player_id = 1; }
            Metadata md = new Metadata();
            md.put(AuthInterceptor.PLAYER_CREDENTIAL, credential);
            md.put(AuthInterceptor.PLAYER_ID, reg.getCredential().getId());
            PlayerServiceGrpc.PlayerServiceBlockingStub authed =
                    PlayerServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));
            WhoamiResponse who = authed.whoami(WhoamiRequest.getDefaultInstance());
            System.out.println("[3] app.player.PlayerService/Whoami (authkey set) -> player_id="
                    + who.getPlayerId());

            boolean ok = !ver.getVersion().isEmpty()
                    && !credential.isEmpty()
                    && reg.getProfileId() != 0
                    && who.getPlayerId().equals(String.valueOf(reg.getProfileId()));
            System.out.println("== SMOKE TEST " + (ok ? "PASS" : "FAIL") + " ==");
            if (!ok) {
                System.exit(2);
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
