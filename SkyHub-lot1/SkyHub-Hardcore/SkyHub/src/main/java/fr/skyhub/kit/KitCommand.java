package fr.skyhub.kit;

import fr.skyhub.ffa.FfaMode;
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

/** /kit, /kit set <1-3>, /kit <1-3>, /kit create <zone> (admin). */
public final class KitCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final KitManager kits;
    private final KitGui gui;

    public KitCommand(KitManager kits, KitGui gui) {
        this.kits = kits;
        this.gui = gui;
    }

    private static int num(String s) {
        try {
            int n = Integer.parseInt(s);
            return n >= 1 && n <= KitManager.MAX_KITS ? n : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(MM.deserialize("<red>Commande réservée aux joueurs."));
            return true;
        }

        if (a.length >= 1 && a[0].equalsIgnoreCase("create")) {
            if (!p.hasPermission("skyhub.admin")) {
                p.sendMessage(MM.deserialize("<red>Tu n'as pas la permission."));
                return true;
            }
            FfaMode z = a.length >= 2 ? FfaMode.parse(a[1]) : null;
            if (z == null) {
                p.sendMessage(MM.deserialize("<red>Usage : /kit create <uhc|mace|crystal|cart|pot>"));
                return true;
            }
            gui.openAdmin(p, z);
            return true;
        }

        if (!kits.isFfa(p.getWorld())) {
            p.sendMessage(MM.deserialize("<red>Les kits sont disponibles uniquement dans le FFA."));
            return true;
        }
        FfaMode zone = kits.getMode(p.getUniqueId());
        if (zone == null) {
            p.sendMessage(MM.deserialize("<red>Choisis d'abord une zone avec le mob FFA."));
            return true;
        }

        if (a.length == 0) {
            gui.openEditor(p, zone, 0);
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("set")) {
            int n = a.length >= 2 ? num(a[1]) : 0;
            if (n == 0) {
                p.sendMessage(MM.deserialize("<red>Usage : /kit set <1-3>"));
                return true;
            }
            String err = kits.saveKit(p, zone, n);
            if (err != null) {
                p.sendMessage(MM.deserialize("<red>Kit non sauvegardé : <white>" + err
                        + "</white> n'est pas autorisé dans la zone " + zone.label + "."));
            } else {
                kits.setSelected(p.getUniqueId(), n);
                p.sendMessage(MM.deserialize("<green>Kit <white>" + n + "</white> (" + zone.label + ") sauvegardé."));
            }
            return true;
        }

        int n = sub.equals("load") ? (a.length >= 2 ? num(a[1]) : 0) : num(sub);
        if (n == 0) {
            p.sendMessage(MM.deserialize("<red>Usage : /kit, /kit set <1-3>, /kit <1-3>"));
            return true;
        }
        int r = kits.loadKit(p, zone, n);
        if (r < 0) {
            p.sendMessage(MM.deserialize("<red>Tu n'as pas encore de kit " + n + " pour la zone " + zone.label + "."));
        } else {
            kits.setSelected(p.getUniqueId(), n);
            p.sendMessage(MM.deserialize("<green>Kit <white>" + n + "</white> (" + zone.label + ") équipé."
                    + (r > 0 ? " <gray>(" + r + " objet(s) retiré(s), plus autorisé(s))" : "")));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("set");
            out.add("load");
            out.add("1");
            out.add("2");
            out.add("3");
            if (s.hasPermission("skyhub.admin")) out.add("create");
        } else if (a.length == 2) {
            if (a[0].equalsIgnoreCase("create") && s.hasPermission("skyhub.admin")) {
                for (FfaMode m : FfaMode.values()) out.add(m.key);
            } else if (a[0].equalsIgnoreCase("set") || a[0].equalsIgnoreCase("load")) {
                out.add("1");
                out.add("2");
                out.add("3");
            }
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(prefix));
        return out;
    }
}
