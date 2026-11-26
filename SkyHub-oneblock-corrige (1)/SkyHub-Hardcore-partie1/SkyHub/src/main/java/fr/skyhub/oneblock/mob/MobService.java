package fr.skyhub.oneblock.mob;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.phase.Phase;
import fr.skyhub.oneblock.utils.Effects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/** Apparitions de mobs déclenchées uniquement par une casse valide du OneBlock. */
public final class MobService {

    private final OneBlockPlugin plugin;

    public MobService(OneBlockPlugin plugin) { this.plugin = plugin; }

    public void onBreak(Island island, Player breaker, Phase phase) {
        MobSettings s = plugin.content().mobs();
        long now = System.currentTimeMillis();
        if (now - island.lastMobEventMillis < s.cooldownMs()) return;
        MobTable table = s.tables().get(phase.id());
        if (table == null) return;
        MobChoice choice = table.table().roll(ThreadLocalRandom.current());
        if (choice.kind() == MobChoice.Kind.NONE) return;

        int alive = countAlive(island);
        int room = s.maxMobs() - alive;
        if (room <= 0) return;

        int count = choice.kind() == MobChoice.Kind.SURPRISE ? Math.min(room, s.waveSize().roll()) : 1;
        World w = plugin.islands().world();
        boolean spawned = false;
        for (int n = 0; n < count; n++) {
            EntityType type = choice.kind() == MobChoice.Kind.SURPRISE
                    ? table.wave().get(ThreadLocalRandom.current().nextInt(table.wave().size())) : choice.type();
            Location loc = findSpot(island, w, s.radius());
            if (loc == null) break;
            spawn(island, loc, type, s);
            spawned = true;
        }
        if (spawned) {
            island.lastMobEventMillis = now;
            if (choice.kind() == MobChoice.Kind.SURPRISE) plugin.messages().send(breaker, "mob.wave");
        }
    }

    private void spawn(Island island, Location loc, EntityType type, MobSettings s) {
        Entity e = loc.getWorld().spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        e.getPersistentDataContainer().set(plugin.islandKey(), PersistentDataType.INTEGER, island.id());
        if (e instanceof LivingEntity le) {
            AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null && s.healthMultiplier() != 1.0) { hp.setBaseValue(hp.getBaseValue() * s.healthMultiplier()); le.setHealth(hp.getValue()); }
            AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null && s.damageMultiplier() != 1.0) dmg.setBaseValue(dmg.getBaseValue() * s.damageMultiplier());
        }
        Effects.burst(loc.clone().add(0, 0.5, 0), "poof", 12, 0.3);
    }

    /** Position libre (deux blocs d'air au-dessus d'un sol solide) autour du OneBlock ; null si introuvable. */
    private Location findSpot(Island island, World w, int radius) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = island.centerX() + r.nextInt(-radius, radius + 1), z = island.centerZ() + r.nextInt(-radius, radius + 1);
            if (x == island.centerX() && z == island.centerZ()) continue;
            for (int dy = 2; dy >= -2; dy--) {
                int y = island.oneBlockY() + 1 + dy;
                Block feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z), ground = w.getBlockAt(x, y - 1, z);
                if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid() && ground.getType().isSolid()) {
                    return new Location(w, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    /** Mobs issus du OneBlock de cette île actuellement chargés. */
    public int countAlive(Island island) {
        World w = plugin.islands().world();
        Location c = island.oneBlockLocation(w);
        int r = plugin.content().islands().protectionRadius();
        Collection<Entity> near = w.getNearbyEntities(c, r, 128, r, ent -> {
            Integer id = ent.getPersistentDataContainer().get(plugin.islandKey(), PersistentDataType.INTEGER);
            return id != null && id == island.id() && !ent.isDead();
        });
        return near.size();
    }
}
