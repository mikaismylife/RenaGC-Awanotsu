package io.renagc.awanotsu.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.renagc.awanotsu.master.MasterDataStore;
import io.renagc.awanotsu.proto.common.BandItem;
import io.renagc.awanotsu.proto.common.Deck;
import io.renagc.awanotsu.proto.common.DeckCard;
import io.renagc.awanotsu.proto.common.DeckMemberCardDetail;
import io.renagc.awanotsu.proto.common.Gem;
import io.renagc.awanotsu.proto.common.Item;
import io.renagc.awanotsu.proto.common.LiveBoost;
import io.renagc.awanotsu.proto.common.LiveLaneSkin;
import io.renagc.awanotsu.proto.common.LiveMusic;
import io.renagc.awanotsu.proto.common.LiveNoteEffectSkin;
import io.renagc.awanotsu.proto.common.LiveNoteSeGroup;
import io.renagc.awanotsu.proto.common.LiveNoteSkin;
import io.renagc.awanotsu.proto.common.MemberCard;
import io.renagc.awanotsu.proto.common.PlayerData;
import io.renagc.awanotsu.proto.common.PlayerSimpleProfile;
import io.renagc.awanotsu.proto.common.Stamp;
import io.renagc.awanotsu.proto.common.SupportCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the fresh-player {@link PlayerData} a brand-new account starts with, derived
 * from the real decrypted master DB.
 *
 * <p>Source of truth = {@code MasterPlayerData} (the canonical initial-grant template).
 * Its rows are grouped by {@code _playerDataType}:
 * <ul>
 *   <li><b>type 1</b> → a granted <b>member card</b>: {@code _parameter1} = the
 *       {@code MasterMemberCard._id} to grant ({@code _parameter2} = exp).</li>
 *   <li><b>type 2</b> → a granted <b>support card</b>: {@code _parameter1} =
 *       {@code MasterSupportCard._id}.</li>
 *   <li><b>type 5</b> → a <b>deck</b>: {@code _parameter0} = deck id, {@code _parameter1}
 *       = name, {@code _parameter2} = comma-separated member-card slot order
 *       (e.g. {@code "2,4,1,5,3"}, {@code -1} = empty slot).</li>
 * </ul>
 * The {@code "Default"} group is used. Member/support cards are given
 * player-local ids 1..N in template order;
 * the deck slot numbers reference those local ids.
 *
 * <p><b>Documented defaults</b> (values the template does not specify, chosen so the
 * client loads to home and can play): card_rank = 1, all skill levels = 1, awake = 0;
 * gem.free = 114514 starter; live_boost.amount = {@code live_boost_by_star} (master
 * param, default 67); ALL {@code MasterLiveMusic} songs unlocked (custom server);
 * profile name = {@code "Rena"}. Timestamps are Unix epoch SECONDS.
 *
 * <p><b>Initial-ownership depth</b> (derived from the master DB so Home is realistic,
 * not just the card roster): every row flagged {@code _isInitialOwnership=true} is
 * granted — {@code MasterStamp} (20 stamps), {@code MasterLiveLaneSkin},
 * {@code MasterLiveNoteSkin}, {@code MasterLiveNoteEffectSkin},
 * {@code MasterLiveNoteSeGroup}. All {@code MasterBandItem} rows (no flag in source)
 * are granted at level 1. A soft-currency starter ({@code MasterItem._id=3}, SC) is
 * added so the inventory is non-empty.
 */
public final class FreshPlayerFactory {

    private static final Logger log = LoggerFactory.getLogger(FreshPlayerFactory.class);

    private static final String DEFAULT_GROUP = "Default";
    private static final int TYPE_MEMBER_CARD = 1;
    private static final int TYPE_SUPPORT_CARD = 2;
    private static final int TYPE_DECK = 5;

    private static final String DEFAULT_NAME = "Rena";
    private static final int STARTER_GEMS = 114514;
    private static final int DEFAULT_LIVE_BOOST = 67;

    /** Soft-currency (coins) item master id ({@code MasterItem._id=3}, ItemType.SC) and starter amount. */
    private static final long SC_ITEM_MASTER_ID = 3;
    private static final int STARTER_SC = 100_000;
    /** Initial band-item level (template grants none; server grants all at level 1 so the band screen is non-empty). */
    private static final int DEFAULT_BAND_ITEM_LEVEL = 1;

    private FreshPlayerFactory() {
    }

