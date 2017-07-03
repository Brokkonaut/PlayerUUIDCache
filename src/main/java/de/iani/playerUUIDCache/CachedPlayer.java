package de.iani.playerUUIDCache;

import java.util.UUID;

public final class CachedPlayer {
    private final UUID uuid;

    private final String name;

    private final long lastSeen;

    private final long cacheLoadTime;

    public CachedPlayer(UUID uuid, String name, long lastSeen, long cacheLoadTime) {
        com.google.common.base.Preconditions.checkNotNull(uuid);
        com.google.common.base.Preconditions.checkNotNull(name);
        this.uuid = uuid;
        this.name = name;
        this.lastSeen = lastSeen;
        this.cacheLoadTime = cacheLoadTime;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    long getCacheLoadTime() {
        return cacheLoadTime;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + uuid.hashCode() + (int) lastSeen;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != CachedPlayer.class) {
            return false;
        }
        CachedPlayer other = (CachedPlayer) obj;
        return name.equalsIgnoreCase(other.name) && uuid.equals(other.uuid) && lastSeen == other.lastSeen;
    }
}
