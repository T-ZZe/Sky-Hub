package fr.skyhub.oneblock.phase;

import java.util.List;

/** Définition immuable d'une phase (données de phases.yml). */
public record Phase(int id, String name, long threshold, String icon, String biome, String color,
                    String particle, String sound, List<String> rewards) {}