    public static PlayerData build(MasterDataStore master, long profileId) {
        long now = System.currentTimeMillis() / 1000L;
        PlayerData.Builder pd = PlayerData.newBuilder();

        JsonArray template = master.table("MasterPlayerData");
        int memberLocalId = 0;
        int supportLocalId = 0;
        long firstMemberMasterId = 0;

        for (JsonElement e : template) {
            if (!e.isJsonObject()) continue;
            JsonObject row = e.getAsJsonObject();
            if (!DEFAULT_GROUP.equals(str(row, "_group"))) continue;
            int type = (int) num(row, "_playerDataType");
            switch (type) {
                case TYPE_MEMBER_CARD -> {
                    int localId = ++memberLocalId;
                    long masterId = parseLong(str(row, "_parameter1"), 0);
                    if (firstMemberMasterId == 0) firstMemberMasterId = masterId;
                    // Keep awake_count=1 and leader_skill_level unset (=0). Sending a
                    // leader skill for cards that do not define one can crash the home flow.
                    pd.addMemberCards(MemberCard.newBuilder()
                            .setId(localId)
                            .setMasterId(masterId)
                            .setExp((int) parseLong(str(row, "_parameter2"), 0))
                            .setAwakeCount(1)
                            .setCardRank(1)
                            // leader_skill_level intentionally left 0.
                            .setLiveSkillLevel(1)
                            .setPerformanceSkillLevel(1)
                            .setLinkSkillLevel(1)
                            .setGekisouSkillLevel(1)
                            .setGainAt(now)
                            .build());
                }
                case TYPE_SUPPORT_CARD -> {
                    int localId = ++supportLocalId;
                    long masterId = parseLong(str(row, "_parameter1"), 0);
                    pd.addSupportCards(SupportCard.newBuilder()
                            .setId(localId)
                            .setMasterId(masterId)
                            .setExp((int) parseLong(str(row, "_parameter2"), 0))
                            .setCardRank(1)
                            .setDuplicateCount(0)
                            .setGainAt(now)
                            .build());
                }
                case TYPE_DECK -> pd.addDecks(buildDeck(row));
                default -> { /* ignore unknown template types */ }
            }
        }

        // All songs unlocked on this custom server (ignore startAt / unlock conditions).
        // NOTE: live_music is stripped from the lean GetPlayerData (Flows.getPlayerData), so
        // this is internal player state — it must NOT be re-added to that response (doing so
        // reintroduces the title->home boot crash).
        JsonArray music = master.table("MasterLiveMusic");
        for (JsonElement e : music) {
            if (!e.isJsonObject()) continue;
            long musicId = num(e.getAsJsonObject(), "_id");
            if (musicId != 0) {
                pd.addLiveMusic(LiveMusic.newBuilder().setMusicId(musicId).setGotAt(now).build());
            }
        }

        // AP-all is applied on demand via the /apall console command (not baked into
        // the fresh player) so the boot/home flow stays byte-identical to the
        // known-good baseline while we isolate the device-side ACE init kill.

        // Initial-ownership cosmetics + stamps: every master row flagged
        // _isInitialOwnership=true is granted to a fresh account (verified in dump:
        // MasterStamp 1..20, MasterLiveLaneSkin 1, MasterLiveNoteSkin 1,
        // MasterLiveNoteEffectSkin 1, MasterLiveNoteSeGroup 1..2).
        for (long id : initialOwnershipIds(master, "MasterStamp")) {
            pd.addStamps(Stamp.newBuilder().setId(id).setGotAt(now).build());
        }
        for (long id : initialOwnershipIds(master, "MasterLiveLaneSkin")) {
            pd.addLiveLaneSkins(LiveLaneSkin.newBuilder().setMasterId(id).build());
        }
        for (long id : initialOwnershipIds(master, "MasterLiveNoteSkin")) {
            pd.addLiveNoteSkins(LiveNoteSkin.newBuilder().setMasterId(id).build());
        }
        for (long id : initialOwnershipIds(master, "MasterLiveNoteEffectSkin")) {
            pd.addLiveNoteEffectSkins(LiveNoteEffectSkin.newBuilder().setMasterId(id).build());
        }
        for (long id : initialOwnershipIds(master, "MasterLiveNoteSeGroup")) {
            pd.addLiveNoteSeGroups(LiveNoteSeGroup.newBuilder().setMasterId(id).build());
        }

        // Band items: MasterBandItem has no initial-ownership flag, so grant all 10
        // rows (band1 101..105, band2 201..205) at level 1 for a non-empty band screen.
        for (JsonElement e : master.table("MasterBandItem")) {
            if (!e.isJsonObject()) continue;
            long id = num(e.getAsJsonObject(), "_id");
            if (id != 0) {
                pd.addBandItems(BandItem.newBuilder().setMasterId(id).setLevel(DEFAULT_BAND_ITEM_LEVEL).build());
            }
        }

        // Soft-currency starter (server choice; MasterPlayerData grants no items and
        // there is no login-bonus/mission/gacha source in the dec tables).
        pd.addItems(Item.newBuilder().setMasterId(SC_ITEM_MASTER_ID).setAmount(STARTER_SC).build());

        int liveBoost = liveBoostMax(master);
        pd.setMainDeck(1)
                .setPlayerRankExp(0)
                .setGem(Gem.newBuilder().setFree(STARTER_GEMS).setPaid(0).build())
                .setLiveBoost(LiveBoost.newBuilder()
                        .setAmount(liveBoost)
                        .setPreviousRecoveryAt(now)
                        .setLastDailyResetAt(now)
                        .build())
                .setMyProfile(PlayerSimpleProfile.newBuilder()
                        .setId(String.valueOf(profileId))
                        .setProfileId(profileId)
                        .setName(DEFAULT_NAME)
                        .setRankExp(0)
                        .setLastUpdatedAt(now)
                        .setFavoriteMemberCardMasterId(firstMemberMasterId)
                        .setFavoriteMemberCard(DeckMemberCardDetail.newBuilder()
                                .setCardId(firstMemberMasterId)
                                .setAwakeCount(1)
                                .setCardRank(1)
                                .setLiveSkillLevel(1)
                                .setPerformanceSkillLevel(1)
                                .build())
                        .build());

        // Populate the singular message sub-objects so the client never dereferences a
        // null during boot (LoadOrCreate touches these; an unset proto3 message = null).
        pd.setLiveSkip(io.renagc.awanotsu.proto.common.LiveSkip.newBuilder().setLastSkip(0).build())
                .setPlayerMissionData(io.renagc.awanotsu.proto.common.PlayerMissionData.getDefaultInstance())
                .setLiveSetting(io.renagc.awanotsu.proto.common.LiveSetting.getDefaultInstance())
                .setLiveStampReward(io.renagc.awanotsu.proto.common.LiveStampReward.newBuilder().setLastLiveCompletedAt(0).build())
                .setComeback(io.renagc.awanotsu.proto.common.Comeback.newBuilder().setLastLoginDate(now).build())
                .setCircleId(0);

        PlayerData built = pd.build();
        log.info("Built fresh player {}: {} member, {} support, {} decks, {} songs, {} live-boost, "
                        + "{} stamps, {} band-items, {} items.",
                profileId, built.getMemberCardsCount(), built.getSupportCardsCount(),
                built.getDecksCount(), built.getLiveMusicCount(), liveBoost,
                built.getStampsCount(), built.getBandItemsCount(), built.getItemsCount());
        return built;
    }

