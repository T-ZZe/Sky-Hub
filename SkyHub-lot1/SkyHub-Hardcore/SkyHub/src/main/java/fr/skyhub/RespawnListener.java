package fr.skyhub;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * On réapparaît dans le monde où l'on est mort (FFA, Event...), pas dans le monde principal
 * (qui est le monde hardcore). Le nether / l'end d'un monde restent liés à ce monde (comportement normal).
 */
public final class RespawnListener implements Listener {
    private final SkyHubPlugin plugin;

    public RespawnListener(SkyHubPlugin plugin) {
        this.plugin = plugin;
    }

    // LOW : s'exécute avant le module hardcore (HIGH) qui vérifie le monde de réapparition
    @EventHandler(priority = EventPriority.LOW)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        World died = p.getWorld();
        // FFA : on réapparaît au spawn de SA zone (même map, endroit différent)
        if (plugin.ffa().isFfa(died)) {
            fr.skyhub.ffa.FfaMode mode = plugin.ffa().getMode(p.getUniqueId());
            Location zone = mode == null ? null : plugin.ffa().getSpawn(mode);
            if (zone != null) {
                e.setRespawnLocation(zone);
                return;
            }
        }
        Location respawn = e.getRespawnLocation();
        World rw = respawn.getWorld();
        if (rw == null || rw.equals(died)) return;
        // même groupe (world / world_nether / world_the_end) : comportement vanilla
        if (InventoryManager.group(rw).equals(InventoryManager.group(died))) return;

        Location dest = plugin.hub().isHub(died) ? plugin.hub().spawn() : died.getSpawnLocation();
        e.setRespawnLocation(dest);
    }
}
