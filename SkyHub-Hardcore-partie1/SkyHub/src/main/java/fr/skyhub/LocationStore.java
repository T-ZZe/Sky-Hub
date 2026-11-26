package fr.skyhub;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Sauvegarde la dernière position de chaque joueur dans chaque monde (fichier locations.yml). */
public final class LocationStore {

    private final SkyHubPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public LocationStore(SkyHubPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private String path(UUID id, String worldName) {
        return "players." + id + "." + worldName.replace('.', '_');
    }

    /** Enregistre la position actuelle du joueur pour le monde où il se trouve. */
    public void save(Player player) {
        Location l = player.getLocation();
        String base = path(player.getUniqueId(), l.getWorld().getName());
        data.set(base + ".x", l.getX());
        data.set(base + ".y", l.getY());
        data.set(base + ".z", l.getZ());
        data.set(base + ".yaw", (double) l.getYaw());
        data.set(base + ".pitch", (double) l.getPitch());
        write();
    }

    /** Dernière position connue du joueur dans ce monde, ou null. */
    public Location get(UUID id, World world) {
        ConfigurationSection s = data.getConfigurationSection(path(id, world.getName()));
        if (s == null) return null;
        return new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void remove(UUID id, String worldName) {
        data.set(path(id, worldName), null);
        write();
    }

    private void write() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire locations.yml : " + e.getMessage());
        }
    }
}
