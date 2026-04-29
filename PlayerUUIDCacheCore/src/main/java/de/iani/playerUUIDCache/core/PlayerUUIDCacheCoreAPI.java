package de.iani.playerUUIDCache.core;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

public interface PlayerUUIDCacheCoreAPI {
    CachedPlayerData getPlayer(String playerName);

    CachedPlayerData getPlayer(String playerName, boolean queryMojangIfUnknown);

    void getPlayerAsynchronously(String playerName, CoreCallback<CachedPlayerData> synchronousCallback);

    Future<CachedPlayerData> loadPlayerAsynchronously(String playerName);

    Collection<CachedPlayerData> getPlayers(Collection<String> playerNames, boolean queryMojangIfUnknown);

    CachedPlayerData getPlayer(UUID playerUUID);

    CachedPlayerData getPlayer(UUID playerUUID, boolean queryMojangIfUnknown);

    void getPlayerAsynchronously(UUID playerUUID, CoreCallback<CachedPlayerData> synchronousCallback);

    Future<CachedPlayerData> loadPlayerAsynchronously(UUID playerUUID);

    CachedPlayerData getPlayerFromNameOrUUID(String playerNameOrUUID);

    CachedPlayerData getPlayerFromNameOrUUID(String playerNameOrUUID, boolean queryMojangIfUnknown);

    List<CachedPlayerData> searchPlayersByPartialName(String partialName);

    void loadAllPlayersFromDatabase();

    NameHistoryData getNameHistory(UUID playerUUID);

    @Deprecated(forRemoval = true)
    NameHistoryData getNameHistory(UUID playerUUID, boolean queryMojangIfUnknown);

    @Deprecated(forRemoval = true)
    void getNameHistoryAsynchronously(UUID playerUUID, CoreCallback<NameHistoryData> synchronousCallback);

    @Deprecated(forRemoval = true)
    Future<NameHistoryData> loadNameHistoryAsynchronously(UUID playerUUID);

    Set<UUID> getCurrentAndPreviousPlayers(String name);
}
