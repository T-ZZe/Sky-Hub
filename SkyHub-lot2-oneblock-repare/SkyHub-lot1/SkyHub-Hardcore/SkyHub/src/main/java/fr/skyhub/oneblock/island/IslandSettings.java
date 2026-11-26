package fr.skyhub.oneblock.island;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Paramètres issus de islands.yml (immuable). */
public record IslandSettings(String worldName, int spacing, int protectionRadius, int oneBlockY, String initialBlock,
                             int maxPlayers, int inviteExpireSeconds, int deleteConfirmSeconds,
                             int blocksPerLevel, int maxLevel,
                             Map<IslandFlag, Boolean> member, Map<IslandFlag, Boolean> visitor,
                             boolean pvp, boolean explosionsBreakBlocks, boolean explosionsDamageEntities,
                             boolean fireSpread, boolean mobGriefing, boolean voidTeleport,
                             int clearMinY, int clearMaxY, int chunksPerTick) {

    public static IslandSettings parse(FileConfiguration c, List<String> errors) {
        int before = errors.size();
        String world = c.getString("world.name", "oneblock_world");
        int spacing = c.getInt("layout.spacing", 300);
        int radius = c.getInt("layout.protection-radius", 100);
        int y = c.getInt("layout.oneblock-y", 64);
        String initial = c.getString("layout.initial-block", "GRASS_BLOCK");
        int max = c.getInt("limits.max-players", 6);
        if (world.isBlank()) errors.add("islands.yml : world.name vide");
        if (spacing < 20) errors.add("islands.yml : layout.spacing doit être >= 20");
        if (radius < 5 || radius * 2 >= spacing) errors.add("islands.yml : layout.protection-radius doit être >= 5 et < spacing/2");
        if (y < -60 || y > 300) errors.add("islands.yml : layout.oneblock-y hors limites (-60..300)");
        if (org.bukkit.Material.matchMaterial(initial) == null || !org.bukkit.Material.matchMaterial(initial).isBlock()) errors.add("islands.yml : layout.initial-block invalide: " + initial);
        if (max < 1 || max > 6) errors.add("islands.yml : limits.max-players doit être entre 1 et 6");
        int perLevel = c.getInt("level.blocks-per-level", 250);
        if (perLevel < 1) errors.add("islands.yml : level.blocks-per-level doit être >= 1");
        int minY = c.getInt("deletion.clear-min-y", -64), maxY = c.getInt("deletion.clear-max-y", 200);
        if (minY >= maxY) errors.add("islands.yml : deletion.clear-min-y doit être < clear-max-y");
        if (errors.size() > before) return null;
        return new IslandSettings(world, spacing, radius, y, initial.toUpperCase(), max,
                Math.max(5, c.getInt("limits.invite-expire-seconds", 60)), Math.max(3, c.getInt("limits.delete-confirm-seconds", 15)),
                perLevel, Math.max(1, c.getInt("level.max-level", 1000)),
                flags(c.getConfigurationSection("permissions.member")), flags(c.getConfigurationSection("permissions.visitor")),
                c.getBoolean("protection.pvp", false), c.getBoolean("protection.explosions-break-blocks", false),
                c.getBoolean("protection.explosions-damage-entities", false), c.getBoolean("protection.fire-spread", false),
                c.getBoolean("protection.mob-griefing", false), c.getBoolean("protection.void-teleport", true),
                minY, maxY, Math.max(1, c.getInt("deletion.chunks-per-tick", 1)));
    }

    private static Map<IslandFlag, Boolean> flags(ConfigurationSection s) {
        Map<IslandFlag, Boolean> m = new EnumMap<>(IslandFlag.class);
        for (IslandFlag f : IslandFlag.values()) m.put(f, s != null && s.getBoolean(f.name().toLowerCase(), false));
        return m;
    }

    public boolean allowed(IslandRole role, IslandFlag flag) {
        if (role == IslandRole.OWNER) return true;
        return (role == IslandRole.MEMBER ? member : visitor).getOrDefault(flag, false);
    }

    public int levelFor(long blocks) {
        return (int) Math.min(maxLevel, 1 + blocks / blocksPerLevel);
    }
}
