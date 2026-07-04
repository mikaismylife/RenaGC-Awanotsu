package io.renagc.awanotsu.flow;

import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.auth.AuthInterceptor;
import io.renagc.awanotsu.persistence.LiveSession;
import io.renagc.awanotsu.proto.common.Announcement;
import io.renagc.awanotsu.proto.common.AnnouncementCategory;
import io.renagc.awanotsu.proto.common.Item;
import io.renagc.awanotsu.proto.common.LiveBoost;
import io.renagc.awanotsu.proto.common.LiveRankingPlayer;
import io.renagc.awanotsu.proto.common.LiveScore;
import io.renagc.awanotsu.proto.common.LiveSetting;
import io.renagc.awanotsu.proto.common.Limitation;
import io.renagc.awanotsu.proto.common.LimitationType;
import io.renagc.awanotsu.proto.common.Notification;
import io.renagc.awanotsu.proto.common.PlayerData;
import io.renagc.awanotsu.proto.common.ResourceType;
import io.renagc.awanotsu.proto.common.RewardResource;
import io.renagc.awanotsu.proto.deck.SaveDecksRequest;
import io.renagc.awanotsu.proto.deck.SaveDecksResponse;
import io.renagc.awanotsu.proto.home.GetHomeRequest;
import io.renagc.awanotsu.proto.home.GetHomeResponse;
import io.renagc.awanotsu.proto.live.FinishFreeRequest;
import io.renagc.awanotsu.proto.live.FinishFreeResponse;
import io.renagc.awanotsu.proto.live.SaveSettingRequest;
import io.renagc.awanotsu.proto.live.SaveSettingResponse;
import io.renagc.awanotsu.proto.live.SkipFreeRequest;
import io.renagc.awanotsu.proto.live.SkipFreeResponse;
import io.renagc.awanotsu.proto.live.StartFreeRequest;
import io.renagc.awanotsu.proto.live.StartFreeResponse;
import io.renagc.awanotsu.proto.liveboost.LiveBoostRecoveryRequest;
import io.renagc.awanotsu.proto.liveboost.LiveBoostRecoveryResponse;
import io.renagc.awanotsu.proto.liveboost.LiveBoostReqCheckResponse;
import io.renagc.awanotsu.proto.livemusic.GetRankingRequest;
import io.renagc.awanotsu.proto.livemusic.GetRankingResponse;
import io.renagc.awanotsu.proto.player.EditProfileRequest;
import io.renagc.awanotsu.proto.player.EditProfileResponse;
import io.renagc.awanotsu.proto.player.GetPlayerDataRequest;
import io.renagc.awanotsu.proto.player.GetPlayerDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Server-side bodies for the login → home → play → result flow. The generated
 * {@code *ServiceImpl} methods delegate here one-to-one (see
 * {@code tools/genservice.py} {@code boot_overrides()}), keeping the heavy logic in
 * hand-written, regen-safe code.
 *
 * <p>Each method reads the caller's profile from the auth metadata, mutates +
 * persists {@link PlayerData} via {@code ctx.players()}, and returns a populated,
 * non-empty response derived from the real master DB.
 */
public final class Flows {

    private static final Logger log = LoggerFactory.getLogger(Flows.class);

    /** Score rank used for a skipped live (master param {@code live_skip_result_score_rank} = "B" ≈ 3). */
    private static final int SKIP_RESULT_RANK = 3;
    private static final long FAR_FUTURE = 4102444800L; // 2100-01-01, "always active"

    private Flows() {
    }

    // --- player --------------------------------------------------------------

    public static void getPlayerData(ServerContext ctx, GetPlayerDataRequest request,
                                     StreamObserver<GetPlayerDataResponse> obs) {
        long pid = currentProfile(ctx);
        PlayerData full = playerOrEmpty(ctx, pid);
        // The lean GetPlayerData does NOT carry items / band_items / live_music /
        // the four skin lists (those load via their own services later). Strip them
        // so this response stays minimal. The full player keeps them for the
        // per-feature services.
        PlayerData pd = full.toBuilder()
                .clearItems()
                .clearBandItems()
                .clearLiveMusic()
                .clearLiveLaneSkins()
                .clearLiveNoteSkins()
                .clearLiveNoteEffectSkins()
                .clearLiveNoteSeGroups()
                .build();
        GetPlayerDataResponse resp = GetPlayerDataResponse.newBuilder()
                .setPlayerData(pd)
                .setNotification(Notification.getDefaultInstance())
                .setLimitation(Limitation.newBuilder()
                        .setLimitationType(LimitationType.LIMITATION_TYPE_NONE).build())
                // friends(4) + invitation(5) are message fields the client's LoadOrCreate
                // dereferences while processing this response; proto3 leaves an unset
                // message null, so populate them (Friends' three FriendProfiles sub-lists
                // too) to keep the boot from NRE'ing right after GetPlayerData.
                .setFriends(io.renagc.awanotsu.proto.common.Friends.newBuilder()
                        .setAccepted(io.renagc.awanotsu.proto.common.FriendProfiles.getDefaultInstance())
                        .setPendingSent(io.renagc.awanotsu.proto.common.FriendProfiles.getDefaultInstance())
                        .setReceive(io.renagc.awanotsu.proto.common.FriendProfiles.getDefaultInstance())
                        .build())
                .setInvitation(io.renagc.awanotsu.proto.common.Invitation.getDefaultInstance())
                .build();
        log.info("GetPlayerData profile={} -> {} cards, {} songs.",
                pid, pd.getMemberCardsCount(), pd.getLiveMusicCount());
        respond(obs, resp);
    }

