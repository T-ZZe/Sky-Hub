package fr.skyhub.oneblock.mob;

import fr.skyhub.oneblock.utils.Ranges;

import java.util.Map;

public record MobSettings(int maxMobs, long cooldownMs, int radius, Ranges waveSize, double healthMultiplier, double damageMultiplier,
                          Map<Integer, MobTable> tables) {}
