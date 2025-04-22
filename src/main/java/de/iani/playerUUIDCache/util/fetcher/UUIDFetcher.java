package de.iani.playerUUIDCache.util.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * From https://gist.github.com/evilmidget38/26d70114b834f71fb3b4
 */
public class UUIDFetcher implements Callable<Map<String, UUID>> {
    private static final Pattern NAME_PATTERN = Pattern.compile("([A-Za-z0-9_]){2,16}");

    private static final int PROFILES_PER_REQUEST = 10;

    private static final String PROFILE_URL = "https://api.mojang.com/profiles/minecraft";

    private final ArrayList<String> names;

    private final boolean rateLimiting;

    public UUIDFetcher(List<String> names, boolean rateLimiting) {
        this.names = new ArrayList<>();
        for (String name : names) {
            if (name != null) {
                name = name.trim();
                if (isUserNameValid(name)) {
                    this.names.add(name);
                }
            }
        }
        this.rateLimiting = rateLimiting;
    }

    public UUIDFetcher(List<String> names) {
        this(names, true);
    }

    public static boolean isUserNameValid(String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    @Override
    public Map<String, UUID> call() throws Exception {
        Map<String, UUID> uuidMap = new HashMap<>();
        int requests = (names.size() + PROFILES_PER_REQUEST - 1) / PROFILES_PER_REQUEST;
        for (int i = 0; i < requests; i++) {
            HttpURLConnection connection = createConnection();
            JsonArray requestNames = new JsonArray();
            for (String s : names.subList(i * PROFILES_PER_REQUEST, Math.min((i + 1) * PROFILES_PER_REQUEST, names.size()))) {
                requestNames.add(s);
            }
            writeBody(connection, requestNames.toString());
            InputStream is = null;
            try {
                is = connection.getInputStream();
            } catch (IOException e) {
                if (e.getMessage().startsWith("Server returned HTTP response code: 403")) {
                    continue; // user not found
                }
                throw e;
            }
            if (is == null) {
                continue;
            }
            JsonElement response = JsonParser.parseReader(new InputStreamReader(is));
            if (response.isJsonNull()) {
                continue;
            }
            JsonArray array = (JsonArray) response;
            for (JsonElement profile : array) {
                JsonObject jsonObject = (JsonObject) profile;
                String id = jsonObject.get("id").getAsString();
                String name = jsonObject.get("name").getAsString();
                UUID uuid = getUUIDFromMojangString(id);
                uuidMap.put(name, uuid);
            }
            if (rateLimiting && i != requests - 1) {
                Thread.sleep(100L);
            }
        }
        return uuidMap;
    }

    private static void writeBody(HttpURLConnection connection, String body) throws Exception {
        OutputStream stream = connection.getOutputStream();
        stream.write(body.getBytes());
        stream.flush();
        stream.close();
    }

    private static HttpURLConnection createConnection() throws Exception {
        URL url = new URI(PROFILE_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        return connection;
    }

    private static UUID getUUIDFromMojangString(String id) {
        return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20, 32));
    }
}