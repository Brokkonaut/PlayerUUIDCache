package de.iani.playerUUIDCache.core;

public class CacheConfig {
    private final long memoryCacheExpirationTime;
    private final long nameHistoryCacheExpirationTime;
    private final boolean useSQL;
    private final SQLConfig sqlConfig;

    public CacheConfig(long memoryCacheExpirationTime, long nameHistoryCacheExpirationTime, boolean useSQL, SQLConfig sqlConfig) {
        this.memoryCacheExpirationTime = memoryCacheExpirationTime;
        this.nameHistoryCacheExpirationTime = nameHistoryCacheExpirationTime;
        this.useSQL = useSQL;
        this.sqlConfig = sqlConfig;
    }

    public long getMemoryCacheExpirationTime() {
        return memoryCacheExpirationTime;
    }

    public long getNameHistoryCacheExpirationTime() {
        return nameHistoryCacheExpirationTime;
    }

    public boolean useSQL() {
        return useSQL;
    }

    public SQLConfig getSqlConfig() {
        return sqlConfig;
    }
}
