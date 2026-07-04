package io.renagc.awanotsu;

import io.renagc.awanotsu.master.MasterDataStore;
import io.renagc.awanotsu.persistence.DatabaseManager;
import io.renagc.awanotsu.persistence.PlayerStore;

/**
 * Process-wide shared state handed to every gRPC service impl at construction.
 * Keeps the generated *ServiceImpl constructors uniform (single arg).
 */
public final class ServerContext {

    private final Config config;
    private final PlayerStore playerStore;
    private final DatabaseManager database;
    private final MasterDataStore masterData;

    public ServerContext(Config config, PlayerStore playerStore, DatabaseManager database,
                         MasterDataStore masterData) {
        this.config = config;
        this.playerStore = playerStore;
        this.database = database;
        this.masterData = masterData;
    }

    public Config config() {
        return config;
    }

    public PlayerStore players() {
        return playerStore;
    }

    public DatabaseManager database() {
        return database;
    }

    public MasterDataStore masterData() {
        return masterData;
    }
}
