package de.iani.playerUUIDCache;

import com.destroystokyo.paper.profile.ProfileProperty;
import de.iani.playerUUIDCache.core.util.sql.MySQLConnection;
import de.iani.playerUUIDCache.core.util.sql.SQLConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class UUIDDatabase {
    private final SQLConnection connection;
    private final String profilesTableName;
    private final String insertPlayerProfile;
    private final String selectPlayerProfileByUUID;
    private final String deleteOldPlayerProfiles;

    private boolean mayUseProfilesTable;

    public UUIDDatabase(SQLConfig config) throws SQLException {
        connection = new MySQLConnection(config.getHost(), config.getDatabase(), config.getUser(), config.getPassword());
        profilesTableName = config.getProfilesTableName();
        insertPlayerProfile = "INSERT INTO " + profilesTableName + " (uuid, profile, lastSeen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE profile = ?, lastSeen = ?";
        selectPlayerProfileByUUID = "SELECT profile, lastSeen FROM " + profilesTableName + " WHERE uuid = ?";
        deleteOldPlayerProfiles = "DELETE FROM " + profilesTableName + " WHERE lastSeen < ?";
    }

    public void createProfilePropertiesTable() throws SQLException {
        connection.runCommands((connection, sqlConnection) -> {
            if (!sqlConnection.hasTable(profilesTableName)) {
                Statement smt = connection.createStatement();
                smt.executeUpdate("CREATE TABLE `" + profilesTableName + "` (`uuid` CHAR( 36 ) NOT NULL,`profile` MEDIUMTEXT NOT NULL ,`lastSeen` BIGINT NOT NULL DEFAULT '0',PRIMARY KEY ( `uuid` ), INDEX ( `lastSeen` ) ) ENGINE = innodb");
                smt.close();
            }
            return null;
        });
        mayUseProfilesTable = true;
    }

    public void addOrUpdatePlayerProfile(CachedPlayerProfile entry) throws SQLException {
        if (!mayUseProfilesTable) {
            return;
        }
        connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(insertPlayerProfile);
            String properties = serializeProperties(entry);
            smt.setString(1, entry.getUUID().toString());
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
        return connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(selectPlayerProfileByUUID);
            smt.setString(1, uuid.toString());
            ResultSet rs = smt.executeQuery();
            if (!rs.next()) {
                rs.close();
                return null;
            }
            LinkedHashSet<ProfileProperty> properties = deserializeProperties(rs.getString(1));
            long time = rs.getLong(2);
            rs.close();
            return properties == null ? null : new CachedPlayerProfile(uuid, properties, time, System.currentTimeMillis());
        });
    }

    public void deleteOldPlayerProfiles() throws SQLException {
        connection.runCommands((connection, sqlConnection) -> {
            PreparedStatement smt = sqlConnection.getOrCreateStatement(deleteOldPlayerProfiles);
            smt.setLong(1, System.currentTimeMillis() - PlayerUUIDCache.PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME * 2);
            smt.executeUpdate();
            return null;
        });
    }

    public void disconnect() {
        connection.disconnect();
    }

    private String serializeProperties(CachedPlayerProfile entry) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ProfileProperty pp : entry.getProperties()) {
            ConfigurationSection section = yaml.createSection(pp.getName());
            section.set("value", pp.getValue());
            section.set("signature", pp.getSignature());
        }
        return yaml.saveToString();
    }

    private LinkedHashSet<ProfileProperty> deserializeProperties(String value) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(value);
        } catch (Throwable t) {
            return null;
        }
        LinkedHashSet<ProfileProperty> properties = new LinkedHashSet<>();
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            if (section != null) {
                properties.add(new ProfileProperty(name, section.getString("value"), section.getString("signature")));
            }
        }
        return properties;
    }
}
