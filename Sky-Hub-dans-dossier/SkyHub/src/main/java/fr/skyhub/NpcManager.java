package fr.skyhub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.Locale;

/** Crée les PNJ (villageois sans IA) et gère les téléportations. */
public final class NpcManager {

    private final SkyHubPlugin plugin;
    private final HubWorld hub;
    private final NamespacedKey npcKey;

    public NpcManager(SkyHubPlugin plugin, HubWorld hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.npcKey = new NamespacedKey(plugin, "npc_id");
    }

    /** Supprime les anciens PNJ et les recrée depuis la config. */
    public void spawnAll() {
        World world = hub.world();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("npcs");
        if (section == null) return;

        // Charger et garder chargés les chunks des PNJ
        for (String id : section.getKeys(false)) {
            Location loc = readLocation(world, section.getConfigurationSection(id));
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            world.getChunkAt(cx, cz);
            world.addPluginChunkTicket(cx, cz, plugin);
        }

        // Retirer les anciens PNJ
        for (Villager v : world.getEntitiesByClass(Villager.class)) {
            if (getNpcId(v) != null) v.remove();
        }

        // Créer les nouveaux
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            Location loc = readLocation(world, s);
            Component name = MiniMessage.miniMessage().deserialize(s.getString("name", id));
            Villager.Profession profession = parseProfession(s.getString("profession", "none"));

            world.spawn(loc, Villager.class, v -> {
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setPersistent(true);
                v.setRemoveWhenFarAway(false);
                v.setCollidable(false);
                v.setProfession(profession);
                v.setVillagerLevel(5);
                v.customName(name);
                v.setCustomNameVisible(true);
                v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);
            });
        }
    }

    /** Retourne l'id du PNJ si l'entité en est un, sinon null. */
    public String getNpcId(Entity entity) {
        return entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
    }

    /** Téléporte le joueur vers le monde associé au PNJ. */
    public void teleport(Player player, String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("npcs." + id);
        if (s == null) return;

        String worldName = s.getString("world", "world");
        String displayName = s.getString("name", id);
        World target = resolveWorld(worldName);
        if (target == null) {
            player.sendMessage(plugin.message("world-missing", "%world%", worldName));
            return;
        }

        GameMode gm;
        try {
            gm = GameMode.valueOf(s.getString("gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            gm = GameMode.SURVIVAL;
        }
        final GameMode gamemode = gm;

        player.sendMessage(plugin.message("teleporting", "%name%", displayName));
        Location dest = target.getSpawnLocation().add(0.5, 0, 0.5);
        player.teleportAsync(dest).thenAccept(ok -> {
            if (ok) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setGameMode(gamemode);
                    player.setFallDistance(0);
                });
            }
        });
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

    private Villager.Profession parseProfession(String name) {
        Villager.Profession p = Registry.VILLAGER_PROFESSION.get(
                NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return p != null ? p : Villager.Profession.NONE;
    }
}
