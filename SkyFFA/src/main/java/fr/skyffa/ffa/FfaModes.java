package fr.skyffa.ffa;

import fr.skyffa.SkyFfaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/** Menu de choix de la zone FFA + spawns propres à chaque zone (même monde). */
public final class FfaModes implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    private final SkyFfaPlugin plugin;

    public FfaModes(SkyFfaPlugin plugin) {
        this.plugin = plugin;
    }

    static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static Component c(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    // ---------------------------------------------------------- monde & spawns

    public String ffaWorldName() {
        return plugin.getConfig().getString("ffa.world", "ffa");
    }

    public World ffaWorld() {
        return Bukkit.getWorld(ffaWorldName());
    }

    public boolean isFfa(World w) {
        return w != null && w.getName().equalsIgnoreCase(ffaWorldName());
    }

    public FfaMode getMode(java.util.UUID id) {
        return plugin.kits().getMode(id);
    }

    /** Spawn de la zone, ou null s'il n'a pas été défini avec /ffa spawn. */
    public Location getSpawn(FfaMode m) {
        World w = ffaWorld();
        String base = "ffa-spawns." + m.key + ".";
        if (m == FfaMode.UHC && !plugin.getConfig().contains(base + "x")) base = "ffa-spawns.diamond."; // ancien nom
        if (w == null || !plugin.getConfig().contains(base + "x")) return null;
        return new Location(w,
                plugin.getConfig().getDouble(base + "x"),
                plugin.getConfig().getDouble(base + "y"),
                plugin.getConfig().getDouble(base + "z"),
                (float) plugin.getConfig().getDouble(base + "yaw"),
                (float) plugin.getConfig().getDouble(base + "pitch"));
    }

    public void setSpawn(FfaMode m, Location l) {
        String base = "ffa-spawns." + m.key + ".";
        plugin.getConfig().set(base + "x", l.getX());
        plugin.getConfig().set(base + "y", l.getY());
        plugin.getConfig().set(base + "z", l.getZ());
        plugin.getConfig().set(base + "yaw", (double) l.getYaw());
        plugin.getConfig().set(base + "pitch", (double) l.getPitch());
        plugin.saveConfig();
    }

    // ---------------------------------------------------------- menu

    public void openMenu(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, c("<gold><bold>FFA</bold> <gray>- choisis ta zone"));
        h.inv = inv;
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.empty());
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        FfaMode[] modes = FfaMode.values();
        for (int i = 0; i < modes.length; i++) {
            FfaMode m = modes[i];
            ItemStack it = new ItemStack(m.icon);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(c(m.color + "<bold>" + m.label));
            meta.lore(List.of(
                    c("<gray>Zone " + m.label + " : même map,"),
                    c("<gray>spawn différent."),
                    Component.empty(),
                    c("<yellow>▶ Clique pour rejoindre")));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            meta.setEnchantmentGlintOverride(true); // les objets du menu FFA brillent
            it.setItemMeta(meta);
            inv.setItem(SLOTS[i], it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        FfaMode[] modes = FfaMode.values();
        for (int i = 0; i < modes.length; i++) {
            if (e.getSlot() == SLOTS[i]) {
                join(p, modes[i]);
                return;
            }
        }
    }

    // ---------------------------------------------------------- rejoindre une zone

    public void join(Player p, FfaMode m) {
        // en combat dans le FFA : pas de changement de zone (c'est une téléportation)
        if (plugin.ffaCombat().isInCombat(p) && !plugin.ffaCombat().bypass(p)) {
            p.closeInventory();
            p.sendMessage(plugin.message("ffa-in-combat",
                    "%seconds%", String.valueOf(plugin.ffaCombat().remaining(p))));
            return;
        }
        World w = ffaWorld();
        if (w == null) {
            p.closeInventory();
            p.sendMessage(plugin.message("world-closed", "%name%", "FFA"));
            return;
        }
        Location dest = getSpawn(m);
        if (dest == null) dest = w.getSpawnLocation().add(0.5, 0, 0.5);

        GameMode gm;
        try {
            gm = GameMode.valueOf(plugin.getConfig().getString("ffa.gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            gm = GameMode.SURVIVAL;
        }
        final GameMode gamemode = gm;

        p.closeInventory();
        // changement de zone dans le FFA : l'ancien stuff reste 0,5 s dans l'inventaire (on peut le jeter),
        // puis il est sauvegardé dans l'ancienne zone et le stuff de la nouvelle zone est récupéré
        FfaMode old = plugin.kits().getMode(p.getUniqueId());
        boolean inFfa = isFfa(p.getWorld());
        final boolean swap = !(inFfa && old == m); // re-cliquer sa zone actuelle ne change rien
        if (old != null && old != m && inFfa) plugin.kits().beginSwitch(p.getUniqueId(), old);
        plugin.kits().setMode(p.getUniqueId(), m);
        p.sendMessage(MM.deserialize("<gray>Direction la zone " + m.colored() + "<gray> !"));
        p.teleportAsync(dest).thenAccept(ok -> {
            if (!ok) return;
            // petit délai : l'inventaire du FFA est restauré par le changement de monde
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.setGameMode(gamemode);
                p.setFallDistance(0);
            }, 3L);
            // le stuff de CETTE zone est récupéré (après la restauration de l'inventaire du monde FFA)
            if (!swap) return;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && isFfa(p.getWorld())) plugin.kits().finishSwitch(p, m);
            }, 10L); // 0,5 s
        });
    }
}
