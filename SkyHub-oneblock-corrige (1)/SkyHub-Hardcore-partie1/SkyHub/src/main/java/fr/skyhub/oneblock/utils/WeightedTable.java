package fr.skyhub.oneblock.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/** Table de tirage pondéré immuable (recherche binaire sur les poids cumulés). */
public final class WeightedTable<T> {

    public record Entry<T>(T value, double weight) {}

    private final List<Entry<T>> entries;
    private final double[] cumulative;
    private final double total;

    public WeightedTable(List<Entry<T>> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("La table de tirage est vide");
        }
        List<Entry<T>> copy = new ArrayList<>(entries.size());
        this.cumulative = new double[entries.size()];
        double sum = 0;
        int i = 0;
        for (Entry<T> e : entries) {
            if (e.value() == null) throw new IllegalArgumentException("Valeur nulle dans la table");
            if (!(e.weight() > 0) || Double.isInfinite(e.weight())) {
                throw new IllegalArgumentException("Poids invalide (" + e.weight() + ") pour " + e.value());
            }
            sum += e.weight();
            cumulative[i++] = sum;
            copy.add(e);
        }
        this.entries = Collections.unmodifiableList(copy);
        this.total = sum;
    }

    public T roll(RandomGenerator random) {
        double r = random.nextDouble() * total;
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (r < cumulative[mid]) hi = mid; else lo = mid + 1;
        }
        return entries.get(lo).value();
    }

    public double totalWeight() { return total; }

    public List<Entry<T>> entries() { return entries; }

    /** Probabilité réelle (en %) d'une entrée, après normalisation. */
    public double percent(Entry<T> e) { return e.weight() / total * 100.0; }
}
