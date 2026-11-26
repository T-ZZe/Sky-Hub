package fr.skyhub.menu;

import fr.skyhub.SkyHubPlugin;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Limites et réglages du menu : actifs UNIQUEMENT dans le monde Hardcore
 * (world, world_nether et world_the_end). Partout ailleurs, aucune règle ne s'applique.
 */
public class LimitManager {

    public static final int NO_PIECE_LIMIT = WorldLimits.NO_PIECE_LIMIT;

    private final SkyHubPlugin plugin;
    private final File file;
    private final Map<String, WorldLimits> worlds = new HashMap<>();

    public LimitManager(SkyHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "limits.yml");
    }

    /** Réglages neutres (aucune limite, aucun cooldown) : utilisés partout sauf dans le monde Hardcore. */
    private final WorldLimits neutral = new WorldLimits(() -> { });

    /** Clé du monde Hardcore (world, world_nether et world_the_end partagent les mêmes réglages). */
    public String hardcoreKey() {
        return plugin.getConfig().getString("hardcore.world", "world").toLowerCase(Locale.ROOT);
    }

    /** Vrai si le menu s'applique dans ce monde (monde Hardcore, son Nether et son End). */
    public boolean active(World w) {
        return plugin.isHardcoreWorld(w);
    }

    /** Réglages du monde Hardcore, éditables avec /menu. */
    public WorldLimits hardcore() {
        return worlds.computeIfAbsent(hardcoreKey(), x -> new WorldLimits(this::save));
    }

    /** Réglages appliqués dans ce monde : ceux du Hardcore, ou neutres ailleurs. */
    public WorldLimits of(World w) {
        return active(w) ? hardcore() : neutral;
    }

    public void load() {
        worlds.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("worlds");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            WorldLimits wl = new WorldLimits(this::save);
            wl.load(sec.getConfigurationSection(k));
            worlds.put(k.toLowerCase(Locale.ROOT), wl);
        }
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (var e : worlds.entrySet()) {
            e.getValue().write(cfg.createSection("worlds." + e.getKey()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder limits.yml : " + ex.getMessage());
        }
    }
}
