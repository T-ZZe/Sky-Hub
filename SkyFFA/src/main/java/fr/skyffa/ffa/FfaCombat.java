package fr.skyffa.ffa;

import fr.skyffa.SkyFfaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Combat tag du monde FFA : frapper un joueur ou se faire frapper = combat pendant
 * {@code ffa.combat-seconds} secondes (20 par défaut).
 * <ul>
 *   <li>en combat : /spawn, /lobby, changement de zone et /tp (joueurs normaux) sont refusés ;</li>
 *   <li>se déconnecter en combat = mort (on ne peut pas bloquer une déconnexion côté client,
 *       donc on punit : le joueur meurt et ses objets tombent).</li>
 * </ul>
 * Permission {@code skyhub.combat.bypass} : ignore tout ça. Indépendant du combat tag du Hardcore.
 */
public final class FfaCombat implements Listener {

    private static final String BYPASS = "skyhub.combat.bypass";
    private static final double EXPLOSION_RADIUS_SQ = 12.0 * 12.0;

    private final SkyFfaPlugin plugin;
    private final Map<UUID, Long> end = new HashMap<>();

    public FfaCombat(SkyFfaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Compte à rebours / fin de combat : toutes les secondes. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public int seconds() {
        return Math.max(1, plugin.getConfig().getInt("ffa.combat-seconds", 20));
    }

    public boolean bypass(Player p) {
        return p.hasPermission(BYPASS);
    }

    /** Vrai si le joueur est en combat DANS le monde FFA. */
    public boolean isInCombat(Player p) {
        Long fin = end.get(p.getUniqueId());
        return fin != null && fin > System.currentTimeMillis() && plugin.ffa().isFfa(p.getWorld());
    }

    /** Secondes de combat restantes (0 si pas en combat). */
    public int remaining(Player p) {
        Long fin = end.get(p.getUniqueId());
        if (fin == null) return 0;
        return (int) Math.max(0, (fin - System.currentTimeMillis() + 999) / 1000);
    }

    public void clear(Player p) {
        end.remove(p.getUniqueId());
    }

    private void tag(Player p) {
        if (p.isDead() || bypass(p) || !plugin.ffa().isFfa(p.getWorld())) return;
        boolean already = isInCombat(p);
        end.put(p.getUniqueId(), System.currentTimeMillis() + seconds() * 1000L);
        // un /spawn ou /lobby en cours est annulé dès qu'on entre en combat
        plugin.warmup().cancel(p, "tp-cancelled-combat");
        if (!already) {
            p.sendMessage(plugin.message("ffa-combat-tag", "%seconds%", String.valueOf(seconds())));
        }
    }

    // ------------------------------------------------------------ coups

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!plugin.ffa().isFfa(victim.getWorld())) return;
        Player attacker = attackerOf(e.getDamager(), victim);
        if (attacker == null || attacker.equals(victim)) return;
        tag(victim);
        tag(attacker);
    }

    private Player attackerOf(Entity damager, Player victim) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        // cristaux / TNT / minecart TNT : on ne connaît pas le poseur, on prend le joueur le plus proche
        if (damager instanceof EnderCrystal || damager instanceof TNTPrimed || damager instanceof ExplosiveMinecart) {
            return nearestOther(victim);
        }
        return null;
    }

    private Player nearestOther(Player victim) {
        Player best = null;
        double bestDist = EXPLOSION_RADIUS_SQ;
        for (Player o : victim.getWorld().getPlayers()) {
            if (o.equals(victim) || o.getGameMode() == GameMode.SPECTATOR) continue;
            double d = o.getLocation().distanceSquared(victim.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    /** Pot PvP : une potion néfaste lancée sur un autre joueur = combat. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;
        if (!plugin.ffa().isFfa(shooter.getWorld()) || !harmful(e.getPotion().getEffects())) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player victim && !victim.equals(shooter)) {
                tag(victim);
                tag(shooter);
            }
        }
    }

    private static boolean harmful(Iterable<PotionEffect> effects) {
        for (PotionEffect fx : effects) {
            PotionEffectType t = fx.getType();
            if (t.equals(PotionEffectType.INSTANT_DAMAGE) || t.equals(PotionEffectType.POISON)
                    || t.equals(PotionEffectType.WITHER) || t.equals(PotionEffectType.SLOWNESS)
                    || t.equals(PotionEffectType.WEAKNESS)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ interdits en combat

    /** Se déconnecter en combat = mort. LOWEST : avant la sauvegarde du stuff par le module kits. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean punish = isInCombat(p) && !bypass(p) && !Bukkit.isStopping();
        end.remove(p.getUniqueId());
        plugin.warmup().forget(p);
        if (!punish) return;
        for (Player o : p.getWorld().getPlayers()) {
            if (!o.equals(p)) o.sendMessage(plugin.message("ffa-combat-quit", "%player%", p.getName()));
        }
        p.setHealth(0.0);
    }

    /** /tp tapé en combat : refusé (les admins skyhub.admin peuvent). Perles et chorus restent permis. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;
        Player p = e.getPlayer();
        if (!isInCombat(p) || p.hasPermission("skyhub.admin")) return;
        e.setCancelled(true);
        p.sendMessage(plugin.message("ffa-in-combat", "%seconds%", String.valueOf(remaining(p))));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        end.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!plugin.ffa().isFfa(e.getPlayer().getWorld())) end.remove(e.getPlayer().getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = end.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || !plugin.ffa().isFfa(p.getWorld())) {
                it.remove();
                continue;
            }
            long rest = (en.getValue() - now + 999) / 1000;
            if (rest <= 0) {
                it.remove();
                p.sendMessage(plugin.message("ffa-combat-end"));
            } else {
                p.sendActionBar(plugin.message("ffa-combat-bar", "%seconds%", String.valueOf(rest)));
            }
        }
    }
}
