package de.iani.playerUUIDCache;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import org.bukkit.OfflinePlayer;

public interface PlayerUUIDCacheAPI {
    /**
     * Gets a CachedPlayer for an OfflinePlayer. The result may be null if this player is not found in the cache.
     * This method will never block for querying Mojang.
     * This method can be called from any thread.
     *
     * @param player
     *            the player to lookup
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayer(OfflinePlayer player);

    /**
     * Gets a CachedPlayer for a name.
     * The result may be null if this player is not found in the cache.
     * This method can be called from any thread.
     *
     * @param playerName
     *            the name of the player
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayer(String playerName);

    /**
     * Gets a CachedPlayer for a name.
     * If no entry is found in the cache this method will query Mojang if queryMojangIfUnknown is true. This
     * query is blocking, so avoid calling it in the main thread if possible.
     * The result may be null if this player is not found in the cache.
     * This method can be called from any thread.
     *
     * @param playerName
     *            the name of the player
     * @param queryMojangIfUnknown
     *            query Mojang if this parameter is true and no entry is found in the cache
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayer(String playerName, boolean queryMojangIfUnknown);

    /**
     * Gets a CachedPlayer for a name asyncronously.
     * If no entry is found in the cache this method will query Mojang. When the result is available,
     * the callback is executed in the main thread. This query is not blocking the main thread.
     * The callback will be called with null, if this player is not found.
     * This method can be called from any thread.
     *
     * @param playerName
     *            the name of the player
     * @param synchronousCallback
     *            a callback to execute when the result of the query to Mojang is completed
     * @return the CachedPlayer or null
     */
    void getPlayerAsynchronously(String playerName, Callback<CachedPlayer> synchronousCallback);

    /**
     * Gets a CachedPlayer for a name from Mojang.
     * This method will not query the cache but will always send a request to Mojang. If possible,
     * you should call Future.get() in a seperate thread to avoid blocking the main thread.
     * This method can be called from any thread.
     *
     * The Future will return null, if this player is not found.
     *
     * @param playerName
     *            the name of the player
     * @return a Future to query the result
     */
    Future<CachedPlayer> loadPlayerAsynchronously(String playerName);

    /**
     * Gets multiple CachedPlayers by their name.
     * This method will query Mojang if queryMojangIfUnknown is true and some players are not in the cache.
     * This query is blocking, so avoid calling it in the main thread if possible.
     * The result will only contain the players found.
     * This method can be called from any thread.
     *
     * @param playerNames
     *            a Collection of player names
     * @param queryMojangIfUnknown
     *            query Mojang if this parameter is true and not all players are found in the cache
     * @return a Collection of CachedPlayers
     */
    Collection<CachedPlayer> getPlayers(Collection<String> playerNames, boolean queryMojangIfUnknown);

    /**
     * Gets a CachedPlayer for a UUID.
     * The result may be null if this player is not found in the cache.
     *
     * @param playerUUID
     *            the UUID of the player
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayer(UUID playerUUID);

    /**
     * Gets a CachedPlayer for a UUID.
     * If no entry is found in the cache this method will query Mojang if queryMojangIfUnknown is true. This
     * query is blocking, so avoid calling it in the main thread if possible.
     * The result may be null if this player is not found in the cache.
     * This method can be called from any thread.
     *
     * @param playerUUID
     *            the UUID of the player
     * @param queryMojangIfUnknown
     *            query Mojang if this parameter is true and no entry is found in the cache
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayer(UUID playerUUID, boolean queryMojangIfUnknown);

    /**
     * Gets a CachedPlayer for a UUID asyncronously.
     * If no entry is found in the cache this method will query Mojang. When the result is available,
     * the callback is executed in the main thread. This query is not blocking the main thread.
     * The callback will be called with null, if this player is not found.
     * This method can be called from any thread.
     *
     * @param playerUUID
     *            the UUID of the player
     * @param synchronousCallback
     *            a callback to execute when the result of the query to Mojang is completed
     * @return the CachedPlayer or null
     */
    void getPlayerAsynchronously(UUID playerUUID, Callback<CachedPlayer> synchronousCallback);

    /**
     * Gets a CachedPlayer for a UUID from Mojang.
     * This method will not query the cache but will always send a request to Mojang. If possible,
     * you should call Future.get() in a seperate thread to avoid blocking the main thread.
     * This method can be called from any thread.
     *
     * The Future will return null, if this player is not found.
     *
     * @param playerUUID
     *            the UUID of the player
     * @return a Future to query the result
     */
    Future<CachedPlayer> loadPlayerAsynchronously(UUID playerUUID);

