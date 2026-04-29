package de.iani.playerUUIDCache.core;

public class CacheStats {
    private volatile int uuid2nameLookups;
    private volatile int name2uuidLookups;
    private volatile int nameHistoryLookups;
    private volatile int mojangQueries;
    private volatile int databaseUpdates;
    private volatile int databaseQueries;

    public int getUuid2nameLookups() {
        return uuid2nameLookups;
    }

    public int getName2uuidLookups() {
        return name2uuidLookups;
    }

    public int getNameHistoryLookups() {
        return nameHistoryLookups;
    }

    public int getMojangQueries() {
        return mojangQueries;
    }

    public int getDatabaseUpdates() {
        return databaseUpdates;
    }

    public int getDatabaseQueries() {
        return databaseQueries;
    }

    void incrementUuid2nameLookups(int amount) {
        uuid2nameLookups += amount;
    }

    void incrementName2uuidLookups(int amount) {
        name2uuidLookups += amount;
    }

    void incrementNameHistoryLookups() {
        nameHistoryLookups++;
    }

    void incrementMojangQueries() {
        mojangQueries++;
    }

    void incrementDatabaseUpdates() {
        databaseUpdates++;
    }

    void incrementDatabaseQueries() {
        databaseQueries++;
    }
}
