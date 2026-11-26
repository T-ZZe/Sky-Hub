package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.island.IslandSettings;
import fr.skyhub.oneblock.mob.MobSettings;
import fr.skyhub.oneblock.utils.WeightedTable;
import org.bukkit.Material;

import java.util.Map;

/** Instantané immuable de toute la configuration de gameplay ; remplacé atomiquement au rechargement. */
public record Content(PhaseRegistry phases, Map<Integer, BlockPool> pools, WeightedTable<SurpriseEvent> surprises, Material surpriseBlock,
                      MobSettings mobs, IslandSettings islands, GeneralSettings general) {}
