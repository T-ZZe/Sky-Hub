package fr.skyhub;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/** Protège le hub : pas de dégâts, pas de faim, pas de construction, pas de monstres. */
public final class LobbyProtectionListener implements Listener {

    private final SkyHubPlugin plugin;
    private final HubWorld hub;

    public LobbyProtectionListener(SkyHubPlugin plugin, HubWorld hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (hub.isHub(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("skyhub.build")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (hub.isHub(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("skyhub.build")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && hub.isHub(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (hub.isHub(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        // On autorise uniquement les spawns "CUSTOM" (nos PNJ)
        if (hub.isHub(e.getLocation().getWorld())
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (hub.isHub(e.getWorld()) && e.toWeatherState()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        int limit = plugin.getConfig().getInt("lobby.void-teleport-below", 70);
        if (e.getTo().getY() < limit && hub.isHub(e.getTo().getWorld())) {
            e.getPlayer().teleportAsync(hub.spawn());
            e.getPlayer().setFallDistance(0);
        }
    }
}
