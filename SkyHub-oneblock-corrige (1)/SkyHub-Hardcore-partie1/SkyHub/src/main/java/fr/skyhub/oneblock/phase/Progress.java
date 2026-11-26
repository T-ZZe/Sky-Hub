package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.island.Island;

/** Avancement d'une île vers la phase suivante. {@code next} est null en phase finale. */
public record Progress(Phase current, Phase next, long blocks, long remaining, double percent) {

    public static Progress of(PhaseRegistry reg, Island island) {
        Phase cur = reg.get(island.phase());
        Phase next = reg.next(cur.id()).orElse(null);
        if (next == null) return new Progress(cur, null, island.blocks(), 0, 100.0);
        long span = next.threshold() - cur.threshold();
        long done = Math.max(0, island.blocks() - cur.threshold());
        double pct = Math.min(100.0, done * 100.0 / span);
        return new Progress(cur, next, island.blocks(), Math.max(0, next.threshold() - island.blocks()), pct);
    }
}
