package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.island.IslandSettings;
import fr.skyhub.oneblock.mob.MobChoice;
import fr.skyhub.oneblock.mob.MobSettings;
import fr.skyhub.oneblock.mob.MobTable;
import fr.skyhub.oneblock.utils.Effects;
import fr.skyhub.oneblock.utils.ItemSpec;
import fr.skyhub.oneblock.utils.Ranges;
import fr.skyhub.oneblock.utils.WeightedTable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lit et valide phases.yml, blocks.yml, mobs.yml, islands.yml, config.yml. Retourne null si au moins une erreur. */
public final class ContentLoader {

    private ContentLoader() {}

    public static Content load(Map<String, FileConfiguration> files, List<String> errors, List<String> warnings) {
        int before = errors.size();
        PhaseRegistry phases = loadPhases(files.get("phases.yml"), errors);
        IslandSettings islands = IslandSettings.parse(files.get("islands.yml"), errors);
        FileConfiguration cfg = files.get("config.yml");
        GeneralSettings general = new GeneralSettings(cfg.getString("server-name", "OneBlockRPG"),
                Math.max(5, cfg.getInt("database.save-interval-seconds", 30)),
                Math.max(0, cfg.getLong("gameplay.min-break-interval-ms", 50)),
                cfg.getBoolean("gameplay.count-creative-breaks", false), cfg.getBoolean("gameplay.creative-drops", false));
        if (phases == null) return null;

        Map<Integer, BlockPool> pools = new HashMap<>();
        FileConfiguration bf = files.get("blocks.yml");
        Material surpriseBlock = Material.matchMaterial(bf.getString("surprise-block", "AMETHYST_BLOCK"));
        if (surpriseBlock == null || !surpriseBlock.isBlock()) { errors.add("blocks.yml : surprise-block invalide"); surpriseBlock = Material.AMETHYST_BLOCK; }
        WeightedTable<SurpriseEvent> surprises = loadSurprises(bf, errors);
        for (Phase p : phases.all()) {
            BlockPool pool = loadPool(bf, p.id(), errors, warnings);
            if (pool != null) pools.put(p.id(), pool);
        }
        MobSettings mobs = loadMobs(files.get("mobs.yml"), phases, errors, warnings);
        if (errors.size() > before || islands == null || mobs == null || surprises == null) return null;
        return new Content(phases, Map.copyOf(pools), surprises, surpriseBlock, mobs, islands, general);
    }

    // ---------------------------------------------------------------- phases

    private static PhaseRegistry loadPhases(FileConfiguration f, List<String> errors) {
        ConfigurationSection root = f.getConfigurationSection("phases");
        if (root == null) { errors.add("phases.yml : section 'phases' absente"); return null; }
        List<Integer> ids = new ArrayList<>();
        for (String k : root.getKeys(false)) {
            try { ids.add(Integer.parseInt(k)); } catch (NumberFormatException e) { errors.add("phases.yml : identifiant de phase non numérique '" + k + "'"); }
        }
        ids.sort(Integer::compare);
        List<Phase> list = new ArrayList<>();
        int before = errors.size();
        for (int id : ids) {
            ConfigurationSection s = root.getConfigurationSection(String.valueOf(id));
            if (s == null) { errors.add("phases.yml : phase " + id + " mal formée"); continue; }
            String where = "phases.yml (phase " + id + ") : ";
            String icon = s.getString("icon", "STONE");
            Material m = Material.matchMaterial(icon);
            if (m == null || !m.isItem()) errors.add(where + "icon invalide '" + icon + "'");
            String biome = s.getString("biome", "plains");
            NamespacedKey bk = NamespacedKey.fromString(biome.toLowerCase());
            if (bk == null || Registry.BIOME.get(bk) == null) errors.add(where + "biome inconnu '" + biome + "'");
            String particle = s.getString("particle", "happy_villager");
            if (!Effects.isSimpleParticle(particle)) errors.add(where + "particule inconnue ou non supportée '" + particle + "'");
            String sound = s.getString("sound", "entity.player.levelup");
            if (!Effects.isSoundKey(sound)) errors.add(where + "son invalide '" + sound + "'");
            List<String> rewards = s.getStringList("rewards");
            for (String r : rewards) { String err = Rewards.validate(r); if (err != null) errors.add(where + "récompense '" + r + "' : " + err); }
            list.add(new Phase(id, s.getString("name", "Phase " + id), s.getLong("threshold", -1), icon.toUpperCase(), biome.toLowerCase(),
                    s.getString("color", "<white>"), particle.toLowerCase(), sound.toLowerCase(), List.copyOf(rewards)));
        }
        if (errors.size() > before) return null;
        try { return new PhaseRegistry(list); }
        catch (IllegalArgumentException e) { errors.add("phases.yml : " + e.getMessage()); return null; }
    }

