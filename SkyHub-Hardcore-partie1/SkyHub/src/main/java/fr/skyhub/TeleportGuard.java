package fr.skyhub;

import fr.skyhub.menu.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Règles de /lobby (/hub, /l) et /spawn :
 * - Hardcore : /lobby interdit pendant un combat (le combat tag du menu est utilisé).
 * - FFA : /spawn et /lobby interdits en combat, sinon 5 s sans bouger (seule la caméra peut bouger).
 */
public final class TeleportGuard implements Listener {

    private static final String BYPASS = "enchantlimit.bypass";

    private final SkyHubPlugin plugin;
    private final Map<UUID, Long> ffaCombat = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    private static final class Pending {
        final Location origin;
        BukkitTask task;

        Pending(Location origin) {
            this.origin = origin;
        }
    }

    public TeleportGuard(SkyHubPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ réglages

    private int delaySeconds() {
        return Math.max(0, plugin.getConfig().getInt("ffa.teleport-delay-seconds", 5));
    }

    private int ffaCombatSeconds() {
        return Math.max(1, plugin.getConfig().getInt("ffa.combat-seconds", 15));
    }

    // ------------------------------------------------------------ combat

    public boolean inCombat(Player p) {
        if (plugin.isHardcoreWorld(p.getWorld())) {
            CombatManager cm = plugin.menuModule() == null ? null : plugin.menuModule().combat();
            return cm != null && cm.isInCombat(p);
        }
        if (plugin.ffa().isFfa(p.getWorld())) {
            Long end = ffaCombat.get(p.getUniqueId());
            return end != null && end > System.currentTimeMillis();
        }
        return false;
    }

    private void tagFfa(Player p) {
        if (p.hasPermission(BYPASS) || !plugin.ffa().isFfa(p.getWorld())) return;
        boolean already = inCombat(p);
        ffaCombat.put(p.getUniqueId(), System.currentTimeMillis() + ffaCombatSeconds() * 1000L);
        cancel(p, "tp-cancel-combat"); // un téléport en attente est annulé si on entre en combat
        if (!already) p.sendMessage(plugin.message("ffa-combat", "%s%", String.valueOf(ffaCombatSeconds())));
    }

    // ------------------------------------------------------------ commandes

    /** /lobby, /hub, /l */
    public void requestHub(Player p) {
        World w = p.getWorld();
        boolean bypass = p.hasPermission(BYPASS);
        if (!bypass && inCombat(p)) {
            p.sendMessage(plugin.message("combat-no-hub"));
            return;
        }
        if (!bypass && plugin.ffa().isFfa(w) && delaySeconds() > 0) {
            startDelay(p, () -> {
                plugin.hub().sendToHub(p);
                p.sendMessage(plugin.message("hub-teleport"));
            });
            return;
        }
        plugin.hub().sendToHub(p);
        p.sendMessage(plugin.message("hub-teleport"));
    }

    /** /spawn : uniquement dans le FFA, vers le spawn de ta zone. */
    public void requestSpawn(Player p) {
        if (!plugin.ffa().isFfa(p.getWorld())) {
            p.sendMessage(plugin.message("spawn-ffa-only"));
            return;
        }
        boolean bypass = p.hasPermission(BYPASS);
        if (!bypass && inCombat(p)) {
            p.sendMessage(plugin.message("combat-no-spawn"));
            return;
        }
        if (bypass || delaySeconds() == 0) {
            doSpawn(p);
            return;
        }
        startDelay(p, () -> doSpawn(p));
    }

    private void doSpawn(Player p) {
        if (!plugin.ffa().isFfa(p.getWorld())) return;
        var mode = plugin.ffa().getMode(p.getUniqueId());
        Location dest = mode == null ? null : plugin.ffa().getSpawn(mode);
        if (dest == null) dest = p.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
        p.teleportAsync(dest).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.setFallDistance(0);
                p.sendMessage(plugin.message("spawn-teleport"));
            });
        });
    }

    // ------------------------------------------------------------ compte à rebours

    private void startDelay(Player p, Runnable action) {
        if (pending.containsKey(p.getUniqueId())) {
            p.sendMessage(plugin.message("tp-already"));
            return;
        }
        Pending pd = new Pending(p.getLocation().clone());
        pending.put(p.getUniqueId(), pd);
        final int[] left = {delaySeconds()};
        p.sendMessage(plugin.message("tp-start", "%s%", String.valueOf(left[0])));
        pd.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || p.isDead()) {
                clear(p.getUniqueId());
                return;
            }
            if (inCombat(p)) {
                cancel(p, "tp-cancel-combat");
                return;
            }
            if (left[0] <= 0) {
                clear(p.getUniqueId());
                action.run();
                return;
            }
            p.sendActionBar(plugin.message("tp-countdown", "%s%", String.valueOf(left[0])));
            left[0]--;
        }, 0L, 20L);
    }

    private void clear(UUID id) {
        Pending pd = pending.remove(id);
        if (pd != null && pd.task != null) pd.task.cancel();
    }

    private void cancel(Player p, String messageKey) {
        if (!pending.containsKey(p.getUniqueId())) return;
        clear(p.getUniqueId());
        p.sendMessage(plugin.message(messageKey));
        p.sendActionBar(net.kyori.adventure.text.Component.empty());
    }

    // ------------------------------------------------------------ événements

    /** Seule la caméra peut bouger : tout changement de position annule la téléportation. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null) return;
        Location to = e.getTo();
        if (to == null) return;
        Location from = e.getFrom();
        if (Math.abs(to.getX() - from.getX()) > 1.0E-4 || Math.abs(to.getY() - from.getY()) > 1.0E-4
                || Math.abs(to.getZ() - from.getZ()) > 1.0E-4) {
            cancel(p, "tp-cancel-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) return;
        tagFfa(victim);
        tagFfa(attacker);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        clear(p.getUniqueId());
        if (!plugin.ffa().isFfa(p.getWorld())) ffaCombat.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        clear(id);
        ffaCombat.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        clear(id);
        ffaCombat.remove(id);
    }
}
