package de.iani.playerUUIDCache.util.fetcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class NameFetcher implements Callable<Map<UUID, String>> {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final JsonParser jsonParser = new JsonParser();

    private final ArrayList<UUID> uuids;

    public NameFetcher(List<UUID> uuids) {
        this.uuids = new ArrayList<>(uuids);
    }

    @Override
    public Map<UUID, String> call() throws Exception {
        Map<UUID, String> uuidStringMap = new HashMap<>();
        for (UUID uuid : uuids) {
            HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL + uuid.toString().replace("-", "")).openConnection();
            connection.setConnectTimeout(5000);
            InputStream is = connection.getInputStream();
            if (is == null) {
                continue;
            }
            JsonElement response = jsonParser.parse(new InputStreamReader(is));
            if (response.isJsonNull()) {
                continue;
            }
            JsonObject object = (JsonObject) response;
            String name = object.get("name").getAsString();
            if (name == null) {
                continue;
            }
            JsonElement cause = object.get("cause");
            JsonElement errorMessage = object.get("errorMessage");
            if (cause != null && cause.getAsString().length() > 0) {
                throw new IllegalStateException(errorMessage == null ? null : errorMessage.getAsString());
            }
            uuidStringMap.put(uuid, name);
        }
        return uuidStringMap;
    }
}