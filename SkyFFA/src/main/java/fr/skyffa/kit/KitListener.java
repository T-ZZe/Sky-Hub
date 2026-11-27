package fr.skyffa.kit;

import fr.skyffa.SkyFfaPlugin;
import fr.skyffa.ffa.FfaMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class KitListener implements Listener {

    private final SkyFfaPlugin plugin;
    private final KitManager kits;
    private final KitGui gui;

    public KitListener(SkyFfaPlugin plugin, KitManager kits, KitGui gui) {
        this.plugin = plugin;
        this.kits = kits;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof KitHolder h) {
            gui.click(e, h, p);
            return;
        }
        // Dans le FFA : shift-clic sur une shulker dans son inventaire = on l'ouvre
        if (e.getClickedInventory() == p.getInventory() && e.isShiftClick()
                && top.getType() == InventoryType.CRAFTING && kits.isFfa(p.getWorld())) {
            ItemStack cur = e.getCurrentItem();
            if (cur != null && Tag.SHULKER_BOXES.isTagged(cur.getType())) {
                e.setCancelled(true);
                int slot = e.getSlot();
                Bukkit.getScheduler().runTask(plugin, () -> gui.openShulker(p, slot));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof KitHolder h) gui.drag(e, h);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof KitHolder h && e.getPlayer() instanceof Player p) {
            gui.close(h, p);
        }
    }

    /** Quitter le FFA : le stuff est sauvegardé dans la zone qu'on quitte. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (!kits.isFfa(e.getFrom().getWorld())) return;
        if (to != null && kits.isFfa(to.getWorld())) return;
        FfaMode z = kits.stuffZone(e.getPlayer().getUniqueId());
        if (z != null) kits.saveStuff(e.getPlayer(), z);
        kits.clearPending(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!kits.isFfa(p.getWorld())) return;
        FfaMode z = kits.stuffZone(p.getUniqueId());
        if (z != null) kits.saveStuff(p, z);
        kits.clearPending(p.getUniqueId());
    }

    /** Respawn dans le FFA : le dernier kit utilisé dans cette zone est rééquipé. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.getConfig().getBoolean("kits.give-on-respawn", true)) return;
        Player p = e.getPlayer();
        if (!kits.isFfa(e.getRespawnLocation().getWorld())) return;
        FfaMode z = kits.getMode(p.getUniqueId());
        if (z == null) return;
        int n = kits.last(p.getUniqueId(), z);
        if (n <= 0 || !kits.hasKit(p.getUniqueId(), z, n)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && kits.isFfa(p.getWorld())) kits.loadKit(p, z, n);
        }, 2L);
    }
}
