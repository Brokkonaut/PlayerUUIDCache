package de.iani.playerUUIDCache;

import de.iani.playerUUIDCache.NameHistory.NameChange;
import de.iani.playerUUIDCache.util.fetcher.NameFetcher;
import de.iani.playerUUIDCache.util.fetcher.NameHistoryFetcher;
import de.iani.playerUUIDCache.util.fetcher.ProfileFetcher;
import de.iani.playerUUIDCache.util.fetcher.UUIDFetcher;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerUUIDCache extends JavaPlugin implements PlayerUUIDCacheAPI {
    public static final long PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME = 1000 * 60 * 60 * 24;// 1 day
    public static final long PROFILE_PROPERTIES_LOCAL_CACHE_EXPIRATION_TIME = 1000 * 60 * 30;// 30 minutes

    protected PluginConfig config;

    protected HashMap<String, CachedPlayer> playersByName;

    protected HashMap<UUID, CachedPlayer> playersByUUID;

    protected HashMap<UUID, CachedPlayerProfile> playerProfiles;

    protected HashMap<UUID, NameHistory> nameHistories;

    protected UUIDDatabase database;

    private BinaryStorage binaryStorage;

    private volatile int uuid2nameLookups;
    private volatile int name2uuidLookups;
    private volatile int nameHistoryLookups;

    private volatile int mojangQueries;
    private volatile int databaseUpdates;
    private volatile int databaseQueries;

    private volatile int profilePropertiesLookups;
    private volatile int profilePropertiesLookupQueries;
    private boolean hasProfileAPI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        getServer().getPluginManager().registerEvents(new PlayerLoginListener(), this);

        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");// check if this is a Paper server with PlayerProfileAPI
            getLogger().info("Paper Profile API detected, registering listener");
            getServer().getPluginManager().registerEvents(new PaperProfileAPIListener(this), this);
            if (config.useSQL()) {
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
                                    Iterator<CachedPlayerProfile> it = playerProfiles.values().iterator();
                                    while (it.hasNext()) {
                                        CachedPlayerProfile entry = it.next();
                                        if (entry.getLastSeen() + PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME <= System.currentTimeMillis()) {
                                            it.remove();
                                        }
                                    }
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
        if (binaryStorage != null) {
            binaryStorage.close();
            binaryStorage = null;
        }
        if (database != null) {
            database.disconnect();
            database = null;
        }
    }

    @Override
    public synchronized void reloadConfig() {
        closeDatabase();
        super.reloadConfig();
        config = new PluginConfig(this);
        if (config.getMemoryCacheExpirationTime() != 0) {
            playersByName = new HashMap<>();
            playersByUUID = new HashMap<>();
            nameHistories = new HashMap<>();
        } else {
            playersByName = null;
            playersByUUID = null;
            nameHistories = null;
        }
        if (config.useSQL()) {
            getLogger().info("Using mysql backend");
            try {
                database = new UUIDDatabase(config.getSqlConfig());

                if (BinaryStorage.getDatabaseFile(this).isFile()) {
                    getLogger().info("Importing players from local file");
                    try {
                        BinaryStorage tempBinaryStorage = new BinaryStorage(this);
                        ArrayList<CachedPlayer> allPlayers = tempBinaryStorage.loadAllPlayers();
                        tempBinaryStorage.close();
                        updateEntries(true, allPlayers.toArray(new CachedPlayer[allPlayers.size()]));
                    } catch (IOException e) {
                        getLogger().log(Level.SEVERE, "Error while trying to import from file backend", e);
                    }
                    BinaryStorage.getDatabaseFile(this).delete();
                    getLogger().info("Import completed");
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        } else {
            getLogger().info("Using storage file backend");
            try {
                binaryStorage = new BinaryStorage(this);
                ArrayList<CachedPlayer> allPlayers = binaryStorage.loadAllPlayers();
                getLogger().info("Loaded " + allPlayers.size() + " players");
                if (!allPlayers.isEmpty()) {
                    updateEntries(false, allPlayers.toArray(new CachedPlayer[allPlayers.size()]));
                } else {
                    getLogger().info("Importing local players on first run");
                    importLocalOfflinePlayers();
                    getLogger().info("Import completed");
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the storage file", e);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("No permission!");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
            sender.sendMessage("uuid2nameLookups: " + uuid2nameLookups);
            sender.sendMessage("name2uuidLookups: " + name2uuidLookups);
            sender.sendMessage("nameHistoryLookups: " + nameHistoryLookups);
            sender.sendMessage("mojangQueries: " + mojangQueries);
            sender.sendMessage("databaseUpdates: " + databaseUpdates);
            sender.sendMessage("databaseQueries: " + databaseQueries);
            if (hasProfileAPI) {
                sender.sendMessage("profilePropertiesLookups: " + profilePropertiesLookups);
                sender.sendMessage("profilePropertiesLookupQueries: " + profilePropertiesLookupQueries);
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lookup")) {
            String nameOrId = args[1];
            CachedPlayer result = null;
            try {
                UUID id = UUID.fromString(nameOrId);
                result = getPlayer(id, true);
            } catch (Exception e) {
                result = getPlayer(nameOrId, true);
            }
            if (result == null) {
                sender.sendMessage("Unknown Account");
            } else {
                sender.sendMessage("Name: " + result.getName() + " ID: " + result.getUUID());
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lookupHistory")) {
            String idString = args[1];
            UUID uuid;
            try {
                uuid = UUID.fromString(idString);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Illegal UUID.");
                return true;
            }

            NameHistory result = null;
            result = getNameHistory(uuid, true);
            if (result == null) {
                sender.sendMessage("Unknown Account");
            } else {
                sender.sendMessage("First name: " + result.getFirstName());
                if (result.getNameChanges().isEmpty()) {
                    sender.sendMessage("(keine Umbenennungen)");
                    return true;
                }

                DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (NameChange change : result.getNameChanges()) {
                    sender.sendMessage(format.format(new Date(change.getDate())) + ": change to " + change.getNewName());
                }
            }
            return true;
        }
        sender.sendMessage(label + " stats");
        sender.sendMessage(label + " lookup <player>");
        sender.sendMessage(label + " lookupHistory <uuid>");
        return true;
    }

    private class PlayerLoginListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerLogin(PlayerLoginEvent e) {
            String name = e.getPlayer().getName();
            UUID uuid = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            updateEntries(true, new CachedPlayer(uuid, name, now, now));

            NameHistory oldHistory = getNameHistory(e.getPlayer());
            if (oldHistory == null || (!e.getPlayer().getName().equals(oldHistory.getName(System.currentTimeMillis())))) {
                PlayerUUIDCache.this.loadNameHistoryAsynchronously(e.getPlayer().getUniqueId());
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerQuit(PlayerQuitEvent e) {
            String name = e.getPlayer().getName();
            UUID uuid = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            updateEntries(true, new CachedPlayer(uuid, name, now, now));

            NameHistory oldHistory = getNameHistory(e.getPlayer());
            if (oldHistory == null || (!e.getPlayer().getName().equals(oldHistory.getName(System.currentTimeMillis())))) {
                PlayerUUIDCache.this.loadNameHistoryAsynchronously(e.getPlayer().getUniqueId());
            }
        }
    }

    public void importLocalOfflinePlayers() {
        long now = System.currentTimeMillis();
        ArrayList<CachedPlayer> toUpdate = new ArrayList<>();
        for (OfflinePlayer p : getServer().getOfflinePlayers()) {
            if (p.getName() != null && p.getUniqueId() != null) {
                @SuppressWarnings("deprecation")
                long lastPlayed = p.getLastPlayed();
                CachedPlayer knownPlayer = getPlayer(p.getUniqueId());
                if (knownPlayer == null || knownPlayer.getLastSeen() < lastPlayed) {
                    toUpdate.add(new CachedPlayer(p.getUniqueId(), p.getName(), lastPlayed, now));
                }
            }
        }
        if (toUpdate.size() > 0) {
            updateEntries(true, toUpdate.toArray(new CachedPlayer[toUpdate.size()]));
        }
    }

    @Override
    public CachedPlayer getPlayer(OfflinePlayer player) {
        return getPlayer(player.getUniqueId());
    }

    @Override
    public CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID) {
        return getPlayerFromNameOrUUID(playerNameOrUUID, false);
    }

    @Override
    public CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID, boolean queryMojangIfUnknown) {
        playerNameOrUUID = playerNameOrUUID.trim();
        if (playerNameOrUUID.length() == 36) {
            try {
                return getPlayer(UUID.fromString(playerNameOrUUID), queryMojangIfUnknown);
            } catch (IllegalArgumentException e) {
                // ignored
            }
        }
        return getPlayer(playerNameOrUUID, queryMojangIfUnknown);
    }

    @Override
    public Collection<CachedPlayer> getPlayers(Collection<String> playerNames, boolean queryMojangIfUnknown) {
        name2uuidLookups += playerNames.size();
        ArrayList<CachedPlayer> rv = new ArrayList<>();
        ArrayList<String> loadNames = queryMojangIfUnknown ? new ArrayList<>() : null;
        for (String player : playerNames) {
            CachedPlayer entry = getPlayer(player);
            if (entry == null) {
                if (queryMojangIfUnknown) {
                    loadNames.add(player);
                }
            } else {
                rv.add(entry);
            }
        }
        if (queryMojangIfUnknown && loadNames.size() > 0) {
            try {
                mojangQueries++;
                long now = System.currentTimeMillis();
                for (Entry<String, UUID> e : new UUIDFetcher(loadNames).call().entrySet()) {
                    final CachedPlayer entry = new CachedPlayer(e.getValue(), e.getKey(), now, now);
                    updateEntries(true, entry);
                    rv.add(entry);
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error while trying to load players", e);
            }
        }
        return rv;
    }

    @Override
    public CachedPlayer getPlayer(String playerName) {
        name2uuidLookups++;
        synchronized (this) {
            if (playersByName != null) {
                CachedPlayer entry = playersByName.get(playerName.toLowerCase());
                if (entry != null) {
                    if (config.getMemoryCacheExpirationTime() == -1 || entry.getCacheLoadTime() + config.getMemoryCacheExpirationTime() > System.currentTimeMillis()) {
                        return entry;
                    }
                }
            }
        }
        if (database != null) {
            try {
                databaseQueries++;
                CachedPlayer entry = database.getPlayer(playerName);
                if (entry != null) {
                    updateEntries(false, entry);
                    return entry;
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        return null;
    }

    @Override
    public CachedPlayer getPlayer(String playerName, boolean queryMojangIfUnknown) {
        CachedPlayer entry = getPlayer(playerName);
        if (entry != null || !queryMojangIfUnknown) {
            return entry;
        }
        return getPlayerFromMojang(playerName);
    }

    @Override
    public Future<CachedPlayer> loadPlayerAsynchronously(final String playerName) {
        FutureTask<CachedPlayer> futuretask = new FutureTask<>(() -> getPlayerFromMojang(playerName));
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    @Override
    public void getPlayerAsynchronously(final String playerName, final Callback<CachedPlayer> synchronousCallback) {
        final CachedPlayer entry = getPlayer(playerName);
        if (entry != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(entry);
                } else {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(entry));
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, (Runnable) () -> {
            final CachedPlayer p = getPlayerFromMojang(playerName);
            if (synchronousCallback != null) {
                getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(p));
            }
        });
    }

    @Override
    public CachedPlayer getPlayer(UUID playerUUID) {
        uuid2nameLookups++;
        synchronized (this) {
            if (playersByUUID != null) {
                CachedPlayer entry = playersByUUID.get(playerUUID);
                if (entry != null) {
                    if (config.getMemoryCacheExpirationTime() == -1 || entry.getCacheLoadTime() + config.getMemoryCacheExpirationTime() > System.currentTimeMillis()) {
                        return entry;
                    }
                }
            }
        }
        if (database != null) {
            try {
                databaseQueries++;
                CachedPlayer entry = database.getPlayer(playerUUID);
                if (entry != null) {
                    updateEntries(false, entry);
                    return entry;
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        return null;
    }

    @Override
    public CachedPlayer getPlayer(UUID playerUUID, boolean queryMojangIfUnknown) {
        CachedPlayer entry = getPlayer(playerUUID);
        if (entry != null || !queryMojangIfUnknown) {
            return entry;
        }
        return getPlayerFromMojang(playerUUID);
    }

    @Override
    public Future<CachedPlayer> loadPlayerAsynchronously(final UUID playerUUID) {
        FutureTask<CachedPlayer> futuretask = new FutureTask<>(() -> getPlayerFromMojang(playerUUID));
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    @Override
    public void getPlayerAsynchronously(final UUID playerUUID, final Callback<CachedPlayer> synchronousCallback) {
        final CachedPlayer entry = getPlayer(playerUUID);
        if (entry != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(entry);
                } else {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(entry));
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, (Runnable) () -> {
            final CachedPlayer p = getPlayerFromMojang(playerUUID);
            if (synchronousCallback != null) {
                getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(p));
            }
        });
    }

    protected CachedPlayer getPlayerFromMojang(String playerName) {
        mojangQueries++;
        try {
            for (Entry<String, UUID> e : new UUIDFetcher(Collections.singletonList(playerName)).call().entrySet()) {
                if (playerName.equalsIgnoreCase(e.getKey())) {
                    long now = System.currentTimeMillis();
                    final CachedPlayer entry = new CachedPlayer(e.getValue(), e.getKey(), now, now);
                    if (getServer().isPrimaryThread()) {
                        updateEntries(true, entry);
                    } else {
                        getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> updateEntries(true, entry));
                    }
                    return entry;
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error while trying to load player", e);
        }
        return null;
    }

    protected CachedPlayer getPlayerFromMojang(UUID playerUUID) {
        mojangQueries++;
        try {
            for (Entry<UUID, String> e : new NameFetcher(Collections.singletonList(playerUUID)).call().entrySet()) {
                if (playerUUID.equals(e.getKey())) {
                    long now = System.currentTimeMillis();
                    final CachedPlayer entry = new CachedPlayer(e.getKey(), e.getValue(), now, now);
                    if (getServer().isPrimaryThread()) {
                        updateEntries(true, entry);
                    } else {
                        getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> updateEntries(true, entry));
                    }
                    return entry;
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error while trying to load player", e);
        }
        return null;
    }

    protected synchronized void updateEntries(boolean updateDB, CachedPlayer... entries) {
        if (entries == null || entries.length == 0) {
            return;
        }
        if (playersByUUID != null && playersByName != null) {
            for (CachedPlayer entry : entries) {
                CachedPlayer oldEntry = playersByUUID.get(entry.getUUID());
                if (oldEntry != null) {
                    String oldName = oldEntry.getName();
                    if (!oldName.equalsIgnoreCase(entry.getName())) {
                        CachedPlayer oldNameEntry = playersByName.get(oldName.toLowerCase());
                        if (oldNameEntry != null && oldNameEntry.getUUID().equals(entry.getUUID())) {
                            playersByName.remove(oldName.toLowerCase());
                        }
                    }
                }
                if (oldEntry == null || oldEntry.getLastSeen() <= entry.getLastSeen()) {
                    playersByUUID.put(entry.getUUID(), entry);
                }
                String newLowerName = entry.getName().toLowerCase();
                oldEntry = playersByName.get(newLowerName);
                if (oldEntry == null || oldEntry.getLastSeen() <= entry.getLastSeen()) {
                    playersByName.put(newLowerName, entry);
                }
            }
        }
        if (updateDB) {
            if (database != null) {
                try {
                    databaseUpdates++;
                    database.addOrUpdatePlayers(entries);
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
                }
            }
            if (binaryStorage != null) {
                try {
                    databaseUpdates++;
                    for (CachedPlayer player : entries) {
                        binaryStorage.addOrUpdatePlayer(player);
                    }
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "Error while trying to access the storage file", e);
                }
            }
        }
    }

    protected synchronized void updateProfileProperties(boolean updateDB, CachedPlayerProfile entry) {
        if (playerProfiles != null) {
            CachedPlayerProfile oldEntry = playerProfiles.get(entry.getUUID());
            if (oldEntry == null || oldEntry.getLastSeen() <= entry.getLastSeen()) {
                playerProfiles.put(entry.getUUID(), entry);
            }
        }
        if (updateDB) {
            if (database != null) {
                try {
                    databaseUpdates++;
                    database.addOrUpdatePlayerProfile(entry);
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
                }
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
                    if (entry.getCacheLoadTime() + PROFILE_PROPERTIES_LOCAL_CACHE_EXPIRATION_TIME > now && entry.getLastSeen() + PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME > now) {
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
                if (entry != null && entry.getLastSeen() + PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME > System.currentTimeMillis()) {
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
        if (entry != null || !queryMojangIfUnknown) {
            return entry;
        }
        return getPlayerProfileFromMojang(playerUUID);
    }

    public void getPlayerProfileAsynchronously(final UUID playerUUID, final Callback<CachedPlayerProfile> synchronousCallback) {
        final CachedPlayerProfile entry = getPlayerProfile(playerUUID);
        if (entry != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(entry);
                } else {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(entry));
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, (Runnable) () -> {
            final CachedPlayerProfile p = getPlayerProfileFromMojang(playerUUID);
            if (synchronousCallback != null) {
                getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(p));
            }
        });
    }

    protected CachedPlayerProfile getPlayerProfileFromMojang(UUID playerUUID) {
        mojangQueries++;
        try {
            CachedPlayerProfile entry = new ProfileFetcher(playerUUID).call();
            if (entry != null) {
                updateProfileProperties(true, entry);
            }
            return entry;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error while trying to load player", e);
        }
        return null;
    }

    @Override
    public NameHistory getNameHistory(UUID playerUUID) {
        return getNameHistory(playerUUID, false);
    }

    @Override
    public NameHistory getNameHistory(UUID playerUUID, boolean queryMojangIfUnknown) {
        nameHistoryLookups++;
        NameHistory result;
        synchronized (this) {
            result = nameHistories.get(playerUUID);
            if (result != null) {
                if (config.getMemoryCacheExpirationTime() == -1 || result.getCacheLoadTime() + config.getMemoryCacheExpirationTime() > System.currentTimeMillis()) {
                    return result;
                }
            }
        }

        if (database != null) {
            databaseQueries++;
            try {
                result = database.getNameHistory(playerUUID);
                if (result != null) {
                    updateHistory(false, result);
                    return result;
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }

        if (!queryMojangIfUnknown) {
            return null;
        }

        return getNameHistoryFromMojang(playerUUID);
    }

    @Override
    public void getNameHistoryAsynchronously(UUID playerUUID, Callback<NameHistory> synchronousCallback) {
        final NameHistory history = getNameHistory(playerUUID);
        if (history != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(history);
                } else {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(history));
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, (Runnable) () -> {
            final NameHistory h = getNameHistoryFromMojang(playerUUID);
            if (synchronousCallback != null) {
                getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> synchronousCallback.onComplete(h));
            }
        });
    }

    @Override
    public Future<NameHistory> loadNameHistoryAsynchronously(UUID playerUUID) {
        FutureTask<NameHistory> futuretask = new FutureTask<>(() -> getNameHistoryFromMojang(playerUUID));
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    @Override
    public Set<UUID> getCurrentAndPreviousPlayers(String name) {
        Set<UUID> result = null;
        if (database != null) {
            try {
                databaseQueries++;
                result = database.getKnownUsersFromHistory(name);
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }

        if (result == null) {
            result = new HashSet<>();
            for (NameHistory history : nameHistories.values()) {
                if (history.getFirstName().equals(name)) {
                    result.add(history.getUUID());
                    continue;
                }
                for (NameChange change : history.getNameChanges()) {
                    if (change.getNewName().equals(name)) {
                        result.add(history.getUUID());
                        break;
                    }
                }
            }
        }

        CachedPlayer current = getPlayer(name, false);
        if (current != null) {
            result.add(current.getUniqueId());
        }

        return result;
    }

    protected NameHistory getNameHistoryFromMojang(UUID playerUUID) {
        mojangQueries++;
        try {
            NameHistory result = new NameHistoryFetcher(playerUUID).call();
            if (result == null) {
                return null;
            }
            if (getServer().isPrimaryThread()) {
                updateHistory(true, result);
            } else {
                getServer().getScheduler().runTask(PlayerUUIDCache.this, (Runnable) () -> updateHistory(true, result));
            }
            return result;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error while trying to load name history", e);
        }
        return null;
    }

    protected synchronized void updateHistory(boolean updateDB, NameHistory history) {
        if (nameHistories != null) {
            nameHistories.put(history.getUUID(), history);
        }
        if (updateDB) {
            if (database != null) {
                try {
                    databaseUpdates++;
                    database.addOrUpdateHistory(history);
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
                }
            }
        }
    }

}
