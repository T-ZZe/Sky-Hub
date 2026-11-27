package fr.skyffa.ffa;

import fr.skyffa.SkyFfaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /spawn et /lobby dans le FFA : compte à rebours de {@code ffa.teleport-delay-seconds} (5 par défaut)
 * pendant lequel on ne peut pas bouger (seule la caméra est libre). Annulé si on bouge ou si on entre
 * en combat ; impossible de le lancer en combat.
 * Le /lobby des autres mondes reste géré (et instantané) par SkyHub : ici on ne fait qu'intercepter
 * /lobby, /hub et /l tapés DANS le monde FFA, puis on lance /hub une fois le compte à rebours fini.
 */
public final class TeleportWarmup implements Listener {

    private final SkyFfaPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public TeleportWarmup(SkyFfaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ commandes

    /** /spawn : retour au spawn de sa zone FFA. */
    public void spawn(Player p) {
        if (!plugin.ffa().isFfa(p.getWorld())) {
            p.sendMessage(plugin.message("spawn-only-ffa"));
            return;
        }
        begin(p, this::doSpawn);
    }

    // ------------------------------------------------------------ compte à rebours

    private void begin(Player p, Consumer<Player> action) {
        boolean bypass = plugin.ffaCombat().bypass(p);
        if (!bypass && plugin.ffaCombat().isInCombat(p)) {
            p.sendMessage(plugin.message("ffa-in-combat",
                    "%seconds%", String.valueOf(plugin.ffaCombat().remaining(p))));
            return;
        }
        if (pending.containsKey(p.getUniqueId())) {
            p.sendMessage(plugin.message("tp-already"));
            return;
        }
        int delay = Math.max(0, plugin.getConfig().getInt("ffa.teleport-delay-seconds", 5));
        if (bypass || delay == 0) {
            action.accept(p);
            return;
        }
        Pending pd = new Pending(p, delay, action);
        pending.put(p.getUniqueId(), pd);
        p.sendMessage(plugin.message("tp-start", "%seconds%", String.valueOf(delay)));
        p.sendActionBar(plugin.message("tp-bar", "%seconds%", String.valueOf(delay)));
        pd.runTaskTimer(plugin, 20L, 20L);
    }

    private final class Pending extends BukkitRunnable {
        private final Player player;
        private final Consumer<Player> action;
        private int left;

        Pending(Player player, int left, Consumer<Player> action) {
            this.player = player;
            this.left = left;
            this.action = action;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead()) {
                stop(player);
                return;
            }
            if (plugin.ffaCombat().isInCombat(player) && !plugin.ffaCombat().bypass(player)) {
                TeleportWarmup.this.cancel(player, "tp-cancelled-combat");
                return;
            }
            left--;
            if (left <= 0) {
                stop(player);
                action.accept(player);
            } else {
                player.sendActionBar(plugin.message("tp-bar", "%seconds%", String.valueOf(left)));
            }
        }
    }

    /** Annule le compte à rebours du joueur (avec un message si messageKey != null). */
    public void cancel(Player p, String messageKey) {
        if (stop(p) && messageKey != null && p.isOnline()) {
            p.sendMessage(plugin.message(messageKey));
        }
    }

    /** Oublie le joueur sans message (déconnexion). */
    public void forget(Player p) {
        stop(p);
    }

    private boolean stop(Player p) {
        Pending pd = pending.remove(p.getUniqueId());
        if (pd == null) return false;
        pd.cancel();
        return true;
    }

    // ------------------------------------------------------------ destinations

    private void doSpawn(Player p) {
        if (!p.isOnline() || !plugin.ffa().isFfa(p.getWorld())) return;
        FfaMode mode = plugin.kits().getMode(p.getUniqueId());
        Location dest = mode == null ? null : plugin.ffa().getSpawn(mode);
        if (dest == null) dest = p.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
        p.teleportAsync(dest).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                p.setFallDistance(0);
                p.sendMessage(plugin.message("spawn-done"));
            });
        });
    }

    /** Une fois le compte à rebours fini : le vrai /hub de SkyHub (performCommand n'est pas ré-intercepté). */
    private void doLobby(Player p) {
        if (!p.isOnline()) return;
        if (!p.performCommand("hub")) p.sendMessage(plugin.message("hub-missing"));
    }

    // ------------------------------------------------------------ événements

    /** /lobby, /hub, /l (sans argument) tapés dans le FFA : compte à rebours + interdit en combat. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.ffa().isFfa(p.getWorld())) return;
        String[] parts = e.getMessage().substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':'); // skyhub:hub
        if (colon >= 0) label = label.substring(colon + 1);
        boolean lobby = label.equals("hub") || label.equals("lobby")
                || ((label.equals("l") || label.equals("skyhub")) && parts.length == 1);
        if (!lobby) return;
        e.setCancelled(true);
        begin(p, this::doLobby);
    }

    /** Seule la caméra peut bouger : tout changement de position annule la téléportation. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (pending.isEmpty()) return;
        Player p = e.getPlayer();
        if (!pending.containsKey(p.getUniqueId())) return;
        Location to = e.getTo();
        if (to == null) return;
        Location from = e.getFrom();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            cancel(p, "tp-cancelled-move");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        forget(e.getEntity());
    }
}
