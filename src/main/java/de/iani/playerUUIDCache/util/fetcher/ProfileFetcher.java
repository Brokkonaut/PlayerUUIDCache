package de.iani.playerUUIDCache.util.fetcher;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.iani.playerUUIDCache.CachedPlayerProfile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ProfileFetcher implements Callable<CachedPlayerProfile> {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String PROFILE_URL2 = "?unsigned=false";

    private final UUID uuid;

    public ProfileFetcher(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public CachedPlayerProfile call() throws Exception {
        String id = uuid.toString().replace("-", "");

        HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL + id + PROFILE_URL2).openConnection();
        connection.setConnectTimeout(5000);
        InputStream is = connection.getInputStream();
        if (is == null) {
            return null;
        }
        JsonElement response = JsonParser.parseReader(new InputStreamReader(is));
        if (response.isJsonNull()) {
            return null;
        }
        JsonObject jsonObject = (JsonObject) response;
        String id2 = jsonObject.get("id").getAsString();
        if (!Objects.equals(id, id2)) {
            return null;
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
        long now = System.currentTimeMillis();
        return new CachedPlayerProfile(uuid, propertiesSet, now, now);
    }
}