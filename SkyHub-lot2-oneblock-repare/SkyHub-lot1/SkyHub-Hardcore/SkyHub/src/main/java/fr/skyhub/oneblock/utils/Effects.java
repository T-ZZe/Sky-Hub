package fr.skyhub.oneblock.utils;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;

/** Particules et sons via clés minecraft (ex. "happy_villager", "entity.player.levelup"). */
public final class Effects {

    private Effects() {}

    public static Particle particle(String key) {
        NamespacedKey k = NamespacedKey.fromString(key.toLowerCase());
        return k == null ? null : Registry.PARTICLE_TYPE.get(k);
    }

    /** Une particule est utilisable ici si elle ne requiert pas de données supplémentaires. */
    public static boolean isSimpleParticle(String key) {
        Particle p = particle(key);
        return p != null && p.getDataType() == Void.class;
    }

    public static boolean isSoundKey(String key) { return NamespacedKey.fromString(key.toLowerCase()) != null; }

    public static void burst(Location loc, String key, int count, double spread) {
        Particle p = particle(key);
        World w = loc.getWorld();
        if (p == null || w == null || p.getDataType() != Void.class) return;
        w.spawnParticle(p, loc, count, spread, spread, spread, 0.02);
    }

    public static void sound(Location loc, String key, float volume, float pitch) {
        World w = loc.getWorld();
        if (w != null && isSoundKey(key)) w.playSound(loc, key, SoundCategory.PLAYERS, volume, pitch);
    }
}
