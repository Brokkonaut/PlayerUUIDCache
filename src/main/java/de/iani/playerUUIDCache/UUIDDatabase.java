package de.iani.playerUUIDCache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import de.iani.playerUUIDCache.util.sql.MySQLConnection;
import de.iani.playerUUIDCache.util.sql.SQLConnection;
import de.iani.playerUUIDCache.util.sql.SQLRunnable;

public class UUIDDatabase {
    private SQLConnection connection;

    private String tableName;

    private final String insertPlayer;

    private final String selectPlayerByUUID;

    private final String selectPlayerByName;

    public UUIDDatabase(SQLConfig config) throws SQLException {
        connection = new MySQLConnection(config.getHost(), config.getDatabase(), config.getUser(), config.getPassword());
        this.tableName = config.getTableName();

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
}