    /**
     * Gets a CachedPlayer for a name or UUID. This method detects if the perameter is a valid UUID
     * and will try to use that for the lookup if possible. Otherwise it will try to use the String as a name.
     * The result may be null if this player is not found in the cache.
     * This method can be called from any thread.
     *
     * @param playerNameOrUUID
     *            the player to lookup - either the name or the UUID
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID);

    /**
     * Gets a CachedPlayer for a name or UUID. This method detects if the perameter is a valid UUID
     * and will try to use that for the lookup if possible. Otherwise it will try to use the String as a name.
     * If no entry is found in the cache this method will query Mojang if queryMojangIfUnknown is true. This
     * query is blocking, so avoid calling it in the main thread if possible.
     * The result may be null if this player is not found.
     * This method can be called from any thread.
     *
     * @param playerNameOrUUID
     *            the player to lookup - either the name or the UUID
     * @param queryMojangIfUnknown
     *            query Mojang if this parameter is true and no entry is found in the cache
     * @return the CachedPlayer or null
     */
    CachedPlayer getPlayerFromNameOrUUID(String playerNameOrUUID, boolean queryMojangIfUnknown);

    /**
     * Searches for all known players whose names contain the given string. If a database is present, it will be used.
     * If no database is present, or if the database query fails, the local cache will be used. If no lokal cache is
     * present, null will be returned. This will never query Mojang. The resulting list is ordered by when the players
     * were last seen on the server, with players seen more recently coming first.
     *
     * @param partialName
     *            a part of a name to search for
     * @return a List of CachedPlayers whose names contain that part
     */
    List<CachedPlayer> searchPlayersByPartialName(String partialName);

    /**
     * Gets a NameHistory for a UUID.
     * The result may be null if this player's history is not found in the cache.
     *
     * @param playerUUID
     *            the UUID of the player
     * @return the NameHistory or null
     */
    NameHistory getNameHistory(UUID playerUUID);

    /**
     * Gets a NameHistory for a player.
     * The result may be null if this player's history is not found in the cache.
     * The OfflinePlayer must return a UUID.
     *
     * @param player
     *            the player
     * @return the NameHistory or null
     */
    default NameHistory getNameHistory(OfflinePlayer player) {
        return getNameHistory(player.getUniqueId());
    }

    /**
     * Gets a NameHistory for a UUID.
     * If no entry is found in the cache this method will query Mojang if queryMojangIfUnknown is true. This
     * query is blocking, so avoid calling it in the main thread if possible.
     * The result may be null if this player's history is not found in the cache.
     * This method can be called from any thread.
     *
     * @param playerUUID
     *            the UUID of the player
     * @param queryMojangIfUnknown
     *            query Mojang if this parameter is true and no entry is found in the cache
     * @return the NameHistory or null
     */
    NameHistory getNameHistory(UUID playerUUID, boolean queryMojangIfUnknown);

    /**
     * Gets a NameHistory for a player.
     * If no entry is found in the cache this method will query Mojang if queryMojangIfUnknown is true. This
     * query is blocking, so avoid calling it in the main thread if possible.
     * The result may be null if this player's history is not found in the cache.
     * This method can be called from any thread.
     * The OfflinePlayer must return a UUID.
     *
     * @param player
     *            the player
     * @param queryMojangIfUnknown
     *            query Mojang if this parameter is true and no entry is found in the cache
     * @return the NameHistory or null
     */
    default NameHistory getNameHistory(OfflinePlayer player, boolean queryMojangIfUnknown) {
        return getNameHistory(player.getUniqueId(), queryMojangIfUnknown);
    }

    /**
     * Gets a NameHistory for a UUID asyncronously.
     * If no entry is found in the cache this method will query Mojang. When the result is available,
     * the callback is executed in the main thread. This query is not blocking the main thread.
     * The callback will be called with null, if this player is not found.
     * This method can be called from any thread.
     *
     * @param playerUUID
     *            the UUID of the player
     * @param synchronousCallback
     *            a callback to execute when the result of the query to Mojang is completed
     * @return the NameHistory or null
     */
    void getNameHistoryAsynchronously(UUID playerUUID, Callback<NameHistory> synchronousCallback);

    /**
     * Gets a NameHistory for a UUID from Mojang.
     * This method will not query the cache but will always send a request to Mojang. If possible,
     * you should call Future.get() in a seperate thread to avoid blocking the main thread.
     * This method can be called from any thread.
     *
     * The Future will return null, if this player is not found.
     *
     * @param playerUUID
     *            the UUID of the player
     * @return a Future to query the result
     */
    Future<NameHistory> loadNameHistoryAsynchronously(UUID playerUUID);

    /**
     * Returns the UUIDs of all player known to have used the given name in the past or present.
     * This method will never query mojang. If no players are found, an empty set is returned.
     * 
     * This method will usually query the database. If no database is present, it will iterate
     * over the entire memory cache. Expect according performance.
     * 
     * @param name
     *            the name to search for
     * @return a set of all known players associated with that name
     */
    Set<UUID> getCurrentAndPreviousPlayers(String name);
}