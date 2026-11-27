package fr.skyhub.oneblock.island;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQL des îles. Chaque méthode s'exécute dans la transaction fournie par {@link fr.skyhub.oneblock.database.Database}. */
public final class IslandRepository {

    private IslandRepository() {}

    public record Row(int id, UUID owner, int slot, String name, long blocks, int phase, int rewarded, long mobs, long created) {}
    public record MemberRow(int islandId, UUID uuid, String name, IslandRole role, long joined, long contributed) {}

    public static List<Row> loadIslands(Connection c) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, owner, slot, name, blocks, phase, rewarded_phase, mobs_killed, created FROM islands");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new Row(rs.getInt(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getString(4),
                    rs.getLong(5), rs.getInt(6), rs.getInt(7), rs.getLong(8), rs.getLong(9)));
        }
        return out;
    }

    public static List<MemberRow> loadMembers(Connection c) throws SQLException {
        List<MemberRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT island_id, uuid, name, role, joined, contributed FROM island_members");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new MemberRow(rs.getInt(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                    IslandRole.valueOf(rs.getString(4)), rs.getLong(5), rs.getLong(6)));
        }
        return out;
    }

    /** UPDATE puis INSERT si la ligne n'existe pas ; membres réécrits si nécessaire. */
    public static void save(Connection c, IslandData d) throws SQLException {
        int n;
        try (PreparedStatement ps = c.prepareStatement("UPDATE islands SET name=?, blocks=?, phase=?, rewarded_phase=?, mobs_killed=? WHERE id=?")) {
            ps.setString(1, d.name()); ps.setLong(2, d.blocks()); ps.setInt(3, d.phase());
            ps.setInt(4, d.rewardedPhase()); ps.setLong(5, d.mobsKilled()); ps.setInt(6, d.id());
            n = ps.executeUpdate();
        }
        if (n == 0) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO islands (id, owner, slot, name, blocks, phase, rewarded_phase, mobs_killed, created) VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, d.id()); ps.setString(2, d.owner().toString()); ps.setInt(3, d.slot()); ps.setString(4, d.name());
                ps.setLong(5, d.blocks()); ps.setInt(6, d.phase()); ps.setInt(7, d.rewardedPhase()); ps.setLong(8, d.mobsKilled()); ps.setLong(9, d.created());
                ps.executeUpdate();
            }
        }
        if (d.writeMembers()) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM island_members WHERE island_id=?")) {
                del.setInt(1, d.id());
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO island_members (island_id, uuid, name, role, joined, contributed) VALUES (?,?,?,?,?,?)")) {
                for (IslandData.MemberData m : d.members()) {
                    ins.setInt(1, d.id()); ins.setString(2, m.uuid().toString()); ins.setString(3, m.name());
                    ins.setString(4, m.role().name()); ins.setLong(5, m.joined()); ins.setLong(6, m.contributed());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement("UPDATE island_members SET name=?, contributed=? WHERE island_id=? AND uuid=?")) {
                for (IslandData.MemberData m : d.members()) {
                    ps.setString(1, m.name()); ps.setLong(2, m.contributed()); ps.setInt(3, d.id()); ps.setString(4, m.uuid().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    public static void delete(Connection c, int islandId) throws SQLException {
        try (PreparedStatement a = c.prepareStatement("DELETE FROM island_members WHERE island_id=?");
             PreparedStatement b = c.prepareStatement("DELETE FROM islands WHERE id=?")) {
            a.setInt(1, islandId); a.executeUpdate();
            b.setInt(1, islandId); b.executeUpdate();
        }
    }
}
