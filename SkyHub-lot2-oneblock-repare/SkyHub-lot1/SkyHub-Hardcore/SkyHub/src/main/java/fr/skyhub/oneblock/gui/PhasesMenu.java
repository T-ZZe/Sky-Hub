package fr.skyhub.oneblock.gui;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.phase.BlockChoice;
import fr.skyhub.oneblock.phase.BlockPool;
import fr.skyhub.oneblock.phase.Phase;
import fr.skyhub.oneblock.phase.Rewards;
import fr.skyhub.oneblock.utils.ItemBuilder;
import fr.skyhub.oneblock.utils.Messages;
import fr.skyhub.oneblock.utils.WeightedTable;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /ob phases : toutes les phases avec état, seuil, récompenses et chances de génération. */
public final class PhasesMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39};

    private final Island island;

    public PhasesMenu(OneBlockPlugin plugin, Player viewer, Island island) {
        super(plugin, viewer, plugin.messages().get("gui.phases.title"), 6);
        this.island = island;
    }

    @Override protected void build() {
        var msg = plugin.messages();
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.FRANCE);
        List<Phase> phases = plugin.content().phases().all();
        for (int i = 0; i < phases.size() && i < SLOTS.length; i++) {
            Phase p = phases.get(i);
            boolean reached = p.id() <= island.phase();
            boolean current = p.id() == island.phase();
            ItemBuilder b = new ItemBuilder(reached ? Material.valueOf(p.icon()) : Material.GRAY_STAINED_GLASS_PANE)
                    .name(Messages.mm(p.color() + "<bold>" + p.id() + ". " + p.name()))
                    .glow(current)
                    .lore(msg.get(current ? "gui.phases.state-current" : reached ? "gui.phases.state-done" : "gui.phases.state-locked"))
                    .lore(msg.get("gui.progress.threshold", "threshold", nf.format(p.threshold())));
            BlockPool pool = plugin.content().pools().get(p.id());
            if (pool != null) {
                b.lore(msg.get("gui.phases.chances"));
                pool.table().entries().stream()
                        .sorted(Comparator.comparingDouble((WeightedTable.Entry<BlockChoice> e) -> e.weight()).reversed()).limit(6)
                        .forEach(e -> b.lore(msg.get("gui.phases.chance-line", "block", label(e.value()),
                                "percent", String.format(Locale.FRANCE, "%.1f", pool.table().percent(e)))));
            }
            if (!p.rewards().isEmpty()) {
                b.lore(msg.get("gui.progress.rewards"));
                p.rewards().forEach(r -> b.lore(msg.get("gui.progress.reward-line", "reward", Rewards.describe(r))));
            }
            set(SLOTS[i], b.build());
        }
        set(49, new ItemBuilder(Material.ARROW).name(msg.get("gui.back")).build(), e -> new ProgressMenu(plugin, viewer, island).open());
    }

    private static String label(BlockChoice c) {
        return switch (c.kind()) {
            case CHEST_BONUS -> "Coffre bonus";
            case SURPRISE -> "Bloc surprise";
            default -> c.material().name().toLowerCase().replace('_', ' ');
        };
    }
}
