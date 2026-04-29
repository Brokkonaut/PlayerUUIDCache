package de.iani.playerUUIDCache.core;

import de.iani.playerUUIDCache.core.NameHistoryData.NameChange;
import de.iani.playerUUIDCache.core.util.fetcher.NameFetcher;
import de.iani.playerUUIDCache.core.util.fetcher.UUIDFetcher;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

public class PlayerUUIDCacheCore implements PlayerUUIDCacheCoreAPI {
    private final CachePlatform platform;
    private final CacheStats stats = new CacheStats();

    private CacheConfig config;
    private HashMap<String, CachedPlayerData> playersByName;
    private HashMap<UUID, CachedPlayerData> playersByUUID;
    private HashMap<UUID, NameHistoryData> nameHistories;
    private UUIDDatabase database;
    private BinaryStorage binaryStorage;

    public PlayerUUIDCacheCore(CachePlatform platform) {
        this.platform = platform;
    }

    public synchronized void reload(CacheConfig config) {
        close();
        this.config = config;
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
            platform.getLogger().info("Using mysql backend");
            try {
                database = new UUIDDatabase(config.getSqlConfig());
                if (BinaryStorage.getDatabaseFile(platform.getDataFolder()).isFile()) {
                    platform.getLogger().info("Importing players from local file");
                    try {
                        BinaryStorage tempBinaryStorage = new BinaryStorage(platform.getDataFolder(), platform.getLogger());
                        ArrayList<CachedPlayerData> allPlayers = tempBinaryStorage.loadAllPlayers();
                        tempBinaryStorage.close();
                        updateEntries(true, allPlayers.toArray(new CachedPlayerData[allPlayers.size()]));
                    } catch (IOException e) {
                        platform.getLogger().log(Level.SEVERE, "Error while trying to import from file backend", e);
                    }
                    BinaryStorage.getDatabaseFile(platform.getDataFolder()).delete();
                    platform.getLogger().info("Import completed");
                }
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        } else {
            platform.getLogger().info("Using storage file backend");
            try {
                binaryStorage = new BinaryStorage(platform.getDataFolder(), platform.getLogger());
                ArrayList<CachedPlayerData> allPlayers = binaryStorage.loadAllPlayers();
                platform.getLogger().info("Loaded " + allPlayers.size() + " players");
                if (!allPlayers.isEmpty()) {
                    updateEntries(false, allPlayers.toArray(new CachedPlayerData[allPlayers.size()]));
                } else {
                    platform.getLogger().info("Importing local players on first run");
                    importKnownPlayers();
                    platform.getLogger().info("Import completed");
                }
            } catch (IOException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the storage file", e);
            }
        }
    }

    public synchronized void close() {
        if (binaryStorage != null) {
            binaryStorage.close();
            binaryStorage = null;
        }
        if (database != null) {
            database.disconnect();
            database = null;
        }
    }

    public CacheStats getStats() {
        return stats;
    }

    public void importKnownPlayers() {
        Collection<CachedPlayerData> knownPlayers = platform.getKnownPlayers();
        if (!knownPlayers.isEmpty()) {
            updateEntries(true, knownPlayers.toArray(new CachedPlayerData[knownPlayers.size()]));
        }
    }

    @Override
    public CachedPlayerData getPlayerFromNameOrUUID(String playerNameOrUUID) {
        return getPlayerFromNameOrUUID(playerNameOrUUID, false);
    }

