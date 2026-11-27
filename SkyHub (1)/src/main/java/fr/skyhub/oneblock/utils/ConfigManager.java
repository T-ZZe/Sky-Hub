package fr.skyhub.oneblock.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import fr.skyhub.oneblock.OneBlockPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chargement des fichiers YAML : copie des défauts, lecture stricte, erreurs explicites sans casser l'existant. */
public final class ConfigManager {

    public static final List<String> FILES = List.of("config.yml", "phases.yml", "blocks.yml", "mobs.yml", "islands.yml", "messages.yml");
    /** Fichiers dont les valeurs manquantes retombent sur les défauts embarqués. */
    private static final List<String> WITH_DEFAULTS = List.of("config.yml", "islands.yml", "messages.yml");

    private final OneBlockPlugin plugin;

    public ConfigManager(OneBlockPlugin plugin) { this.plugin = plugin; }

    public void saveDefaults() {
        for (String f : FILES) {
            if (!new File(plugin.getDataFolder(), f).exists()) plugin.host().saveResource("oneblock/" + f, false);
        }
    }

    /** Charge tous les fichiers ; ajoute un message par fichier illisible à {@code errors}. */
    public Map<String, FileConfiguration> loadAll(List<String> errors) {
        Map<String, FileConfiguration> out = new LinkedHashMap<>();
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            YamlConfiguration cfg = new YamlConfiguration();
            try {
                cfg.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                errors.add(name + " : YAML illisible ou invalide -> " + e.getMessage());
                continue;
            }
            if (WITH_DEFAULTS.contains(name)) {
                try (Reader r = new InputStreamReader(plugin.host().getResource("oneblock/" + name), StandardCharsets.UTF_8)) {
                    cfg.setDefaults(YamlConfiguration.loadConfiguration(r));
                } catch (IOException | NullPointerException ignored) {
                    // pas de défauts embarqués : on garde le fichier tel quel
                }
            }
            out.put(name, cfg);
        }
        return out;
    }
}
