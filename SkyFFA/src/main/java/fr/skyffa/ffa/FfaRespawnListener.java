package fr.skyffa.ffa;

import fr.skyffa.SkyFfaPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** FFA : on réapparaît au spawn de SA zone (même map, endroit différent). */
public final class FfaRespawnListener implements Listener {
    private final SkyFfaPlugin plugin;

    public FfaRespawnListener(SkyFfaPlugin plugin) {
        this.plugin = plugin;
    }

    // LOW : avant le rééquipement du kit (NORMAL) qui regarde le monde de réapparition
    @EventHandler(priority = EventPriority.LOW)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!plugin.ffa().isFfa(p.getWorld())) return;
        FfaMode mode = plugin.kits().getMode(p.getUniqueId());
        Location zone = mode == null ? null : plugin.ffa().getSpawn(mode);
        if (zone != null) e.setRespawnLocation(zone);
    }
}
