package fr.skyhub;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class HubCommand implements CommandExecutor, TabCompleter {

    private final SkyHubPlugin plugin;
    private final HubWorld hub;

    public HubCommand(SkyHubPlugin plugin, HubWorld hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("hub")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("players-only"));
                return true;
            }
            hub.sendToHub(player);
            player.sendMessage(plugin.message("hub-teleport"));
            return true;
        }

        // /skyhub
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.npcManager().spawnAll();
            sender.sendMessage(plugin.message("reloaded"));
            return true;
        }
        sender.sendMessage(Component.text("Usage : /skyhub reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("skyhub") && args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
