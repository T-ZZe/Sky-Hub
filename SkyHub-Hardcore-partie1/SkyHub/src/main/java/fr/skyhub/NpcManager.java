package fr.skyhub;

import fr.skyhub.hardcore.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Crée les mobs de téléportation (Wither Squelette, One Block, Breeze...) et gère les téléportations. */
public final class NpcManager {

    private final SkyHubPlugin plugin;
    private final HubWorld hub;
    private final NamespacedKey npcKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public NpcManager(SkyHubPlugin plugin, HubWorld hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.npcKey = new NamespacedKey(plugin, "npc_id");
    }

    // ------------------------------------------------------------------
    //  Infos
    // ------------------------------------------------------------------

    public Set<String> ids() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("npcs");
        return s == null ? Set.of() : s.getKeys(false);
    }

    /** Retrouve l'id (sans tenir compte de la casse), ou null. */
    public String matchId(String input) {
        for (String id : ids()) {
            if (id.equalsIgnoreCase(input)) return id;
        }
        return null;
    }

    public String displayName(String id) {
        return plugin.getConfig().getString("npcs." + id + ".name", id);
    }

    /** Retourne l'id du mob si l'entité en est un, sinon null. */
    public String getNpcId(Entity entity) {
        return entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    //  Création / suppression
    // ------------------------------------------------------------------

    /** Supprime tous les anciens mobs et recrée ceux de la config. */
    public void spawnAll() {
        World world = hub.world();
        for (String id : ids()) loadChunk(id);

        for (Entity e : world.getEntities()) {
            if (getNpcId(e) != null) e.remove();
        }
        for (String id : ids()) spawn(id);
    }

    /** Recrée le mob "id" : l'ancien est supprimé. */
    public void spawn(String id) {
        World world = hub.world();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("npcs." + id);
        if (s == null) return;

        loadChunk(id);
        for (Entity e : world.getEntities()) {
            if (id.equals(getNpcId(e))) e.remove();
        }

        Location loc = readLocation(world, s);
        String defaultType = switch (id.toLowerCase(Locale.ROOT)) {
            case "hardcore" -> "wither_skeleton";
            case "oneblock" -> "iron_golem";
            case "ffa" -> "breeze";
            default -> "villager";
        };
        EntityType type = Registry.ENTITY_TYPE.get(
                NamespacedKey.minecraft(s.getString("entity", defaultType).toLowerCase(Locale.ROOT)));
        if (type == null) {
            plugin.getLogger().warning("Type de mob inconnu pour '" + id + "', villageois utilisé.");
            type = EntityType.VILLAGER;
        }

        Entity entity = world.spawnEntity(loc, type);
        if (!(entity instanceof LivingEntity mob)) {
            plugin.getLogger().warning("Le type de '" + id + "' n'est pas un mob vivant.");
            entity.remove();
            return;
        }

        mob.setAI(false);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCollidable(false);
        mob.setCanPickupItems(false);
        mob.setRotation(loc.getYaw(), loc.getPitch());
        mob.customName(MiniMessage.miniMessage().deserialize(s.getString("name", id)));
        mob.setCustomNameVisible(true);
        mob.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);

        // Mob plus grand + glowing coloré
        double baseHeight = mob.getHeight();
        double scale = Math.max(0.0625, Math.min(16.0, s.getDouble("scale", 2.0)));
        AttributeInstance sc = mob.getAttribute(Attribute.SCALE);
        if (sc != null) sc.setBaseValue(scale); else scale = 1.0;
        mob.setGlowing(true);
        applyGlow(id, mob, s.getString("glow-color", defaultGlow(id)));

        // Titre géant flottant au-dessus du mob (remplace le petit nom)
        boolean titleOn = s.getBoolean("title-enabled", true);
        mob.setCustomNameVisible(!titleOn);
        if (titleOn) spawnTitle(id, s, loc, baseHeight * scale);

        // Wither squelette sans épée, etc.
        if (s.getBoolean("clear-equipment", true)) {
            EntityEquipment eq = mob.getEquipment();
            if (eq != null) eq.clear();
        }
        plugin.getLogger().info("Mob '" + id + "' posé : " + mob.getType() + " en "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    private String defaultGlow(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "hardcore" -> "dark_red";
            case "oneblock" -> "green";
            case "ffa" -> "gold";
            default -> "white";
        };
    }

    /** Glowing de la couleur choisie (via une team du scoreboard principal). */
    private void applyGlow(String id, LivingEntity mob, String colorName) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String tn = ("sh_" + id).toLowerCase(Locale.ROOT);
        if (tn.length() > 16) tn = tn.substring(0, 16);
        Team team = sb.getTeam(tn);
        if (team == null) team = sb.registerNewTeam(tn);
        for (String e : new ArrayList<>(team.getEntries())) team.removeEntry(e);
        NamedTextColor col = NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT));
        team.color(col != null ? col : NamedTextColor.WHITE);
        team.addEntity(mob);
    }

    private void spawnTitle(String id, ConfigurationSection s, Location loc, double mobHeight) {
        float ts = (float) Math.max(0.5, s.getDouble("title-scale", 4.0));
        String text = s.getString("title", s.getString("name", id));
        Location at = loc.clone().add(0, mobHeight + 0.4 + 0.1 * ts, 0);
        at.setYaw(0);
        at.setPitch(0);
        loc.getWorld().spawn(at, TextDisplay.class, td -> {
            td.text(MiniMessage.miniMessage().deserialize(text));
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setViewRange(2.0f);
            td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(ts, ts, ts), new AxisAngle4f()));
            td.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);
        });
    }

    /** Nom du monde de destination du mob. */
    public String worldName(String id) {
        return plugin.getConfig().getString("npcs." + id + ".world", "world");
    }

    /** Enregistre la position du mob (face au spawn), supprime l'ancien et en pose un nouveau. */
    public void setLocation(String id, Location where) {
        Location spawn = hub.spawn();
        double dx = spawn.getX() - where.getX();
        double dz = spawn.getZ() - where.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        String base = "npcs." + id + ".location.";
        plugin.getConfig().set(base + "x", where.getX());
        plugin.getConfig().set(base + "y", where.getY());
        plugin.getConfig().set(base + "z", where.getZ());
        plugin.getConfig().set(base + "yaw", yaw);
        plugin.getConfig().set(base + "pitch", 0.0);
        plugin.saveConfig();

        spawn(id);
    }

    private void loadChunk(String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("npcs." + id);
        if (s == null) return;
        World world = hub.world();
        int cx = ((int) Math.floor(s.getDouble("location.x"))) >> 4;
        int cz = ((int) Math.floor(s.getDouble("location.z"))) >> 4;
        world.getChunkAt(cx, cz);
        world.addPluginChunkTicket(cx, cz, plugin);
    }

    // ------------------------------------------------------------------
    //  Téléportation
    // ------------------------------------------------------------------

    /** Téléporte le joueur vers le monde associé au mob. */
    public void teleport(Player player, String id) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < 1500) return;
        cooldowns.put(player.getUniqueId(), now);

        // Le mob FFA ouvre le choix de zone (UHC / Mace / Crystal / Cart PvP / Pot)
        if (id.equalsIgnoreCase("ffa")) {
            plugin.ffa().openMenu(player);
            return;
        }

        // Le mob One Block envoie sur l'île du joueur (créée automatiquement la première fois)
        if (id.equalsIgnoreCase("oneblock")) {
            if (plugin.oneBlock() == null) {
                player.sendMessage(plugin.message("world-closed", "%name%", displayName(id)));
                return;
            }
            player.sendMessage(plugin.message("teleporting", "%name%", displayName(id)));
            plugin.oneBlock().enter(player);
            return;
        }

        ConfigurationSection s = plugin.getConfig().getConfigurationSection("npcs." + id);
        if (s == null) return;

        String worldName = s.getString("world", "world");
        World target = Bukkit.getWorld(worldName);
        if (target == null) {
            player.sendMessage(plugin.message("world-closed", "%name%", displayName(id)));
            return;
        }

        // Banni du hardcore : l'accès au monde est refusé avec le message de ban
        if (plugin.isHardcoreWorld(target) && !player.hasPermission("hardcore.bypass")) {
            Long until = plugin.data().activeBan(player.getUniqueId());
            if (until != null) {
                player.sendMessage(Gui.banMessage("Tu es banni du monde hardcore.", until));
                return;
            }
        }

        GameMode gm;
        try {
            gm = GameMode.valueOf(s.getString("gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            gm = GameMode.SURVIVAL;
        }
        final GameMode gamemode = gm;

        player.sendMessage(plugin.message("teleporting", "%name%", displayName(id)));
        // Retour à la dernière position dans ce monde (sinon spawn du monde)
        Location dest = null;
        if (plugin.getConfig().getBoolean("lobby.save-positions", true)) {
            dest = plugin.locations().get(player.getUniqueId(), target);
        }
        // Première arrivée dans le monde hardcore : spawn aléatoire dans un rayon autour du spawn
        if (plugin.isHardcoreWorld(target) && !plugin.data().hasFirstSpawn(player.getUniqueId())) {
            if (dest == null) {
                Location random = randomSpawn(target);
                if (random != null) dest = random;
            }
            plugin.data().markFirstSpawn(player.getUniqueId());
        }
        if (dest == null) {
            dest = target.getSpawnLocation().add(0.5, 0, 0.5);
        }
        player.teleportAsync(dest).thenAccept(ok -> {
            if (ok) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setGameMode(gamemode);
                    player.setFallDistance(0);
                });
            }
        });
    }

    /** Point aléatoire sûr dans un disque autour du spawn du monde (rayon : hardcore.spawn-radius, 250 par défaut). */
    private Location randomSpawn(World w) {
        if (w.getEnvironment() != World.Environment.NORMAL) return null;
        int radius = Math.max(1, plugin.getConfig().getInt("hardcore.spawn-radius", 250));
        Location center = w.getSpawnLocation();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double r = radius * Math.sqrt(rnd.nextDouble()); // répartition uniforme dans le disque
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * r);
            int y = w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
            org.bukkit.block.Block ground = w.getBlockAt(x, y, z);
            if (ground.isLiquid() || unsafeGround(ground.getType())) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5, (float) (rnd.nextDouble() * 360 - 180), 0f);
        }
        return null; // pas d'endroit sûr trouvé : spawn normal du monde
    }

    private static boolean unsafeGround(Material m) {
        return switch (m) {
            case CACTUS, MAGMA_BLOCK, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH,
                 POWDER_SNOW, WITHER_ROSE, CAVE_AIR, VOID_AIR, AIR -> true;
            default -> false;
        };
    }

    private World resolveWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (folder.isDirectory()) {
            return new WorldCreator(name).createWorld();
        }
        return null;
    }

    private Location readLocation(World world, ConfigurationSection s) {
        return new Location(world,
                s.getDouble("location.x"),
                s.getDouble("location.y"),
                s.getDouble("location.z"),
                (float) s.getDouble("location.yaw"),
                (float) s.getDouble("location.pitch"));
    }
}
