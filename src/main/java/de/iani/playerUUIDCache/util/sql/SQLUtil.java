package de.iani.playerUUIDCache.util.sql;

import java.util.regex.Matcher;

public class SQLUtil {

    public static final String escapeLike(String arg) {
        arg = arg.replaceAll("\\\\", Matcher.quoteReplacement("\\\\"));
        arg = arg.replaceAll("\\_", Matcher.quoteReplacement("\\_"));
        arg = arg.replaceAll("\\%", Matcher.quoteReplacement("\\%"));
        return arg;
    }

}
