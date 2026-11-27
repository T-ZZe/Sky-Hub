package fr.skyffa.ffa;

import fr.skyffa.SkyFfaPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
 * /ffa spawn &lt;zone&gt; : (admin) définit le spawn de la zone là où tu es (ancien /l ffa spawn).
 * /ffa reload         : (admin) recharge la config.
 * /ffa menu           : ouvre le choix de zone. Réservé au mob FFA du hub de SkyHub (et aux admins).
 */
public final class FfaCommand implements CommandExecutor, TabCompleter {

    /** Posée par SkyHub quand un joueur clique sur le mob FFA du hub. */
    private static final String HUB_CLICK = "skyhub_ffa_menu";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SkyFfaPlugin plugin;

    public FfaCommand(SkyFfaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("skyhub.admin");

        switch (sub) {
            case "menu" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.message("players-only"));
                    return true;
                }
                if (!admin && !p.hasMetadata(HUB_CLICK)) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                plugin.ffa().openMenu(p);
            }
            case "spawn" -> {
                if (!admin) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.message("players-only"));
                    return true;
                }
                FfaMode mode = args.length >= 2 ? FfaMode.parse(args[1]) : null;
                if (mode == null) {
                    p.sendMessage(MM.deserialize("<yellow>/ffa spawn <uhc|mace|crystal|cart|pot> <gray>- définit le spawn de la zone là où tu es"));
                    return true;
                }
                if (!plugin.ffa().isFfa(p.getWorld())) {
                    p.sendMessage(MM.deserialize("<red>Va dans le monde FFA (<white>" + plugin.ffa().ffaWorldName()
                            + "</white>) pour définir ce spawn."));
                    return true;
                }
                plugin.ffa().setSpawn(mode, p.getLocation());
                p.sendMessage(MM.deserialize("<green>Spawn de la zone " + mode.colored() + "<green> défini ici."));
            }
            case "reload" -> {
                if (!admin) {
                    sender.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(plugin.message("reloaded"));
            }
            default -> sender.sendMessage(MM.deserialize(
                    admin ? "<yellow>/ffa spawn <zone> | /ffa menu | /ffa reload" : "<yellow>/spawn <gray>- retour au spawn de ta zone"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("skyhub.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("spawn", "menu", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            for (FfaMode m : FfaMode.values()) {
                if (m.key.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(m.key);
            }
        }
        return out;
    }
}
