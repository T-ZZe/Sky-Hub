package fr.skyhub.oneblock.utils;

import java.util.concurrent.ThreadLocalRandom;

/** Intervalle d'entiers inclusif, parsé depuis "5" ou "3-8". */
public record Ranges(int min, int max) {

    public Ranges {
        if (min < 0 || max < min) throw new IllegalArgumentException("Intervalle invalide: " + min + "-" + max);
    }

    public static Ranges parse(String s) {
        String t = s.trim();
        try {
            int dash = t.indexOf('-', 1);
            if (dash < 0) {
                int v = Integer.parseInt(t);
                return new Ranges(v, v);
            }
            return new Ranges(Integer.parseInt(t.substring(0, dash).trim()), Integer.parseInt(t.substring(dash + 1).trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Nombre ou intervalle invalide: '" + s + "'");
        }
    }

    public int roll() {
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
