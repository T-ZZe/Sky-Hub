package fr.skyhub;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class NpcListener implements Listener {

    private final NpcManager npcs;

    public NpcListener(NpcManager npcs) {
        this.npcs = npcs;
    }

    /** Clic droit = téléportation. */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        String id = npcs.getNpcId(event.getRightClicked());
        if (id == null) return;

        event.setCancelled(true); // pas de monture, pas de menu, pas de laisse...
        if (event.getHand() != EquipmentSlot.HAND) return;
        npcs.teleport(event.getPlayer(), id);
    }

    /** Les mobs sont invincibles. Un clic gauche d'un joueur normal téléporte aussi. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        String id = npcs.getNpcId(event.getEntity());
        if (id == null) return;

        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player player
                && !player.hasPermission("skyhub.build")) {
            npcs.teleport(player, id);
        }
    }
}
