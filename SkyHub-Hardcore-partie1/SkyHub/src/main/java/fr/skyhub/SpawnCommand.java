package fr.skyhub;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /spawn : retour au spawn de ta zone FFA (5 s sans bouger, interdit en combat). */
public final class SpawnCommand implements CommandExecutor {

    private final TeleportGuard guard;

    public SpawnCommand(TeleportGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        guard.requestSpawn(p);
        return true;
    }
}
