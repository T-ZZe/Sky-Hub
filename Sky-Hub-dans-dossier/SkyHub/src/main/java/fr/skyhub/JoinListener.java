package fr.skyhub;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
            hub.sendToHub(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getConfig().getBoolean("lobby.teleport-on-respawn", false)) {
            event.setRespawnLocation(hub.spawn());
            var player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> hub.applyLobbyState(player));
        }
    }
}