    @Override
    public CachedPlayerData getPlayerFromNameOrUUID(String playerNameOrUUID, boolean queryMojangIfUnknown) {
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
    public Collection<CachedPlayerData> getPlayers(Collection<String> playerNames, boolean queryMojangIfUnknown) {
        stats.incrementName2uuidLookups(playerNames.size());
        ArrayList<CachedPlayerData> rv = new ArrayList<>();
        ArrayList<String> loadNames = queryMojangIfUnknown ? new ArrayList<>() : null;
        for (String player : playerNames) {
            CachedPlayerData entry = getPlayer(player);
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
                stats.incrementMojangQueries();
                long now = System.currentTimeMillis();
                for (Entry<String, UUID> e : new UUIDFetcher(loadNames).call().entrySet()) {
                    final CachedPlayerData entry = new CachedPlayerData(e.getValue(), e.getKey(), now, now);
                    updateEntries(true, entry);
                    rv.add(entry);
                }
            } catch (Exception e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to load players: " + loadNames, e);
            }
        }
        return rv;
    }

    @Override
    public CachedPlayerData getPlayer(String playerName) {
        stats.incrementName2uuidLookups(1);
        synchronized (this) {
            if (playersByName != null) {
                CachedPlayerData entry = playersByName.get(playerName.toLowerCase());
                if (entry != null && (config.getMemoryCacheExpirationTime() == -1 || entry.getCacheLoadTime() + config.getMemoryCacheExpirationTime() > System.currentTimeMillis())) {
                    return entry;
                }
            }
        }
        if (database != null) {
            try {
                stats.incrementDatabaseQueries();
                CachedPlayerData entry = database.getPlayer(playerName);
                if (entry != null) {
                    updateEntries(false, entry);
                    return entry;
                }
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        return null;
    }

    @Override
    public CachedPlayerData getPlayer(String playerName, boolean queryMojangIfUnknown) {
        CachedPlayerData entry = getPlayer(playerName);
        return entry != null || !queryMojangIfUnknown ? entry : getPlayerFromMojang(playerName);
    }

    @Override
    public Future<CachedPlayerData> loadPlayerAsynchronously(final String playerName) {
        FutureTask<CachedPlayerData> futuretask = new FutureTask<>(() -> getPlayerFromMojang(playerName));
        platform.runAsync(futuretask);
        return futuretask;
    }

    @Override
    public void getPlayerAsynchronously(final String playerName, final CoreCallback<CachedPlayerData> synchronousCallback) {
        final CachedPlayerData entry = getPlayer(playerName);
        if (entry != null) {
            completeSynchronously(synchronousCallback, entry);
            return;
        }
        platform.runAsync(() -> {
            final CachedPlayerData p = getPlayerFromMojang(playerName);
            completeSynchronously(synchronousCallback, p);
        });
    }

    @Override
    public CachedPlayerData getPlayer(UUID playerUUID) {
        stats.incrementUuid2nameLookups(1);
        synchronized (this) {
            if (playersByUUID != null) {
                CachedPlayerData entry = playersByUUID.get(playerUUID);
                if (entry != null && (config.getMemoryCacheExpirationTime() == -1 || entry.getCacheLoadTime() + config.getMemoryCacheExpirationTime() > System.currentTimeMillis())) {
                    return entry;
                }
            }
        }
        if (database != null) {
            try {
                stats.incrementDatabaseQueries();
                CachedPlayerData entry = database.getPlayer(playerUUID);
                if (entry != null) {
                    updateEntries(false, entry);
                    return entry;
                }
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        return null;
    }

    @Override
    public CachedPlayerData getPlayer(UUID playerUUID, boolean queryMojangIfUnknown) {
        CachedPlayerData entry = getPlayer(playerUUID);
        return entry != null || !queryMojangIfUnknown ? entry : getPlayerFromMojang(playerUUID);
    }

    @Override
    public Future<CachedPlayerData> loadPlayerAsynchronously(final UUID playerUUID) {
        FutureTask<CachedPlayerData> futuretask = new FutureTask<>(() -> getPlayerFromMojang(playerUUID));
        platform.runAsync(futuretask);
        return futuretask;
    }

    @Override
    public void getPlayerAsynchronously(final UUID playerUUID, final CoreCallback<CachedPlayerData> synchronousCallback) {
        final CachedPlayerData entry = getPlayer(playerUUID);
        if (entry != null) {
            completeSynchronously(synchronousCallback, entry);
            return;
        }
        platform.runAsync(() -> {
            final CachedPlayerData p = getPlayerFromMojang(playerUUID);
            completeSynchronously(synchronousCallback, p);
        });
    }

    protected CachedPlayerData getPlayerFromMojang(String playerName) {
        stats.incrementMojangQueries();
        try {
            for (Entry<String, UUID> e : new UUIDFetcher(Collections.singletonList(playerName)).call().entrySet()) {
                if (playerName.equalsIgnoreCase(e.getKey())) {
                    long now = System.currentTimeMillis();
                    final CachedPlayerData entry = new CachedPlayerData(e.getValue(), e.getKey(), now, now);
                    updateOnPrimaryThread(entry);
                    return entry;
                }
            }
        } catch (Exception e) {
            platform.getLogger().log(Level.SEVERE, "Error while trying to load player: " + playerName, e);
        }
        return null;
    }

    protected CachedPlayerData getPlayerFromMojang(UUID playerUUID) {
        stats.incrementMojangQueries();
        try {
            for (Entry<UUID, String> e : new NameFetcher(Collections.singletonList(playerUUID)).call().entrySet()) {
                if (playerUUID.equals(e.getKey())) {
                    long now = System.currentTimeMillis();
                    final CachedPlayerData entry = new CachedPlayerData(e.getKey(), e.getValue(), now, now);
                    updateOnPrimaryThread(entry);
                    return entry;
                }
            }
        } catch (Exception e) {
            platform.getLogger().log(Level.SEVERE, "Error while trying to load player name: " + playerUUID, e);
        }
        return null;
    }

    @Override
    public List<CachedPlayerData> searchPlayersByPartialName(String partialName) {
        List<CachedPlayerData> result = null;
        if (database != null) {
            stats.incrementDatabaseQueries();
            try {
                result = database.searchPlayers(partialName);
                updateEntries(false, result.toArray(new CachedPlayerData[result.size()]));
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        if (result == null && playersByUUID != null) {
            partialName = partialName.toLowerCase();
            result = new ArrayList<>();
            synchronized (this) {
                for (CachedPlayerData player : playersByUUID.values()) {
                    if (player.getName().toLowerCase().contains(partialName)) {
                        result.add(player);
                    }
                }
                result.sort((p1, p2) -> -1 * Long.compare(p1.getLastSeen(), p2.getLastSeen()));
            }
        }
        return result;
    }

    @Override
    public void loadAllPlayersFromDatabase() {
        if (database == null) {
            return;
        }
        try {
            Set<CachedPlayerData> players = database.getAllPlayers();
            updateEntries(false, players.toArray(new CachedPlayerData[players.size()]));
        } catch (Exception e) {
            platform.getLogger().log(Level.SEVERE, "Error while trying to load players", e);
        }
    }

    public synchronized void updateEntries(boolean updateDB, CachedPlayerData... entries) {
        if (entries == null || entries.length == 0) {
            return;
        }
        if (playersByUUID != null && playersByName != null) {
            for (CachedPlayerData entry : entries) {
                CachedPlayerData oldEntry = playersByUUID.get(entry.getUUID());
                if (oldEntry != null) {
                    String oldName = oldEntry.getName();
                    if (!oldName.equalsIgnoreCase(entry.getName())) {
                        CachedPlayerData oldNameEntry = playersByName.get(oldName.toLowerCase());
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
                    stats.incrementDatabaseUpdates();
                    database.addOrUpdatePlayers(entries);
                } catch (SQLException e) {
                    platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
                }
            }
            if (binaryStorage != null) {
                try {
                    stats.incrementDatabaseUpdates();
                    for (CachedPlayerData player : entries) {
                        binaryStorage.addOrUpdatePlayer(player);
                    }
                } catch (IOException e) {
                    platform.getLogger().log(Level.SEVERE, "Error while trying to access the storage file", e);
                }
            }
        }
    }

    public NameHistoryData getNameHistoryAndMaybeUpdate(UUID id, String currentName) {
        if (id == null) {
            return null;
        }
        NameHistoryData history = getNameHistory(id);
        if (currentName != null) {
            long time = System.currentTimeMillis();
            if (history == null) {
                history = new NameHistoryData(id, currentName, List.of(), time);
                updateHistory(true, history);
            } else if (!currentName.equals(history.getName(time))) {
                history = getNameHistoryInternal(id, true);
                if (history != null && !currentName.equals(history.getName(time))) {
                    ArrayList<NameChange> nameChanges = new ArrayList<>(history.getNameChanges());
                    nameChanges.add(new NameChange(currentName, time));
                    history = new NameHistoryData(history.getUUID(), history.getFirstName(), nameChanges, time);
                    updateHistory(true, history);
                }
            }
        }
        return history;
    }

    @Deprecated
    @Override
    public NameHistoryData getNameHistory(UUID playerUUID, boolean queryMojangIfUnknown) {
        return getNameHistory(playerUUID);
    }

    @Override
    public NameHistoryData getNameHistory(UUID playerUUID) {
        return getNameHistoryInternal(playerUUID, false);
    }

    private NameHistoryData getNameHistoryInternal(UUID playerUUID, boolean skipCache) {
        stats.incrementNameHistoryLookups();
        NameHistoryData result;
        if (!skipCache) {
            synchronized (this) {
                result = nameHistories == null ? null : nameHistories.get(playerUUID);
                if (result != null && (config.getNameHistoryCacheExpirationTime() == -1 || result.getCacheLoadTime() + config.getNameHistoryCacheExpirationTime() > System.currentTimeMillis())) {
                    return result;
                }
            }
        }

        if (database != null) {
            stats.incrementDatabaseQueries();
            try {
                result = database.getNameHistory(playerUUID);
                if (result != null) {
                    updateHistory(false, result);
                    return result;
                }
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
        return null;
    }

    @Deprecated
    @Override
    public void getNameHistoryAsynchronously(UUID playerUUID, CoreCallback<NameHistoryData> synchronousCallback) {
        final NameHistoryData history = getNameHistory(playerUUID);
        if (history != null) {
            completeSynchronously(synchronousCallback, history);
        }
    }

    @Deprecated
    @Override
    public Future<NameHistoryData> loadNameHistoryAsynchronously(UUID playerUUID) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Set<UUID> getCurrentAndPreviousPlayers(String name) {
        Set<UUID> result = null;
        if (database != null) {
            try {
                stats.incrementDatabaseQueries();
                result = database.getKnownUsersFromHistory(name);
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }

        if (result == null) {
            result = new HashSet<>();
            if (nameHistories != null) {
                for (NameHistoryData history : nameHistories.values()) {
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
        }

        CachedPlayerData current = getPlayer(name, false);
        if (current != null) {
            result.add(current.getUniqueId());
        }
        return result;
    }

    public synchronized void updateHistory(boolean updateDB, NameHistoryData history) {
        if (nameHistories != null) {
            nameHistories.put(history.getUUID(), history);
        }
        if (updateDB && database != null) {
            try {
                stats.incrementDatabaseUpdates();
                database.addOrUpdateHistory(history);
            } catch (SQLException e) {
                platform.getLogger().log(Level.SEVERE, "Error while trying to access the database", e);
            }
        }
    }

    private void updateOnPrimaryThread(CachedPlayerData entry) {
        if (platform.isPrimaryThread()) {
            updateEntries(true, entry);
        } else {
            platform.runSync(() -> updateEntries(true, entry));
        }
    }

    private <T> void completeSynchronously(CoreCallback<T> callback, T value) {
        if (callback == null) {
            return;
        }
        if (platform.isPrimaryThread()) {
            callback.onComplete(value);
        } else {
            platform.runSync(() -> callback.onComplete(value));
        }
    }
}
