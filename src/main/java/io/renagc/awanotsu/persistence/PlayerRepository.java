package io.renagc.awanotsu.persistence;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

/**
 * Mongo persistence for full player state. Each player is one document in the
 * {@code players} collection:
 *
 * <pre>{ _id: &lt;profileId long&gt;, credential: &lt;string&gt;, data: &lt;Binary protobuf PlayerData&gt; }</pre>
 *
 * <p>SOFT-FAIL: when Mongo is not connected ({@link DatabaseManager#isConnected()}
 * is false) every method is a no-op / returns empty, and {@link PlayerStore}
 * keeps everything purely in memory. Persistence is therefore best-effort and
 * never blocks the login → home → play → result flow on this dev host.
 */
public final class PlayerRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerRepository.class);
    private static final String COLLECTION = "players";

    private final MongoCollection<Document> col; // null when Mongo is absent

    public PlayerRepository(DatabaseManager db) {
        MongoCollection<Document> c = null;
        if (db != null && db.isConnected() && db.getDatabase() != null) {
            try {
                c = db.getDatabase().getCollection(COLLECTION);
                log.info("PlayerRepository: persisting players to Mongo collection '{}'.", COLLECTION);
            } catch (RuntimeException e) {
                log.warn("PlayerRepository: could not open '{}' ({}); memory-only.", COLLECTION, e.toString());
            }
        } else {
            log.info("PlayerRepository: Mongo absent — player state is memory-only.");
        }
        this.col = c;
    }

    public boolean enabled() {
        return col != null;
    }

    /** Upsert one player document (no-op when Mongo is absent). */
    public void save(long profileId, String credential, byte[] data) {
        if (col == null) return;
        try {
            Document doc = new Document("_id", profileId)
                    .append("credential", credential)
                    .append("data", new Binary(data));
            col.replaceOne(eq("_id", profileId), doc, new ReplaceOptions().upsert(true));
        } catch (RuntimeException e) {
            log.warn("PlayerRepository.save({}) failed: {}", profileId, e.toString());
        }
    }

    /** Load every stored player (empty when Mongo is absent). */
    public List<StoredPlayer> loadAll() {
        List<StoredPlayer> out = new ArrayList<>();
        if (col == null) return out;
        try {
            for (Document d : col.find()) {
                Long id = d.getLong("_id");
                String cred = d.getString("credential");
                Object data = d.get("data");
                byte[] bytes = data instanceof Binary ? ((Binary) data).getData() : null;
                if (id != null && cred != null && bytes != null) {
                    out.add(new StoredPlayer(id, cred, bytes));
                }
            }
            log.info("PlayerRepository: restored {} player(s) from Mongo.", out.size());
        } catch (RuntimeException e) {
            log.warn("PlayerRepository.loadAll failed: {}", e.toString());
        }
        return out;
    }

    /** One persisted player row. */
    public record StoredPlayer(long profileId, String credential, byte[] data) {
    }
}
