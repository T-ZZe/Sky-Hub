package fr.skyhub.oneblock.utils;

import java.util.ArrayList;
import java.util.List;

/** Convertit un numéro d'emplacement d'île en coordonnées de grille (spirale carrée autour de 0,0). */
public final class SlotGrid {

    private static final List<int[]> CACHE = new ArrayList<>(List.of(new int[]{0, 0}));
    private static int x = 0, z = 0, dx = 0, dz = -1;

    private SlotGrid() {}

    public static synchronized int[] of(int slot) {
        if (slot < 0) throw new IllegalArgumentException("slot < 0");
        while (CACHE.size() <= slot) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int t = dx;
                dx = -dz;
                dz = t;
            }
            x += dx;
            z += dz;
            CACHE.add(new int[]{x, z});
        }
        int[] g = CACHE.get(slot);
        return new int[]{g[0], g[1]};
    }
}