    // ---------------------------------------------------------------- blocs

    private static BlockPool loadPool(FileConfiguration f, int phase, List<String> errors, List<String> warnings) {
        String where = "blocks.yml (phase " + phase + ") : ";
        ConfigurationSection s = f.getConfigurationSection("phases." + phase + ".blocks");
        if (s == null) { errors.add(where + "section 'blocks' absente"); return null; }
        List<WeightedTable.Entry<BlockChoice>> entries = new ArrayList<>();
        double sum = 0;
        boolean ok = true;
        for (String k : s.getKeys(false)) {
            Object raw = s.get(k);
            if (!(raw instanceof Number n) || n.doubleValue() <= 0) { errors.add(where + "poids invalide pour " + k); ok = false; continue; }
            BlockChoice choice;
            if (k.equalsIgnoreCase("CHEST_BONUS")) choice = new BlockChoice(BlockChoice.Kind.CHEST_BONUS, Material.CHEST);
            else if (k.equalsIgnoreCase("SURPRISE")) choice = new BlockChoice(BlockChoice.Kind.SURPRISE, null);
            else {
                Material m = Material.matchMaterial(k);
                if (m == null || !m.isBlock() || m.isAir()) { errors.add(where + "bloc inconnu ou non posable '" + k + "'"); ok = false; continue; }
                choice = new BlockChoice(BlockChoice.Kind.MATERIAL, m);
            }
            entries.add(new WeightedTable.Entry<>(choice, n.doubleValue()));
            sum += n.doubleValue();
        }
        List<ItemSpec> chest = new ArrayList<>();
        for (String it : f.getStringList("phases." + phase + ".bonus-chest")) {
            try { chest.add(ItemSpec.parse(it)); } catch (IllegalArgumentException e) { errors.add(where + "bonus-chest : " + e.getMessage()); ok = false; }
        }
        boolean hasChest = entries.stream().anyMatch(e -> e.value().kind() == BlockChoice.Kind.CHEST_BONUS);
        if (hasChest && chest.isEmpty()) { errors.add(where + "CHEST_BONUS présent mais bonus-chest vide"); ok = false; }
        if (entries.isEmpty()) { errors.add(where + "aucun bloc défini"); return null; }
        if (!ok) return null;
        if (Math.abs(sum - 100.0) > 0.01) warnings.add(where + "la somme des poids vaut " + sum + " (au lieu de 100) ; normalisée automatiquement");
        return new BlockPool(new WeightedTable<>(entries), List.copyOf(chest));
    }

    private static WeightedTable<SurpriseEvent> loadSurprises(FileConfiguration f, List<String> errors) {
        List<Map<?, ?>> list = f.getMapList("surprise-events");
        List<WeightedTable.Entry<SurpriseEvent>> out = new ArrayList<>();
        int idx = 0, before = errors.size();
        for (Map<?, ?> m : list) {
            idx++;
            String where = "blocks.yml (surprise-events #" + idx + ") : ";
            try {
                SurpriseEvent.Type type = SurpriseEvent.Type.valueOf(String.valueOf(m.get("type")).toUpperCase());
                double w = Double.parseDouble(String.valueOf(m.get("weight")));
                SurpriseEvent ev;
                switch (type) {
                    case DROPS -> {
                        List<ItemSpec> items = new ArrayList<>();
                        Object o = m.get("items");
                        if (!(o instanceof List<?> l) || l.isEmpty()) throw new IllegalArgumentException("items manquant");
                        for (Object e : l) items.add(ItemSpec.parse(String.valueOf(e)));
                        ev = new SurpriseEvent(type, List.copyOf(items), null, null, 0, 0);
                    }
                    case XP -> ev = new SurpriseEvent(type, List.of(), Ranges.parse(String.valueOf(m.get("amount"))), null, 0, 0);
                    default -> {
                        NamespacedKey k = NamespacedKey.fromString(String.valueOf(m.get("effect")).toLowerCase());
                        PotionEffectType pt = k == null ? null : Registry.EFFECT.get(k);
                        if (pt == null) throw new IllegalArgumentException("effet inconnu '" + m.get("effect") + "'");
                        int seconds = Integer.parseInt(String.valueOf(m.get("seconds"))), level = Integer.parseInt(String.valueOf(m.get("level")));
                        if (seconds < 1 || level < 1) throw new IllegalArgumentException("seconds et level doivent être >= 1");
                        ev = new SurpriseEvent(type, List.of(), null, pt, seconds, level);
                    }
                }
                out.add(new WeightedTable.Entry<>(ev, w));
            } catch (IllegalArgumentException | NullPointerException e) {
                errors.add(where + e.getMessage());
            }
        }
        if (out.isEmpty() && errors.size() == before) errors.add("blocks.yml : surprise-events vide");
        return errors.size() > before ? null : new WeightedTable<>(out);
    }

