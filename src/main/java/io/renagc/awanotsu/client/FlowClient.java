package io.renagc.awanotsu.client;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.renagc.awanotsu.auth.AuthInterceptor;
import io.renagc.awanotsu.proto.common.PlayerData;
import io.renagc.awanotsu.proto.home.GetHomeRequest;
import io.renagc.awanotsu.proto.home.GetHomeResponse;
import io.renagc.awanotsu.proto.home.HomeServiceGrpc;
import io.renagc.awanotsu.proto.live.FinishFreeRequest;
import io.renagc.awanotsu.proto.live.FinishFreeResponse;
import io.renagc.awanotsu.proto.live.LiveServiceGrpc;
import io.renagc.awanotsu.proto.live.StartFreeRequest;
import io.renagc.awanotsu.proto.livemusic.GetRankingRequest;
import io.renagc.awanotsu.proto.livemusic.GetRankingResponse;
import io.renagc.awanotsu.proto.livemusic.LiveMusicServiceGrpc;
import io.renagc.awanotsu.proto.masterdata.MasterdataServiceGrpc;
import io.renagc.awanotsu.proto.masterdata.VersionRequest;
import io.renagc.awanotsu.proto.player.GetPlayerDataRequest;
import io.renagc.awanotsu.proto.player.GetPlayerDataResponse;
import io.renagc.awanotsu.proto.player.PlayerServiceGrpc;
import io.renagc.awanotsu.proto.player.RegisterRequest;
import io.renagc.awanotsu.proto.player.RegisterResponse;

import java.util.concurrent.TimeUnit;

/**
 * End-to-end flow client: walks the main app sequence against a running RenaGC
 * server and asserts each step returns valid, non-empty data.
 *
 * <pre>
 *   Version → Register → GetPlayerData → GetHome → StartFree → FinishFree → GetRanking
 * </pre>
 *
 * After Register the issued credential is attached as the {@code authkey} call
 * metadata on every subsequent call. Usage: {@code gradlew flowTest} (custom JavaExec task) or pass
 * host/port as args.
 */
public final class FlowClient {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 20000;

        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        boolean ok = true;
        try {
            System.out.println("== RenaGC flow test (login -> home -> play -> result) against "
                    + host + ":" + port + " ==");

            // 1. Version
            String version = MasterdataServiceGrpc.newBlockingStub(channel)
                    .version(VersionRequest.getDefaultInstance()).getVersion();
            ok &= check("Version", !version.isEmpty(), "version=\"" + version + "\"");

            // 2. Register
            RegisterResponse reg = PlayerServiceGrpc.newBlockingStub(channel)
                    .register(RegisterRequest.getDefaultInstance());
            String credential = reg.getCredential().getCredential();
            long profileId = reg.getProfileId();
            ok &= check("Register", !credential.isEmpty() && profileId != 0,
                    "profile_id=" + profileId);

            // Auth metadata for all later calls (recovered x-* wire keys).
            Metadata md = new Metadata();
            md.put(AuthInterceptor.PLAYER_CREDENTIAL, credential);
            md.put(AuthInterceptor.PLAYER_ID, reg.getCredential().getId());
            var authHeaders = MetadataUtils.newAttachHeadersInterceptor(md);
            var player = PlayerServiceGrpc.newBlockingStub(channel).withInterceptors(authHeaders);
            var home = HomeServiceGrpc.newBlockingStub(channel).withInterceptors(authHeaders);
            var live = LiveServiceGrpc.newBlockingStub(channel).withInterceptors(authHeaders);
            var liveMusic = LiveMusicServiceGrpc.newBlockingStub(channel).withInterceptors(authHeaders);

            // 3. GetPlayerData
            GetPlayerDataResponse pdr = player.getPlayerData(GetPlayerDataRequest.getDefaultInstance());
            PlayerData pd = pdr.getPlayerData();
            long firstMusic = pd.getLiveMusicCount() > 0 ? pd.getLiveMusic(0).getMusicId() : 100001;
            ok &= check("GetPlayerData",
                    pd.getMemberCardsCount() > 0 && pd.getSupportCardsCount() > 0
                            && pd.getDecksCount() > 0
                            // NOTE: live_music is intentionally stripped from the lean GetPlayerData
                            // Songs are unlocked through masterdata; do NOT assert songs here.
                            && pd.getMyProfile().getProfileId() == profileId
                            && pd.getLiveBoost().getAmount() > 0,
                    pd.getMemberCardsCount() + " cards, " + pd.getSupportCardsCount() + " support, "
                            + pd.getDecksCount() + " decks, " + pd.getLiveMusicCount() + " songs, gem="
                            + pd.getGem().getFree() + ", boost=" + pd.getLiveBoost().getAmount()
                            + ", name=\"" + pd.getMyProfile().getName() + "\"");

            // 4. GetHome
            GetHomeResponse homeResp = home.get(GetHomeRequest.getDefaultInstance());
            ok &= check("GetHome", homeResp.getAnnouncementsCount() > 0,
                    homeResp.getAnnouncementsCount() + " announcement(s)");

            // 5. StartFree (enter the chart)
            int difficulty = 1;
            live.startFree(StartFreeRequest.newBuilder()
                    .setMusicId(firstMusic).setDifficulty(difficulty).setBoost(0).setDeckId(1).build());
            check("StartFree", true, "entered music=" + firstMusic + " diff=" + difficulty);

            // 6. FinishFree (reach the result)
            int score = 5_000_000;
            FinishFreeResponse fin = live.finishFree(FinishFreeRequest.newBuilder()
                    .setClearStatus(1).setScore(score).setCombo(400).build());
            boolean resultOk = fin.hasNotification() || fin.getMusicRewardsCount() > 0
                    || fin.getRewardIdCount() > 0;
            ok &= check("FinishFree", resultOk,
                    fin.getMusicRewardsCount() + " music reward(s), "
                            + fin.getNotification().getItemDiffsCount() + " item diff(s)");

            // 7. GetPlayerData again — the score must now be persisted.
            PlayerData pd2 = player.getPlayerData(GetPlayerDataRequest.getDefaultInstance()).getPlayerData();
            int recorded = pd2.getLiveScoreList().stream()
                    .filter(s -> s.getMusicId() == firstMusic).mapToInt(s -> s.getHighScore()).max().orElse(0);
            ok &= check("Score persisted", recorded == score, "high_score=" + recorded);

            // 8. GetRanking (livemusic) — self appears.
            GetRankingResponse rank = liveMusic.getRanking(
                    GetRankingRequest.newBuilder().setMusicId(firstMusic).build());
            ok &= check("GetRanking", rank.getPlayersCount() >= 1 && rank.getMyRank() >= 1,
                    rank.getPlayersCount() + " player(s), my_rank=" + rank.getMyRank());

            System.out.println("== FLOW TEST " + (ok ? "PASS" : "FAIL") + " ==");
            if (!ok) {
                System.exit(2);
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static boolean check(String step, boolean pass, String detail) {
        System.out.printf("[%s] %s -> %s%n", pass ? "OK" : "XX", step, detail);
        return pass;
    }
}
