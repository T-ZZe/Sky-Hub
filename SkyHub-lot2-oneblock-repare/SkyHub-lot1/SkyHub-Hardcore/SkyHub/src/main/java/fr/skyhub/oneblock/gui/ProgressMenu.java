package fr.skyhub.oneblock.gui;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.phase.Phase;
import fr.skyhub.oneblock.phase.Progress;
import fr.skyhub.oneblock.phase.Rewards;
import fr.skyhub.oneblock.utils.ItemBuilder;
import fr.skyhub.oneblock.utils.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

/** /ob progress : phase, blocs, seuil, pourcentage, phase suivante, récompenses. */
public final class ProgressMenu extends Menu {

    private final Island island;

    public ProgressMenu(OneBlockPlugin plugin, Player viewer, Island island) {
        super(plugin, viewer, plugin.messages().get("gui.progress.title"), 4);
        this.island = island;
    }

    @Override protected void build() {
        var msg = plugin.messages();
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.FRANCE);
        Progress p = Progress.of(plugin.content().phases(), island);
        Phase cur = p.current();

        set(4, new ItemBuilder(Material.valueOf(cur.icon())).name(Messages.mm(cur.color() + "<bold>" + cur.name())).glow(true)
                .lore(msg.get("gui.progress.phase", "id", String.valueOf(cur.id()), "total", String.valueOf(plugin.content().phases().size())))
                .lore(msg.get("gui.progress.blocks", "blocks", nf.format(p.blocks()))).build());

        // Barre de progression sur 9 emplacements
        int filled = (int) Math.floor(p.percent() / 100.0 * 9);
        for (int i = 0; i < 9; i++) {
            boolean on = i < filled || p.next() == null;
            set(18 + i, new ItemBuilder(on ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(msg.get("gui.progress.percent", "percent", String.format(Locale.FRANCE, "%.1f", p.percent()))).build());
        }

        if (p.next() != null) {
            Phase n = p.next();
            set(13, new ItemBuilder(Material.valueOf(n.icon()))
                    .name(msg.get("gui.progress.next", "name", n.name()))
                    .lore(msg.get("gui.progress.threshold", "threshold", nf.format(n.threshold())))
                    .lore(msg.get("gui.progress.remaining", "remaining", nf.format(p.remaining())))
                    .lore(msg.get("gui.progress.percent", "percent", String.format(Locale.FRANCE, "%.1f", p.percent()))).build());
        } else {
            set(13, new ItemBuilder(Material.NETHER_STAR).name(msg.get("gui.progress.max")).glow(true).build());
        }

        ItemBuilder rewards = new ItemBuilder(Material.CHEST).name(msg.get("gui.progress.rewards"));
        Phase target = p.next() != null ? p.next() : cur;
        for (String r : target.rewards()) rewards.lore(msg.get("gui.progress.reward-line", "reward", Rewards.describe(r)));
        set(15, rewards.build());

        set(31, new ItemBuilder(Material.BOOK).name(msg.get("gui.progress.all-phases")).build(), e -> new PhasesMenu(plugin, viewer, island).open());
        set(35, new ItemBuilder(Material.BARRIER).name(msg.get("gui.close")).build(), e -> viewer.closeInventory());
    }
}
