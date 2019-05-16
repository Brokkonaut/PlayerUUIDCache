package de.iani.playerUUIDCache;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class CachedPlayer implements OfflinePlayer {
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

    /**
     * Gets the UUID of the cached player. This is never null.
     *
     * @return the UUID of the player
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Gets the name of the cached player. This is never null, but the name might be outdated.
     *
     * @return the name of the player
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getLastSeen() {
        return lastSeen;
    }

    @Override
    public long getLastLogin() {
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

    @Override
    public boolean isOp() {
        return Bukkit.getOfflinePlayer(uuid).isOp();
    }

    @Override
    public void setOp(boolean value) {
        Bukkit.getOfflinePlayer(uuid).setOp(value);
    }

    @Override
    public Map<String, Object> serialize() {
        return Bukkit.getOfflinePlayer(uuid).serialize();
    }

    @Override
    public boolean isOnline() {
        return Bukkit.getPlayer(uuid) != null;
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public boolean isBanned() {
        return Bukkit.getOfflinePlayer(uuid).isBanned();
    }

    @Override
    public boolean isWhitelisted() {
        return Bukkit.getOfflinePlayer(uuid).isWhitelisted();
    }

    @Override
    public void setWhitelisted(boolean value) {
        Bukkit.getOfflinePlayer(uuid).setWhitelisted(value);
    }

    @Override
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    public long getFirstPlayed() {
        return Bukkit.getOfflinePlayer(uuid).getFirstPlayed();
    }

    @Override
    @Deprecated
    public long getLastPlayed() {
        return Bukkit.getOfflinePlayer(uuid).getLastPlayed();
    }

    @Override
    public boolean hasPlayedBefore() {
        return Bukkit.getOfflinePlayer(uuid).hasPlayedBefore();
    }

    @Override
    public Location getBedSpawnLocation() {
        return Bukkit.getOfflinePlayer(uuid).getBedSpawnLocation();
    }
}
