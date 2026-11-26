package fr.skyhub.oneblock.gui;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.utils.Effects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Base des interfaces : tous les clics sont annulés, seuls les items enregistrés réagissent. */
public abstract class Menu implements InventoryHolder {

    protected final OneBlockPlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected Menu(OneBlockPlugin plugin, Player viewer, Component title, int rows) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /** Remplit l'inventaire ; appelé à chaque ouverture/rafraîchissement. */
    protected abstract void build();

    protected void set(int slot, ItemStack item) { inventory.setItem(slot, item); handlers.remove(slot); }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) { inventory.setItem(slot, item); handlers.put(slot, onClick); }

    public void open() {
        inventory.clear();
        handlers.clear();
        build();
        viewer.openInventory(inventory);
        Effects.sound(viewer.getLocation(), "ui.button.click", 0.5f, 1.2f);
    }

    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(inventory)) return;
        Consumer<InventoryClickEvent> h = handlers.get(e.getSlot());
        if (h == null) return;
        try {
            h.accept(e);
        } catch (RuntimeException ex) {
            viewer.closeInventory();
            plugin.messages().send(viewer, "error.gui");
            plugin.getLogger().warning("Erreur GUI " + getClass().getSimpleName() + " : " + ex);
        }
    }

    @Override public Inventory getInventory() { return inventory; }
}
