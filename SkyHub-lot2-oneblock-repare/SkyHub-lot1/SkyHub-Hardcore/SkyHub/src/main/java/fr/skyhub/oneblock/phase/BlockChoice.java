package fr.skyhub.oneblock.phase;

import org.bukkit.Material;

public record BlockChoice(Kind kind, Material material) {
    public enum Kind { MATERIAL, CHEST_BONUS, SURPRISE }
}
