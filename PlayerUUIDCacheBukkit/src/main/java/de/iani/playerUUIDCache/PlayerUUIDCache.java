package de.iani.playerUUIDCache;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.iani.playerUUIDCache.NameHistory.NameChange;
import de.iani.playerUUIDCache.core.CacheConfig;
import de.iani.playerUUIDCache.core.CachePlatform;
import de.iani.playerUUIDCache.core.CacheStats;
import de.iani.playerUUIDCache.core.CachedPlayerData;
import de.iani.playerUUIDCache.core.NameHistoryData;
import de.iani.playerUUIDCache.core.PlayerUUIDCacheCore;
import de.iani.playerUUIDCache.util.fetcher.ProfileFetcher;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerUUIDCache extends JavaPlugin implements PlayerUUIDCacheAPI {
    public static final long PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME = 1000 * 60 * 60 * 24 * 10;// 10 days
    public static final long PROFILE_PROPERTIES_LOCAL_CACHE_EXPIRATION_TIME = 1000 * 60 * 30;// 30 minutes

    protected PluginConfig config;
    protected HashMap<UUID, CachedPlayerProfile> playerProfiles;
    protected UUIDDatabase database;

    private PlayerUUIDCacheCore core;

    private volatile int profilePropertiesLookups;
    private volatile int profilePropertiesLookupQueries;
    private boolean hasProfileAPI;

    @Override
    public void onEnable() {
        core = new PlayerUUIDCacheCore(new BukkitCachePlatform());
        saveDefaultConfig();
        reloadConfig();

        getServer().getPluginManager().registerEvents(new PlayerLoginListener(), this);

        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            getLogger().info("Paper Profile API detected, registering listener");
            getServer().getPluginManager().registerEvents(new PaperProfileAPIListener(this), this);
            if (config.useSQL() && database != null) {
                getLogger().info("Using profile properties cache");
                try {
                    database.createProfilePropertiesTable();
                    playerProfiles = new HashMap<>();
                    getServer().getPluginManager().registerEvents(new PaperProfilePropertiesAPIListener(this), this);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            synchronized (PlayerUUIDCache.this) {
                                if (playerProfiles != null) {
                                    playerProfiles.values().removeIf(entry -> entry.getLastSeen() + PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME <= System.currentTimeMillis());
                                }
                            }
                            try {
                                database.deleteOldPlayerProfiles();
                            } catch (SQLException e) {
                                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
                            }
                        }
                    }.runTaskTimerAsynchronously(this, (long) (Math.random() * 20 * 60 * 60 * 24), 20 * 60 * 60 * 24);
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "Could not create profiles table", e);
                }
            }
            hasProfileAPI = true;
        } catch (ClassNotFoundException e) {
            // ignore
        }

        getServer().getServicesManager().register(PlayerUUIDCacheAPI.class, this, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        closeDatabase();
    }

    private synchronized void closeDatabase() {
        if (database != null) {
            database.disconnect();
            database = null;
        }
        if (core != null) {
            core.close();
        }
    }

    @Override
    public synchronized void reloadConfig() {
        closeDatabase();
        super.reloadConfig();
        config = new PluginConfig(this);
        if (config.useSQL()) {
            try {
                database = new UUIDDatabase(config.getSqlConfig());
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the profile properties database", e);
            }
        }
        core.reload(toCoreConfig());
    }

    public PlayerUUIDCacheCore getCore() {
        return core;
    }

    private CacheConfig toCoreConfig() {
        de.iani.playerUUIDCache.core.SQLConfig sqlConfig = config.useSQL() ? config.getSqlConfig() : null;
        return new CacheConfig(config.getMemoryCacheExpirationTime(), config.getNameHistoryCacheExpirationTime(), config.useSQL(), sqlConfig);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("No permission!");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
            CacheStats stats = core.getStats();
            sender.sendMessage("uuid2nameLookups: " + stats.getUuid2nameLookups());
            sender.sendMessage("name2uuidLookups: " + stats.getName2uuidLookups());
            sender.sendMessage("nameHistoryLookups: " + stats.getNameHistoryLookups());
            sender.sendMessage("mojangQueries: " + stats.getMojangQueries());
            sender.sendMessage("databaseUpdates: " + stats.getDatabaseUpdates());
            sender.sendMessage("databaseQueries: " + stats.getDatabaseQueries());
            if (hasProfileAPI) {
                sender.sendMessage("profilePropertiesLookups: " + profilePropertiesLookups);
                sender.sendMessage("profilePropertiesLookupQueries: " + profilePropertiesLookupQueries);
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lookup")) {
            CachedPlayer result = getPlayerFromNameOrUUID(args[1], true);
            if (result == null) {
                sender.sendMessage("Unknown Account");
            } else {
                sender.sendMessage("Name: " + result.getName() + " ID: " + result.getUUID());
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lookupHistory")) {
            CachedPlayer cachedPlayer = getPlayerFromNameOrUUID(args[1], true);
            UUID uuid;
            if (cachedPlayer != null) {
                uuid = cachedPlayer.getUniqueId();
            } else {
                try {
                    uuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("Illegal UUID.");
                    return true;
                }
            }

            NameHistory result = getNameHistory(uuid);
            if (result == null) {
                sender.sendMessage("Für diesen Account ist keine Namenshistory verfügbar");
            } else {
                sender.sendMessage("First name: " + result.getFirstName());
                if (result.getNameChanges().isEmpty()) {
                    sender.sendMessage("(keine Umbenennungen)");
                    return true;
                }

                DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (NameChange change : result.getNameChanges()) {
                    sender.sendMessage(format.format(new java.util.Date(change.getDate())) + ": change to " + change.getNewName());
                }
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lookupUUIDs")) {
            sender.sendMessage("UUIDs for: " + args[1]);
            for (UUID uuid : getCurrentAndPreviousPlayers(args[1])) {
                sender.sendMessage("  " + uuid.toString());
            }
            return true;
        }
        sender.sendMessage(label + " stats");
        sender.sendMessage(label + " lookup <player>");
        sender.sendMessage(label + " lookupHistory <player>");
        sender.sendMessage(label + " lookupUUIDs <name>");
        return true;
    }

    private class PlayerLoginListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerConnectionValidateLogin(PlayerConnectionValidateLoginEvent e) {
            if (e.getConnection() instanceof PlayerLoginConnection connection) {
                PlayerProfile profile = connection.getAuthenticatedProfile();
                String name = profile.getName();
                UUID uuid = profile.getId();
                long now = System.currentTimeMillis();
                updateEntries(true, new CachedPlayer(uuid, name, now, now));
                getNameHistory(profile);
                if (playerProfiles != null) {
                    playerProfiles.remove(uuid);
                }
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerQuit(PlayerQuitEvent e) {
            String name = e.getPlayer().getName();
            UUID uuid = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            updateEntries(true, new CachedPlayer(uuid, name, now, now));
        }
    }

    public void importLocalOfflinePlayers() {
        core.importKnownPlayers();
    }

    @Override
    public CachedPlayer getPlayer(OfflinePlayer player) {
        return getPlayer(player.getUniqueId());
    }

    @Override
    public CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID) {
        return fromData(core.getPlayerFromNameOrUUID(playerNameOrUUID));
    }

    @Override
    public CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID, boolean queryMojangIfUnknown) {
        return fromData(core.getPlayerFromNameOrUUID(playerNameOrUUID, queryMojangIfUnknown));
    }

    @Override
    public Collection<CachedPlayer> getPlayers(Collection<String> playerNames, boolean queryMojangIfUnknown) {
        return fromData(core.getPlayers(playerNames, queryMojangIfUnknown));
    }

    @Override
    public CachedPlayer getPlayer(String playerName) {
        return fromData(core.getPlayer(playerName));
    }

    @Override
    public CachedPlayer getPlayer(String playerName, boolean queryMojangIfUnknown) {
        return fromData(core.getPlayer(playerName, queryMojangIfUnknown));
    }

    @Override
    public Future<CachedPlayer> loadPlayerAsynchronously(final String playerName) {
        FutureTask<CachedPlayer> futuretask = new FutureTask<>(() -> fromData(core.loadPlayerAsynchronously(playerName).get()));
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    @Override
    public void getPlayerAsynchronously(final String playerName, final Callback<CachedPlayer> synchronousCallback) {
        core.getPlayerAsynchronously(playerName, p -> {
            if (synchronousCallback != null) {
                synchronousCallback.onComplete(fromData(p));
            }
        });
    }

    @Override
    public CachedPlayer getPlayer(UUID playerUUID) {
        return fromData(core.getPlayer(playerUUID));
    }

    @Override
    public CachedPlayer getPlayer(UUID playerUUID, boolean queryMojangIfUnknown) {
        return fromData(core.getPlayer(playerUUID, queryMojangIfUnknown));
    }

    @Override
    public Future<CachedPlayer> loadPlayerAsynchronously(final UUID playerUUID) {
        FutureTask<CachedPlayer> futuretask = new FutureTask<>(() -> fromData(core.loadPlayerAsynchronously(playerUUID).get()));
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    @Override
    public void getPlayerAsynchronously(final UUID playerUUID, final Callback<CachedPlayer> synchronousCallback) {
        core.getPlayerAsynchronously(playerUUID, p -> {
            if (synchronousCallback != null) {
                synchronousCallback.onComplete(fromData(p));
            }
        });
    }

    protected CachedPlayer getPlayerFromMojang(String playerName) {
        return getPlayer(playerName, true);
    }

    protected CachedPlayer getPlayerFromMojang(UUID playerUUID) {
        return getPlayer(playerUUID, true);
    }

    @Override
    public List<CachedPlayer> searchPlayersByPartialName(String partialName) {
        return fromData(core.searchPlayersByPartialName(partialName));
    }

    @Override
    public void loadAllPlayersFromDatabase() {
        core.loadAllPlayersFromDatabase();
    }

    public synchronized void updateEntries(boolean updateDB, CachedPlayer... entries) {
        if (entries == null || entries.length == 0) {
            return;
        }
        core.updateEntries(updateDB, entries);
    }

    protected synchronized void updateProfileProperties(boolean updateDB, CachedPlayerProfile entry) {
        if (playerProfiles != null) {
            CachedPlayerProfile oldEntry = playerProfiles.get(entry.getUUID());
            if (oldEntry == null || oldEntry.getLastSeen() <= entry.getLastSeen()) {
                playerProfiles.put(entry.getUUID(), entry);
            }
        }
        if (updateDB && database != null) {
            try {
                database.addOrUpdatePlayerProfile(entry);
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
    }

    public CachedPlayerProfile getPlayerProfile(UUID playerUUID) {
        profilePropertiesLookups++;
        synchronized (this) {
            if (playerProfiles != null) {
                CachedPlayerProfile entry = playerProfiles.get(playerUUID);
                if (entry != null) {
                    long now = System.currentTimeMillis();
                    if (entry.getCacheLoadTime() + PROFILE_PROPERTIES_LOCAL_CACHE_EXPIRATION_TIME > now && entry.getExpiration() > now) {
                        return entry;
                    } else {
                        playerProfiles.remove(playerUUID);
                    }
                }
            }
        }
        if (database != null) {
            try {
                profilePropertiesLookupQueries++;
                CachedPlayerProfile entry = database.getPlayerProfile(playerUUID);
                if (entry != null && entry.getExpiration() > System.currentTimeMillis()) {
                    updateProfileProperties(false, entry);
                    return entry;
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        return null;
    }

    public Future<CachedPlayerProfile> loadPlayerProfileAsynchronously(final UUID playerUUID) {
        FutureTask<CachedPlayerProfile> futuretask = new FutureTask<>(() -> getPlayerProfileFromMojang(playerUUID));
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    public CachedPlayerProfile getPlayerProfile(UUID playerUUID, boolean queryMojangIfUnknown) {
        CachedPlayerProfile entry = getPlayerProfile(playerUUID);
        return entry != null || !queryMojangIfUnknown ? entry : getPlayerProfileFromMojang(playerUUID);
    }

    public void getPlayerProfileAsynchronously(final UUID playerUUID, final Callback<CachedPlayerProfile> synchronousCallback) {
        final CachedPlayerProfile entry = getPlayerProfile(playerUUID);
        if (entry != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(entry);
                } else {
                    getServer().getScheduler().runTask(this, () -> synchronousCallback.onComplete(entry));
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            final CachedPlayerProfile p = getPlayerProfileFromMojang(playerUUID);
            if (synchronousCallback != null) {
                getServer().getScheduler().runTask(this, () -> synchronousCallback.onComplete(p));
            }
        });
    }

    protected CachedPlayerProfile getPlayerProfileFromMojang(UUID playerUUID) {
        try {
            CachedPlayerProfile entry = new ProfileFetcher(playerUUID).call();
            if (entry != null) {
                updateProfileProperties(true, entry);
            }
            return entry;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error while trying to load player profile: " + playerUUID, e);
        }
        return null;
    }

    @Override
    public NameHistory getNameHistory(OfflinePlayer player) {
        return fromData(core.getNameHistoryAndMaybeUpdate(player.getUniqueId(), player.getName()));
    }

    @Override
    public NameHistory getNameHistory(PlayerProfile player) {
        return fromData(core.getNameHistoryAndMaybeUpdate(player.getId(), player.getName()));
    }

    @Deprecated
    @Override
    public NameHistory getNameHistory(UUID playerUUID, boolean queryMojangIfUnknown) {
        return getNameHistory(playerUUID);
    }

    @Override
    public NameHistory getNameHistory(UUID playerUUID) {
        return fromData(core.getNameHistory(playerUUID));
    }

    @Deprecated
    @Override
    public void getNameHistoryAsynchronously(UUID playerUUID, Callback<NameHistory> synchronousCallback) {
        core.getNameHistoryAsynchronously(playerUUID, history -> {
            if (synchronousCallback != null) {
                synchronousCallback.onComplete(fromData(history));
            }
        });
    }

    @Deprecated
    @Override
    public Future<NameHistory> loadNameHistoryAsynchronously(UUID playerUUID) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Set<UUID> getCurrentAndPreviousPlayers(String name) {
        return core.getCurrentAndPreviousPlayers(name);
    }

    protected synchronized void updateHistory(boolean updateDB, NameHistory history) {
        core.updateHistory(updateDB, toData(history));
    }

    private CachedPlayer fromData(CachedPlayerData data) {
        return data == null ? null : data instanceof CachedPlayer player ? player : new CachedPlayer(data.getUUID(), data.getName(), data.getLastSeen(), data.getCacheLoadTime());
    }

    private ArrayList<CachedPlayer> fromData(Collection<CachedPlayerData> data) {
        ArrayList<CachedPlayer> result = new ArrayList<>();
        if (data != null) {
            for (CachedPlayerData player : data) {
                result.add(fromData(player));
            }
        }
        return result;
    }

    private NameHistory fromData(NameHistoryData data) {
        if (data == null) {
            return null;
        }
        ArrayList<NameChange> changes = new ArrayList<>();
        for (NameHistoryData.NameChange change : data.getNameChanges()) {
            changes.add(new NameChange(change.getNewName(), change.getDate()));
        }
        return new NameHistory(data.getUUID(), data.getFirstName(), changes, data.getCacheLoadTime());
    }

    private NameHistoryData toData(NameHistory history) {
        return history.toData();
    }

    private class BukkitCachePlatform implements CachePlatform {
        @Override
        public java.io.File getDataFolder() {
            return PlayerUUIDCache.this.getDataFolder();
        }

        @Override
        public java.util.logging.Logger getLogger() {
            return PlayerUUIDCache.this.getLogger();
        }

        @Override
        public boolean isPrimaryThread() {
            return Bukkit.isPrimaryThread();
        }

        @Override
        public void runAsync(Runnable runnable) {
            getServer().getScheduler().runTaskAsynchronously(PlayerUUIDCache.this, runnable);
        }

        @Override
        public void runSync(Runnable runnable) {
            getServer().getScheduler().runTask(PlayerUUIDCache.this, runnable);
        }

        @Override
        public Collection<CachedPlayerData> getKnownPlayers() {
            long now = System.currentTimeMillis();
            ArrayList<CachedPlayerData> result = new ArrayList<>();
            for (OfflinePlayer p : getServer().getOfflinePlayers()) {
                if (p.getName() != null && p.getUniqueId() != null) {
                    @SuppressWarnings("deprecation")
                    long lastPlayed = p.getLastPlayed();
                    result.add(new CachedPlayerData(p.getUniqueId(), p.getName(), lastPlayed, now));
                }
            }
            return result;
        }
    }
}
