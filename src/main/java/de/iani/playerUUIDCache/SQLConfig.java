package de.iani.playerUUIDCache;

import org.bukkit.configuration.ConfigurationSection;

public class SQLConfig {
    private String host = "localhost";

    private String user = "CHANGETHIS";

    private String password = "CHANGETHIS";

    private String database = "CHANGETHIS";

    private String tablename = "playeruuids";

    private String profilestablename = "playerprofiles";

    private String namehistoriestablename = "namehistories";

    private String namechangestablename = "namechanges";

    public SQLConfig(ConfigurationSection section) {
        if (section != null) {
            host = section.getString("host", host);
            user = section.getString("user", user);
            password = section.getString("password", password);
            database = section.getString("database", database);
            tablename = section.getString("tablename", tablename);
            profilestablename = section.getString("profilestablename", profilestablename);
            namehistoriestablename = section.getString("namehistoriestablename", namehistoriestablename);
            namechangestablename = section.getString("namechangestablename", namechangestablename);
        }
    }

    public String getHost() {
        return host;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabase() {
        return database;
    }

    public String getTableName() {
        return tablename;
    }

    public String getProfilesTableName() {
        return profilestablename;
    }

    public String getNameHistoriesTableName() {
        return namehistoriestablename;
    }

    public String getNameChangesTableName() {
        return namechangestablename;
    }
}
