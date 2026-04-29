package de.iani.playerUUIDCache.core.util.sql;

public class SQLUtil {
    public static final String escapeLike(String arg) {
        return arg.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
