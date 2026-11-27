package fr.skyhub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import fr.skyhub.hardcore.DataManager;
import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.menu.MenuModule;
import fr.skyhub.hardcore.GuiListener;
import fr.skyhub.hardcore.HardcoreCommand;
import fr.skyhub.hardcore.PlayerListener;
import fr.skyhub.hardcore.UnbanCommand;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkyHubPlugin extends JavaPlugin {

    private HubWorld hubWorld;
    private NpcManager npcManager;
    private WorldManager worldManager;
    private OneBlockPlugin oneBlock;
    private MenuModule menu;
    private LocationStore locations;
    private DataManager hardcoreData;
    private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Ajoute automatiquement les nouvelles options à un ancien config.yml
        // Migration : le mob / monde « Event » est remplacé par « One Block »
        if (getConfig().isConfigurationSection("npcs.event")) {
            if (!getConfig().isConfigurationSection("npcs.oneblock")) {
                for (String k : new String[]{"x", "y", "z", "yaw", "pitch"}) {
                    if (getConfig().contains("npcs.event.location." + k)) {
                        getConfig().set("npcs.oneblock.location." + k, getConfig().get("npcs.event.location." + k));
                    }
                }
            }
            getConfig().set("npcs.event", null);
        }
        getConfig().options().copyDefaults(true);

        // Migration : le mob / titre Hardcore doit être en rouge FONCÉ (dark_red), pas en rouge
        String g = getConfig().getString("npcs.hardcore.glow-color", "dark_red");
        if (g.equalsIgnoreCase("red")) getConfig().set("npcs.hardcore.glow-color", "dark_red");
        for (String key : new String[]{"npcs.hardcore.name", "npcs.hardcore.title"}) {
            String v = getConfig().getString(key);
            if (v != null && v.contains("<red>")) getConfig().set(key, v.replace("<red>", "<dark_red>"));
        }
        saveConfig();

        locations = new LocationStore(this);

        hubWorld = new HubWorld(this);
        hubWorld.load();

        worldManager = new WorldManager(this, hubWorld);
        worldManager.loadSaved();

        npcManager = new NpcManager(this, hubWorld);
        npcManager.spawnAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinListener(this, hubWorld), this);
        pm.registerEvents(new LobbyProtectionListener(this, hubWorld), this);
        pm.registerEvents(new NpcListener(npcManager), this);
        pm.registerEvents(new InventoryManager(this), this);
        pm.registerEvents(new RespawnListener(this), this);

        // Gamerules indépendantes par monde
        WorldGameRules gameRules = new WorldGameRules(this);
        pm.registerEvents(gameRules, this);
        gameRules.applyAll();

        // One Block (remplace l'ancien monde Event) : uniquement dans son monde
        oneBlock = new OneBlockPlugin(this);
        if (!oneBlock.enable()) oneBlock = null;

        // Menu de modération (/menu) : réglages séparés par monde
        menu = new MenuModule(this);
        menu.enable();

        HubCommand command = new HubCommand(this, hubWorld, npcManager, worldManager);
        getCommand("hub").setExecutor(command);
        getCommand("l").setExecutor(command);
        getCommand("l").setTabCompleter(command);

        // ---- Module Hardcore (actif uniquement dans le monde hardcore) ----
        hardcoreData = new DataManager(this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        UnbanCommand unban = new UnbanCommand(this);
        getCommand("unban").setExecutor(unban);
        getCommand("unban").setTabCompleter(unban);
        getCommand("hardcore").setExecutor(new HardcoreCommand(this));

        getLogger().info("SkyHub v" + getPluginMeta().getVersion() + " activé.");
    }

    @Override
    public void onDisable() {
        if (oneBlock != null) oneBlock.disable();
        if (hardcoreData != null) hardcoreData.save();
        if (menu != null) menu.disable();
    }

    /** Module /menu (null s'il n'est pas encore démarré). */
    public MenuModule menu() {
        return menu;
    }

    /** Module One Block (null s'il n'a pas pu démarrer). */
    public OneBlockPlugin oneBlock() {
        return oneBlock;
    }

    public boolean isOneBlockWorld(World w) {
        return oneBlock != null && oneBlock.islands() != null && w != null && w.equals(oneBlock.islands().world());
    }

    public HubWorld hub() {
        return hubWorld;
    }

    public DataManager data() {
        return hardcoreData;
    }

    public Set<UUID> awaiting() {
        return awaiting;
    }

    /** Vrai si le monde est le monde hardcore (ou son nether / end : world, world_nether, world_the_end). */
    public boolean isHardcoreWorld(World w) {
        if (w == null) return false;
        String base = getConfig().getString("hardcore.world", "world").toLowerCase(Locale.ROOT);
        String n = w.getName().toLowerCase(Locale.ROOT);
        return n.equals(base) || n.equals(base + "_nether") || n.equals(base + "_the_end");
    }

    public NpcManager npcManager() {
        return npcManager;
    }

    public LocationStore locations() {
        return locations;
    }

    /** Récupère un message de la config (MiniMessage) avec des remplacements clé/valeur. */
    public Component message(String key, String... replacements) {
        String raw = getConfig().getString("messages." + key, "<red>Message manquant : " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return MiniMessage.miniMessage().deserialize(raw);
    }
}
