package fr.skyhub;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Ouvre (monde flat) et ferme des mondes à la volée : /l open <monde>, /l close [monde]. */
public final class WorldManager {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private final SkyHubPlugin plugin;
    private final HubWorld hub;
    private final File file;
    private final Set<String> opened = new LinkedHashSet<>();

    public WorldManager(SkyHubPlugin plugin, HubWorld hub) {
        this.plugin = plugin;
        this.hub = hub;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "worlds.yml");
        opened.addAll(YamlConfiguration.loadConfiguration(file).getStringList("open"));
    }

    private void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("open", new ArrayList<>(opened));
        try {
            c.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire worlds.yml : " + e.getMessage());
        }
    }

    /** Recharge au démarrage les mondes ouverts avant le redémarrage. */
    public void loadSaved() {
        for (String name : new ArrayList<>(opened)) {
            if (Bukkit.getWorld(name) == null && createFlat(name) == null) {
                opened.remove(name);
            }
        }
        save();
    }

    public boolean validName(String name) {
        return NAME.matcher(name).matches();
    }

    /** Hub, monde principal et monde hardcore ne peuvent jamais être fermés. */
    public boolean isProtected(World w) {
        return hub.isHub(w) || w.equals(Bukkit.getWorlds().get(0)) || plugin.isHardcoreWorld(w) || plugin.isOneBlockWorld(w);
    }

    public Set<String> openedNames() {
        return opened;
    }

    private World createFlat(String name) {
        try {
            return new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .createWorld();
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible d'ouvrir le monde " + name + " : " + e.getMessage());
            return null;
        }
    }

    /** Crée (ou recharge) un monde flat. */
    public World open(String name) {
        World w = createFlat(name);
        if (w != null) {
            opened.add(name);
            save();
        }
        return w;
    }

    /** Envoie les joueurs au hub puis décharge le monde. */
    public void close(World w, CommandSender sender) {
        String name = w.getName();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Player p : new ArrayList<>(w.getPlayers())) {
            p.sendMessage(plugin.message("world-closing-kick", "%name%", name));
            futures.add(hub.sendToHub(p));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, t) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.unloadWorld(w, true)) {
                        opened.remove(name);
                        save();
                        sender.sendMessage(plugin.message("world-closed-done", "%name%", name));
                    } else {
                        sender.sendMessage(plugin.message("world-close-failed", "%name%", name));
                    }
                }));
    }

    /** Ferme tous les mondes ouverts avec /l open. Retourne le nombre de mondes fermés. */
    public int closeAll(CommandSender sender) {
        int n = 0;
        for (String name : new ArrayList<>(opened)) {
            World w = Bukkit.getWorld(name);
            if (w == null) {
                opened.remove(name);
                continue;
            }
            if (isProtected(w)) continue;
            close(w, sender);
            n++;
        }
        save();
        return n;
    }
}
