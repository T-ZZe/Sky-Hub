package fr.skyhub;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class NpcListener implements Listener {

    private final NpcManager npcs;

    public NpcListener(NpcManager npcs) {
        this.npcs = npcs;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        String id = npcs.getNpcId(event.getRightClicked());
        if (id == null) return;

        event.setCancelled(true); // pas de menu d'échange
        if (event.getHand() != EquipmentSlot.HAND) return;
        npcs.teleport(event.getPlayer(), id);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (npcs.getNpcId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }
}
