package de.iani.playerUUIDCache.bungeecord;

import de.iani.playerUUIDCache.core.CacheConfig;
import de.iani.playerUUIDCache.core.CachePlatform;
import de.iani.playerUUIDCache.core.CacheStats;
import de.iani.playerUUIDCache.core.CachedPlayerData;
import de.iani.playerUUIDCache.core.PlayerUUIDCacheCore;
import de.iani.playerUUIDCache.core.PlayerUUIDCacheCoreAPI;
import de.iani.playerUUIDCache.core.SQLConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class PlayerUUIDCacheBungeeCord extends Plugin implements Listener {
    private static PlayerUUIDCacheBungeeCord instance;

    private PlayerUUIDCacheCore core;

    public static PlayerUUIDCacheBungeeCord getInstance() {
        return instance;
    }

    public PlayerUUIDCacheCoreAPI getAPI() {
        return core;
    }

    public PlayerUUIDCacheCore getCore() {
        return core;
    }

    @Override
    public void onEnable() {
        instance = this;
        ensureDefaultConfig();
        core = new PlayerUUIDCacheCore(new BungeeCachePlatform());
        core.reload(loadConfig());
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new UUIDCacheCommand());
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.close();
            core = null;
        }
        instance = null;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent e) {
        updatePlayer(e.getPlayer());
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent e) {
        updatePlayer(e.getPlayer());
    }

    private void updatePlayer(ProxiedPlayer player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        long now = System.currentTimeMillis();
        core.updateEntries(true, new CachedPlayerData(uuid, name, now, now));
        core.getNameHistoryAndMaybeUpdate(uuid, name);
    }

    private CacheConfig loadConfig() {
        try {
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
            boolean useSQL = config.getBoolean("useSQL", false);
            long memoryCacheExpirationTime = !useSQL ? -1 : config.getLong("memoryCacheExpirationTime", -1);
            long nameHistoryCacheExpirationTime = config.getLong("nameHistoryCacheExpirationTime", 1000L * 60 * 60 * 24 * 30);
            SQLConfig sqlConfig = useSQL ? new SQLConfig(
                    config.getString("database.host", "localhost"),
                    config.getString("database.user", "CHANGETHIS"),
                    config.getString("database.password", "CHANGETHIS"),
                    config.getString("database.database", "CHANGETHIS"),
                    config.getString("database.tablename", "playeruuids"),
                    config.getString("database.profilestablename", "playerprofiles"),
                    config.getString("database.namehistoriestablename", "namehistories"),
                    config.getString("database.namechangestablename", "namechanges")) : null;
            return new CacheConfig(memoryCacheExpirationTime, nameHistoryCacheExpirationTime, useSQL, sqlConfig);
        } catch (IOException e) {
            getLogger().severe("Could not load config.yml: " + e.getMessage());
            return new CacheConfig(-1, 1000L * 60 * 60 * 24 * 30, false, null);
        }
    }

    private void ensureDefaultConfig() {
        if (!getDataFolder().isDirectory()) {
            getDataFolder().mkdirs();
        }
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.isFile()) {
            return;
        }
        try (InputStream in = getResourceAsStream("config.yml")) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
            }
        } catch (IOException e) {
            getLogger().severe("Could not save default config.yml: " + e.getMessage());
        }
    }

    private class BungeeCachePlatform implements CachePlatform {
        @Override
        public File getDataFolder() {
            return PlayerUUIDCacheBungeeCord.this.getDataFolder();
        }

        @Override
        public Logger getLogger() {
            return PlayerUUIDCacheBungeeCord.this.getLogger();
        }

        @Override
        public boolean isPrimaryThread() {
            return true;
        }

        @Override
        public void runAsync(Runnable runnable) {
            getProxy().getScheduler().runAsync(PlayerUUIDCacheBungeeCord.this, runnable);
        }

        @Override
        public void runSync(Runnable runnable) {
            getProxy().getScheduler().schedule(PlayerUUIDCacheBungeeCord.this, runnable, 0, TimeUnit.MILLISECONDS);
        }

        @Override
        public Collection<CachedPlayerData> getKnownPlayers() {
            long now = System.currentTimeMillis();
            ArrayList<CachedPlayerData> result = new ArrayList<>();
            for (ProxiedPlayer player : getProxy().getPlayers()) {
                result.add(new CachedPlayerData(player.getUniqueId(), player.getName(), now, now));
            }
            return result;
        }
    }

    private class UUIDCacheCommand extends Command {
        UUIDCacheCommand() {
            super("uuidcache", null, "playeruuidcache");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission("playeruuidcache.command")) {
                send(sender, ChatColor.RED + "No permission!");
                return;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
                CacheStats stats = core.getStats();
                send(sender, "uuid2nameLookups: " + stats.getUuid2nameLookups());
                send(sender, "name2uuidLookups: " + stats.getName2uuidLookups());
                send(sender, "nameHistoryLookups: " + stats.getNameHistoryLookups());
                send(sender, "mojangQueries: " + stats.getMojangQueries());
                send(sender, "databaseUpdates: " + stats.getDatabaseUpdates());
                send(sender, "databaseQueries: " + stats.getDatabaseQueries());
                return;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("lookup")) {
                CachedPlayerData result = core.getPlayerFromNameOrUUID(args[1], true);
                if (result == null) {
                    send(sender, "Unknown Account");
                } else {
                    send(sender, "Name: " + result.getName() + " ID: " + result.getUUID());
                }
                return;
            }
            send(sender, "/uuidcache stats");
            send(sender, "/uuidcache lookup <player>");
        }

        private void send(CommandSender sender, String message) {
            sender.sendMessage(new TextComponent(message));
        }
    }
}
