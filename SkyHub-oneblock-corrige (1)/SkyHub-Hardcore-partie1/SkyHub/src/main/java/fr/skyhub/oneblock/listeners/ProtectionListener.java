package fr.skyhub.oneblock.listeners;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.island.IslandFlag;
import fr.skyhub.oneblock.island.IslandManager;
import fr.skyhub.oneblock.island.IslandRole;
import fr.skyhub.oneblock.island.IslandSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Protection des îles : droits par rôle, coffres, entités, explosions, feu, pistons, liquides. */
public final class ProtectionListener implements Listener {

    private static final Set<Material> BUILD_ITEMS = Set.of(Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.ARMOR_STAND,
            Material.END_CRYSTAL, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.PAINTING);

    private final OneBlockPlugin plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public ProtectionListener(OneBlockPlugin plugin) { this.plugin = plugin; }

    private IslandManager im() { return plugin.islands(); }
    private IslandSettings cfg() { return plugin.content().islands(); }

    // ---------------------------------------------------------- droits

    private boolean can(Player p, Location loc, IslandFlag flag) {
        if (loc.getWorld() == null || !loc.getWorld().equals(im().world())) return true;
        if (p.hasPermission("oneblock.bypass")) return true;
        Island i = im().islandAt(loc);
        if (i == null) return false;
        IslandRole role = i.isOwner(p.getUniqueId()) ? IslandRole.OWNER : i.isMember(p.getUniqueId()) ? IslandRole.MEMBER : null;
        if (role == null) return cfg().allowed(null, flag) ;
        return cfg().allowed(role, flag);
    }

    private void deny(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastWarn.put(p.getUniqueId(), now);
        p.sendActionBar(plugin.messages().get("protection.denied"));
    }

    public void forget(UUID u) { lastWarn.remove(u); }

