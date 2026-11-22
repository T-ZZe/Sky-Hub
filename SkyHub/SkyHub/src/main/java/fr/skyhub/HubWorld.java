package fr.skyhub;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Random;

/** Gère le monde void du hub et l'île flottante. */
public final class HubWorld {

    private final SkyHubPlugin plugin;
    private World world;

    public HubWorld(SkyHubPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration cfg = plugin.getConfig();
        String name = cfg.getString("lobby.world-name", "hub");

        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidGenerator())
                    .generateStructures(false)
                    .createWorld();
        }
        if (w == null) {
            throw new IllegalStateException("Impossible de créer le monde du hub : " + name);
        }
        this.world = w;

        w.setSpawnLocation(spawn());
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);

        int y = cfg.getInt("lobby.island-y", 100);
        if (w.getBlockAt(0, y, 0).getType().isAir()) {
            plugin.getLogger().info("Construction de l'île flottante du hub...");
            buildIsland(y, cfg.getInt("lobby.radius", 14));
        }

        // Jour permanent et temps clair dans le hub
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            world.setTime(6000);
            world.setStorm(false);
            world.setThundering(false);
        }, 20L, 100L);
    }

    public World world() {
        return world;
    }

    public boolean isHub(World w) {
        return w != null && world != null && w.getUID().equals(world.getUID());
    }

    public Location spawn() {
        FileConfiguration c = plugin.getConfig();
        return new Location(world,
                c.getDouble("lobby.spawn.x", 0.5),
                c.getDouble("lobby.spawn.y", 101),
                c.getDouble("lobby.spawn.z", 0.5),
                (float) c.getDouble("lobby.spawn.yaw", 180),
                (float) c.getDouble("lobby.spawn.pitch", 0));
    }

    /** Téléporte un joueur au hub. */
    public void sendToHub(Player player) {
        player.teleportAsync(spawn()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> applyLobbyState(player)));
    }

    public void applyLobbyState(Player player) {
        player.setFallDistance(0);
        player.setFoodLevel(20);
        if (!player.hasPermission("skyhub.build")) {
            try {
                player.setGameMode(GameMode.valueOf(
                        plugin.getConfig().getString("lobby.gamemode", "ADVENTURE").toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Construction de l'île
    // ------------------------------------------------------------------

    private void buildIsland(int topY, int radius) {
        Random rnd = new Random(1337L);
        int depth = Math.max(6, radius * 2 / 3);

        // Forme d'île : disque en haut, cône inversé en dessous
        for (int dy = 0; dy <= depth; dy++) {
            double r = radius * Math.pow(1.0 - (double) dy / (depth + 1), 0.65);
            int ri = (int) Math.ceil(r) + 1;
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    double jitter = dy > 2 ? (rnd.nextDouble() - 0.5) * 2.0 : 0.0;
                    if (dist > r + jitter) continue;

                    Material m;
                    if (dy == 0) m = Material.GRASS_BLOCK;
                    else if (dy <= 3) m = Material.DIRT;
                    else m = rnd.nextInt(10) == 0 ? Material.COBBLESTONE : Material.STONE;

                    world.getBlockAt(x, topY - dy, z).setType(m, false);
                }
            }
        }

        // Petites plantes décoratives
        Material[] plants = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.DANDELION,
                Material.POPPY, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.FERN};
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.sqrt(x * x + z * z) >= radius - 1) continue;
                if (world.getBlockAt(x, topY, z).getType() != Material.GRASS_BLOCK) continue;
                if (rnd.nextInt(14) == 0) {
                    world.getBlockAt(x, topY + 1, z).setType(plants[rnd.nextInt(plants.length)], false);
                }
            }
        }

        // Podiums sous les PNJ
        ConfigurationSection npcs = plugin.getConfig().getConfigurationSection("npcs");
        if (npcs != null) {
            for (String id : npcs.getKeys(false)) {
                int cx = (int) Math.floor(npcs.getDouble(id + ".location.x"));
                int cz = (int) Math.floor(npcs.getDouble(id + ".location.z"));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Material m = (dx == 0 && dz == 0) ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS;
                        world.getBlockAt(cx + dx, topY, cz + dz).setType(m, false);
                        world.getBlockAt(cx + dx, topY + 1, cz + dz).setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
