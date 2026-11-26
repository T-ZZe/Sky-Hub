package fr.skyhub;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /hub (/lobby) : retour au hub.
 * /l            : retour au hub.
 * /l sp [nom]   : (admin) pose le mob à ta position, l'ancien est supprimé.
 * /l reload     : (admin) recharge la config.
 */
public final class HubCommand implements CommandExecutor, TabCompleter {

    private final SkyHubPlugin plugin;
    private final HubWorld hub;
    private final NpcManager npcs;
    private final WorldManager worlds;

    public HubCommand(SkyHubPlugin plugin, HubWorld hub, NpcManager npcs, WorldManager worlds) {
        this.plugin = plugin;
        this.hub = hub;
        this.npcs = npcs;
        this.worlds = worlds;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        // /hub ou /l sans argument
        if (command.getName().equalsIgnoreCase("hub") || args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("players-only"));
                return true;
            }
            hub.sendToHub(player);
            player.sendMessage(plugin.message("hub-teleport"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sp", "spawn" -> {
                if (!sender.hasPermission("skyhub.admin")) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.message("players-only"));
                    return true;
                }
                String ids = String.join(", ", npcs.ids());
                if (args.length < 2) {
                    player.sendMessage(plugin.message("usage-sp", "%ids%", ids));
                    return true;
                }
                String id = npcs.matchId(args[1]);
                if (id == null) {
                    player.sendMessage(plugin.message("npc-unknown", "%ids%", ids));
                    return true;
                }
                if (!hub.isHub(player.getWorld())) {
                    player.sendMessage(plugin.message("not-in-hub"));
                    return true;
                }
                npcs.setLocation(id, player.getLocation());
                player.sendMessage(plugin.message("npc-placed", "%name%", npcs.displayName(id)));
            }
            case "reload" -> {
                if (!sender.hasPermission("skyhub.admin")) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                npcs.spawnAll();
                sender.sendMessage(plugin.message("reloaded"));
            }
            case "ffa" -> {
                if (!sender.hasPermission("skyhub.admin")) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.message("players-only"));
                    return true;
                }
                var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                fr.skyhub.ffa.FfaMode mode = args.length >= 3 && args[1].equalsIgnoreCase("spawn")
                        ? fr.skyhub.ffa.FfaMode.parse(args[2]) : null;
                if (mode == null) {
                    player.sendMessage(mm.deserialize("<yellow>/l ffa spawn <uhc|mace|crystal|cart|pot> <gray>- définit le spawn de la zone là où tu es"));
                    return true;
                }
                if (!player.getWorld().getName().equalsIgnoreCase(plugin.ffa().ffaWorldName())) {
                    player.sendMessage(mm.deserialize("<red>Va dans le monde FFA (<white>" + plugin.ffa().ffaWorldName()
                            + "</white>) pour définir ce spawn."));
                    return true;
                }
                plugin.ffa().setSpawn(mode, player.getLocation());
                player.sendMessage(mm.deserialize("<green>Spawn de la zone " + mode.colored() + "<green> défini ici."));
            }
            case "open" -> {
                if (!sender.hasPermission("skyhub.admin")) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.message("usage"));
                    return true;
                }
                String id = npcs.matchId(args[1]);
                String name = id != null ? npcs.worldName(id) : args[1];
                if (!worlds.validName(name)) {
                    sender.sendMessage(plugin.message("world-bad-name"));
                    return true;
                }
                if (Bukkit.getWorld(name) != null) {
                    sender.sendMessage(plugin.message("world-already-open", "%name%", name));
                    return true;
                }
                sender.sendMessage(plugin.message("world-opening", "%name%", name));
                World w = worlds.open(name);
                sender.sendMessage(w != null
                        ? plugin.message("world-opened", "%name%", name)
                        : plugin.message("world-open-failed", "%name%", name));
            }
            case "close" -> {
                if (!sender.hasPermission("skyhub.admin")) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
                    int n = worlds.closeAll(sender);
                    if (n == 0) sender.sendMessage(plugin.message("world-none-open"));
                    return true;
                }
                String id = npcs.matchId(args[1]);
                String name = id != null ? npcs.worldName(id) : args[1];
                World w = Bukkit.getWorld(name);
                if (w == null) {
                    sender.sendMessage(plugin.message("world-not-open", "%name%", name));
                    return true;
                }
                if (worlds.isProtected(w)) {
                    sender.sendMessage(plugin.message("world-protected", "%name%", name));
                    return true;
                }
                worlds.close(w, sender);
            }
            default -> sender.sendMessage(plugin.message("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("l") || !sender.hasPermission("skyhub.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("sp", "reload", "open", "close", "ffa")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("sp") || args[0].equalsIgnoreCase("spawn"))) {
            for (String id : npcs.ids()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(id);
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("ffa")) {
            if ("spawn".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("spawn");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ffa")) {
            for (fr.skyhub.ffa.FfaMode m : fr.skyhub.ffa.FfaMode.values()) {
                if (m.key.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(m.key);
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            for (String id : npcs.ids()) {
                if (!id.equalsIgnoreCase("hardcore") && id.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(id);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("close")) {
            out.add("all");
            for (World w : Bukkit.getWorlds()) {
                if (!worlds.isProtected(w)) out.add(w.getName());
            }
            out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)));
        }
        return out;
    }
}
