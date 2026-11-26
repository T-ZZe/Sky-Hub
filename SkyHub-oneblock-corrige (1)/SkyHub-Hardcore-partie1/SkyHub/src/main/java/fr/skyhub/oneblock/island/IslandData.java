package fr.skyhub.oneblock.island;

import java.util.List;
import java.util.UUID;

/** Instantané immuable d'une île, transmis au thread SQL. */
public record IslandData(int id, UUID owner, int slot, String name, long blocks, int phase, int rewardedPhase,
                         long mobsKilled, long created, boolean writeMembers, List<MemberData> members) {

    public record MemberData(UUID uuid, String name, IslandRole role, long joined, long contributed) {}

    public static IslandData of(Island i) {
        List<MemberData> ms = i.members().values().stream()
                .map(m -> new MemberData(m.uuid, m.name, m.role, m.joined, m.contributed)).toList();
        return new IslandData(i.id(), i.owner(), i.slot(), i.name(), i.blocks(), i.phase(), i.rewardedPhase(),
                i.mobsKilled(), i.created(), i.membersDirty(), ms);
    }
}
