package fr.skyhub.oneblock.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** État d'une île. Mutable, accédé uniquement depuis le thread principal. */
public final class Island {

    private final int id;
    private final UUID owner;
    private final int slot;
    private final int centerX, centerZ, oneBlockY;
    private final long created;
    private final Map<UUID, IslandMember> members = new LinkedHashMap<>();
    private String name;
    private long blocks;
    private int phase;
    private int rewardedPhase;
    private long mobsKilled;
    private boolean membersDirty;
    public long lastBreakMillis;
    public long lastMobEventMillis;

    public Island(int id, UUID owner, String name, int slot, int centerX, int centerZ, int oneBlockY,
                  long blocks, int phase, int rewardedPhase, long mobsKilled, long created) {
        this.id = id; this.owner = owner; this.name = name; this.slot = slot;
        this.centerX = centerX; this.centerZ = centerZ; this.oneBlockY = oneBlockY;
        this.blocks = blocks; this.phase = phase; this.rewardedPhase = rewardedPhase;
        this.mobsKilled = mobsKilled; this.created = created;
    }

    public int id() { return id; }
    public UUID owner() { return owner; }
    public int slot() { return slot; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public int oneBlockY() { return oneBlockY; }
    public long created() { return created; }
    public String name() { return name; }
    public long blocks() { return blocks; }
    public int phase() { return phase; }
    public int rewardedPhase() { return rewardedPhase; }
    public long mobsKilled() { return mobsKilled; }
    public boolean membersDirty() { return membersDirty; }
    public void clearMembersDirty() { membersDirty = false; }
    public void markMembersDirty() { membersDirty = true; }

    public void setName(String n) { this.name = n; }
    public void setBlocks(long b) { this.blocks = Math.max(0, b); }
    public void setPhase(int p) { this.phase = p; }
    public void setRewardedPhase(int p) { this.rewardedPhase = p; }
    public void addMobKill() { mobsKilled++; }

    public Map<UUID, IslandMember> members() { return members; }
    public List<IslandMember> memberList() { return new ArrayList<>(members.values()); }
    public int size() { return members.size(); }
    public boolean isMember(UUID u) { return members.containsKey(u); }
    public boolean isOwner(UUID u) { return owner.equals(u); }
    public IslandMember member(UUID u) { return members.get(u); }

    public IslandMember findMember(String name) {
        for (IslandMember m : members.values()) if (m.name.equalsIgnoreCase(name)) return m;
        return null;
    }

    public Location home(World w) { return new Location(w, centerX + 0.5, oneBlockY + 1.0, centerZ + 0.5, 0f, 0f); }
    public Location oneBlockLocation(World w) { return new Location(w, centerX, oneBlockY, centerZ); }

    public boolean isOneBlock(Block b) {
        return b.getX() == centerX && b.getY() == oneBlockY && b.getZ() == centerZ;
    }

    public boolean contains(int x, int z, int radius) {
        return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }
}
