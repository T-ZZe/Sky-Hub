package fr.skyhub;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Inventaire + XP séparés par monde : chaque monde garde son équipement.
 * Le nether et l'end d'un monde partagent l'inventaire de ce monde
 * (world, world_nether, world_the_end = même groupe).
 */
public final class InventoryManager implements Listener {

    private final SkyHubPlugin plugin;
    private final File folder;

    public InventoryManager(SkyHubPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "inventories");
        folder.mkdirs();
    }

    public static String group(World w) {
        String n = w.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith("_the_end")) n = n.substring(0, n.length() - 8);
        else if (n.endsWith("_nether")) n = n.substring(0, n.length() - 7);
        return n;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!plugin.getConfig().getBoolean("inventories.separate", true)) return;
        Player p = e.getPlayer();
        String from = group(e.getFrom());
        String to = group(p.getWorld());
        if (from.equals(to)) return;

        File file = new File(folder, p.getUniqueId() + ".yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // 1) on range l'équipement du monde quitté
        String base = "groups." + from;
        List<String> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            items.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        cfg.set(base + ".items", items);
        cfg.set(base + ".level", p.getLevel());
        cfg.set(base + ".exp", (double) p.getExp());

        // 2) on charge celui du monde d'arrivée (vide si jamais visité)
        ConfigurationSection s = cfg.getConfigurationSection("groups." + to);
        p.getInventory().clear();
        p.setLevel(0);
        p.setExp(0f);
        if (s != null) {
            List<String> saved = s.getStringList("items");
            ItemStack[] contents = new ItemStack[p.getInventory().getSize()];
            for (int i = 0; i < saved.size() && i < contents.length; i++) {
                String str = saved.get(i);
                if (!str.isEmpty()) {
                    try {
                        contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(str));
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Objet illisible pour " + p.getName() + " (slot " + i + ")");
                    }
                }
            }
            p.getInventory().setContents(contents);
            p.setLevel(s.getInt("level"));
            p.setExp((float) s.getDouble("exp"));
        }
        p.updateInventory();

        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder l'inventaire de " + p.getName() + " : " + ex.getMessage());
        }
    }
}
