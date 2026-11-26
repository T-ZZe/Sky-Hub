package fr.skyhub.oneblock.mob;

import org.bukkit.entity.EntityType;

public record MobChoice(Kind kind, EntityType type) {
    public enum Kind { NONE, SURPRISE, ENTITY }
}
