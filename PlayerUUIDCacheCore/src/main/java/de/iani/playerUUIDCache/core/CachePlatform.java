package de.iani.playerUUIDCache.core;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

public interface CachePlatform {
    File getDataFolder();

    Logger getLogger();

    boolean isPrimaryThread();

    void runAsync(Runnable runnable);

    void runSync(Runnable runnable);

    default Collection<CachedPlayerData> getKnownPlayers() {
        return List.of();
    }
}
