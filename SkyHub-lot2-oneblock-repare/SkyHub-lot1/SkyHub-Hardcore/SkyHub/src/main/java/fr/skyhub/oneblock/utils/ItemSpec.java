package fr.skyhub.oneblock.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** "MATERIAL", "MATERIAL:3" ou "MATERIAL:2-5". */
public record ItemSpec(Material material, Ranges amount) {

    public static ItemSpec parse(String s) {
        String[] parts = s.trim().split(":", 2);
        Material m = Material.matchMaterial(parts[0].trim());
        if (m == null || !m.isItem() || m.isAir()) throw new IllegalArgumentException("Objet inconnu: '" + parts[0] + "'");
        Ranges r = parts.length == 1 ? new Ranges(1, 1) : Ranges.parse(parts[1]);
        if (r.max() < 1) throw new IllegalArgumentException("Quantité nulle: '" + s + "'");
        return new ItemSpec(m, r);
    }

    public ItemStack roll() {
        int n = Math.max(1, amount.roll());
        return new ItemStack(material, Math.min(n, material.getMaxStackSize()));
    }
}
