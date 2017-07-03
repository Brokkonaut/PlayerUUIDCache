package de.iani.playerUUIDCache.util.sql;

import java.sql.SQLException;

public class MySQLConnection extends SQLConnection {
    public MySQLConnection(String host, String database, String user, String password) throws SQLException {
        super("jdbc:mysql://" + host + "/" + database, database, user, password, "com.mysql.jdbc.Driver");
    }
}
