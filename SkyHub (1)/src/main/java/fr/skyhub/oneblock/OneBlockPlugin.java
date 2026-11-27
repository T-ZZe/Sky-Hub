package fr.skyhub.oneblock;

import fr.skyhub.SkyHubPlugin;
import fr.skyhub.oneblock.commands.OneBlockCommand;
import fr.skyhub.oneblock.database.Database;
import fr.skyhub.oneblock.gui.MenuListener;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.island.IslandManager;
import fr.skyhub.oneblock.listeners.OneBlockListener;
import fr.skyhub.oneblock.listeners.ProtectionListener;
import fr.skyhub.oneblock.listeners.WorldListener;
import fr.skyhub.oneblock.mob.MobService;
import fr.skyhub.oneblock.phase.Content;
import fr.skyhub.oneblock.phase.ContentLoader;
import fr.skyhub.oneblock.phase.PhaseService;
import fr.skyhub.oneblock.utils.ConfigManager;
import fr.skyhub.oneblock.utils.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Module One Block de SkyHub (ex-plugin OneBlockRPG).
 * Il ne fonctionne que dans le monde One Block (islands.yml : world.name) : listeners limités à ce monde,
 * commandes /ob réservées à ce monde (sauf pour les admins).
 * Fichiers : plugins/SkyHub/oneblock/*.yml et oneblock.db.
 */
public final class OneBlockPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SkyHubPlugin host;
    private final File dataFolder;
    private ConfigManager configs;
    private final Messages messages = new Messages();
    private volatile Content content;
    private FileConfiguration config;
    private Database database;
    private IslandManager islands;
    private PhaseService phases;
    private MobService mobs;
    private NamespacedKey islandKey;
    private BukkitTask saveTask;

    public OneBlockPlugin(SkyHubPlugin host) {
        this.host = host;
        this.dataFolder = new File(host.getDataFolder(), "oneblock");
    }

    // ------------------------------------------------------------ accès au plugin hôte (SkyHub)

    public SkyHubPlugin host() { return host; }
    public Logger getLogger() { return host.getLogger(); }
    public File getDataFolder() { return dataFolder; }
    public FileConfiguration getConfig() { return config; }

    // ------------------------------------------------------------ cycle de vie

    /** @return false si le module n'a pas pu démarrer (SkyHub continue de fonctionner sans lui). */
    public boolean enable() {
        dataFolder.mkdirs();
        islandKey = new NamespacedKey(host, "ob_island");
        configs = new ConfigManager(this);
        configs.saveDefaults();

        List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
        Map<String, FileConfiguration> files = configs.loadAll(errors);
        Content loaded = errors.isEmpty() ? ContentLoader.load(files, errors, warnings) : null;
        warnings.forEach(w -> getLogger().warning("[OneBlock] " + w));
        if (loaded == null || !errors.isEmpty()) {
            errors.forEach(e -> getLogger().severe("[OneBlock] " + e));
            getLogger().severe("[OneBlock] Configuration invalide : module One Block désactivé. Corrigez les erreurs ci-dessus.");
            return false;
        }
        this.content = loaded;
        messages.reload(files.get("messages.yml"));
        this.config = files.get("config.yml");

        try {
            database = new Database(this);
            database.createSchema();
            islands = new IslandManager(this, database);
            phases = new PhaseService(this);
            mobs = new MobService(this);
            islands.init();
        } catch (Throwable e) {
            getLogger().log(Level.SEVERE, "[OneBlock] Initialisation impossible : module One Block désactivé", e);
            return false;
        }

        ProtectionListener protection = new ProtectionListener(this);
        host.getServer().getPluginManager().registerEvents(protection, host);
        host.getServer().getPluginManager().registerEvents(new OneBlockListener(this), host);
        host.getServer().getPluginManager().registerEvents(new WorldListener(this, protection), host);
        host.getServer().getPluginManager().registerEvents(new MenuListener(), host);
        host.getServer().getPluginManager().registerEvents(this, host);

        PluginCommand ob = host.getCommand("ob");
        OneBlockCommand handler = new OneBlockCommand(this);
        ob.setExecutor(handler);
        ob.setTabCompleter(handler);

        long period = content.general().saveIntervalSeconds() * 20L;
        saveTask = host.getServer().getScheduler().runTaskTimer(host, () -> islands.flushAsync(), period, period);
        getLogger().info("[OneBlock] Module activé (" + content.phases().size() + " phases, monde " + islands.world().getName() + ").");
        return true;
    }

    public void disable() {
        if (saveTask != null) saveTask.cancel();
        if (islands != null) islands.flushSync();
        if (database != null) database.close();
    }

    /**
     * Recharge la configuration de gameplay. Tout est validé dans un instantané séparé : en cas d'erreur
     * rien n'est modifié. Les clés de monde/géométrie/base nécessitent un redémarrage.
     * @return la liste des erreurs (vide si succès)
     */
    public List<String> reloadAll() {
        List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
        Map<String, FileConfiguration> files = configs.loadAll(errors);
        if (!errors.isEmpty()) return errors;
        Content c = ContentLoader.load(files, errors, warnings);
        warnings.forEach(w -> getLogger().warning(w));
        if (c == null || !errors.isEmpty()) return errors.isEmpty() ? List.of("Configuration invalide") : errors;
        var s = c.islands();
        if (!s.worldName().equals(islands.world().getName()) || s.spacing() != islands.spacing() || s.oneBlockY() != islands.oneBlockY()) {
            getLogger().warning("islands.yml : world.name, layout.spacing ou layout.oneblock-y modifié : redémarrage requis, anciennes valeurs conservées pour la géométrie.");
        }
        this.content = c;
        messages.reload(files.get("messages.yml"));
        return List.of();
    }

    public Content content() { return content; }
    public Messages messages() { return messages; }
    public IslandManager islands() { return islands; }
    public PhaseService phases() { return phases; }
    public MobService mobs() { return mobs; }
    public NamespacedKey islandKey() { return islandKey; }

    // ------------------------------------------------------------ entrée dans le monde One Block

    /** Appelé par le mob « One Block » du hub : téléporte sur l'île du joueur (créée la première fois). */
    public void enter(Player p) {
        Island i = islands.islandOf(p.getUniqueId());
        boolean created = false;
        if (i == null) {
            if (islands.create(p) == IslandManager.CreateResult.OK) {
                i = islands.islandOf(p.getUniqueId());
                created = true;
            }
        }
        if (i == null) {
            p.sendMessage(MM.deserialize("<red>Impossible d'ouvrir ton île One Block."));
            return;
        }
        if (created) p.sendMessage(MM.deserialize("<green>Ton île One Block a été créée !"));
        islands.teleportHome(p, i);
        Bukkit.getScheduler().runTaskLater(host, () -> {
            if (!p.isOnline()) return;
            p.setGameMode(GameMode.SURVIVAL);
            p.setFallDistance(0);
        }, 5L);
    }

    /** Mort dans le monde One Block : on réapparaît sur son île. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (islands == null || !p.getWorld().equals(islands.world())) return;
        Island i = islands.islandOf(p.getUniqueId());
        if (i != null) e.setRespawnLocation(i.home(islands.world()));
    }
}
