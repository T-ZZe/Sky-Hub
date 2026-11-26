package fr.skyhub.menu;

import fr.skyhub.SkyHubPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Menu de modération (/menu) : enchantements, objets, cooldowns, PvP, équilibrage, monde. */
public class MenuModule {

    private final SkyHubPlugin plugin;

    public MenuModule(SkyHubPlugin plugin) {
        this.plugin = plugin;
    }

    private LimitManager limits;
    private GuiManager gui;

    public void enable() {
        limits = new LimitManager(plugin);
        limits.load();
        gui = new GuiManager(limits);

        LimitListener listener = new LimitListener(plugin, limits);
        CombatManager combat = new CombatManager(limits);
        CooldownListener cooldowns = new CooldownListener(limits, combat);
        ItemLimitListener itemLimits = new ItemLimitListener(limits);
        RulesListener rules = new RulesListener(limits);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(gui, plugin);
        pm.registerEvents(listener, plugin);
        pm.registerEvents(combat, plugin);
        pm.registerEvents(cooldowns, plugin);
        pm.registerEvents(itemLimits, plugin);
        pm.registerEvents(rules, plugin);

        // Le combat tag est maintenant integre : on coupe l'ancien plugin pour eviter les doublons.
        Plugin old = pm.getPlugin("CombatTagPlugin");
        if (old != null && old.isEnabled()) {
            plugin.getLogger().warning("CombatTagPlugin detecte : le combat tag est inclus dans EnchantLimit, "
                    + "l'ancien plugin est desactive. Tu peux supprimer CombatTagPlugin.jar.");
            pm.disablePlugin(old);
        }

        var cmd = plugin.getCommand("menu");
        if (cmd != null) {
            cmd.setExecutor(new EnchantCommand(gui));
        }

        // Pieces d'armure portees : controle toutes les 5 ticks
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) listener.enforce(p);
        }, 5L, 5L);

        // Quantite d'objets portes + compte a rebours du combat : toutes les secondes
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) itemLimits.enforce(p);
            combat.tick();
        }, 20L, 20L);

        // Niveaux d'enchantement : controle des inventaires toutes les 2 secondes
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Inventory inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (it != null && limits.of(p.getWorld()).clamp(it)) inv.setItem(i, it);
                }
            }
        }, 40L, 40L);
    }

    public void disable() {
        if (limits != null) limits.save();
    }
}
