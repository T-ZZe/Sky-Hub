package fr.skyhub.oneblock.phase;

import java.util.List;
import java.util.Optional;

/** Liste ordonnée des phases ; les seuils sont strictement croissants et le premier vaut 0. */
public final class PhaseRegistry {

    private final List<Phase> phases;

    public PhaseRegistry(List<Phase> phases) {
        if (phases.isEmpty()) throw new IllegalArgumentException("Aucune phase");
        for (int i = 0; i < phases.size(); i++) {
            Phase p = phases.get(i);
            if (p.id() != i + 1) throw new IllegalArgumentException("Les phases doivent être numérotées 1..n sans trou (trouvé " + p.id() + " en position " + (i + 1) + ")");
            if (i == 0 && p.threshold() != 0) throw new IllegalArgumentException("La phase 1 doit avoir un seuil de 0");
            if (i > 0 && p.threshold() <= phases.get(i - 1).threshold()) {
                throw new IllegalArgumentException("Seuils non croissants à la phase " + p.id());
            }
        }
        this.phases = List.copyOf(phases);
    }

    public int size() { return phases.size(); }

    public List<Phase> all() { return phases; }

    public Phase get(int id) {
        if (id < 1 || id > phases.size()) throw new IllegalArgumentException("Phase inconnue: " + id);
        return phases.get(id - 1);
    }

    public Optional<Phase> next(int id) {
        return id >= phases.size() ? Optional.empty() : Optional.of(phases.get(id));
    }

    /** Phase correspondant à un nombre cumulé de blocs. */
    public Phase phaseFor(long blocks) {
        Phase result = phases.get(0);
        for (Phase p : phases) {
            if (blocks >= p.threshold()) result = p; else break;
        }
        return result;
    }
}
