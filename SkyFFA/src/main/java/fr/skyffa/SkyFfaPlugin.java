package fr.skyffa;

import fr.skyffa.claim.ClaimCommand;
import fr.skyffa.claim.ClaimListener;
import fr.skyffa.claim.ClaimManager;
import fr.skyffa.ffa.FfaCombat;
import fr.skyffa.ffa.FfaCommand;
import fr.skyffa.ffa.FfaItemCleaner;
import fr.skyffa.ffa.FfaModes;
import fr.skyffa.ffa.FfaRespawnListener;
import fr.skyffa.ffa.SpawnCommand;
import fr.skyffa.ffa.TeleportWarmup;
import fr.skyffa.kit.KitCommand;
import fr.skyffa.kit.KitGui;
import fr.skyffa.kit.KitListener;
import fr.skyffa.kit.KitManager;
import fr.skyffa.npc.CustomNpcManager;
import fr.skyffa.npc.NpcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * SkyFFA : tout le FFA (zones, combat, /spawn), les kits (/kit), les zones protégées (/claim)
 * et les PNJ personnalisés (/npc). Plugin séparé de SkyHub (hub, Hardcore, One Block, /menu).
 */
public final class SkyFfaPlugin extends JavaPlugin {

    private FfaModes ffa;
    private FfaCombat ffaCombat;
    private TeleportWarmup warmup;
    private KitManager kits;
    private ClaimManager claimManager;
    private CustomNpcManager customNpcs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateFromSkyHub();
        getConfig().options().copyDefaults(true);
        saveConfig();

        var pm = getServer().getPluginManager();

        // Zones FFA
        ffa = new FfaModes(this);
        pm.registerEvents(ffa, this);
        pm.registerEvents(new FfaRespawnListener(this), this);

        // Combat de 20 s (pas de déco / tp), /spawn et /lobby avec 5 s sans bouger
        ffaCombat = new FfaCombat(this);
        pm.registerEvents(ffaCombat, this);
        ffaCombat.start();
        warmup = new TeleportWarmup(this);
        pm.registerEvents(warmup, this);
        getCommand("spawn").setExecutor(new SpawnCommand(this));

        FfaCommand ffaCommand = new FfaCommand(this);
        getCommand("ffa").setExecutor(ffaCommand);
        getCommand("ffa").setTabCompleter(ffaCommand);

        // FFA : les objets au sol disparaissent après un délai
        FfaItemCleaner.start(this);

        // Kits FFA (/kit)
        kits = new KitManager(this);
        KitGui kitGui = new KitGui(this, kits);
        pm.registerEvents(new KitListener(this, kits, kitGui), this);
        KitCommand kitCommand = new KitCommand(kits, kitGui);
        getCommand("kit").setExecutor(kitCommand);
        getCommand("kit").setTabCompleter(kitCommand);

        // Zones protégées (/claim)
        claimManager = new ClaimManager(this);
        claimManager.start();
        pm.registerEvents(new ClaimListener(claimManager), this);
        ClaimCommand claimCommand = new ClaimCommand(claimManager);
        getCommand("claim").setExecutor(claimCommand);
        getCommand("claim").setTabCompleter(claimCommand);

        // PNJ personnalisés (/npc)
        customNpcs = new CustomNpcManager(this);
        pm.registerEvents(customNpcs, this);
        NpcCommand npcCommand = new NpcCommand(customNpcs);
        getCommand("npc").setExecutor(npcCommand);
        getCommand("npc").setTabCompleter(npcCommand);
        customNpcs.start();

        getLogger().info("SkyFFA v" + getPluginMeta().getVersion() + " activé.");
    }

    @Override
    public void onDisable() {
        if (claimManager != null) claimManager.shutdown();
        if (customNpcs != null) customNpcs.save();
    }

    public FfaModes ffa() {
        return ffa;
    }

    public FfaCombat ffaCombat() {
        return ffaCombat;
    }

    public TeleportWarmup warmup() {
        return warmup;
    }

    public KitManager kits() {
        return kits;
    }

    /** Récupère un message de la config (MiniMessage) avec des remplacements clé/valeur. */
    public Component message(String key, String... replacements) {
        String raw = getConfig().getString("messages." + key, "<red>Message manquant : " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return MiniMessage.miniMessage().deserialize(raw);
    }

    // ------------------------------------------------------------------
    //  Reprise des données de l'ancien SkyHub (une seule fois)
    // ------------------------------------------------------------------

    /**
     * Avant la séparation, kits / claims / PNJ / spawns de zones étaient dans plugins/SkyHub/.
     * Au premier lancement, ils sont COPIÉS ici (rien n'est supprimé dans SkyHub).
     */
    private void migrateFromSkyHub() {
        File marker = new File(getDataFolder(), ".migrated-from-skyhub");
        File old = new File(getDataFolder().getParentFile(), "SkyHub");
        if (marker.exists() || !old.isDirectory()) return;
        getDataFolder().mkdirs();
        try {
            for (String name : new String[]{"kit-data.yml", "claims.yml", "claims", "custom-npcs.yml"}) {
                File src = new File(old, name);
                File dst = new File(getDataFolder(), name);
                if (src.exists() && !dst.exists()) copy(src.toPath(), dst.toPath());
            }
            File oldCfg = new File(old, "config.yml");
            if (oldCfg.isFile()) {
                YamlConfiguration o = YamlConfiguration.loadConfiguration(oldCfg);
                ConfigurationSection spawns = o.getConfigurationSection("ffa-spawns");
                if (spawns != null && !getConfig().isConfigurationSection("ffa-spawns")) {
                    for (String key : spawns.getKeys(true)) {
                        if (!spawns.isConfigurationSection(key)) getConfig().set("ffa-spawns." + key, spawns.get(key));
                    }
                }
                String world = o.getString("npcs.ffa.world");
                if (world != null) getConfig().set("ffa.world", world);
                String gm = o.getString("npcs.ffa.gamemode");
                if (gm != null) getConfig().set("ffa.gamemode", gm);
                saveConfig();
            }
            marker.createNewFile();
            getLogger().info("Données FFA / kits / claims / PNJ reprises depuis plugins/SkyHub.");
        } catch (IOException ex) {
            getLogger().warning("Reprise des données de SkyHub impossible : " + ex.getMessage());
        }
    }

    private static void copy(Path src, Path dst) throws IOException {
        if (!Files.isDirectory(src)) {
            Files.copy(src, dst);
            return;
        }
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path target = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
    }
}
