package de.iani.playerUUIDCache;

import com.destroystokyo.paper.profile.ProfileProperty;
import de.iani.playerUUIDCache.NameHistory.NameChange;
import de.iani.playerUUIDCache.util.sql.MySQLConnection;
import de.iani.playerUUIDCache.util.sql.SQLConnection;
import de.iani.playerUUIDCache.util.sql.SQLUtil;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class UUIDDatabase {
    private final SQLConnection connection;

    private final String tableName;

    private final String profilesTableName;

    private final String nameHistoriesTableName;

    private final String nameChangesTableName;

    private final String insertPlayer;

    private final String selectPlayerByUUID;

    private final String selectPlayerByName;

    private final String searchPlayersByPartialName;

    private final String selectAllPlayers;

    private final String insertPlayerProfile;

    private final String selectPlayerProfileByUUID;

    private final String deleteOldPlayerProfiles;

    private boolean mayUseProfilesTable;

    private final String insertNameHistory;

    private final String updateNameHistoryRefreshed;

    private final String insertNameChange;

    private final String selectNameHistory;

    private final String selectNameChanges;

    private final String selectNameUsers;

    public UUIDDatabase(SQLConfig config) throws SQLException {
        connection = new MySQLConnection(config.getHost(), config.getDatabase(), config.getUser(), config.getPassword());
        tableName = config.getTableName();
        profilesTableName = config.getProfilesTableName();
        nameHistoriesTableName = config.getNameHistoriesTableName();
        nameChangesTableName = config.getNameChangesTableName();

        insertPlayer = "INSERT INTO " + tableName + " (uuid, name, lastSeen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, lastSeen = ?";

        selectPlayerByUUID = "SELECT name, lastSeen FROM " + tableName + " WHERE uuid = ?";

        selectPlayerByName = "SELECT uuid, name, lastSeen FROM " + tableName + " WHERE name = ?";

        searchPlayersByPartialName = "SELECT uuid, name, lastSeen FROM " + tableName + " WHERE name LIKE ? ORDER BY lastSeen DESC";

        selectAllPlayers = "SELECT uuid, name, lastSeen FROM " + tableName;

        this.connection.runCommands((connection, sqlConnection) -> {
            if (!sqlConnection.hasTable(tableName)) {
                Statement smt = connection.createStatement();
                smt.executeUpdate("CREATE TABLE `" + tableName + "` ("//
                        + "`uuid` CHAR( 36 ) NOT NULL,"//
                        + "`name` VARCHAR( 100 ) NOT NULL ,"//
                        + "`lastSeen` BIGINT NOT NULL DEFAULT '0',"//
                        + "PRIMARY KEY ( `uuid` ), INDEX ( `name` ) ) ENGINE = innodb");
                smt.close();
            }
            return null;
        });

        insertPlayerProfile = "INSERT INTO " + profilesTableName + " (uuid, profile, lastSeen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE profile = ?, lastSeen = ?";

        selectPlayerProfileByUUID = "SELECT profile, lastSeen FROM " + profilesTableName + " WHERE uuid = ?";

        deleteOldPlayerProfiles = "DELETE FROM " + profilesTableName + " WHERE lastSeen < ?";

        insertNameHistory = "INSERT INTO " + nameHistoriesTableName + " (uuid, firstName, refreshed) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE refreshed = ?";

        updateNameHistoryRefreshed = "UPDATE " + nameHistoriesTableName + " SET refreshed = ? WHERE uuid = ?";

        insertNameChange = "INSERT IGNORE INTO " + nameChangesTableName + " (uuid, date, newName) VALUES (?, ?, ?)";

        selectNameHistory = "SELECT firstName, refreshed FROM " + nameHistoriesTableName + " WHERE uuid = ?";

        selectNameChanges = "SELECT date, newName FROM " + nameChangesTableName + " WHERE uuid = ?";

        selectNameUsers = "SELECT uuid, date FROM ((SELECT uuid, 0 AS date FROM " + nameHistoriesTableName + " WHERE firstName = ?) UNION (SELECT uuid, lastSeen AS date FROM " + tableName + " WHERE name = ?) UNION (SELECT uuid, date FROM " + nameChangesTableName
                + " WHERE newName = ?)) AS t ORDER BY date DESC";

        this.connection.runCommands((connection, sqlConnection) -> {
            if (!sqlConnection.hasTable(nameHistoriesTableName)) {
                Statement smt = connection.createStatement();
                smt.executeUpdate("CREATE TABLE `" + nameHistoriesTableName + "` ("//
                        + "`uuid` CHAR( 36 ) NOT NULL,"//
                        + "`firstName` VARCHAR( 16 ) NOT NULL,"//
                        + "`refreshed` BIGINT NOT NULL DEFAULT '0',"//
                        + "PRIMARY KEY ( `uuid` ), INDEX ( `firstName` ) ) ENGINE = innodb");
                smt.close();
            }
            if (!sqlConnection.hasColumn(nameHistoriesTableName, "refreshed")) {
                Statement smt = connection.createStatement();
                smt.executeUpdate("ALTER TABLE `" + nameHistoriesTableName + "` ADD `refreshed` BIGINT NOT NULL DEFAULT '0' AFTER firstName");
                smt.close();
            }
            return null;
        });

        this.connection.runCommands((connection, sqlConnection) -> {
            if (!sqlConnection.hasTable(nameChangesTableName)) {
                Statement smt = connection.createStatement();
                smt.executeUpdate("CREATE TABLE `" + nameChangesTableName + "` ("//
                        + "`uuid` CHAR( 36 ) NOT NULL,"//
                        + "`date` BIGINT NOT NULL,"//
                        + "`newName` VARCHAR( 16 ) NOT NULL,"//
                        + "PRIMARY KEY ( `uuid`, `date` ), INDEX( `newName` ) ) ENGINE = innodb");
                smt.close();
            }
            return null;
        });

    }

    public void createProfilePropertiesTable() throws SQLException {
        this.connection.runCommands((connection, sqlConnection) -> {
            if (!sqlConnection.hasTable(profilesTableName)) {
                Statement smt = connection.createStatement();
                smt.executeUpdate("CREATE TABLE `" + profilesTableName + "` ("//
                        + "`uuid` CHAR( 36 ) NOT NULL,"//
                        + "`profile` MEDIUMTEXT NOT NULL ,"//
                        + "`lastSeen` BIGINT NOT NULL DEFAULT '0',"//
                        + "PRIMARY KEY ( `uuid` ), INDEX ( `lastSeen` ) ) ENGINE = innodb");
                smt.close();
            }
            return null;
        });
        mayUseProfilesTable = true;
    }

    public void addOrUpdatePlayers(final CachedPlayer... entries) throws SQLException {
        if (entries == null || entries.length == 0) {
            return;
        }
        this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(insertPlayer);
            for (CachedPlayer entry : entries) {
                smt.setString(1, entry.getUUID().toString());
                smt.setString(2, entry.getName());
                smt.setLong(3, entry.getLastSeen());
                smt.setString(4, entry.getName());
                smt.setLong(5, entry.getLastSeen());
                smt.executeUpdate();
            }
            return null;
        });
    }

    public CachedPlayer getPlayer(final UUID uuid) throws SQLException {
        return this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectPlayerByUUID);
            smt.setString(1, uuid.toString());
            ResultSet rs = smt.executeQuery();
            if (rs.next()) {
                String name = rs.getString(1);
                long time = rs.getLong(2);
                rs.close();
                return new CachedPlayer(uuid, name, time, System.currentTimeMillis());
            }
            rs.close();
            return null;
        });
    }

    public CachedPlayer getPlayer(final String name) throws SQLException {
        return this.connection.runCommands((connection, sqlConnection) -> {
            String realName = name;
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectPlayerByName);
            smt.setString(1, name);
            ResultSet rs = smt.executeQuery();

            UUID uuid = null;
            long time = Long.MIN_VALUE;
            while (rs.next()) {
                long thisTime = rs.getLong(3);
                if (thisTime > time) {
                    try {
                        uuid = UUID.fromString(rs.getString(1));
                        realName = rs.getString(2);
                        time = thisTime;
                    } catch (IllegalArgumentException e) {
                        // ignore invalid uuid
                    }
                }
            }
            rs.close();
            if (uuid != null) {
                return new CachedPlayer(uuid, realName, time, System.currentTimeMillis());
            }
            return null;
        });
    }

    public List<CachedPlayer> searchPlayers(final String partialName) throws SQLException {
        return this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(searchPlayersByPartialName);
            smt.setString(1, "%" + SQLUtil.escapeLike(partialName) + "%");
            ResultSet rs = smt.executeQuery();

            List<CachedPlayer> result = new ArrayList<>();
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    String name = rs.getString(2);
                    long time = rs.getLong(3);
                    result.add(new CachedPlayer(uuid, name, time, System.currentTimeMillis()));
                } catch (IllegalArgumentException e) {
                    // ignore invalid uuid
                }
            }
            rs.close();
            return result;
        });
    }

    public Set<CachedPlayer> getAllPlayers() throws SQLException {
        return this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectAllPlayers);
            ResultSet rs = smt.executeQuery();

            Set<CachedPlayer> result = new HashSet<>();
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    String name = rs.getString(2);
                    long time = rs.getLong(3);
                    result.add(new CachedPlayer(uuid, name, time, System.currentTimeMillis()));
                } catch (IllegalArgumentException e) {
                    // ignore invalid uuid
                }
            }
            rs.close();
            return result;
        });
    }

    public void disconnect() {
        connection.disconnect();
    }

    public void addOrUpdatePlayerProfile(CachedPlayerProfile entry) throws SQLException {
        if (!mayUseProfilesTable) {
            return;
        }
        this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(insertPlayerProfile);
            smt.setString(1, entry.getUUID().toString());
            YamlConfiguration yaml = new YamlConfiguration();
            for (ProfileProperty pp : entry.getProperties()) {
                ConfigurationSection section = yaml.createSection(pp.getName());
                section.set("value", pp.getValue());
                section.set("signature", pp.getSignature());
            }
            String properties = yaml.saveToString();
            smt.setString(2, properties);
            smt.setLong(3, entry.getLastSeen());
            smt.setString(4, properties);
            smt.setLong(5, entry.getLastSeen());
            smt.executeUpdate();
            return null;
        });
    }

    public CachedPlayerProfile getPlayerProfile(UUID uuid) throws SQLException {
        if (!mayUseProfilesTable) {
            return null;
        }
        return this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectPlayerProfileByUUID);
            smt.setString(1, uuid.toString());
            ResultSet rs = smt.executeQuery();
            if (rs.next()) {
                YamlConfiguration yaml = new YamlConfiguration();
                try {
                    yaml.loadFromString(rs.getString(1));
                } catch (Throwable t) {
                    return null;
                }
                LinkedHashSet<ProfileProperty> properties = new LinkedHashSet<>();
                for (String name : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(name);
                    if (section != null) {
                        String value = section.getString("value");
                        String signature = section.getString("signature");
                        properties.add(new ProfileProperty(name, value, signature));
                    }
                }

                long time = rs.getLong(2);
                rs.close();
                return new CachedPlayerProfile(uuid, properties, time, System.currentTimeMillis());
            }
            rs.close();
            return null;
        });
    }

    public void deleteOldPlayerProfiles() throws SQLException {
        this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(deleteOldPlayerProfiles);
            smt.setLong(1, System.currentTimeMillis() - PlayerUUIDCache.PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME);
            smt.executeUpdate();
            return null;
        });
    }

    public void addOrUpdateHistory(final NameHistory history) throws SQLException {
        this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(insertNameHistory);
            smt.setString(1, history.getUUID().toString());
            smt.setString(2, history.getFirstName());
            smt.setLong(3, history.getCacheLoadTime());
            smt.setLong(4, history.getCacheLoadTime());
            smt.executeUpdate();

            smt = sqlConnection.getOrCreateStatement(insertNameChange);
            for (NameChange change : history.getNameChanges()) {
                smt.setString(1, history.getUUID().toString());
                smt.setLong(2, change.getDate());
                smt.setString(3, change.getNewName());
                smt.addBatch();
            }
            smt.executeBatch();

            return null;
        });
    }

    public NameHistory getNameHistory(final UUID uuid) throws SQLException {
        return this.connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectNameHistory);
            smt.setString(1, uuid.toString());
            ResultSet rs = smt.executeQuery();

            if (!rs.next()) {
                return null;
            }

            String firstName = rs.getString(1);
            long refreshed = rs.getLong(2);
            rs.close();

            smt = sqlConnection.getOrCreateStatement(selectNameChanges);
            smt.setString(1, uuid.toString());
            rs = smt.executeQuery();

            List<NameChange> changes = new ArrayList<>();
            while (rs.next()) {
                changes.add(new NameChange(rs.getString(2), rs.getLong(1)));
            }
            rs.close();

            return new NameHistory(uuid, firstName, changes, refreshed);
        });
    }

    public Set<UUID> getKnownUsersFromHistory(final String name) throws SQLException {
        return this.connection.runCommands((connectino, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectNameUsers);
            smt.setString(1, name);
            smt.setString(2, name);
            smt.setString(3, name);
            ResultSet rs = smt.executeQuery();

            Set<UUID> result = new LinkedHashSet<>();
            while (rs.next()) {
                result.add(UUID.fromString(rs.getString(1)));
            }

            rs.close();
            return result;
        });
    }
}
