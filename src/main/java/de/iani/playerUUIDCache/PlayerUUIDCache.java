package de.iani.playerUUIDCache;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import de.iani.playerUUIDCache.util.fetcher.NameFetcher;
import de.iani.playerUUIDCache.util.fetcher.ProfileFetcher;
import de.iani.playerUUIDCache.util.fetcher.UUIDFetcher;

public class PlayerUUIDCache extends JavaPlugin {
    public static final long PROFILE_PROPERTIES_CACHE_EXPIRATION_TIME = 1000 * 60 * 60 * 24;// 1 day
    public static final long PROFILE_PROPERTIES_LOCAL_CACHE_EXPIRATION_TIME = 1000 * 60 * 30;// 30 minutes

    protected PluginConfig config;

    protected HashMap<String, CachedPlayer> playersByName;

    protected HashMap<UUID, CachedPlayer> playersByUUID;

    protected HashMap<UUID, CachedPlayerProfile> playerProfiles;

    protected UUIDDatabase database;

    private BinaryStorage binaryStorage;

    private int uuid2nameLookups;

    private int name2uuidLookups;

    private int mojangQueries;

    private int databaseUpdates;

    private int databaseQueries;

    private int profilePropertiesLookups;

    private int profilePropertiesLookupQueries;

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
        } catch (

        ClassNotFoundException e) {
            // ignore
        }
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
        } else {
            playersByName = null;
            playersByUUID = null;
        }
        if (config.useSQL()) {
            getLogger().info("Using mysql backend");
            try {
                database = new UUIDDatabase(config.getSqlConfig());
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        } else {
            getLogger().info("Using storage file backend");
            try {
                binaryStorage = new BinaryStorage(this);
                ArrayList<CachedPlayer> allPlayers = binaryStorage.loadAllPlayers();
                getLogger().info("Loaded " + allPlayers.size() + " players");
                updateEntries(false, allPlayers.toArray(new CachedPlayer[allPlayers.size()]));
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
        if (args.length == 1 && args[0].equalsIgnoreCase("import")) {
            importLocalOfflinePlayers();
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
            sender.sendMessage("uuid2nameLookups: " + uuid2nameLookups);
            sender.sendMessage("name2uuidLookups: " + name2uuidLookups);
            sender.sendMessage("mojangQueries: " + mojangQueries);
            sender.sendMessage("databaseUpdates: " + databaseUpdates);
            sender.sendMessage("databaseQueries: " + databaseQueries);
            sender.sendMessage("profilePropertiesLookups: " + profilePropertiesLookups);
            sender.sendMessage("profilePropertiesLookupQueries: " + profilePropertiesLookupQueries);
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
        sender.sendMessage(label + " import");
        sender.sendMessage(label + " lookup <player>");
        return true;
    }

    private class PlayerLoginListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerLogin(PlayerLoginEvent e) {
            String name = e.getPlayer().getName();
            UUID uuid = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            updateEntries(true, new CachedPlayer(uuid, name, now, now));
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
        long now = System.currentTimeMillis();
        ArrayList<CachedPlayer> toUpdate = new ArrayList<>();
        for (OfflinePlayer p : getServer().getOfflinePlayers()) {
            if (p.getName() != null && p.getUniqueId() != null) {
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

    public CachedPlayer getPlayer(OfflinePlayer player) {
        return getPlayer(player.getUniqueId());
    }

    public CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID) {
        return getPlayerFromNameOrUUID(playerNameOrUUID, false);
    }

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

    public CachedPlayer getPlayer(String playerName, boolean queryMojangIfUnknown) {
        CachedPlayer entry = getPlayer(playerName);
        if (entry != null || !queryMojangIfUnknown) {
            return entry;
        }
        return getPlayerFromMojang(playerName);
    }

    public Future<CachedPlayer> loadPlayerAsynchronously(final String playerName) {
        FutureTask<CachedPlayer> futuretask = new FutureTask<>(new Callable<CachedPlayer>() {
            @Override
            public CachedPlayer call() throws Exception {
                return getPlayerFromMojang(playerName);
            }
        });
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    public void getPlayerAsynchronously(final String playerName, final Callback<CachedPlayer> synchronousCallback) {
        final CachedPlayer entry = getPlayer(playerName);
        if (entry != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(entry);
                } else {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                        @Override
                        public void run() {
                            synchronousCallback.onComplete(entry);
                        }
                    });
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {

            @Override
            public void run() {
                final CachedPlayer p = getPlayerFromMojang(playerName);
                if (synchronousCallback != null) {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                        @Override
                        public void run() {
                            synchronousCallback.onComplete(p);
                        }
                    });
                }
            }

        });
    }

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

    public CachedPlayer getPlayer(UUID playerUUID, boolean queryMojangIfUnknown) {
        CachedPlayer entry = getPlayer(playerUUID);
        if (entry != null || !queryMojangIfUnknown) {
            return entry;
        }
        return getPlayerFromMojang(playerUUID);
    }

    public Future<CachedPlayer> loadPlayerAsynchronously(final UUID playerUUID) {
        FutureTask<CachedPlayer> futuretask = new FutureTask<>(new Callable<CachedPlayer>() {
            @Override
            public CachedPlayer call() throws Exception {
                return getPlayerFromMojang(playerUUID);
            }
        });
        getServer().getScheduler().runTaskAsynchronously(this, futuretask);
        return futuretask;
    }

    public void getPlayerAsynchronously(final UUID playerUUID, final Callback<CachedPlayer> synchronousCallback) {
        final CachedPlayer entry = getPlayer(playerUUID);
        if (entry != null) {
            if (synchronousCallback != null) {
                if (Bukkit.isPrimaryThread()) {
                    synchronousCallback.onComplete(entry);
                } else {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                        @Override
                        public void run() {
                            synchronousCallback.onComplete(entry);
                        }
                    });
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {

            @Override
            public void run() {
                final CachedPlayer p = getPlayerFromMojang(playerUUID);
                if (synchronousCallback != null) {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                        @Override
                        public void run() {
                            synchronousCallback.onComplete(p);
                        }
                    });
                }
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
                        getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                            @Override
                            public void run() {
                                updateEntries(true, entry);
                            }
                        });
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
                        getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                            @Override
                            public void run() {
                                updateEntries(true, entry);
                            }
                        });
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
                        binaryStorage.addOrUpdate(player);
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
        FutureTask<CachedPlayerProfile> futuretask = new FutureTask<>(new Callable<CachedPlayerProfile>() {
            @Override
            public CachedPlayerProfile call() throws Exception {
                return getPlayerProfileFromMojang(playerUUID);
            }
        });
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
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                        @Override
                        public void run() {
                            synchronousCallback.onComplete(entry);
                        }
                    });
                }
            }
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final CachedPlayerProfile p = getPlayerProfileFromMojang(playerUUID);
                if (synchronousCallback != null) {
                    getServer().getScheduler().runTask(PlayerUUIDCache.this, new Runnable() {
                        @Override
                        public void run() {
                            synchronousCallback.onComplete(p);
                        }
                    });
                }
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

}
