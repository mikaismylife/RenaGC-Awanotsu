package io.renagc.awanotsu.persistence;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB bootstrap. A reachable Mongo instance is REQUIRED — {@link #tryConnect}
 * returns {@code false} on failure and the caller ({@code RenaGC.main}) refuses to
 * launch the server (no memory-only fallback), matching upstream Grasscutter.
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private MongoClient client;
    private MongoDatabase database;
    private boolean connected;

    /** Attempt to connect; returns true on success, false on soft-fail. */
    public boolean tryConnect(String uri) {
        if (uri == null || uri.isBlank()) {
            log.error("No mongo URI configured (config.mongo). MongoDB is required.");
            return false;
        }
        try {
            com.mongodb.ConnectionString cs = new com.mongodb.ConnectionString(uri);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(cs)
                    .applyToClusterSettings(b ->
                            b.serverSelectionTimeout(5000, TimeUnit.MILLISECONDS))
                    .build();
            MongoClient c = MongoClients.create(settings);
            String dbName = cs.getDatabase() != null ? cs.getDatabase() : "renagc";
            MongoDatabase db = c.getDatabase(dbName);
            // Force a real round-trip so we actually detect an absent server.
            db.runCommand(new Document("ping", 1));

            this.client = c;
            this.database = db;
            this.connected = true;
            log.info("Connected to MongoDB database '{}'.", dbName);
            return true;
        } catch (MongoTimeoutException e) {
            log.error("MongoDB not reachable ({}).", e.getMessage());
        } catch (RuntimeException e) {
            log.error("MongoDB init failed ({}).", e.toString());
        }
        this.connected = false;
        return false;
    }

    public boolean isConnected() {
        return connected;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // best-effort
            }
        }
    }
}
