package de.iani.playerUUIDCache.core;

public class SQLConfig {
    private final String host;
    private final String user;
    private final String password;
    private final String database;
    private final String tableName;
    private final String profilesTableName;
    private final String nameHistoriesTableName;
    private final String nameChangesTableName;

    public SQLConfig(String host, String user, String password, String database, String tableName, String profilesTableName, String nameHistoriesTableName, String nameChangesTableName) {
        this.host = host == null ? "localhost" : host;
        this.user = user == null ? "CHANGETHIS" : user;
        this.password = password == null ? "CHANGETHIS" : password;
        this.database = database == null ? "CHANGETHIS" : database;
        this.tableName = tableName == null ? "playeruuids" : tableName;
        this.profilesTableName = profilesTableName == null ? "playerprofiles" : profilesTableName;
        this.nameHistoriesTableName = nameHistoriesTableName == null ? "namehistories" : nameHistoriesTableName;
        this.nameChangesTableName = nameChangesTableName == null ? "namechanges" : nameChangesTableName;
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
        return tableName;
    }

    public String getProfilesTableName() {
        return profilesTableName;
    }

    public String getNameHistoriesTableName() {
        return nameHistoriesTableName;
    }

    public String getNameChangesTableName() {
        return nameChangesTableName;
    }
}
