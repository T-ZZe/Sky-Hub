package fr.skyhub.oneblock.island;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.database.Database;
import fr.skyhub.oneblock.phase.Phase;
import fr.skyhub.oneblock.utils.SlotGrid;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Registre et cycle de vie des îles. Thread principal uniquement ; la persistance est asynchrone. */
public final class IslandManager {

    public record Invite(int islandId, UUID inviter, String inviterName, long expiresAt) {}

    public enum CreateResult { OK, ALREADY_HAS_ISLAND }

    private final OneBlockPlugin plugin;
    private final Database db;
    private final String worldName;
    private final int spacing;
    private final int oneBlockY;
    private World world;

    private final Map<Integer, Island> byId = new HashMap<>();
    private final Map<UUID, Island> byPlayer = new HashMap<>();
    private final Map<Long, Island> byGrid = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();
    private final Set<Island> dirty = new HashSet<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, long[]> deleteConfirm = new HashMap<>();
    private int nextId = 1;

    public IslandManager(OneBlockPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        IslandSettings s = plugin.content().islands();
        this.worldName = s.worldName();
        this.spacing = s.spacing();
        this.oneBlockY = s.oneBlockY();
    }

    // ------------------------------------------------------------ démarrage

    public void init() throws SQLException {
        world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.createWorld(new WorldCreator(worldName).generator(new VoidGenerator()).generateStructures(false));
        if (world == null) throw new IllegalStateException("Impossible de créer le monde " + worldName);

        var rows = db.sync(IslandRepository::loadIslands);
        var members = db.sync(IslandRepository::loadMembers);
        for (var r : rows) {
            int[] g = SlotGrid.of(r.slot());
            Island i = new Island(r.id(), r.owner(), r.name(), r.slot(), g[0] * spacing, g[1] * spacing, oneBlockY,
                    r.blocks(), r.phase(), r.rewarded(), r.mobs(), r.created());
            register(i, g);
            nextId = Math.max(nextId, r.id() + 1);
        }
        for (var m : members) {
            Island i = byId.get(m.islandId());
            if (i == null) continue;
            i.members().put(m.uuid(), new IslandMember(m.uuid(), m.name(), m.role(), m.joined(), m.contributed()));
            byPlayer.put(m.uuid(), i);
        }
        plugin.getLogger().info(rows.size() + " île(s) chargée(s).");
    }

    private void register(Island i, int[] grid) {
        byId.put(i.id(), i);
        byGrid.put(key(grid[0], grid[1]), i);
        usedSlots.add(i.slot());
    }

    private static long key(int gx, int gz) { return ((long) gx << 32) ^ (gz & 0xffffffffL); }

    // ------------------------------------------------------------ accès

    public World world() { return world; }
    public Collection<Island> all() { return byId.values(); }
    public Island byId(int id) { return byId.get(id); }
    public Island islandOf(UUID player) { return byPlayer.get(player); }
    public int spacing() { return spacing; }
    public int oneBlockY() { return oneBlockY; }
    public IslandSettings settings() { return plugin.content().islands(); }

