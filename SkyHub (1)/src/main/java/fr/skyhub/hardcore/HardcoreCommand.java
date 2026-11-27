package fr.skyhub.hardcore;

import fr.skyhub.SkyHubPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HardcoreCommand implements CommandExecutor {
    private final SkyHubPlugin pl;

    public HardcoreCommand(SkyHubPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("hardcore.admin")) {
            p.sendMessage(Gui.MM.deserialize("<red>Permission refusée."));
            return true;
        }
        Gui.openMain(pl, p);
        return true;
    }
}
