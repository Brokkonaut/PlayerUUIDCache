package de.iani.playerUUIDCache;

import org.bukkit.configuration.ConfigurationSection;

public class SQLConfig extends de.iani.playerUUIDCache.core.SQLConfig {
    public SQLConfig(ConfigurationSection section) {
        super(
                getString(section, "host"),
                getString(section, "user"),
                getString(section, "password"),
                getString(section, "database"),
                getString(section, "tablename"),
                getString(section, "profilestablename"),
                getString(section, "namehistoriestablename"),
                getString(section, "namechangestablename"));
    }

    private static String getString(ConfigurationSection section, String path) {
        return section == null ? null : section.getString(path);
    }
}