    private static Deck buildDeck(JsonObject row) {
        int deckId = (int) parseLong(str(row, "_parameter0"), 0);
        Deck.Builder deck = Deck.newBuilder()
                .setId(deckId)
                .setName(str(row, "_parameter1"));
        String[] slots = str(row, "_parameter2").split(",");
        // Every deck gets 5 fully-populated slots. The home transition renders the main
        // deck's member and support card per slot and looks the support card up by id; a
        // slot with support_card_id=0 (or an entirely empty deck) resolves to a null
        // support card and crashes boot with a NullReferenceException.
        for (int i = 0; i < 5; i++) {
            long memberLocalId = (i < slots.length) ? parseLong(slots[i].trim(), -1) : -1;
            if (memberLocalId <= 0) memberLocalId = i + 1;          // fill empties with cards 1..5
            deck.addCards(DeckCard.newBuilder()
                    .setSlotIndex(i)
                    .setPerformanceOrderIndex(i)
                    .setMemberCardId(memberLocalId)
                    .setSupportCardId(memberLocalId)               // parallel local ids -> valid support card
                    .build());
        }
        return deck.build();
    }

    /** Collect {@code _id} of every row in {@code tableName} flagged {@code _isInitialOwnership=true}. */
    private static java.util.List<Long> initialOwnershipIds(MasterDataStore master, String tableName) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (JsonElement e : master.table(tableName)) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement flag = o.get("_isInitialOwnership");
            if (flag != null && !flag.isJsonNull() && flag.getAsBoolean()) {
                long id = num(o, "_id");
                if (id != 0) ids.add(id);
            }
        }
        return ids;
    }

    /** {@code live_boost_by_star} master param, or {@value #DEFAULT_LIVE_BOOST}. */
    private static int liveBoostMax(MasterDataStore master) {
        for (JsonElement e : master.table("MasterParameter")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if ("live_boost_by_star".equals(str(o, "_id"))) {
                return (int) parseLong(str(o, "_value"), DEFAULT_LIVE_BOOST);
            }
        }
        return DEFAULT_LIVE_BOOST;
    }

    private static String str(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() ? "" : v.getAsString();
    }

    private static long num(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() || !v.isJsonPrimitive() ? 0 : v.getAsLong();
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