    public static void editProfile(ServerContext ctx, EditProfileRequest request,
                                   StreamObserver<EditProfileResponse> obs) {
        long pid = currentProfile(ctx);
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd != null && !request.getName().isEmpty()) {
            PlayerData updated = pd.toBuilder()
                    .setMyProfile(pd.getMyProfile().toBuilder()
                            .setName(request.getName())
                            .setLastUpdatedAt(now())
                            .build())
                    .build();
            ctx.players().savePlayerData(pid, updated);
        }
        respond(obs, EditProfileResponse.getDefaultInstance());
    }

    // --- home ----------------------------------------------------------------

    public static void getHome(ServerContext ctx, GetHomeRequest request,
                               StreamObserver<GetHomeResponse> obs) {
        GetHomeResponse resp = GetHomeResponse.newBuilder()
                .addAnnouncements(Announcement.newBuilder()
                        .setId(1)
                        .setCategory(AnnouncementCategory.ANNOUNCEMENT_CATEGORY_OTHER)
                        .setTitle("Welcome to RenaGC-Awanotsu")
                        .setBody("Welcome to RenaGC-Awanotsu. このサーバーは無料のオープンソースサーバーです。購入して入手した場合は、直ちに返金を要求し、詐欺師を報告してください！THIS IS A FREE AND OPEN-SOURCED SERVER EMULATOR, IF YOU PAID FOR THIS, REQUEST REFUND IMMEDIATELY AND REPORT THE SCAMMER! 此服务器为免费开源服务器，如果您通过购买获取到此产品，请立即要求退款并举报！此伺服器為免費開源伺服器，如果您通過購買獲取到此產品，請立即要求退款並舉報！")
                        // 虽然消息似乎还不能在游戏内正常显示。。。
                        .setStartAt(0)
                        .setEndAt(FAR_FUTURE)
                        .setSortOrder(1)
                        .setLastUpdatedAt(now())
                        .build())
                .setNotification(Notification.getDefaultInstance())
                // friends is a message field the home screen dereferences (accepted/
                // pendingSent/receive sub-lists); accepted.limit=20,
                // pendingSent.limit=30. Populate it so the home transition never NREs.
                .setFriends(io.renagc.awanotsu.proto.common.Friends.newBuilder()
                        .setAccepted(io.renagc.awanotsu.proto.common.FriendProfiles.newBuilder().setLimit(20).build())
                        .setPendingSent(io.renagc.awanotsu.proto.common.FriendProfiles.newBuilder().setLimit(30).build())
                        .setReceive(io.renagc.awanotsu.proto.common.FriendProfiles.getDefaultInstance())
                        .build())
                .build();
        respond(obs, resp);
    }

    // --- live: free play -----------------------------------------------------

    public static void startFree(ServerContext ctx, StartFreeRequest request,
                                 StreamObserver<StartFreeResponse> obs) {
        long pid = currentProfile(ctx);
        ctx.players().startLive(pid, new LiveSession(
                request.getMusicId(), request.getDifficulty(),
                request.getBoost(), request.getDeckId(), now()));
        // Consume live boost (min 1) so PlayerData reflects the spend.
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd != null) {
            int spend = Math.max(1, request.getBoost());
            int remaining = Math.max(0, pd.getLiveBoost().getAmount() - spend);
            ctx.players().savePlayerData(pid, pd.toBuilder()
                    .setLiveBoost(pd.getLiveBoost().toBuilder().setAmount(remaining).build())
                    .build());
        }
        log.info("StartFree profile={} music={} diff={}", pid, request.getMusicId(), request.getDifficulty());
        respond(obs, StartFreeResponse.getDefaultInstance());
    }

    public static void finishFree(ServerContext ctx, FinishFreeRequest request,
                                  StreamObserver<FinishFreeResponse> obs) {
        long pid = currentProfile(ctx);
        LiveSession session = ctx.players().getLive(pid);
        long musicId = session != null ? session.musicId() : 0;
        int difficulty = session != null ? session.difficulty() : 0;

        int score = Math.max(request.getScore(), request.getLiveFinish().getSoloScore());
        int combo = Math.max(request.getCombo(), request.getLiveFinish().getCombo());
        int clearStatus = Math.max(request.getClearStatus(), request.getLiveFinish().getClearStatus());

        int rank = LiveRewards.scoreRank(ctx.masterData(), musicId, score);
        List<RewardResource> rewards = LiveRewards.freeRewards(ctx.masterData(), rank);

        PlayerData pd = ctx.players().getPlayerData(pid);
        Notification.Builder notif = Notification.newBuilder();
        if (pd != null) {
            PlayerData.Builder b = pd.toBuilder();
            recordScore(b, musicId, difficulty, score, clearStatus, combo, /*mode=*/0);
            for (RewardResource r : rewards) {
                if (r.getResourceType() == ResourceType.RESOURCE_TYPE_ITEM) {
                    grantItem(b, r.getResourceId(), r.getResourceCount());
                    notif.addItemDiffs(Item.newBuilder()
                            .setMasterId(r.getResourceId()).setAmount(r.getResourceCount()).build());
                }
            }
            ctx.players().savePlayerData(pid, b.build());
        }
        ctx.players().clearLive(pid);

        FinishFreeResponse.Builder resp = FinishFreeResponse.newBuilder().setNotification(notif.build());
        for (RewardResource r : rewards) {
            resp.addMusicRewards(r);
        }
        log.info("FinishFree profile={} music={} score={} rank={} rewards={}",
                pid, musicId, score, rank, rewards.size());
        respond(obs, resp.build());
    }

    public static void skipFree(ServerContext ctx, SkipFreeRequest request,
                                StreamObserver<SkipFreeResponse> obs) {
        long pid = currentProfile(ctx);
        long musicId = request.getMusicId();
        List<RewardResource> rewards = LiveRewards.freeRewards(ctx.masterData(), SKIP_RESULT_RANK);

        PlayerData pd = ctx.players().getPlayerData(pid);
        Notification.Builder notif = Notification.newBuilder();
        if (pd != null) {
            PlayerData.Builder b = pd.toBuilder();
            for (RewardResource r : rewards) {
                if (r.getResourceType() == ResourceType.RESOURCE_TYPE_ITEM) {
                    grantItem(b, r.getResourceId(), r.getResourceCount());
                    notif.addItemDiffs(Item.newBuilder()
                            .setMasterId(r.getResourceId()).setAmount(r.getResourceCount()).build());
                }
            }
            ctx.players().savePlayerData(pid, b.build());
        }
        log.info("SkipFree profile={} music={} rewards={}", pid, musicId, rewards.size());
        respond(obs, SkipFreeResponse.newBuilder().setNotification(notif.build()).build());
    }

    public static void saveSetting(ServerContext ctx, SaveSettingRequest request,
                                   StreamObserver<SaveSettingResponse> obs) {
        long pid = currentProfile(ctx);
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd != null) {
            ctx.players().savePlayerData(pid, pd.toBuilder()
                    .setLiveSetting(LiveSetting.newBuilder().setSettingAll(request.getSettingAll()).build())
                    .build());
        }
        respond(obs, SaveSettingResponse.getDefaultInstance());
    }

    // --- livemusic -----------------------------------------------------------

    public static void getRanking(ServerContext ctx, GetRankingRequest request,
                                  StreamObserver<GetRankingResponse> obs) {
        long pid = currentProfile(ctx);
        PlayerData pd = ctx.players().getPlayerData(pid);
        GetRankingResponse.Builder resp = GetRankingResponse.newBuilder().setMyRank(1);
        if (pd != null) {
            int best = bestScore(pd, request.getMusicId());
            resp.addPlayers(LiveRankingPlayer.newBuilder()
                    .setPlayerData(pd.getMyProfile())
                    .setScore(best)
                    .build());
        }
        respond(obs, resp.build());
    }

    // --- deck ----------------------------------------------------------------

    public static void saveDecks(ServerContext ctx, SaveDecksRequest request,
                                 StreamObserver<SaveDecksResponse> obs) {
        long pid = currentProfile(ctx);
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd != null && request.getDecksCount() > 0) {
            PlayerData.Builder b = pd.toBuilder().clearDecks().addAllDecks(request.getDecksList());
            if (request.getMainDeck() != 0) {
                b.setMainDeck(request.getMainDeck());
            }
            ctx.players().savePlayerData(pid, b.build());
        }
        respond(obs, SaveDecksResponse.getDefaultInstance());
    }

    // --- liveboost -----------------------------------------------------------

    public static void liveBoostRecovery(ServerContext ctx, LiveBoostRecoveryRequest request,
                                         StreamObserver<LiveBoostRecoveryResponse> obs) {
        long pid = currentProfile(ctx);
        PlayerData pd = ctx.players().getPlayerData(pid);
        int max = liveBoostByStar(ctx);
        LiveBoost boost = LiveBoost.newBuilder().setAmount(max).setPreviousRecoveryAt(now()).build();
        if (pd != null) {
            ctx.players().savePlayerData(pid, pd.toBuilder().setLiveBoost(boost).build());
        }
        respond(obs, LiveBoostRecoveryResponse.newBuilder().setLiveBoost(boost).build());
    }

    public static void liveBoostReqCheck(ServerContext ctx, com.google.protobuf.Empty request,
                                         StreamObserver<LiveBoostReqCheckResponse> obs) {
        int recovery = intParam(ctx, "live_boost_recovery_time", 1800);
        respond(obs, LiveBoostReqCheckResponse.newBuilder().setTimeLimit(recovery).build());
    }

    // --- helpers -------------------------------------------------------------

    private static long currentProfile(ServerContext ctx) {
        // Auto-register unknown client credentials so redirected sessions get a
        // complete player instead of an empty one.
        return ctx.players().resolveOrRegister(AuthInterceptor.CTX_AUTH_KEY.get());
    }

    private static PlayerData playerOrEmpty(ServerContext ctx, long pid) {
        PlayerData pd = ctx.players().getPlayerData(pid);
        return pd != null ? pd : PlayerData.getDefaultInstance();
    }

    /** Update (or append) the high score for a song/difficulty/mode if this run beats it. */
    private static void recordScore(PlayerData.Builder b, long musicId, int difficulty,
                                    int score, int clearStatus, int combo, int mode) {
        for (int i = 0; i < b.getLiveScoreCount(); i++) {
            LiveScore ls = b.getLiveScore(i);
            if (ls.getMusicId() == musicId && ls.getDifficulty() == difficulty && ls.getMode() == mode) {
                if (score > ls.getHighScore()) {
                    b.setLiveScore(i, ls.toBuilder()
                            .setHighScore(score)
                            .setClearStatus(Math.max(clearStatus, ls.getClearStatus()))
                            .setMaxCombo(Math.max(combo, ls.getMaxCombo()))
                            .build());
                }
                return;
            }
        }
        b.addLiveScore(LiveScore.newBuilder()
                .setMusicId(musicId).setDifficulty(difficulty).setMode(mode)
                .setHighScore(score).setClearStatus(clearStatus).setMaxCombo(combo)
                .build());
    }

    private static int bestScore(PlayerData pd, long musicId) {
        int best = 0;
        for (LiveScore ls : pd.getLiveScoreList()) {
            if (ls.getMusicId() == musicId) {
                best = Math.max(best, ls.getHighScore());
            }
        }
        return best;
    }

    /** Add {@code amount} of item {@code masterId} to the player's inventory. */
    private static void grantItem(PlayerData.Builder b, long masterId, int amount) {
        for (int i = 0; i < b.getItemsCount(); i++) {
            Item it = b.getItems(i);
            if (it.getMasterId() == masterId) {
                b.setItems(i, it.toBuilder().setAmount(it.getAmount() + amount).build());
                return;
            }
        }
        b.addItems(Item.newBuilder().setMasterId(masterId).setAmount(amount).build());
    }

    private static int liveBoostByStar(ServerContext ctx) {
        return intParam(ctx, "live_boost_by_star", 10);
    }

    private static int intParam(ServerContext ctx, String id, int fallback) {
        // MasterParameter is keyed by a string _id, so scan manually.
        for (var e : ctx.masterData().table("MasterParameter")) {
            if (!e.isJsonObject()) continue;
            var o = e.getAsJsonObject();
            var idEl = o.get("_id");
            if (idEl != null && id.equals(idEl.getAsString())) {
                try {
                    return Integer.parseInt(o.get("_value").getAsString().trim());
                } catch (RuntimeException ex) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private static <T> void respond(StreamObserver<T> obs, T msg) {
        obs.onNext(msg);
        obs.onCompleted();
    }
}
