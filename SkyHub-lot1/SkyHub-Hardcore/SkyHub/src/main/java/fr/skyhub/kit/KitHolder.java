package fr.skyhub.kit;

import fr.skyhub.ffa.FfaMode;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Holder commun à toutes les interfaces de kits. */
final class KitHolder implements InventoryHolder {

    enum Kind { EDITOR, ADMIN, ARMOR, TYPE, ENCH, TRIM, SHULKER }

    final Kind kind;
    Inventory inv;
    FfaMode zone;
    int tab;
    ItemStack[][] adminPools;   // ADMIN : copie de travail des objets de la zone
    CustomSession custom;       // ARMOR / TYPE / ENCH / TRIM
    int shulkerIndex = -1;      // SHULKER : slot de l'inventaire du joueur

    KitHolder(Kind kind) {
        this.kind = kind;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