    // ---------------------------------------------------------------- mobs

    private static MobSettings loadMobs(FileConfiguration f, PhaseRegistry phases, List<String> errors, List<String> warnings) {
        int before = errors.size();
        int max = f.getInt("settings.max-mobs-per-island", 12), radius = f.getInt("settings.spawn-radius", 3);
        long cd = f.getLong("settings.spawn-cooldown-ms", 1500);
        if (max < 0) errors.add("mobs.yml : max-mobs-per-island doit être >= 0");
        if (radius < 1 || radius > 10) errors.add("mobs.yml : spawn-radius doit être entre 1 et 10");
        Ranges wave = new Ranges(2, 4);
        try { wave = Ranges.parse(f.getString("settings.surprise-wave-size", "2-4")); } catch (IllegalArgumentException e) { errors.add("mobs.yml : surprise-wave-size : " + e.getMessage()); }
        String diff = f.getString("settings.difficulty", "NORMAL").toUpperCase();
        ConfigurationSection d = f.getConfigurationSection("difficulties." + diff);
        if (d == null) errors.add("mobs.yml : difficulté '" + diff + "' non définie dans 'difficulties'");
        double hm = d == null ? 1 : d.getDouble("health-multiplier", 1), dm = d == null ? 1 : d.getDouble("damage-multiplier", 1);
        if (hm <= 0 || dm <= 0) errors.add("mobs.yml : les multiplicateurs de difficulté doivent être > 0");

        Map<Integer, MobTable> tables = new HashMap<>();
        for (Phase p : phases.all()) {
            String where = "mobs.yml (phase " + p.id() + ") : ";
            ConfigurationSection s = f.getConfigurationSection("phases." + p.id() + ".entries");
            if (s == null) { errors.add(where + "section 'entries' absente"); continue; }
            List<WeightedTable.Entry<MobChoice>> entries = new ArrayList<>();
            double sum = 0;
            for (String k : s.getKeys(false)) {
                Object raw = s.get(k);
                if (!(raw instanceof Number n) || n.doubleValue() <= 0) { errors.add(where + "poids invalide pour " + k); continue; }
                MobChoice c;
                if (k.equalsIgnoreCase("NONE")) c = new MobChoice(MobChoice.Kind.NONE, null);
                else if (k.equalsIgnoreCase("SURPRISE")) c = new MobChoice(MobChoice.Kind.SURPRISE, null);
                else {
                    EntityType t = entity(k);
                    if (t == null) { errors.add(where + "type d'entité inconnu ou non apparaissable '" + k + "'"); continue; }
                    c = new MobChoice(MobChoice.Kind.ENTITY, t);
                }
                entries.add(new WeightedTable.Entry<>(c, n.doubleValue()));
                sum += n.doubleValue();
            }
            List<EntityType> waveList = new ArrayList<>();
            for (String w : f.getStringList("phases." + p.id() + ".wave")) {
                EntityType t = entity(w);
                if (t == null) errors.add(where + "wave : entité inconnue '" + w + "'"); else waveList.add(t);
            }
            boolean hasSurprise = entries.stream().anyMatch(e -> e.value().kind() == MobChoice.Kind.SURPRISE);
            if (hasSurprise && waveList.isEmpty()) errors.add(where + "SURPRISE présent mais 'wave' vide");
            if (entries.isEmpty()) { errors.add(where + "aucune entrée"); continue; }
            if (Math.abs(sum - 100.0) > 0.01) warnings.add(where + "la somme des poids vaut " + Math.round(sum * 100) / 100.0 + " (au lieu de 100) ; normalisée");
            tables.put(p.id(), new MobTable(new WeightedTable<>(entries), List.copyOf(waveList)));
        }
        if (errors.size() > before) return null;
        return new MobSettings(max, cd, radius, wave, hm, dm, Map.copyOf(tables));
    }

    private static EntityType entity(String name) {
        try {
            EntityType t = EntityType.valueOf(name.toUpperCase());
            return t.isSpawnable() && t.isAlive() ? t : null;
        } catch (IllegalArgumentException e) { return null; }
    }
}
