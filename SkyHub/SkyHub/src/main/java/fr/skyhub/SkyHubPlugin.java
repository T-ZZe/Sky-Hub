package fr.skyhub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkyHubPlugin extends JavaPlugin {

    private HubWorld hubWorld;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        hubWorld = new HubWorld(this);
        hubWorld.load();

        npcManager = new NpcManager(this, hubWorld);
        npcManager.spawnAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinListener(this, hubWorld), this);
        pm.registerEvents(new LobbyProtectionListener(this, hubWorld), this);
        pm.registerEvents(new NpcListener(npcManager), this);

        HubCommand command = new HubCommand(this, hubWorld);
        getCommand("hub").setExecutor(command);
        getCommand("skyhub").setExecutor(command);
        getCommand("skyhub").setTabCompleter(command);

        getLogger().info("SkyHub activé.");
    }

    public NpcManager npcManager() {
        return npcManager;
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