    /** Île dont la zone protégée contient ce point (null si aucune). */
    public Island islandAt(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(world)) return null;
        return islandAt(loc.getBlockX(), loc.getBlockZ());
    }

    public Island islandAt(int x, int z) {
        int gx = Math.floorDiv(x + spacing / 2, spacing);
        int gz = Math.floorDiv(z + spacing / 2, spacing);
        Island i = byGrid.get(key(gx, gz));
        return i != null && i.contains(x, z, settings().protectionRadius()) ? i : null;
    }

    public boolean isOneBlock(Block b) {
        if (!b.getWorld().equals(world)) return false;
        Island i = byGrid.get(key(Math.floorDiv(b.getX() + spacing / 2, spacing), Math.floorDiv(b.getZ() + spacing / 2, spacing)));
        return i != null && i.isOneBlock(b);
    }

    /** Résout "#12" / "12" (id) ou un pseudo de membre (en ligne ou non). */
    public Island find(String token) {
        String t = token.startsWith("#") ? token.substring(1) : token;
        try { return byId.get(Integer.parseInt(t)); } catch (NumberFormatException ignored) { /* pseudo */ }
        for (Island i : byId.values()) if (i.findMember(token) != null) return i;
        return null;
    }

    public List<Island> top(int n) {
        return byId.values().stream().sorted(Comparator.comparingLong(Island::blocks).reversed().thenComparingInt(Island::id)).limit(n).toList();
    }

    // ------------------------------------------------------------ création

    public CreateResult create(Player p) {
        if (byPlayer.containsKey(p.getUniqueId())) return CreateResult.ALREADY_HAS_ISLAND;
        int slot = 0;
        while (usedSlots.contains(slot)) slot++;
        int[] g = SlotGrid.of(slot);
        Phase first = plugin.content().phases().get(1);
        Island i = new Island(nextId++, p.getUniqueId(), p.getName() + "-island", slot, g[0] * spacing, g[1] * spacing, oneBlockY,
                0, 1, 1, 0, System.currentTimeMillis());
        i.members().put(p.getUniqueId(), new IslandMember(p.getUniqueId(), p.getName(), IslandRole.OWNER, System.currentTimeMillis(), 0));
        i.markMembersDirty();
        register(i, g);
        byPlayer.put(p.getUniqueId(), i);
        world.getBlockAt(i.centerX(), oneBlockY, i.centerZ()).setType(Material.valueOf(settings().initialBlock()), false);
        plugin.phases().applyBiome(i, first);
        markDirty(i);
        flushAsync();
        return CreateResult.OK;
    }

    /** Recrée le bloc central s'il a disparu (crash, chunk réinitialisé...). */
    public void ensureOneBlock(Island i) {
        Block b = world.getBlockAt(i.centerX(), oneBlockY, i.centerZ());
        if (b.getType().isAir()) plugin.phases().generate(i, b, plugin.content().phases().get(i.phase()));
    }

    public void teleportHome(Player p, Island i) {
        ensureOneBlock(i);
        p.teleportAsync(i.home(world));
    }

    /** Sortie du monde One Block : le hub de SkyHub. */
    public Location spawn() { return plugin.host().hub().spawn(); }

    // ------------------------------------------------------------ membres & invitations

    public Invite pendingInvite(UUID player) {
        Invite inv = invites.get(player);
        if (inv != null && inv.expiresAt() < System.currentTimeMillis()) { invites.remove(player); return null; }
        return inv;
    }

    public void sendInvite(Island i, Player inviter, Player target) {
        invites.put(target.getUniqueId(), new Invite(i.id(), inviter.getUniqueId(), inviter.getName(),
                System.currentTimeMillis() + settings().inviteExpireSeconds() * 1000L));
    }

    public void clearInvite(UUID player) { invites.remove(player); }

    public boolean isFull(Island i) { return i.size() >= settings().maxPlayers(); }

    /** Ajoute un membre en revérifiant la limite ; false si pleine ou joueur déjà dans une île. */
    public boolean addMember(Island i, Player p) {
        if (isFull(i) || byPlayer.containsKey(p.getUniqueId())) return false;
        i.members().put(p.getUniqueId(), new IslandMember(p.getUniqueId(), p.getName(), IslandRole.MEMBER, System.currentTimeMillis(), 0));
        byPlayer.put(p.getUniqueId(), i);
        i.markMembersDirty();
        markDirty(i);
        flushAsync();
        return true;
    }

    public void removeMember(Island i, UUID uuid) { removeMember(i, uuid, true); }

    /** teleportOut = false : le joueur n'est pas renvoyé au hub (il rejoint une autre île). */
    public void removeMember(Island i, UUID uuid, boolean teleportOut) {
        if (i.isOwner(uuid)) throw new IllegalArgumentException("Le propriétaire ne peut pas être retiré");
        if (i.members().remove(uuid) == null) return;
        byPlayer.remove(uuid);
        i.markMembersDirty();
        markDirty(i);
        flushAsync();
        Player online = Bukkit.getPlayer(uuid);
        if (teleportOut && online != null && i.contains(online.getLocation().getBlockX(), online.getLocation().getBlockZ(), settings().protectionRadius())
                && online.getWorld().equals(world)) {
            online.teleportAsync(spawn());
        }
    }

    // ------------------------------------------------------------ suppression

    public void requestDelete(UUID owner) { deleteConfirm.put(owner, new long[]{System.currentTimeMillis() + settings().deleteConfirmSeconds() * 1000L}); }

    public boolean confirmDelete(UUID owner) {
        long[] t = deleteConfirm.remove(owner);
        return t != null && t[0] >= System.currentTimeMillis();
    }

    /** Supprime l'île : retire les membres, efface la base immédiatement, nettoie le monde tick par tick puis libère l'emplacement. */
    public void delete(Island i) { delete(i, null); }

    /** keepInPlace : joueur qui n'est pas renvoyé au hub (il rejoint une autre île). */
    public void delete(Island i, UUID keepInPlace) {
        for (IslandMember m : new ArrayList<>(i.members().values())) {
            byPlayer.remove(m.uuid);
            invites.remove(m.uuid);
            Player online = Bukkit.getPlayer(m.uuid);
            if (online != null && !m.uuid.equals(keepInPlace) && online.getWorld().equals(world)) online.teleportAsync(spawn());
        }
        invites.values().removeIf(inv -> inv.islandId() == i.id());
        byId.remove(i.id());
        int[] g = SlotGrid.of(i.slot());
        byGrid.remove(key(g[0], g[1]));
        dirty.remove(i);
        db.async(c -> { IslandRepository.delete(c, i.id()); return null; })
                .exceptionally(t -> { plugin.getLogger().log(Level.SEVERE, "Suppression SQL de l'île " + i.id() + " échouée", t); return null; });
        clearRegion(i, () -> usedSlots.remove(i.slot()));
    }

    private void clearRegion(Island i, Runnable done) {
        IslandSettings s = settings();
        int r = s.protectionRadius();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (i.centerX() - r) >> 4; cx <= (i.centerX() + r) >> 4; cx++)
            for (int cz = (i.centerZ() - r) >> 4; cz <= (i.centerZ() + r) >> 4; cz++) chunks.add(new int[]{cx, cz});
        Iterator<int[]> it = chunks.iterator();
        new BukkitRunnable() {
            @Override public void run() {
                for (int n = 0; n < s.chunksPerTick() && it.hasNext(); n++) clearChunk(it.next(), i, r, s.clearMinY(), s.clearMaxY());
                if (!it.hasNext()) { cancel(); done.run(); }
            }
        }.runTaskTimer(plugin.host(), 1L, 1L);
    }

    private void clearChunk(int[] c, Island i, int radius, int minY, int maxY) {
        Chunk ch = world.getChunkAt(c[0], c[1]);
        for (Entity e : ch.getEntities()) if (!(e instanceof Player)) e.remove();
        int minWy = Math.max(minY, world.getMinHeight()), maxWy = Math.min(maxY, world.getMaxHeight() - 1);
        for (int x = 0; x < 16; x++) {
            int wx = (c[0] << 4) + x;
            for (int z = 0; z < 16; z++) {
                int wz = (c[1] << 4) + z;
                if (!i.contains(wx, wz, radius)) continue;
                for (int y = minWy; y <= maxWy; y++) {
                    Block b = ch.getBlock(x, y, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
            }
        }
    }

    // ------------------------------------------------------------ persistance

    public void markDirty(Island i) { dirty.add(i); }

    /** Écriture asynchrone des îles modifiées (instantanés pris sur le thread principal). */
    public void flushAsync() {
        if (dirty.isEmpty()) return;
        List<IslandData> batch = snapshotDirty();
        db.async(c -> { for (IslandData d : batch) IslandRepository.save(c, d); return null; })
                .exceptionally(t -> {
                    plugin.getLogger().log(Level.SEVERE, "Sauvegarde asynchrone échouée, nouvel essai au prochain cycle", t);
                    Bukkit.getScheduler().runTask(plugin.host(), () -> batch.forEach(d -> { Island i = byId.get(d.id()); if (i != null) { dirty.add(i); if (d.writeMembers()) i.markMembersDirty(); } }));
                    return null;
                });
    }

    public void flushSync() {
        if (dirty.isEmpty()) return;
        List<IslandData> batch = snapshotDirty();
        try {
            db.sync(c -> { for (IslandData d : batch) IslandRepository.save(c, d); return null; });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Sauvegarde finale échouée", e);
        }
    }

    private List<IslandData> snapshotDirty() {
        List<IslandData> batch = new ArrayList<>(dirty.size());
        for (Island i : dirty) { batch.add(IslandData.of(i)); i.clearMembersDirty(); }
        dirty.clear();
        return batch;
    }
}
