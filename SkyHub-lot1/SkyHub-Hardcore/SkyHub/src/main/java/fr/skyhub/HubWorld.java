package fr.skyhub;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** Gère le monde void du hub : île flottante + sphère noire. */
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

        int y = islandY();
        if (w.getBlockAt(0, y, 0).getType().isAir()) {
            plugin.getLogger().info("Construction de l'île flottante du hub...");
            buildIsland(y, cfg.getInt("lobby.radius", 14));
        }

        if (cfg.getBoolean("lobby.sphere.enabled", true)) {
            int bottomY = sphereCenterY() - sphereRadius();
            if (w.getBlockAt(0, bottomY, 0).getType() != sphereMaterial()) {
                plugin.getLogger().info("Construction de la sphère noire du hub...");
                buildSphere();
            }
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

    /** Sous cette hauteur, un joueur du hub est ramené au spawn. */
    public double voidLimit() {
        return islandY() - plugin.getConfig().getInt("lobby.void-teleport-depth", 5);
    }

    /** Téléporte un joueur au hub. */
    public CompletableFuture<Void> sendToHub(Player player) {
        // On retient où il était pour le remettre au même endroit à son retour
        if (plugin.getConfig().getBoolean("lobby.save-positions", true) && !isHub(player.getWorld())) {
            plugin.locations().save(player);
        }
        return player.teleportAsync(spawn()).thenAccept(ok ->
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
    //  Paramètres
    // ------------------------------------------------------------------

    private int islandY() {
        return plugin.getConfig().getInt("lobby.island-y", 100);
    }

    private int sphereRadius() {
        return plugin.getConfig().getInt("lobby.sphere.radius", 24);
    }

    private int sphereCenterY() {
        return islandY() + plugin.getConfig().getInt("lobby.sphere.center-offset-y", 10);
    }

    private Material sphereMaterial() {
        Material m = Material.matchMaterial(
                plugin.getConfig().getString("lobby.sphere.material", "BLACK_CONCRETE"));
        return (m != null && m.isBlock()) ? m : Material.BLACK_CONCRETE;
    }

    // ------------------------------------------------------------------
    //  Construction
    // ------------------------------------------------------------------

    private void buildSphere() {
        FileConfiguration c = plugin.getConfig();
        int r = sphereRadius();
        int t = Math.max(1, c.getInt("lobby.sphere.thickness", 2));
        int cy = sphereCenterY();
        Material mat = sphereMaterial();

        double outer = (r + 0.5) * (r + 0.5);
        double inner = (r - t + 0.5) * (r - t + 0.5);

        // Coque creuse
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d2 = x * x + y * y + z * z;
                    if (d2 <= outer && d2 > inner) {
                        world.getBlockAt(x, cy + y, z).setType(mat, false);
                    }
                }
            }
        }

        // Blocs de lumière invisibles (sinon on ne voit rien dans une sphère fermée)
        if (c.getBoolean("lobby.sphere.lighting", true)) {
            BlockData light = Bukkit.createBlockData("minecraft:light[level=15]");
            int step = 6;
            int lim = r - t - 2;
            int span = (lim / step) * step;
            for (int x = -span; x <= span; x += step) {
                for (int y = -span; y <= span; y += step) {
                    for (int z = -span; z <= span; z += step) {
                        if (x * x + y * y + z * z > lim * lim) continue;
                        Block b = world.getBlockAt(x, cy + y, z);
                        if (b.getType().isAir()) {
                            b.setBlockData(light, false);
                        }
                    }
                }
            }
        }
    }

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

        // Podiums sous les mobs (positions par défaut)
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
