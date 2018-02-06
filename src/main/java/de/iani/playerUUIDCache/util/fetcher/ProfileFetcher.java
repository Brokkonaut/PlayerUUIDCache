package de.iani.playerUUIDCache.util.fetcher;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.destroystokyo.paper.profile.ProfileProperty;

import de.iani.playerUUIDCache.CachedPlayerProfile;

public class ProfileFetcher implements Callable<CachedPlayerProfile> {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String PROFILE_URL2 = "?unsigned=false";

    private final JSONParser jsonParser = new JSONParser();

    private final UUID uuid;

    public ProfileFetcher(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public CachedPlayerProfile call() throws Exception {
        String id = uuid.toString().replace("-", "");

        HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL + id + PROFILE_URL2).openConnection();
        connection.setConnectTimeout(5000);
        JSONObject response = (JSONObject) jsonParser.parse(new InputStreamReader(connection.getInputStream()));
        String id2 = (String) response.get("id");
        if (!Objects.equals(id, id2)) {
            return null;
        }
        LinkedHashSet<ProfileProperty> propertiesSet = new LinkedHashSet<>();
        JSONArray properties = (JSONArray) response.get("properties");
        int l = properties.size();
        for (int i = 0; i < l; i++) {
            JSONObject property = (JSONObject) properties.get(i);
            String name = (String) property.get("name");
            String value = (String) property.get("value");
            String signature = (String) property.get("signature");
            propertiesSet.add(new ProfileProperty(name, value, signature));
        }
        long now = System.currentTimeMillis();
        return new CachedPlayerProfile(uuid, propertiesSet, now, now);
    }
}