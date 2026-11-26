package fr.skyhub.oneblock.mob;

import fr.skyhub.oneblock.utils.WeightedTable;
import org.bukkit.entity.EntityType;

import java.util.List;

public record MobTable(WeightedTable<MobChoice> table, List<EntityType> wave) {}
