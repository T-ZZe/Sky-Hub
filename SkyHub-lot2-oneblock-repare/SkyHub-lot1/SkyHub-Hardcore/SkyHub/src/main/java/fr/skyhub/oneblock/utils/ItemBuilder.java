package fr.skyhub.oneblock.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Construction d'items de GUI (sans italique par défaut). */
public final class ItemBuilder {

    private final ItemStack item;
    private Component name;
    private final List<Component> lore = new ArrayList<>();
    private boolean glow;

    public ItemBuilder(Material m) { this.item = new ItemStack(m); }

    public ItemBuilder amount(int n) { item.setAmount(Math.max(1, Math.min(64, n))); return this; }
    public ItemBuilder name(Component c) { this.name = c; return this; }
    public ItemBuilder lore(Component c) { lore.add(c); return this; }
    public ItemBuilder lore(List<Component> c) { lore.addAll(c); return this; }
    public ItemBuilder glow(boolean g) { this.glow = g; return this; }

    private static Component plain(Component c) { return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE); }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(plain(name));
            if (!lore.isEmpty()) meta.lore(lore.stream().map(ItemBuilder::plain).toList());
            if (glow) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
