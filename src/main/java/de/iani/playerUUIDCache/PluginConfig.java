package de.iani.playerUUIDCache;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {
    private final long memoryCacheExpirationTime;

    private final boolean useSQL;

    private final SQLConfig sqlConfig;

    public PluginConfig(PlayerUUIDCache plugin) {
        FileConfiguration config = plugin.getConfig();
        useSQL = config.getBoolean("useSQL");
        memoryCacheExpirationTime = !useSQL ? -1 : config.getLong("memoryCacheExpirationTime");
        sqlConfig = useSQL ? new SQLConfig(config.getConfigurationSection("database")) : null;
    }

    public long getMemoryCacheExpirationTime() {
        return memoryCacheExpirationTime;
    }

    public boolean useSQL() {
        return useSQL;
    }

    public SQLConfig getSqlConfig() {
        return sqlConfig;
    }
}
