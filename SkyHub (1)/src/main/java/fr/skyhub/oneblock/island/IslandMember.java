package fr.skyhub.oneblock.island;

import java.util.UUID;

public final class IslandMember {
    public final UUID uuid;
    public String name;
    public final IslandRole role;
    public final long joined;
    public long contributed;

    public IslandMember(UUID uuid, String name, IslandRole role, long joined, long contributed) {
        this.uuid = uuid; this.name = name; this.role = role; this.joined = joined; this.contributed = contributed;
    }
}
