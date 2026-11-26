package fr.skyhub.kit;

import fr.skyhub.ffa.FfaMode;
import org.bukkit.inventory.ItemStack;

/** Personnalisation d'une pièce d'armure (type / enchantements / trims). */
final class CustomSession {
    FfaMode zone;
    /** true = pièce du catalogue (copie à prendre à la fin), false = pièce déjà dans l'inventaire du joueur. */
    boolean template;
    ItemStack working;   // template uniquement
    int invIndex = -1;   // index PlayerInventory (0-35, bottes 36, jambières 37, plastron 38, casque 39)
    int editorTab;       // onglet de l'éditeur auquel on revient
    int patIdx;
    int matIdx;
}
