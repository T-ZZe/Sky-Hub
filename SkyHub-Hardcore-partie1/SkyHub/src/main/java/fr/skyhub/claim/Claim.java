package fr.skyhub.claim;

import org.bukkit.World;

/** Une zone cuboïde protégée avec ses règles. */
public final class Claim {
    public final String name;
    public final String world;
    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public boolean place = false;       // poser des blocs
    public boolean breakAllowed = false; // casser n'importe quel bloc (sinon : seulement ceux qu'on a posés)
    public boolean windcharge = false;   // utiliser des wind charges
    public boolean pvp = true;           // les joueurs peuvent se battre entre eux
    public boolean drop = true;          // les joueurs peuvent jeter des objets
    public int pearlSec = 0;             // délai entre deux lancers de perle d'Ender dans la zone (0 = aucune limite)
    public int expireSec = 0;            // les blocs posés disparaissent après X secondes (0 = jamais)
    public int restoreSec = 0;           // la zone est reconstruite toutes les X secondes (0 = jamais)

    public long nextRestore = 0;         // runtime
    public volatile boolean busy = false; // runtime : reconstruction / sauvegarde en cours

    public Claim(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean contains(World w, int x, int y, int z) {
        return w != null && w.getName().equals(world)
                && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String key() {
        return name.toLowerCase(java.util.Locale.ROOT);
    }
}
