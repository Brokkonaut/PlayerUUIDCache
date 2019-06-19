package de.iani.playerUUIDCache.util.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.iani.playerUUIDCache.NameHistory;
import de.iani.playerUUIDCache.NameHistory.NameChange;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

public class NameHistoryFetcher implements Callable<NameHistory> {
    private static final String PROFILE_URL_PREFIX = "https://api.mojang.com/user/profiles/";
    private static final String PROFILE_URL_SUFFIX = "/names";

    private final JsonParser jsonParser = new JsonParser();

    private UUID uuid;

    public NameHistoryFetcher(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public NameHistory call() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL_PREFIX + uuid.toString().replace("-", "") + PROFILE_URL_SUFFIX).openConnection();
        connection.setConnectTimeout(5000);
        long time = System.currentTimeMillis();
        JsonArray response = (JsonArray) jsonParser.parse(new InputStreamReader(connection.getInputStream()));

        String firstName = null;
        List<NameChange> changes = new ArrayList<>();
        for (JsonElement element : response) {
            JsonObject object = (JsonObject) element;

            String name = object.get("name").getAsString();
            if (object.has("changedToAt")) {
                changes.add(new NameChange(name, object.get("changedToAt").getAsLong()));
            } else {
                if (firstName != null) {
                    throw new RuntimeException("illegal response from mojang");
                }
                firstName = name;
            }
        }

        return new NameHistory(uuid, firstName, changes, time);
    }

}
