package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.utils.ItemSpec;
import fr.skyhub.oneblock.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Récompenses textuelles : item:MAT:n | command:cmd | xp:niveaux | message:texte. */
public final class Rewards {

    private Rewards() {}

    /** @return null si valide, sinon la raison. */
    public static String validate(String reward) {
        int c = reward.indexOf(':');
        if (c < 1) return "format attendu type:valeur";
        String type = reward.substring(0, c).toLowerCase(), v = reward.substring(c + 1);
        try {
            switch (type) {
                case "item" -> ItemSpec.parse(v);
                case "xp" -> { if (Integer.parseInt(v.trim()) < 1) return "xp doit être >= 1"; }
                case "command", "message" -> { if (v.isBlank()) return type + " vide"; }
                default -> { return "type inconnu '" + type + "' (item, command, xp, message)"; }
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    public static void grant(Player p, String reward) {
        int c = reward.indexOf(':');
        String type = reward.substring(0, c).toLowerCase(), v = reward.substring(c + 1);
        switch (type) {
            case "item" -> {
                ItemStack it = ItemSpec.parse(v).roll();
                Map<Integer, ItemStack> left = p.getInventory().addItem(it);
                left.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            }
            case "xp" -> p.giveExpLevels(Integer.parseInt(v.trim()));
            case "command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), v.replace("%player%", p.getName()));
            case "message" -> p.sendMessage(Messages.mm(v.replace("%player%", p.getName())));
            default -> { }
        }
    }

    /** Libellé lisible pour les GUI. */
    public static String describe(String reward) {
        int c = reward.indexOf(':');
        String type = reward.substring(0, c).toLowerCase(), v = reward.substring(c + 1);
        return switch (type) {
            case "item" -> { ItemSpec s = ItemSpec.parse(v); yield s.amount().min() + (s.amount().max() > s.amount().min() ? "-" + s.amount().max() : "") + "x " + s.material().name().toLowerCase().replace('_', ' '); }
            case "xp" -> v.trim() + " niveau(x) d'XP";
            case "command" -> "Récompense spéciale";
            default -> v;
        };
    }
}
