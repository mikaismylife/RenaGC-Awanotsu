package io.renagc.awanotsu.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.renagc.awanotsu.master.MasterDataStore;
import io.renagc.awanotsu.proto.common.ResourceType;
import io.renagc.awanotsu.proto.common.RewardResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Live-result scoring + reward derivation from the real master DB.
 *
 * <p>Score rank: a song carries a {@code _liveScoreRankGroup} in {@code MasterLiveMusic};
 * {@code MasterLiveScoreRank} rows for that group map an achieved {@code score} to the
 * highest {@code _liveScoreRank} whose {@code _requiredScore} is met (ranks 2..7 ≈ C..SSS).
 *
 * <p>Result reward: per-clear currency comes from {@code MasterLiveFreeReward}. The table
 * is keyed by a small {@code _group} (1..8) whose exact per-song mapping is not recovered,
 * so this uses group {@value #FREE_REWARD_GROUP} (documented assumption) and grants the
 * row matching the achieved score rank.
 */
public final class LiveRewards {

    private static final int FREE_REWARD_GROUP = 1;
    private static final int RANK_C = 2; // lowest "cleared" rank

    private LiveRewards() {
    }

    /** Highest achieved {@code _liveScoreRank} for {@code score} on {@code musicId}. */
    public static int scoreRank(MasterDataStore master, long musicId, int score) {
        JsonObject music = master.findRow("MasterLiveMusic", "_id", musicId);
        if (music == null) {
            return RANK_C;
        }
        long group = asLong(music, "_liveScoreRankGroup");
        int best = RANK_C;
        for (JsonElement e : master.table("MasterLiveScoreRank")) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            if (asLong(r, "_group") != group) continue;
            long required = asLong(r, "_requiredScore");
            int rank = (int) asLong(r, "_liveScoreRank");
            if (score >= required && rank > best) {
                best = rank;
            }
        }
        return best;
    }

    /**
     * Per-clear reward resources for an achieved score rank. Returns the
     * {@code MasterLiveFreeReward} row(s) (group {@value #FREE_REWARD_GROUP}) whose
     * {@code _liveScoreRank} equals {@code achievedRank}; falls back to the lowest rank.
     */
    public static List<RewardResource> freeRewards(MasterDataStore master, int achievedRank) {
        List<RewardResource> out = new ArrayList<>();
        JsonObject match = null;
        JsonObject lowest = null;
        int lowestRank = Integer.MAX_VALUE;
        for (JsonElement e : master.table("MasterLiveFreeReward")) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            if (asLong(r, "_group") != FREE_REWARD_GROUP) continue;
            int rank = (int) asLong(r, "_liveScoreRank");
            if (rank == achievedRank) {
                match = r;
            }
            if (rank < lowestRank) {
                lowestRank = rank;
                lowest = r;
            }
        }
        JsonObject chosen = match != null ? match : lowest;
        if (chosen != null) {
            out.add(toReward(chosen));
        }
        return out;
    }

    private static RewardResource toReward(JsonObject r) {
        return RewardResource.newBuilder()
                .setResourceType(resourceType((int) asLong(r, "_resourceType")))
                .setResourceId(asLong(r, "_resourceId"))
                .setResourceCount((int) asLong(r, "_resourceCount"))
                .build();
    }

    private static ResourceType resourceType(int n) {
        ResourceType t = ResourceType.forNumber(n);
        return t != null ? t : ResourceType.RESOURCE_TYPE_ITEM;
    }

    private static long asLong(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() || !v.isJsonPrimitive() ? 0 : v.getAsLong();
    }
}
