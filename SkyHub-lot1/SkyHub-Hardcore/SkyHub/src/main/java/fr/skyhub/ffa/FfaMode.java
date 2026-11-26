package fr.skyhub.ffa;

import org.bukkit.Material;

import java.util.Locale;

/** Les 5 zones du FFA (même map, spawns différents). */
public enum FfaMode {
    UHC("uhc", "UHC", "<aqua>", "DIAMOND_SWORD", Material.DIAMOND_SWORD),
    MACE("mace", "Mace", "<gold>", "MACE", Material.MACE),
    CRYSTAL("crystal", "Crystal", "<light_purple>", "END_CRYSTAL", Material.END_CRYSTAL),
    CART("cart", "Cart PvP", "<red>", "TNT_MINECART", Material.MINECART),
    POT("pot", "Pot", "<green>", "SPLASH_POTION", Material.POTION);

    public final String key;
    public final String label;
    public final String color;
    public final Material icon;

    FfaMode(String key, String label, String color, String iconName, Material fallback) {
        this.key = key;
        this.label = label;
        this.color = color;
        Material m = Material.matchMaterial(iconName);
        this.icon = m != null ? m : fallback;
    }

    /** "<aqua>UHC</aqua>" prêt pour MiniMessage. */
    public String colored() {
        return color + label + "</" + color.substring(1);
    }

    public static FfaMode parse(String s) {
        if (s == null) return null;
        String n = s.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (n) {
            case "uhc", "sword", "epee", "diamond", "diamant", "spear", "diamondspear", "lance" -> UHC;
            case "mace", "masse" -> MACE;
            case "crystal", "crystall", "cristal", "endcrystal" -> CRYSTAL;
            case "cart", "cartpvp", "minecart", "tntminecart", "wagon" -> CART;
            case "pot", "pots", "potpvp", "potion" -> POT;
            default -> null;
        };
    }
}
