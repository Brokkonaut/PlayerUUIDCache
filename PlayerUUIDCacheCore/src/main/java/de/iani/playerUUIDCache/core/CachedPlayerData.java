package de.iani.playerUUIDCache.core;

import java.util.Objects;
import java.util.UUID;

public final class CachedPlayerData {
    private final UUID uuid;
    private final String name;
    private final long lastSeen;
    private final long cacheLoadTime;

    public CachedPlayerData(UUID uuid, String name, long lastSeen, long cacheLoadTime) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.lastSeen = lastSeen;
        this.cacheLoadTime = cacheLoadTime;
    }

    public UUID getUUID() {
        return uuid;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public long getLastLogin() {
        return lastSeen;
    }

    public long getCacheLoadTime() {
        return cacheLoadTime;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + uuid.hashCode() + (int) lastSeen;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != CachedPlayerData.class) {
            return false;
        }
        CachedPlayerData other = (CachedPlayerData) obj;
        return name.equalsIgnoreCase(other.name) && uuid.equals(other.uuid) && lastSeen == other.lastSeen;
    }
}
