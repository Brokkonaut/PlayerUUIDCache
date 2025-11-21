package de.iani.playerUUIDCache;

import com.destroystokyo.paper.event.profile.LookupProfileEvent;
import com.destroystokyo.paper.event.profile.PreLookupProfileEvent;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PaperProfileAPIListener implements Listener {
    private final PlayerUUIDCache plugin;

    public PaperProfileAPIListener(PlayerUUIDCache plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLookupProfile(PreLookupProfileEvent e) {
        UUID uuid = e.getUUID();
        String name = e.getName();
        if (uuid == null && name != null) {
            CachedPlayer cachedPlayer = plugin.getPlayer(name);
            if (cachedPlayer != null) {
                uuid = cachedPlayer.getUUID();
                e.setUUID(uuid);
            }
        }
    }

    @EventHandler
    public void onLookupProfile(LookupProfileEvent e) {
        UUID uuid = e.getId();
        String name = e.getName();
        if (uuid != null && name != null) {
            long now = System.currentTimeMillis();
            plugin.updateEntries(true, new CachedPlayer(uuid, name, now, now));
        }
    }
}
