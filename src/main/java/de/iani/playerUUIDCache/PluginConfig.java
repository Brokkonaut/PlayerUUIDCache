package de.iani.playerUUIDCache;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {
    private final long memoryCacheExpirationTime;

    private final long nameHistoryCacheExpirationTime;

    private final boolean useSQL;

    private final SQLConfig sqlConfig;

    public PluginConfig(PlayerUUIDCache plugin) {
        FileConfiguration config = plugin.getConfig();
        if (config.get("nameHistoryCacheExpirationTime", null) == null) {
            config.set("nameHistoryCacheExpirationTime", 1000L * 60 * 60 * 24 * 30);
            plugin.saveConfig();
        }
        useSQL = config.getBoolean("useSQL");
        memoryCacheExpirationTime = !useSQL ? -1 : config.getLong("memoryCacheExpirationTime");
        nameHistoryCacheExpirationTime = config.getLong("nameHistoryCacheExpirationTime", 1000L * 60 * 60 * 24 * 30); // 30 days
        sqlConfig = useSQL ? new SQLConfig(config.getConfigurationSection("database")) : null;
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
