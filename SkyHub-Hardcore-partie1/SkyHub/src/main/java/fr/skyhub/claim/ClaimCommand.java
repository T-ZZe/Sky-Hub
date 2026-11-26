package fr.skyhub.claim;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClaimCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBS = List.of("wand", "pos1", "pos2", "create", "delete", "list", "info",
            "flag", "save", "rebuild", "restore", "expire", "pearl");

    private final ClaimManager claims;

    public ClaimCommand(ClaimManager claims) {
        this.claims = claims;
    }

    private static void msg(CommandSender s, String m) {
        s.sendMessage(MM.deserialize(m));
    }

    private static void help(CommandSender s) {
        msg(s, "<gold><bold>/claim</bold> <gray>- zones protégées (OP)");
        msg(s, "<yellow>/claim wand</yellow> <gray>- baguette de sélection (créatif)  |  <yellow>/claim pos1|pos2");
        msg(s, "<yellow>/claim create <nom></yellow> <gray>- crée la zone depuis la sélection");
        msg(s, "<yellow>/claim flag <nom> <place|break|windcharge|pvp|drop> <allow|deny>");
        msg(s, "<yellow>/claim save <nom></yellow> <gray>- sauvegarde les blocs de la zone");
        msg(s, "<yellow>/claim rebuild <nom></yellow> <gray>- reconstruit la zone maintenant");
        msg(s, "<yellow>/claim restore <nom> <durée|off></yellow> <gray>- reconstruction automatique (ex: 10m)");
        msg(s, "<yellow>/claim expire <nom> <durée|off></yellow> <gray>- les blocs posés disparaissent après (ex: 30s)");
        msg(s, "<yellow>/claim pearl <nom> <durée|off></yellow> <gray>- délai entre deux perles d'Ender dans la zone (ex: 5s)");
        msg(s, "<yellow>/claim list | info [nom] | delete <nom>");
    }

    /** "30", "30s", "5m", "2h", "1d" → secondes ; "off" → 0 ; invalide → -1 */
    private static int parseSeconds(String s) {
        if (s.equalsIgnoreCase("off") || s.equals("0")) return 0;
        try {
            long mult = 1;
            String num = s;
            char u = s.charAt(s.length() - 1);
            if (!Character.isDigit(u)) {
                num = s.substring(0, s.length() - 1);
                mult = switch (Character.toLowerCase(u)) {
                    case 's' -> 1;
                    case 'm' -> 60;
                    case 'h' -> 3600;
                    case 'd' -> 86400;
                    default -> -1;
                };
                if (mult < 0) return -1;
            }
            long v = Long.parseLong(num) * mult;
            return v > 0 && v <= Integer.MAX_VALUE ? (int) v : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String fmt(int sec) {
        if (sec <= 0) return "désactivé";
        if (sec % 3600 == 0) return (sec / 3600) + " h";
        if (sec % 60 == 0) return (sec / 60) + " min";
        return sec + " s";
    }

    private static String onOff(boolean b) {
        return b ? "<green>autorisé" : "<red>interdit";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("skyhub.admin")) {
            msg(p, "<red>Permission refusée.");
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "wand", "pos1", "pos2" -> {
                if (p.getGameMode() != GameMode.CREATIVE) {
                    msg(p, "<red>Il faut être OP et en créatif pour sélectionner une zone.");
                    return true;
                }
                if (sub.equals("wand")) {
                    p.getInventory().addItem(claims.wand());
                    msg(p, "<green>Baguette donnée : <gray>clic gauche = position 1, clic droit = position 2.");
                } else {
                    int which = sub.equals("pos1") ? 1 : 2;
                    claims.setPos(p.getUniqueId(), which, p.getLocation());
                    Location l = p.getLocation();
                    msg(p, "<green>Position " + which + " : <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
            }
            case "create" -> {
                if (a.length < 2) {
                    msg(p, "<yellow>/claim create <nom>");
                    return true;
                }
                Location[] sel = claims.selection(p.getUniqueId());
                if (sel == null) {
                    msg(p, "<red>Sélectionne d'abord la zone (/claim wand, en créatif).");
                    return true;
                }
                String err = claims.create(a[1], sel[0], sel[1]);
                if (err != null) {
                    msg(p, "<red>" + err);
                    return true;
                }
                msg(p, "<green>Zone <white>" + a[1] + "</white> créée. <gray>Règles par défaut : tout interdit."
                        + " <yellow>/claim flag " + a[1] + " place allow</yellow>, puis <yellow>/claim save " + a[1] + "</yellow>.");
            }
            case "list" -> {
                if (claims.all().isEmpty()) {
                    msg(p, "<gray>Aucune zone.");
                    return true;
                }
                for (Claim c : claims.all()) {
                    msg(p, "<gray>- <white>" + c.name + "</white> (" + c.world + ", " + c.volume() + " blocs)"
                            + (claims.hasSnapshot(c) ? " <green>[sauvegardée]" : " <red>[non sauvegardée]"));
                }
            }
            case "delete", "info", "flag", "save", "rebuild", "restore", "expire", "pearl" -> {
                Claim c;
                if (a.length >= 2) {
                    c = claims.get(a[1]);
                } else if (sub.equals("info")) {
                    c = claims.at(p.getLocation());
                } else {
                    help(p);
                    return true;
                }
                if (c == null) {
                    msg(p, "<red>Zone introuvable.");
                    return true;
                }
                handleOnClaim(p, sub, c, a);
            }
            default -> help(p);
        }
        return true;
    }

    private void handleOnClaim(Player p, String sub, Claim c, String[] a) {
        switch (sub) {
            case "delete" -> {
                claims.delete(c);
                msg(p, "<green>Zone <white>" + c.name + "</white> supprimée.");
            }
            case "info" -> {
                msg(p, "<gold><bold>Zone " + c.name);
                msg(p, "<gray>Monde : <white>" + c.world + " <gray>| " + c.volume() + " blocs");
                msg(p, "<gray>De <white>" + c.minX + " " + c.minY + " " + c.minZ + "</white> à <white>"
                        + c.maxX + " " + c.maxY + " " + c.maxZ);
                msg(p, "<gray>Poser : " + onOff(c.place) + " <gray>| Casser : " + onOff(c.breakAllowed)
                        + " <gray>| Wind charge : " + onOff(c.windcharge) + " <gray>| PvP : " + onOff(c.pvp)
                        + " <gray>| Jeter : " + onOff(c.drop));
                msg(p, "<gray>Délai entre deux perles : <white>" + fmt(c.pearlSec));
                msg(p, "<gray>Blocs posés disparaissent après : <white>" + fmt(c.expireSec));
                msg(p, "<gray>Reconstruction automatique : <white>" + fmt(c.restoreSec)
                        + " <gray>| Sauvegarde : " + (claims.hasSnapshot(c) ? "<green>oui" : "<red>non"));
            }
            case "flag" -> {
                if (a.length < 4) {
                    msg(p, "<yellow>/claim flag <nom> <place|break|windcharge|pvp|drop> <allow|deny>");
                    return;
                }
                boolean value;
                if (a[3].equalsIgnoreCase("allow") || a[3].equalsIgnoreCase("on") || a[3].equalsIgnoreCase("true")) value = true;
                else if (a[3].equalsIgnoreCase("deny") || a[3].equalsIgnoreCase("off") || a[3].equalsIgnoreCase("false")) value = false;
                else {
                    msg(p, "<red>Valeur : allow ou deny.");
                    return;
                }
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "place" -> c.place = value;
                    case "break" -> c.breakAllowed = value;
                    case "windcharge", "wind", "wind_charge" -> c.windcharge = value;
                    case "pvp" -> c.pvp = value;
                    case "drop" -> c.drop = value;
                    default -> {
                        msg(p, "<red>Règle inconnue : place, break, windcharge, pvp ou drop.");
                        return;
                    }
                }
                claims.saveClaims();
                msg(p, "<green>Zone <white>" + c.name + "</white> : " + a[2].toLowerCase(Locale.ROOT) + " = " + onOff(value));
            }
            case "save" -> {
                if (!claims.saveSnapshot(c, p)) msg(p, "<red>Impossible : opération déjà en cours ou monde non chargé.");
                else msg(p, "<gray>Sauvegarde de la zone en cours (" + c.volume() + " blocs)...");
            }
            case "rebuild" -> {
                if (!claims.hasSnapshot(c)) {
                    msg(p, "<red>Aucune sauvegarde : fais d'abord <yellow>/claim save " + c.name);
                    return;
                }
                if (!claims.restore(c, p)) msg(p, "<red>Impossible : opération déjà en cours ou monde non chargé.");
                else msg(p, "<gray>Reconstruction de la zone en cours...");
            }
            case "pearl" -> {
                if (a.length < 3) {
                    msg(p, "<yellow>/claim pearl <nom> <durée|off> <gray>(ex : 3s, 10s)");
                    return;
                }
                int sec = parseSeconds(a[2]);
                if (sec < 0) {
                    msg(p, "<red>Durée invalide (ex : 3s, 10s, off).");
                    return;
                }
                claims.setPearl(c, sec);
                msg(p, "<green>Perles d'Ender dans <white>" + c.name + "</white> : délai " + fmt(sec));
            }
            case "restore", "expire" -> {
                if (a.length < 3) {
                    msg(p, "<yellow>/claim " + sub + " <nom> <durée|off> <gray>(ex : 30s, 10m, 1h)");
                    return;
                }
                int sec = parseSeconds(a[2]);
                if (sec < 0) {
                    msg(p, "<red>Durée invalide (ex : 30s, 10m, 1h, off).");
                    return;
                }
                if (sub.equals("restore")) {
                    if (sec > 0 && !claims.hasSnapshot(c)) {
                        msg(p, "<red>Fais d'abord <yellow>/claim save " + c.name + "</yellow> pour enregistrer l'état à restaurer.");
                        return;
                    }
                    claims.setRestore(c, sec);
                    msg(p, "<green>Reconstruction automatique de <white>" + c.name + "</white> : " + fmt(sec));
                } else {
                    claims.setExpire(c, sec);
                    msg(p, "<green>Blocs posés dans <white>" + c.name + "</white> : disparition après " + fmt(sec));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("skyhub.admin")) return out;
        if (a.length == 1) {
            for (String x : SUBS) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && List.of("delete", "info", "flag", "save", "rebuild", "restore", "expire", "pearl").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Claim c : claims.all()) if (c.key().startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(c.name);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("flag")) {
            for (String x : List.of("place", "break", "windcharge", "pvp", "drop")) if (x.startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 4 && a[0].equalsIgnoreCase("flag")) {
            out.addAll(List.of("allow", "deny"));
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("restore") || a[0].equalsIgnoreCase("expire"))) {
            out.addAll(List.of("30s", "1m", "5m", "10m", "1h", "off"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("pearl")) {
            out.addAll(List.of("2s", "3s", "5s", "10s", "off"));
        }
        return out;
    }
}
