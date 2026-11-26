package fr.skyhub;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class JoinListener implements Listener {

    private final SkyHubPlugin plugin;
    private final HubWorld hub;

    public JoinListener(SkyHubPlugin plugin, HubWorld hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("lobby.teleport-on-join", true)) {
            // sendToHub sauvegarde d'abord la position du joueur s'il était dans un autre monde
            hub.sendToHub(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getConfig().getBoolean("lobby.teleport-on-respawn", false)) {
            event.setRespawnLocation(hub.spawn());
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> hub.applyLobbyState(player));
        }
    }

    /** Si le joueur meurt, on oublie sa position dans ce monde. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!hub.isHub(player.getWorld())) {
            plugin.locations().remove(player.getUniqueId(), player.getWorld().getName());
        }
    }
}
