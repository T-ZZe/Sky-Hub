package fr.skyhub.kit;

import fr.skyhub.ffa.FfaMode;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Règles des kits : objets interdits par zone, armures, Curse of Binding. */
public final class KitRules {
    private KitRules() {
    }

    private static final String[] TIERS = {"LEATHER", "CHAINMAIL", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "TURTLE"};

    /** Mace et UHC : pas de netherite. Crystal : ni élytra, ni mace, ni feu d'artifice. */
    public static boolean banned(FfaMode zone, Material m) {
        if (zone == null || m == null) return false;
        if ((zone == FfaMode.UHC || zone == FfaMode.MACE) && m.name().startsWith("NETHERITE_")) return true;
        if (zone == FfaMode.UHC && m == Material.TOTEM_OF_UNDYING) return true; // UHC : aucun totem
        return zone == FfaMode.CRYSTAL
                && (m == Material.ELYTRA || m == Material.MACE || m == Material.FIREWORK_ROCKET);
    }

    /** Nombre max de totems dans un kit : UHC 0, Crystal illimité (-1), les autres zones 1. */
    public static int totemLimit(FfaMode zone) {
        if (zone == FfaMode.UHC) return 0;
        if (zone == FfaMode.CRYSTAL) return -1;
        return 1;
    }

    public static boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    /** Index PlayerInventory : bottes 36, jambières 37, plastron 38, casque 39. */
    public static int armorIndex(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return 39;
        if (n.endsWith("_CHESTPLATE")) return 38;
        if (n.endsWith("_LEGGINGS")) return 37;
        return 36;
    }

    public static Enchantment enchant(String key) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(key));
    }

    public static boolean isCurse(Enchantment e) {
        String k = e.getKey().getKey();
        return k.equals("binding_curse") || k.equals("vanishing_curse");
    }

    /** Ajoute la Curse of Binding aux pièces d'armure. */
    public static ItemStack bind(ItemStack it) {
        if (it == null || !isArmor(it.getType())) return it;
        Enchantment e = enchant("binding_curse");
        if (e != null && it.getEnchantmentLevel(e) == 0) it.addUnsafeEnchantment(e, 1);
        return it;
    }

    public static void ench(ItemStack it, String key, int level) {
        Enchantment e = enchant(key);
        if (e != null) it.addUnsafeEnchantment(e, level);
    }

    /** Les types possibles pour la même pièce d'armure (selon la zone). */
    public static List<Material> armorTypes(FfaMode zone, Material current) {
        String n = current.name();
        String piece = n.substring(n.lastIndexOf('_'));
        List<Material> out = new ArrayList<>();
        for (String t : TIERS) {
            Material m = Material.matchMaterial(t + piece);
            if (m != null && !banned(zone, m)) out.add(m);
        }
        return out;
    }
}
