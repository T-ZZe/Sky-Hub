package fr.skyhub.oneblock.listeners;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.island.IslandMember;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/** Monde des îles : pas d'apparition naturelle, chute dans le vide, statistiques de mobs, connexion. */
public final class WorldListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> DENIED = Set.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN, CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS, CreatureSpawnEvent.SpawnReason.JOCKEY, CreatureSpawnEvent.SpawnReason.MOUNT,
            CreatureSpawnEvent.SpawnReason.TRAP, CreatureSpawnEvent.SpawnReason.DEFAULT, CreatureSpawnEvent.SpawnReason.NETHER_PORTAL);

    private final OneBlockPlugin plugin;
    private final ProtectionListener protection;

    public WorldListener(OneBlockPlugin plugin, ProtectionListener protection) { this.plugin = plugin; this.protection = protection; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getLocation().getWorld().equals(plugin.islands().world()) && DENIED.contains(e.getSpawnReason())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID || !(e.getEntity() instanceof Player p)) return;
        if (!p.getWorld().equals(plugin.islands().world()) || !plugin.content().islands().voidTeleport()) return;
        e.setCancelled(true);
        p.setFallDistance(0);
        Island i = plugin.islands().islandOf(p.getUniqueId());
        if (i != null) plugin.islands().teleportHome(p, i); else p.teleportAsync(plugin.islands().spawn());
    }

    /** Une mort d'un mob du OneBlock par un joueur compte une fois pour l'île (statistique). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Entity ent = e.getEntity();
        Integer id = ent.getPersistentDataContainer().get(plugin.islandKey(), PersistentDataType.INTEGER);
        if (id == null || e.getEntity().getKiller() == null) return;
        Island i = plugin.islands().byId(id);
        if (i == null) return;
        ent.getPersistentDataContainer().remove(plugin.islandKey()); // garantit un seul comptage
        i.addMobKill();
        plugin.islands().markDirty(i);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Island i = plugin.islands().islandOf(p.getUniqueId());
        if (i != null) {
            IslandMember m = i.member(p.getUniqueId());
            if (m != null && !m.name.equals(p.getName())) { m.name = p.getName(); plugin.islands().markDirty(i); }
        }
        if (p.getWorld().equals(plugin.islands().world())) {
            Island at = plugin.islands().islandAt(p.getLocation());
            if (at == null && !p.hasPermission("oneblock.bypass")) {
                if (i != null) plugin.islands().teleportHome(p, i); else p.teleportAsync(plugin.islands().spawn());
            } else if (at != null) {
                plugin.islands().ensureOneBlock(at);
            }
        }
        if (plugin.islands().pendingInvite(p.getUniqueId()) != null) plugin.messages().send(p, "invite.pending");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { protection.forget(e.getPlayer().getUniqueId()); }
}
