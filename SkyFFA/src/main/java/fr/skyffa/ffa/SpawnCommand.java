package fr.skyffa.ffa;

import fr.skyffa.SkyFfaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /spawn : retour au spawn de ta zone FFA après un compte à rebours (voir {@link TeleportWarmup}). */
public final class SpawnCommand implements CommandExecutor {

    private final SkyFfaPlugin plugin;

    public SpawnCommand(SkyFfaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("players-only"));
            return true;
        }
        plugin.warmup().spawn(player);
        return true;
    }
}
