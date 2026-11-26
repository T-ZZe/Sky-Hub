package fr.skyhub.oneblock.listeners;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Seule voie de comptage de la progression : la casse du bloc central par un membre.
 * Les blocs posés, les casses ailleurs ou hors île n'atteignent jamais ce code (position testée exactement).
 * Priorité HIGH + ignoreCancelled : la protection (LOW) a déjà refusé les non-membres.
 */
public final class OneBlockListener implements Listener {

    private final OneBlockPlugin plugin;

    public OneBlockListener(OneBlockPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!plugin.islands().isOneBlock(b)) return;
        Island island = plugin.islands().islandAt(b.getLocation());
        if (island == null) return;
        Player p = e.getPlayer();
        // Un non-membre bypass (admin) peut casser mais ne fait pas progresser l'île.
        boolean member = island.isMember(p.getUniqueId());
        e.setCancelled(true);
        if (!member) return;
        int exp = e.getExpToDrop();
        plugin.phases().handleBreak(island, p, b);
        if (exp > 0 && p.getGameMode() != GameMode.CREATIVE) {
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            b.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(exp));
        }
    }
}
