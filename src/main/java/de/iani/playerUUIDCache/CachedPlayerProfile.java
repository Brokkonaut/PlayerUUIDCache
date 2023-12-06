package de.iani.playerUUIDCache;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class CachedPlayerProfile {

    private final UUID uuid;

    private final Set<ProfileProperty> properties;

    private final int propteriesHashCode;

    private final long lastSeen;

    private final long cacheLoadTime;

    private final long expiration;

    public CachedPlayerProfile(UUID uuid, Set<ProfileProperty> properties, long lastSeen, long cacheLoadTime) {
        com.google.common.base.Preconditions.checkNotNull(uuid);
        this.uuid = uuid;
        this.properties = Collections.unmodifiableSet(new LinkedHashSet<>(properties));
        this.propteriesHashCode = properties.hashCode();
        this.lastSeen = lastSeen;
        this.cacheLoadTime = cacheLoadTime;
        this.expiration = lastSeen + PlayerUUIDCache.PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME + new Random(uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits()).nextLong(PlayerUUIDCache.PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME);
    }

    public UUID getUUID() {
        return uuid;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    long getCacheLoadTime() {
        return cacheLoadTime;
    }

    @Override
    public int hashCode() {
        return uuid.hashCode() + propteriesHashCode + (int) lastSeen;
    }

    public Set<ProfileProperty> getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != CachedPlayerProfile.class) {
            return false;
        }
        CachedPlayerProfile other = (CachedPlayerProfile) obj;
        return uuid.equals(other.uuid) && properties.equals(other.properties) && lastSeen == other.lastSeen;
    }

    public long getExpiration() {
        return expiration;
    }
}
