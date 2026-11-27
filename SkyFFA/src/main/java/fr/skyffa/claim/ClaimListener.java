package fr.skyffa.claim;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ClaimListener implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final ClaimManager claims;

    public ClaimListener(ClaimManager claims) {
        this.claims = claims;
    }

    /** OP en créatif : ignore les règles des zones (pour construire l'arène). */
    private static boolean bypass(Player p) {
        return p.hasPermission("skyhub.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    private static void deny(Player p, String msg) {
        p.sendActionBar(MM.deserialize("<red>" + msg));
    }

    // ------------------------------------------------------------ poser
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Claim c = claims.at(e.getBlockPlaced());
        if (c == null || bypass(p)) return;
        if (!c.place) {
            e.setCancelled(true);
            deny(p, "Tu ne peux pas poser de blocs ici.");
            return;
        }
        claims.track(c, e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        Claim c = claims.at(e.getBlock());
        if (c == null || bypass(p)) return;
        if (!c.place) {
            e.setCancelled(true);
            deny(p, "Tu ne peux pas poser de blocs ici.");
            return;
        }
        claims.track(c, e.getBlock());
    }

    // ------------------------------------------------------------ casser
    /** Sans le droit de casser, on peut quand même casser ce que l'on a posé. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (claims.isWand(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            return;
        }
        Claim c = claims.at(e.getBlock());
        if (c == null || bypass(p)) return;
        if (claims.isTracked(c, e.getBlock())) {
            claims.untrack(c, e.getBlock());
            return;
        }
        if (!c.breakAllowed) {
            e.setCancelled(true);
            deny(p, "Tu ne peux pas casser ce bloc ici.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Claim c = claims.at(e.getBlock());
        if (c == null || bypass(p)) return;
        if (claims.isTracked(c, e.getBlock())) {
            claims.untrack(c, e.getBlock());
            return;
        }
        if (!c.breakAllowed) {
            e.setCancelled(true);
            deny(p, "Tu ne peux pas casser ce bloc ici.");
        }
    }

    // ------------------------------------------------------------ wind charge
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof AbstractWindCharge)) return;
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        Claim c = claims.at(p.getLocation());
        if (c == null || c.windcharge || bypass(p)) return;
        e.setCancelled(true);
        deny(p, "Les wind charges sont interdites ici.");
    }

    // ------------------------------------------------------------ perles d'Ender
    /** Zone avec un délai de perle : après un lancer, impossible d'en relancer avant la fin du délai (cooldown vanilla visible). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof EnderPearl)) return;
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        Claim c = claims.at(p.getLocation());
        if (c == null || c.pearlSec <= 0 || bypass(p)) return;
        p.setCooldown(Material.ENDER_PEARL, c.pearlSec * 20);
    }

    // ------------------------------------------------------------ jeter des objets
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Claim c = claims.at(p.getLocation());
        if (c == null || c.drop || bypass(p)) return;
        e.setCancelled(true);
        deny(p, "Tu ne peux pas jeter d'objets ici.");
    }

    // ------------------------------------------------------------ pvp
    /** PvP désactivé dans la zone : aucun dégât entre joueurs (épée, flèche, cristal, TNT, chariot...). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamageSource().getCausingEntity() instanceof Player attacker) || attacker.equals(victim)) return;
        Claim c = claims.at(victim.getLocation());
        if (c == null || c.pvp) return;
        e.setCancelled(true);
        deny(attacker, "Le PvP est désactivé ici.");
    }

    // ------------------------------------------------------------ explosions
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protectedFromExplosion);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protectedFromExplosion);
    }

    /** Les blocs d'origine d'une zone où l'on ne peut pas casser résistent aux explosions. */
    private boolean protectedFromExplosion(Block b) {
        Claim c = claims.at(b);
        return c != null && !c.breakAllowed && !claims.isTracked(c, b);
    }

    // ------------------------------------------------------------ baguette de sélection
    @EventHandler(priority = EventPriority.HIGH)
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!claims.isWand(e.getItem())) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (!bypass(p)) {
            p.sendMessage(MM.deserialize("<red>Il faut être OP et en créatif pour sélectionner une zone."));
            return;
        }
        Location l = e.getClickedBlock().getLocation();
        int which = a == Action.LEFT_CLICK_BLOCK ? 1 : 2;
        claims.setPos(p.getUniqueId(), which, l);
        p.sendMessage(MM.deserialize("<green>Position " + which + " : <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
        Location[] sel = claims.selection(p.getUniqueId());
        if (sel != null && sel[0].getWorld().equals(sel[1].getWorld())) {
            long vol = (long) (Math.abs(sel[0].getBlockX() - sel[1].getBlockX()) + 1)
                    * (Math.abs(sel[0].getBlockY() - sel[1].getBlockY()) + 1)
                    * (Math.abs(sel[0].getBlockZ() - sel[1].getBlockZ()) + 1);
            p.sendMessage(MM.deserialize("<gray>Sélection : " + vol + " blocs. <yellow>/claim create <nom></yellow>"));
        }
    }
}
