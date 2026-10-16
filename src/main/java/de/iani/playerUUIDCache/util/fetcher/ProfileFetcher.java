package de.iani.playerUUIDCache.util.fetcher;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.iani.playerUUIDCache.CachedPlayerProfile;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ProfileFetcher implements Callable<CachedPlayerProfile> {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String PROFILE_URL2 = "?unsigned=false";

    private final UUID uuid;
    private final boolean waitAndRetryOnTooManyRequests;

    public ProfileFetcher(UUID uuid, boolean waitAndRetryOnTooManyRequests) {
        this.uuid = uuid;
        this.waitAndRetryOnTooManyRequests = waitAndRetryOnTooManyRequests;
    }

    @Override
    public CachedPlayerProfile call() throws Exception {
        String id = uuid.toString().replace("-", "");
        int waitTimeSeconds = 10;
        while (true) {
            HttpURLConnection connection = (HttpURLConnection) new URI(PROFILE_URL + id + PROFILE_URL2).toURL().openConnection();
            connection.setConnectTimeout(5000);
            InputStream is = null;
            try {
                is = connection.getInputStream();
            } catch (IOException e) {
                if (e.getMessage().startsWith("Server returned HTTP response code: 403")) {
                    return new CachedPlayerProfile(uuid, Set.of()); // user not found
                } else if (waitAndRetryOnTooManyRequests && waitTimeSeconds < 600 && e.getMessage().startsWith("Server returned HTTP response code: 429")) {
                    Thread.sleep(Duration.of(waitTimeSeconds, ChronoUnit.SECONDS));
                    waitTimeSeconds *= 1.5;
                    continue;
                }
                throw e;
            }
            if (is == null) {
                return new CachedPlayerProfile(uuid, Set.of());
            }
            JsonElement response = JsonParser.parseReader(new InputStreamReader(is));
            if (response.isJsonNull()) {
                return new CachedPlayerProfile(uuid, Set.of());
            }
            JsonObject jsonObject = (JsonObject) response;
            String id2 = jsonObject.get("id").getAsString();
            if (!Objects.equals(id, id2)) {
                throw new IOException("Server returned wrong user. Expected " + id + "; got " + id2); // what?
            }
            LinkedHashSet<ProfileProperty> propertiesSet = new LinkedHashSet<>();
            JsonArray properties = (JsonArray) jsonObject.get("properties");
            int l = properties.size();
            for (int i = 0; i < l; i++) {
                JsonObject property = (JsonObject) properties.get(i);
                String name = property.get("name").getAsString();
                String value = property.get("value").getAsString();
                String signature = property.get("signature").getAsString();
                propertiesSet.add(new ProfileProperty(name, value, signature));
            }
            return new CachedPlayerProfile(uuid, propertiesSet);
        }
    }
}