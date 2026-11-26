package fr.skyhub.ffa;

import fr.skyhub.SkyHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;

/** Dans le monde FFA, un objet resté au sol plus de X secondes (180 par défaut) disparaît. */
public final class FfaItemCleaner {
    private FfaItemCleaner() {
    }

    public static void start(SkyHubPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int sec = plugin.getConfig().getInt("ffa.item-despawn-seconds", 180);
            if (sec <= 0) return;
            World w = plugin.ffa().ffaWorld();
            if (w == null) return;
            int limit = sec * 20;
            for (Item it : w.getEntitiesByClass(Item.class)) {
                if (it.getTicksLived() >= limit) it.remove();
            }
        }, 100L, 100L);
    }
}