    /** Les visiteurs ne peuvent pas ramasser les objets au sol d'une île qui n'est pas la leur. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!e.getItem().getWorld().equals(im().world())) return;
        if (!can(p, e.getItem().getLocation(), IslandFlag.CONTAINER)) e.setCancelled(true);
    }

    private boolean inIslandWorld(Entity e) { return e.getWorld().equals(im().world()); }

    private static Player shooter(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---------------------------------------------------------- blocs

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!can(e.getPlayer(), e.getBlock().getLocation(), IslandFlag.BUILD)) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!can(e.getPlayer(), e.getBlock().getLocation(), IslandFlag.BUILD) || im().isOneBlock(e.getBlock())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!can(e.getPlayer(), e.getBlock().getLocation(), IslandFlag.BUILD) || im().isOneBlock(e.getBlock())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!can(e.getPlayer(), e.getBlock().getLocation(), IslandFlag.BUILD)) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent e) {
        if (!can(e.getPlayer(), e.getEntity().getLocation(), IslandFlag.ANIMALS)) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null || !b.getWorld().equals(im().world())) return;
        boolean allowed;
        if (e.getAction() == Action.PHYSICAL) {
            allowed = can(p, b.getLocation(), IslandFlag.INTERACT);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material hand = e.getItem() == null ? Material.AIR : e.getItem().getType();
            if (hand.name().endsWith("_SPAWN_EGG") || BUILD_ITEMS.contains(hand)) {
                allowed = can(p, b.getLocation(), IslandFlag.BUILD);
            } else if (b.getState(false) instanceof Container) {
                allowed = can(p, b.getLocation(), IslandFlag.CONTAINER);
            } else if (b.getType().isInteractable() && !p.isSneaking()) {
                allowed = can(p, b.getLocation(), IslandFlag.INTERACT);
            } else {
                allowed = true;
            }
        } else {
            return;
        }
        if (!allowed) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            e.setCancelled(true);
            deny(p);
        }
    }

    // ---------------------------------------------------------- entités

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (t instanceof Player) return;
        IslandFlag flag = t instanceof StorageMinecart ? IslandFlag.CONTAINER : (t instanceof Animals || t instanceof org.bukkit.entity.Villager) ? IslandFlag.ANIMALS : IslandFlag.INTERACT;
        if (!can(e.getPlayer(), t.getLocation(), flag)) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!can(e.getPlayer(), e.getRightClicked().getLocation(), IslandFlag.BUILD)) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && !can(e.getPlayer(), e.getEntity().getLocation(), IslandFlag.BUILD)) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = shooter(e.getRemover());
        if (p != null && !can(p, e.getEntity().getLocation(), IslandFlag.BUILD)) { e.setCancelled(true); deny(p); }
        else if (p == null && inIslandWorld(e.getEntity()) && !(e.getRemover() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        Player p = e.getAttacker() == null ? null : shooter(e.getAttacker());
        if (p != null && !can(p, e.getVehicle().getLocation(), IslandFlag.BUILD)) { e.setCancelled(true); deny(p); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!inIslandWorld(victim)) return;
        Player attacker = shooter(e.getDamager());
        if (attacker == null) return;
        if (attacker.hasPermission("oneblock.bypass")) return;
        if (victim instanceof Player) {
            if (!cfg().pvp()) { e.setCancelled(true); deny(attacker); }
            return;
        }
        if (victim instanceof Enemy) return; // monstres : toujours attaquables
        IslandFlag flag = (victim instanceof Hanging || victim instanceof Vehicle || victim instanceof org.bukkit.entity.ArmorStand) ? IslandFlag.BUILD : IslandFlag.ANIMALS;
        if (!can(attacker, victim.getLocation(), flag)) { e.setCancelled(true); deny(attacker); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!inIslandWorld(e.getEntity())) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        boolean explosion = c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
        if (explosion && !cfg().explosionsDamageEntities()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!inIslandWorld(e.getEntity()) || e.getEntity() instanceof Player || e.getEntity() instanceof FallingBlock) return;
        if (!cfg().mobGriefing() || im().isOneBlock(e.getBlock())) e.setCancelled(true);
    }

    // ---------------------------------------------------------- explosions, feu, liquides, pistons

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (inIslandWorld(e.getEntity())) filterExplosion(e.blockList(), e.getLocation());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (e.getBlock().getWorld().equals(im().world())) filterExplosion(e.blockList(), e.getBlock().getLocation());
    }

    private void filterExplosion(List<Block> blocks, Location origin) {
        if (!cfg().explosionsBreakBlocks()) { blocks.clear(); return; }
        Island src = im().islandAt(origin);
        blocks.removeIf(b -> im().isOneBlock(b) || src == null || im().islandAt(b.getLocation()) != src);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (e.getBlock().getWorld().equals(im().world()) && (!cfg().fireSpread() || im().isOneBlock(e.getBlock()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!e.getBlock().getWorld().equals(im().world())) return;
        if (e.getPlayer() != null) return; // contrôlé par onInteract
        if (!cfg().fireSpread()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (!to.getWorld().equals(im().world())) return;
        Island a = im().islandAt(e.getBlock().getLocation()), b = im().islandAt(to.getLocation());
        if (b == null || a != b || b.isOneBlock(to)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlock().getWorld().equals(im().world()) && !pistonSafe(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlock().getWorld().equals(im().world()) && !pistonSafe(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    private boolean pistonSafe(Block piston, List<Block> moved, BlockFace dir) {
        Island home = im().islandAt(piston.getLocation());
        if (home == null) return false;
        Block head = piston.getRelative(dir);
        if (im().isOneBlock(head) || im().islandAt(head.getLocation()) != home) return false;
        for (Block b : moved) {
            Block dest = b.getRelative(dir);
            if (im().isOneBlock(b) || im().isOneBlock(dest) || im().islandAt(dest.getLocation()) != home || im().islandAt(b.getLocation()) != home) return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (!e.getLocation().getWorld().equals(im().world())) return;
        Island src = im().islandAt(e.getLocation());
        e.getBlocks().removeIf(s -> im().isOneBlock(s.getBlock()) || src == null || im().islandAt(s.getLocation()) != src);
    }
}
