package de.iani.playerUUIDCache;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class CachedPlayer implements OfflinePlayer {
    private final UUID uuid;

    private final String name;

    private final long lastSeen;

    private final long cacheLoadTime;

    private WeakReference<OfflinePlayer> bukkitPlayer;

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

    private OfflinePlayer getOfflinePlayer() {
        WeakReference<OfflinePlayer> loaded = bukkitPlayer;
        OfflinePlayer p = loaded == null ? null : loaded.get();
        if (p == null) {
            p = Bukkit.getOfflinePlayer(uuid);
            bukkitPlayer = new WeakReference<>(p);
        }
        return p;
    }

    @Override
    public boolean isOp() {
        return getOfflinePlayer().isOp();
    }

    @Override
    public void setOp(boolean value) {
        getOfflinePlayer().setOp(value);
    }

    @Override
    public Map<String, Object> serialize() {
        return getOfflinePlayer().serialize();
    }

    @Override
    public boolean isOnline() {
        return getOfflinePlayer().isOnline();
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public boolean isBanned() {
        return getOfflinePlayer().isBanned();
    }

    @Override
    public boolean isWhitelisted() {
        return getOfflinePlayer().isWhitelisted();
    }

    @Override
    public void setWhitelisted(boolean value) {
        getOfflinePlayer().setWhitelisted(value);
    }

    @Override
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    public long getFirstPlayed() {
        return getOfflinePlayer().getFirstPlayed();
    }

    @Override
    @Deprecated
    public long getLastPlayed() {
        return getOfflinePlayer().getLastPlayed();
    }

    @Override
    public boolean hasPlayedBefore() {
        return getOfflinePlayer().hasPlayedBefore();
    }

    @Override
    @Deprecated
    public Location getBedSpawnLocation() {
        return getOfflinePlayer().getBedSpawnLocation();
    }

    @Override
    public void incrementStatistic(Statistic statistic) throws IllegalArgumentException {
        getOfflinePlayer().incrementStatistic(statistic);
    }

    @Override
    public void decrementStatistic(Statistic statistic) throws IllegalArgumentException {
        getOfflinePlayer().decrementStatistic(statistic);
    }

    @Override
    public void incrementStatistic(Statistic statistic, int amount) throws IllegalArgumentException {
        getOfflinePlayer().incrementStatistic(statistic, amount);
    }

    @Override
    public void decrementStatistic(Statistic statistic, int amount) throws IllegalArgumentException {
        getOfflinePlayer().decrementStatistic(statistic, amount);
    }

    @Override
    public void setStatistic(Statistic statistic, int newValue) throws IllegalArgumentException {
        getOfflinePlayer().setStatistic(statistic, newValue);
    }

    @Override
    public int getStatistic(Statistic statistic) throws IllegalArgumentException {
        return getOfflinePlayer().getStatistic(statistic);
    }

    @Override
    public void incrementStatistic(Statistic statistic, Material material) throws IllegalArgumentException {
        getOfflinePlayer().incrementStatistic(statistic, material);
    }

    @Override
    public void decrementStatistic(Statistic statistic, Material material) throws IllegalArgumentException {
        getOfflinePlayer().decrementStatistic(statistic, material);
    }

    @Override
    public int getStatistic(Statistic statistic, Material material) throws IllegalArgumentException {
        return getOfflinePlayer().getStatistic(statistic, material);
    }

    @Override
    public void incrementStatistic(Statistic statistic, Material material, int amount) throws IllegalArgumentException {
        getOfflinePlayer().incrementStatistic(statistic, material, amount);
    }

    @Override
    public void decrementStatistic(Statistic statistic, Material material, int amount) throws IllegalArgumentException {
        getOfflinePlayer().decrementStatistic(statistic, material, amount);
    }

    @Override
    public void setStatistic(Statistic statistic, Material material, int newValue) throws IllegalArgumentException {
        getOfflinePlayer().setStatistic(statistic, material, newValue);
    }

    @Override
    public void incrementStatistic(Statistic statistic, EntityType entityType) throws IllegalArgumentException {
        getOfflinePlayer().incrementStatistic(statistic, entityType);
    }

    @Override
    public void decrementStatistic(Statistic statistic, EntityType entityType) throws IllegalArgumentException {
        getOfflinePlayer().decrementStatistic(statistic, entityType);
    }

    @Override
    public int getStatistic(Statistic statistic, EntityType entityType) throws IllegalArgumentException {
        return getOfflinePlayer().getStatistic(statistic, entityType);
    }

    @Override
    public void incrementStatistic(Statistic statistic, EntityType entityType, int amount) throws IllegalArgumentException {
        getOfflinePlayer().incrementStatistic(statistic, entityType, amount);
    }

    @Override
    public void decrementStatistic(Statistic statistic, EntityType entityType, int amount) {
        getOfflinePlayer().decrementStatistic(statistic, entityType, amount);
    }

    @Override
    public void setStatistic(Statistic statistic, EntityType entityType, int newValue) {
        getOfflinePlayer().setStatistic(statistic, entityType, newValue);
    }

    @Override
    public boolean isConnected() {
        return getOfflinePlayer().isConnected();
    }

    @Override
    public PlayerProfile getPlayerProfile() {
        return Bukkit.createProfile(uuid, name);
    }

    @Override
    public <E extends BanEntry<? super PlayerProfile>> E ban(String reason, Date expires, String source) {
        return getOfflinePlayer().ban(reason, expires, source);
    }

    @Override
    public <E extends BanEntry<? super PlayerProfile>> E ban(String reason, Instant expires, String source) {
        return getOfflinePlayer().ban(reason, expires, source);
    }

    @Override
    public <E extends BanEntry<? super PlayerProfile>> E ban(String reason, Duration duration, String source) {
        return getOfflinePlayer().ban(reason, duration, source);
    }

    @Override
    public Location getRespawnLocation() {
        return getOfflinePlayer().getRespawnLocation();
    }

    @Override
    public Location getLastDeathLocation() {
        return getOfflinePlayer().getLastDeathLocation();
    }

    @Override
    public Location getLocation() {
        return getOfflinePlayer().getLocation();
    }
}
