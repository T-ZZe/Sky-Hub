package fr.skyhub;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gamerules indépendantes par monde.
 * /gamerule <règle> <valeur> [monde] ne change que le monde où tu es (ou celui indiqué),
 * jamais les autres. Les valeurs sont sauvegardées et réappliquées à chaque chargement de monde.
 */
public final class WorldGameRules implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SkyHubPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public WorldGameRules(SkyHubPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "gamerules.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire gamerules.yml : " + e.getMessage());
        }
    }

    private static String key(World w) {
        return "worlds." + w.getName().replace('.', '_');
    }

    // ------------------------------------------------------------------

    public void applyAll() {
        for (World w : Bukkit.getWorlds()) apply(w);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        apply(e.getWorld());
    }

    /** Réapplique les valeurs propres à ce monde. */
    private void apply(World w) {
        ConfigurationSection s = cfg.getConfigurationSection(key(w));
        if (s == null) return;
        for (String rk : s.getKeys(false)) {
            GameRule<?> r = find(rk);
            if (r == null) continue;
            Object v = parse(r, s.getString(rk, ""));
            if (v != null) setRaw(w, r, v);
        }
    }

    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("gamerules.per-world", true)) return;
        String msg = e.getMessage().trim();
        if (!msg.startsWith("/")) return;
        String[] parts = msg.substring(1).split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (!label.equals("gamerule") && !label.equals("minecraft:gamerule")) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("minecraft.command.gamerule")) return; // le serveur répond lui-même
        e.setCancelled(true);

        if (parts.length < 2) {
            p.sendMessage(MM.deserialize("<yellow>Usage : /gamerule <règle> [valeur] [monde]"));
            return;
        }
        GameRule<?> rule = find(parts[1]);
        if (rule == null) {
            p.sendMessage(MM.deserialize("<red>Règle inconnue : " + parts[1].replace("<", "")));
            return;
        }
        // Monde cible : celui du joueur, ou celui donné en 3e argument
        World target = p.getWorld();
        if (parts.length >= 4) {
            target = Bukkit.getWorld(parts[3]);
            if (target == null) {
                p.sendMessage(MM.deserialize("<red>Monde introuvable : " + parts[3].replace("<", "")));
                return;
            }
        }
        String name = rule.getKey().getKey();

        if (parts.length == 2) {
            p.sendMessage(MM.deserialize("<gray>" + name + " = <yellow>" + target.getGameRuleValue(rule)
                    + " <gray>(monde " + target.getName() + ")"));
            return;
        }
        Object value = parse(rule, parts[2]);
        if (value == null) {
            p.sendMessage(MM.deserialize("<red>Valeur invalide pour " + name + " ("
                    + (rule.getType() == Boolean.class ? "true / false" : "nombre entier") + ")."));
            return;
        }
        change(target, rule, value);
        p.sendMessage(MM.deserialize("<green>" + name + " = <yellow>" + value
                + " <green>dans le monde <white>" + target.getName() + "</white> uniquement."));
    }

    /** Change la règle dans UN seul monde et annule toute fuite vers les autres. */
    private void change(World target, GameRule<?> rule, Object value) {
        String rk = rule.getKey().getKey();
        Map<World, Object> before = new HashMap<>();
        for (World w : Bukkit.getWorlds()) {
            if (w.equals(target)) continue;
            Object cur = w.getGameRuleValue(rule);
            if (cur != null) {
                before.put(w, cur);
                // on fige la valeur actuelle des autres mondes (s'ils n'ont pas déjà la leur)
                if (!cfg.contains(key(w) + "." + rk)) cfg.set(key(w) + "." + rk, String.valueOf(cur));
            }
        }
        setRaw(target, rule, value);
        cfg.set(key(target) + "." + rk, String.valueOf(value));
        save();

        Runnable restore = () -> before.forEach((w, old) -> {
            Object cur = w.getGameRuleValue(rule);
            if (!old.equals(cur)) setRaw(w, rule, old);
        });
        restore.run();
        Bukkit.getScheduler().runTaskLater(plugin, restore, 2L);
    }

    // ------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "deprecation", "removal"})
    private static GameRule<?> find(String name) {
        String n = norm(name);
        for (GameRule<?> r : GameRule.values()) {
            if (norm(r.getKey().getKey()).equals(n) || norm(r.getName()).equals(n)) return r;
        }
        return null;
    }

    private static String norm(String s) {
        return s.replace("_", "").replace(":", "").toLowerCase(Locale.ROOT);
    }

    private static Object parse(GameRule<?> r, String s) {
        if (r.getType() == Boolean.class) {
            if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void setRaw(World w, GameRule<T> r, Object v) {
        w.setGameRule(r, (T) v);
    }
}
