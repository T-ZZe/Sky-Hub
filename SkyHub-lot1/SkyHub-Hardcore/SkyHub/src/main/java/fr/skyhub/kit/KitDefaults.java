package fr.skyhub.kit;

import fr.skyhub.ffa.FfaMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/** Objets par défaut de chaque zone (modifiables ensuite avec /kit create <zone>). */
final class KitDefaults {
    private KitDefaults() {
    }

    static ItemStack[][] pool(FfaMode z) {
        ItemStack[][] p = new ItemStack[KitManager.TABS][KitManager.TAB_SLOTS];
        fill(p[0], gear(z), z);
        fill(p[1], utility(z), z);
        fill(p[2], food(z), z);
        return p;
    }

    private static void fill(ItemStack[] dst, List<ItemStack> src, FfaMode z) {
        int i = 0;
        for (ItemStack it : src) {
            if (it == null || KitRules.banned(z, it.getType())) continue;
            if (i >= dst.length) break;
            dst[i++] = it;
        }
    }

    /** enchants = paires (clé, niveau). Renvoie null si le matériau n'existe pas. */
    private static ItemStack item(String mat, int amount, Object... enchants) {
        Material m = Material.matchMaterial(mat);
        if (m == null) return null;
        ItemStack it = new ItemStack(m, amount);
        for (int i = 0; i + 1 < enchants.length; i += 2) {
            KitRules.ench(it, (String) enchants[i], (Integer) enchants[i + 1]);
        }
        return KitRules.bind(it);
    }

    private static ItemStack potion(Material m, PotionType t) {
        ItemStack it = new ItemStack(m);
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        pm.setBasePotionType(t);
        it.setItemMeta(pm);
        return it;
    }

    // ------------------------------------------------------------------ onglet équipement

    private static List<ItemStack> gear(FfaMode z) {
        String tier = (z == FfaMode.UHC || z == FfaMode.MACE) ? "DIAMOND" : "NETHERITE";
        List<ItemStack> l = new ArrayList<>();
        l.add(item(tier + "_HELMET", 1, "protection", 4, "unbreaking", 3, "mending", 1));
        l.add(item(tier + "_CHESTPLATE", 1, "protection", 4, "unbreaking", 3, "mending", 1));
        l.add(item(tier + "_LEGGINGS", 1, "protection", 4, "unbreaking", 3, "mending", 1));
        l.add(item(tier + "_BOOTS", 1, "protection", 4, "feather_falling", 4, "unbreaking", 3, "mending", 1));
        l.add(item("SHIELD", 1, "unbreaking", 3, "mending", 1));
        l.add(item("ELYTRA", 1, "unbreaking", 3, "mending", 1));
        l.add(item("TOTEM_OF_UNDYING", 1));
        l.add(item(tier + "_SWORD", 1, "sharpness", 5, "fire_aspect", 2, "sweeping_edge", 3, "unbreaking", 3, "mending", 1));
        l.add(item(tier + "_AXE", 1, "sharpness", 5, "efficiency", 5, "unbreaking", 3, "mending", 1));
        l.add(item("MACE", 1, "density", 5, "unbreaking", 3, "mending", 1));
        l.add(item("TRIDENT", 1, "impaling", 5, "loyalty", 3, "unbreaking", 3, "mending", 1));
        l.add(item("BOW", 1, "power", 5, "punch", 2, "unbreaking", 3, "mending", 1));
        l.add(item("CROSSBOW", 1, "quick_charge", 3, "piercing", 4, "unbreaking", 3, "mending", 1));
        l.add(item(tier + "_PICKAXE", 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        l.add(item(tier + "_SHOVEL", 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        l.add(item("FIREWORK_ROCKET", 64));
        l.add(item("ARROW", 64));
        return l;
    }

    // ------------------------------------------------------------------ onglet utilitaire

    private static List<ItemStack> utility(FfaMode z) {
        List<ItemStack> l = new ArrayList<>();
        switch (z) {
            case CRYSTAL -> {
                l.add(item("END_CRYSTAL", 64));
                l.add(item("OBSIDIAN", 64));
                l.add(item("RESPAWN_ANCHOR", 64));
                l.add(item("GLOWSTONE", 64));
                l.add(item("SLIME_BLOCK", 64));
            }
            case CART -> {
                l.add(item("TNT_MINECART", 1));
                l.add(item("TNT", 64));
                l.add(item("RAIL", 64));
                l.add(item("POWERED_RAIL", 64));
                l.add(item("ACTIVATOR_RAIL", 64));
                l.add(item("MINECART", 1));
                l.add(item("SLIME_BLOCK", 64));
                l.add(item("LEVER", 64));
                l.add(item("STONE_BUTTON", 64));
            }
            case MACE -> l.add(item("WIND_CHARGE", 64));
            default -> {
            }
        }
        l.add(item("ENDER_PEARL", 16));
        l.add(item("COBWEB", 64));
        l.add(item("WATER_BUCKET", 1));
        l.add(item("LAVA_BUCKET", 1));
        l.add(item("FISHING_ROD", 1, "unbreaking", 3, "mending", 1));
        l.add(item("FLINT_AND_STEEL", 1));
        l.add(item("COBBLESTONE", 64));
        l.add(item("SHULKER_BOX", 1));
        return l;
    }

    // ------------------------------------------------------------------ onglet nourriture & potions

    private static List<ItemStack> food(FfaMode z) {
        List<ItemStack> l = new ArrayList<>();
        l.add(item("GOLDEN_APPLE", 64));
        l.add(item("ENCHANTED_GOLDEN_APPLE", 64));
        l.add(item("COOKED_BEEF", 64));
        l.add(item("COOKED_PORKCHOP", 64));
        l.add(item("GOLDEN_CARROT", 64));
        l.add(item("CHORUS_FRUIT", 64));
        l.add(item("MILK_BUCKET", 1));
        l.add(potion(Material.POTION, PotionType.STRONG_STRENGTH));
        l.add(potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
        l.add(potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE));
        l.add(potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
        if (z == FfaMode.POT) {
            l.add(potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS));
            l.add(potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH));
            l.add(potion(Material.SPLASH_POTION, PotionType.STRONG_REGENERATION));
            l.add(potion(Material.SPLASH_POTION, PotionType.LONG_FIRE_RESISTANCE));
        }
        return l;
    }
}
