package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.utils.ItemSpec;
import fr.skyhub.oneblock.utils.WeightedTable;

import java.util.List;

public record BlockPool(WeightedTable<BlockChoice> table, List<ItemSpec> bonusChest) {}
