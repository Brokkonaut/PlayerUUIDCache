package de.iani.playerUUIDCache;

import com.destroystokyo.paper.event.profile.FillProfileEvent;
import com.destroystokyo.paper.event.profile.PreFillProfileEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PaperProfilePropertiesAPIListener implements Listener {
    private final PlayerUUIDCache plugin;

    public PaperProfilePropertiesAPIListener(PlayerUUIDCache plugin) {
        this.plugin = plugin;
    }

    // @EventHandler
    // public void onPlayerJoin(PlayerJoinEvent e) {
    // UUID uuid = e.getPlayer().getUniqueId();
    // plugin.getPlayerProfileAsynchronously(uuid, null);
    // }

    @EventHandler
    public void onPreFillProfile(PreFillProfileEvent e) {
        PlayerProfile profile = e.getPlayerProfile();
        UUID uuid = profile.getId();
        if (uuid == null) {
            CachedPlayer cached = plugin.getPlayer(profile.getName());
            if (cached != null) {
                uuid = cached.getUUID();
            }
        }
        if (uuid == null) {
            plugin.getLogger().info("PreFillProfile: UUID is null for " + profile.getName());
            return;
        }

        CachedPlayerProfile cachedProfile = plugin.getPlayerProfile(uuid);
        if (cachedProfile != null) {
            profile.setProperties(cachedProfile.getProperties());
        }
    }

    @EventHandler
    public void onFillProfile(FillProfileEvent e) {
        PlayerProfile profile = e.getPlayerProfile();
        UUID uuid = profile.getId();
        if (uuid == null) {
            CachedPlayer cached = plugin.getPlayer(profile.getName());
            if (cached != null) {
                uuid = cached.getUUID();
            }
        }
        if (uuid == null) {
            plugin.getLogger().info("PreFillProfile: UUID is null for " + profile.getName());
            return;
        }

        long now = System.currentTimeMillis();
        plugin.updateProfileProperties(true, new CachedPlayerProfile(uuid, profile.getProperties(), now, now));
    }
}
