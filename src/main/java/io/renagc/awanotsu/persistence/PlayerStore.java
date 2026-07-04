package io.renagc.awanotsu.persistence;

import com.google.protobuf.InvalidProtocolBufferException;
import io.renagc.awanotsu.proto.common.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

/**
 * Player registry backing the login → home → play → result flow. Maps an issued
 * {@code credential} (the {@code _authKey}) to a {@code profileId}, holds the full
 * {@link PlayerData} per player, and tracks the transient {@link LiveSession} of a
 * live in progress.
 *
 * <p>State lives in memory and is written through to Mongo via
 * {@link PlayerRepository} when a database is connected (soft-fail). On boot any
 * persisted players are restored so credentials survive a restart.
 *
 * <p>The fresh-player template is supplied as a {@code LongFunction<PlayerData>}
 * (profileId → fresh PlayerData) injected at construction, so this persistence
 * class does not depend on the flow/master layer that builds it.
 */
public final class PlayerStore {

    private static final Logger log = LoggerFactory.getLogger(PlayerStore.class);

    private final PlayerRepository repo;
    private final LongFunction<PlayerData> freshPlayerBuilder;

    private final ConcurrentHashMap<String, Long> credentialToProfile = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> profileToCredential = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PlayerData> playerData = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LiveSession> liveSessions = new ConcurrentHashMap<>();
    private final AtomicLong nextProfileId = new AtomicLong(10_000_000L);

    public PlayerStore(PlayerRepository repo, LongFunction<PlayerData> freshPlayerBuilder) {
        this.repo = repo;
        this.freshPlayerBuilder = freshPlayerBuilder;
        restore();
    }

    /** Restore persisted players (if any) so credentials survive a restart. */
    private void restore() {
        if (repo == null) return;
        long maxId = nextProfileId.get() - 1;
        for (PlayerRepository.StoredPlayer sp : repo.loadAll()) {
            try {
                PlayerData pd = PlayerData.parseFrom(sp.data());
                credentialToProfile.put(sp.credential(), sp.profileId());
                profileToCredential.put(sp.profileId(), sp.credential());
                playerData.put(sp.profileId(), pd);
                maxId = Math.max(maxId, sp.profileId());
            } catch (InvalidProtocolBufferException e) {
                log.warn("Skipping unreadable stored player {}: {}", sp.profileId(), e.toString());
            }
        }
        nextProfileId.set(maxId + 1);
    }

    /** Issue a fresh credential + profileId pair and build the fresh PlayerData. */
    public Registration register() {
        String credential = UUID.randomUUID().toString().replace("-", "");
        long profileId = nextProfileId.getAndIncrement();
        PlayerData fresh = freshPlayerBuilder.apply(profileId);

        credentialToProfile.put(credential, profileId);
        profileToCredential.put(profileId, credential);
        playerData.put(profileId, fresh);
        persist(profileId);

        log.info("Registered new player: profileId={} credential={}… cards={} decks={} songs={}",
                profileId, credential.substring(0, 8),
                fresh.getMemberCardsCount(), fresh.getDecksCount(), fresh.getLiveMusicCount());
        return new Registration(credential, profileId);
    }

    /** Resolve a credential to a profileId, or 0 if unknown. */
    public long resolveProfile(String credential) {
        if (credential == null) return 0L;
        Long id = credentialToProfile.get(credential);
        return id == null ? 0L : id;
    }

    /**
     * Resolve a credential to a profileId, AUTO-REGISTERING a fresh complete player when the
     * credential is unknown. Redirected sessions may arrive with credentials that were
     * never Register()'d here; without this they would get an empty default player and
     * bounce with a comm error. A null/empty credential maps to a single shared default player so the client
     * still loads a full account. Atomic + idempotent: a credential always resolves to one profile.
     */
    public long resolveOrRegister(String credential) {
        String key = (credential == null || credential.isEmpty()) ? "__default__" : credential;
        return credentialToProfile.computeIfAbsent(key, k -> {
            long profileId = nextProfileId.getAndIncrement();
            PlayerData fresh = freshPlayerBuilder.apply(profileId);
            profileToCredential.put(profileId, k);
            playerData.put(profileId, fresh);
            persist(profileId);
            log.info("Auto-registered client credential -> profileId={} cards={} songs={}",
                    profileId, fresh.getMemberCardsCount(), fresh.getLiveMusicCount());
            return profileId;
        });
    }

    /** Full PlayerData for a profile, or null if unknown. */
    public PlayerData getPlayerData(long profileId) {
        return playerData.get(profileId);
    }

    /** Replace + persist a player's PlayerData. */
    public void savePlayerData(long profileId, PlayerData data) {
        playerData.put(profileId, data);
        persist(profileId);
    }

    private void persist(long profileId) {
        if (repo == null || !repo.enabled()) return;
        PlayerData pd = playerData.get(profileId);
        String cred = profileToCredential.get(profileId);
        if (pd != null && cred != null) {
            repo.save(profileId, cred, pd.toByteArray());
        }
    }

    // --- transient live session (StartFree → FinishFree) ---------------------

    public void startLive(long profileId, LiveSession session) {
        liveSessions.put(profileId, session);
    }

    public LiveSession getLive(long profileId) {
        return liveSessions.get(profileId);
    }

    public void clearLive(long profileId) {
        liveSessions.remove(profileId);
    }

    public int size() {
        return credentialToProfile.size();
    }

    /**
     * The most-recently-registered profileId (the highest), or 0 if none. Operator
     * console commands target this so a grant hits the LIVE client's player (it
     * registers last) rather than the auto-created default.
     */
    public long activeProfile() {
        long max = 0L;
        for (Long id : playerData.keySet()) {
            if (id != null && id > max) max = id;
        }
        return max;
    }

    /** Result of {@link #register()}. */
    public record Registration(String credential, long profileId) {
    }
}
