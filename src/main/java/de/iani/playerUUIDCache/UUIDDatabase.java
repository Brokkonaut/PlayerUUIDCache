package de.iani.playerUUIDCache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.destroystokyo.paper.profile.ProfileProperty;

import de.iani.playerUUIDCache.util.sql.MySQLConnection;
import de.iani.playerUUIDCache.util.sql.SQLConnection;
import de.iani.playerUUIDCache.util.sql.SQLRunnable;

public class UUIDDatabase {
    private final SQLConnection connection;

    private final String tableName;

    private final String profilesTableName;

    private final String insertPlayer;

    private final String selectPlayerByUUID;

    private final String selectPlayerByName;

    private final String insertPlayerProfile;

    private final String selectPlayerProfileByUUID;

    private final String deleteOldPlayerProfiles;

    private boolean mayUseProfilesTable;

    public UUIDDatabase(SQLConfig config) throws SQLException {
        connection = new MySQLConnection(config.getHost(), config.getDatabase(), config.getUser(), config.getPassword());
        this.tableName = config.getTableName();
        this.profilesTableName = config.getProfilesTableName();

        insertPlayer = "INSERT INTO " + tableName + " (uuid, name, lastSeen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, lastSeen = ?";

        selectPlayerByUUID = "SELECT name, lastSeen FROM " + tableName + " WHERE uuid = ?";

        selectPlayerByName = "SELECT uuid, name, lastSeen FROM " + tableName + " WHERE name = ?";

        this.connection.runCommands(new SQLRunnable<Void>() {
            @Override
            public Void execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });

        insertPlayerProfile = "INSERT INTO " + profilesTableName + " (uuid, profile, lastSeen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE profile = ?, lastSeen = ?";

        selectPlayerProfileByUUID = "SELECT profile, lastSeen FROM " + profilesTableName + " WHERE uuid = ?";

        deleteOldPlayerProfiles = "DELETE FROM " + profilesTableName + " WHERE lastSeen < ?";
    }

    public void createProfilePropertiesTable() throws SQLException {
        this.connection.runCommands(new SQLRunnable<Void>() {
            @Override
            public Void execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });
        mayUseProfilesTable = true;
    }

    public void addOrUpdatePlayers(final CachedPlayer... entries) throws SQLException {
        if (entries == null || entries.length == 0) {
            return;
        }
        this.connection.runCommands(new SQLRunnable<Void>() {
            @Override
            public Void execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });
    }

    public CachedPlayer getPlayer(final UUID uuid) throws SQLException {
        return this.connection.runCommands(new SQLRunnable<CachedPlayer>() {
            @Override
            public CachedPlayer execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });
    }

    public CachedPlayer getPlayer(final String name) throws SQLException {
        return this.connection.runCommands(new SQLRunnable<CachedPlayer>() {
            @Override
            public CachedPlayer execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });
    }

    public void disconnect() {
        connection.disconnect();
    }

    public void addOrUpdatePlayerProfile(CachedPlayerProfile entry) throws SQLException {
        if (!mayUseProfilesTable) {
            return;
        }
        this.connection.runCommands(new SQLRunnable<Void>() {
            @Override
            public Void execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });
    }

    public CachedPlayerProfile getPlayerProfile(UUID uuid) throws SQLException {
        if (!mayUseProfilesTable) {
            return null;
        }
        return this.connection.runCommands(new SQLRunnable<CachedPlayerProfile>() {
            @Override
            public CachedPlayerProfile execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
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
            }
        });
    }

    public void deleteOldPlayerProfiles() throws SQLException {
        this.connection.runCommands(new SQLRunnable<Void>() {
            @Override
            public Void execute(Connection connection, SQLConnection sqlConnection) throws SQLException {
                PreparedStatement smt = sqlConnection.getOrCreateStatement(deleteOldPlayerProfiles);
                smt.setLong(1, System.currentTimeMillis() - PlayerUUIDCache.PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME);
                smt.executeUpdate();
                return null;
            }
        });
    }
}
