package fr.skyhub.oneblock.commands;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.gui.PhasesMenu;
import fr.skyhub.oneblock.gui.ProgressMenu;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.island.IslandFlag;
import fr.skyhub.oneblock.island.IslandManager;
import fr.skyhub.oneblock.island.IslandMember;
import fr.skyhub.oneblock.island.IslandRole;
import fr.skyhub.oneblock.phase.BlockChoice;
import fr.skyhub.oneblock.phase.BlockPool;
import fr.skyhub.oneblock.phase.Phase;
import fr.skyhub.oneblock.phase.Progress;
import fr.skyhub.oneblock.utils.WeightedTable;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /ob et ses sous-commandes. */
public final class OneBlockCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("create", "home", "visit", "delete", "invite", "accept", "deny", "kick", "leave", "info", "top", "level", "progress", "phases", "chances", "admin");
    private static final List<String> ADMIN_SUBS = List.of("setphase", "addblocks", "info", "tp", "delete", "removemember", "reload");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private final OneBlockPlugin plugin;

    public OneBlockCommand(OneBlockPlugin plugin) { this.plugin = plugin; }

    private IslandManager im() { return plugin.islands(); }
    private void msg(CommandSender s, String key, String... kv) { plugin.messages().send(s, key, kv); }

    /** Les commandes One Block ne marchent que dans le monde One Block (admins : partout). */
    private boolean allowed(CommandSender s) {
        if (s.hasPermission("oneblock.admin")) return true;
        return s instanceof Player p && p.getWorld().equals(plugin.islands().world());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>Les commandes One Block ne fonctionnent que dans le monde One Block."));
            return true;
        }
        if (args.length == 0) { plugin.messages().list("help").forEach(sender::sendMessage); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) { admin(sender, args); return true; }
        if (!(sender instanceof Player p)) { msg(sender, "error.player-only"); return true; }
        switch (sub) {
            case "create" -> create(p);
            case "home" -> home(p);
            case "delete" -> delete(p, args);
            case "invite" -> invite(p, args);
            case "visit", "visite" -> visit(p, args);
            case "accept" -> accept(p);
            case "deny" -> deny(p);
            case "kick" -> kick(p, args);
            case "leave" -> leave(p);
            case "info" -> info(p);
            case "top" -> top(p);
            case "level" -> level(p);
            case "progress" -> withIsland(p, i -> new ProgressMenu(plugin, p, i).open());
            case "phases" -> withIsland(p, i -> new PhasesMenu(plugin, p, i).open());
            case "chances" -> chances(p, args);
            default -> plugin.messages().list("help").forEach(sender::sendMessage);
        }
        return true;
    }

    private void withIsland(Player p, java.util.function.Consumer<Island> action) {
        Island i = im().islandOf(p.getUniqueId());
        if (i == null) { msg(p, "island.none"); return; }
        action.accept(i);
    }

    // ---------------------------------------------------------------- joueur

    private void create(Player p) {
        if (im().create(p) == IslandManager.CreateResult.ALREADY_HAS_ISLAND) { msg(p, "island.already"); return; }
        Island i = im().islandOf(p.getUniqueId());
        msg(p, "island.created");
        im().teleportHome(p, i);
    }

    private void home(Player p) {
        withIsland(p, i -> { im().teleportHome(p, i); msg(p, "island.home"); });
    }

    private void delete(Player p, String[] args) {
        Island i = im().islandOf(p.getUniqueId());
        if (i == null) { msg(p, "island.none"); return; }
        if (!i.isOwner(p.getUniqueId())) { msg(p, "island.owner-only"); return; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            if (!im().confirmDelete(p.getUniqueId())) { msg(p, "island.delete-expired"); return; }
            im().delete(i);
            msg(p, "island.deleted");
        } else {
            im().requestDelete(p.getUniqueId());
            msg(p, "island.delete-confirm", "seconds", String.valueOf(im().settings().deleteConfirmSeconds()));
        }
    }

    private void invite(Player p, String[] args) {
        Island i = im().islandOf(p.getUniqueId());
        if (i == null) { msg(p, "island.none"); return; }
        if (!im().settings().allowed(i.isOwner(p.getUniqueId()) ? IslandRole.OWNER : IslandRole.MEMBER, IslandFlag.INVITE)) { msg(p, "island.no-invite-right"); return; }
        if (args.length < 2) { msg(p, "usage.invite"); return; }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) { msg(p, "error.player-offline", "player", args[1]); return; }
        if (t.equals(p)) { msg(p, "invite.self"); return; }
        if (im().isFull(i)) { msg(p, "island.full", "max", String.valueOf(im().settings().maxPlayers())); return; }
        im().sendInvite(i, p, t);
        msg(p, "invite.sent", "player", t.getName());
        msg(t, "invite.received", "player", p.getName(), "seconds", String.valueOf(im().settings().inviteExpireSeconds()));
        if (im().islandOf(t.getUniqueId()) != null) msg(t, "invite.warn-lose-island");
    }

    private void accept(Player p) {
        IslandManager.Invite inv = im().pendingInvite(p.getUniqueId());
        if (inv == null) { msg(p, "invite.none"); return; }
        Island i = im().byId(inv.islandId());
        im().clearInvite(p.getUniqueId());
        if (i == null) { msg(p, "invite.island-gone"); return; }
        Island old = im().islandOf(p.getUniqueId());
        if (old != null && old.id() == i.id()) { msg(p, "island.already"); return; }
        if (im().isFull(i)) { msg(p, "island.full", "max", String.valueOf(im().settings().maxPlayers())); return; }
        // L'ancienne île du joueur est supprimée (ou simplement quittée s'il n'en était que membre)
        if (old != null) {
            for (IslandMember m : old.members().values()) {
                Player o = Bukkit.getPlayer(m.uuid);
                if (o != null && !o.equals(p)) msg(o, old.isOwner(p.getUniqueId()) ? "island.old-deleted-notify" : "island.member-left", "player", p.getName());
            }
            if (old.isOwner(p.getUniqueId())) {
                im().delete(old, p.getUniqueId());
                msg(p, "island.old-deleted");
            } else {
                im().removeMember(old, p.getUniqueId(), false);
                msg(p, "island.old-left");
            }
        }
        if (!im().addMember(i, p)) { msg(p, "island.full", "max", String.valueOf(im().settings().maxPlayers())); return; }
        msg(p, "invite.accepted", "owner", ownerName(i));
        for (IslandMember m : i.members().values()) {
            Player o = Bukkit.getPlayer(m.uuid);
            if (o != null && !o.equals(p)) msg(o, "invite.joined", "player", p.getName());
        }
        im().teleportHome(p, i);
    }

    /** /ob visit <joueur|#id> : visite l'île d'un autre joueur (rien ne peut y être cassé, posé ni pris). */
    private void visit(Player p, String[] args) {
        if (args.length < 2) { msg(p, "usage.visit"); return; }
        Island i = im().find(args[1]);
        if (i == null) { msg(p, "visit.not-found", "player", args[1]); return; }
        im().teleportHome(p, i);
        if (i.isMember(p.getUniqueId())) msg(p, "island.home");
        else msg(p, "visit.welcome", "owner", ownerName(i));
    }

    private void deny(Player p) {
        IslandManager.Invite inv = im().pendingInvite(p.getUniqueId());
        if (inv == null) { msg(p, "invite.none"); return; }
        im().clearInvite(p.getUniqueId());
        msg(p, "invite.denied");
        Player inviter = Bukkit.getPlayer(inv.inviter());
        if (inviter != null) msg(inviter, "invite.denied-by", "player", p.getName());
    }

    private void kick(Player p, String[] args) {
        Island i = im().islandOf(p.getUniqueId());
        if (i == null) { msg(p, "island.none"); return; }
        if (!i.isOwner(p.getUniqueId())) { msg(p, "island.owner-only"); return; }
        if (args.length < 2) { msg(p, "usage.kick"); return; }
        IslandMember m = i.findMember(args[1]);
        if (m == null) { msg(p, "island.not-member", "player", args[1]); return; }
        if (m.role == IslandRole.OWNER) { msg(p, "island.cannot-kick-owner"); return; }
        im().removeMember(i, m.uuid);
        msg(p, "island.kicked", "player", m.name);
        Player t = Bukkit.getPlayer(m.uuid);
        if (t != null) msg(t, "island.you-were-kicked");
    }

    private void leave(Player p) {
        Island i = im().islandOf(p.getUniqueId());
        if (i == null) { msg(p, "island.none"); return; }
        if (i.isOwner(p.getUniqueId())) { msg(p, "island.owner-cannot-leave"); return; }
        im().removeMember(i, p.getUniqueId());
        msg(p, "island.left");
        for (IslandMember m : i.members().values()) {
            Player o = Bukkit.getPlayer(m.uuid);
            if (o != null) msg(o, "island.member-left", "player", p.getName());
        }
    }

    private void info(Player p) { withIsland(p, i -> sendInfo(p, i)); }

    private void sendInfo(CommandSender to, Island i) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.FRANCE);
        Progress pr = Progress.of(plugin.content().phases(), i);
        msg(to, "info.header", "id", String.valueOf(i.id()), "name", i.name());
        msg(to, "info.line-owner", "owner", ownerName(i));
        msg(to, "info.line-phase", "phase", pr.current().name(), "id", String.valueOf(pr.current().id()));
        msg(to, "info.line-blocks", "blocks", nf.format(i.blocks()),
                "next", pr.next() == null ? "-" : nf.format(pr.next().threshold()), "percent", String.format(Locale.FRANCE, "%.1f", pr.percent()));
        msg(to, "info.line-level", "level", String.valueOf(im().settings().levelFor(i.blocks())));
        msg(to, "info.line-mobs", "mobs", nf.format(i.mobsKilled()));
        msg(to, "info.line-created", "date", DATE.format(Instant.ofEpochMilli(i.created())));
        msg(to, "info.line-members", "count", String.valueOf(i.size()), "max", String.valueOf(im().settings().maxPlayers()));
        for (IslandMember m : i.members().values()) {
            boolean online = Bukkit.getPlayer(m.uuid) != null;
            msg(to, "info.member", "name", m.name, "role", m.role == IslandRole.OWNER ? "Propriétaire" : "Membre",
                    "status", online ? "<green>●" : "<gray>●", "contributed", nf.format(m.contributed));
        }
    }

    private void top(Player p) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.FRANCE);
        msg(p, "top.header");
        int rank = 1;
        for (Island i : im().top(10)) {
            msg(p, "top.line", "rank", String.valueOf(rank++), "owner", ownerName(i), "blocks", nf.format(i.blocks()),
                    "phase", plugin.content().phases().get(i.phase()).name());
        }
        if (rank == 1) msg(p, "top.empty");
    }

    private void level(Player p) {
        withIsland(p, i -> {
            var s = im().settings();
            int lvl = s.levelFor(i.blocks());
            long toNext = lvl >= s.maxLevel() ? 0 : (long) lvl * s.blocksPerLevel() - i.blocks();
            msg(p, "level.info", "level", String.valueOf(lvl), "remaining", String.valueOf(Math.max(0, toNext)));
        });
    }

    private void chances(Player p, String[] args) {
        Island i = im().islandOf(p.getUniqueId());
        int id = i != null ? i.phase() : 1;
        if (args.length >= 2) {
            try { id = Integer.parseInt(args[1]); } catch (NumberFormatException e) { msg(p, "usage.chances"); return; }
        }
        if (id < 1 || id > plugin.content().phases().size()) { msg(p, "error.unknown-phase", "max", String.valueOf(plugin.content().phases().size())); return; }
        // Une phase non atteinte reste consultable (information publique).
        Phase ph = plugin.content().phases().get(id);
        BlockPool pool = plugin.content().pools().get(id);
        msg(p, "chances.header", "phase", ph.name());
        pool.table().entries().stream().sorted(Comparator.comparingDouble((WeightedTable.Entry<BlockChoice> e) -> e.weight()).reversed())
                .forEach(e -> msg(p, "chances.line", "block", label(e.value()), "percent", String.format(Locale.FRANCE, "%.2f", pool.table().percent(e))));
    }

    private static String label(BlockChoice c) {
        return switch (c.kind()) { case CHEST_BONUS -> "Coffre bonus"; case SURPRISE -> "Bloc surprise"; default -> c.material().name().toLowerCase(Locale.ROOT).replace('_', ' '); };
    }

    private String ownerName(Island i) {
        IslandMember o = i.member(i.owner());
        return o == null ? "?" : o.name;
    }

    // ---------------------------------------------------------------- admin

    private void admin(CommandSender s, String[] args) {
        if (!s.hasPermission("oneblock.mod")) { msg(s, "error.no-permission"); return; }
        if (args.length < 2) { msg(s, "usage.admin"); return; }
        String sub = args[1].toLowerCase(Locale.ROOT);
        boolean modOnly = sub.equals("info") || sub.equals("tp");
        if (!modOnly && !s.hasPermission("oneblock.admin")) { msg(s, "error.no-permission"); return; }
        switch (sub) {
            case "reload" -> {
                List<String> errors = plugin.reloadAll();
                if (errors.isEmpty()) msg(s, "admin.reloaded");
                else { msg(s, "admin.reload-failed", "count", String.valueOf(errors.size())); errors.forEach(e -> plugin.getLogger().severe("[reload] " + e)); }
            }
            case "info" -> island(s, args, 2, i -> sendInfo(s, i));
            case "tp" -> {
                if (!(s instanceof Player p)) { msg(s, "error.player-only"); return; }
                island(s, args, 2, i -> im().teleportHome(p, i));
            }
            case "setphase" -> {
                if (args.length < 4) { msg(s, "usage.setphase"); return; }
                Integer id = parseInt(args[3]);
                if (id == null || id < 1 || id > plugin.content().phases().size()) { msg(s, "error.unknown-phase", "max", String.valueOf(plugin.content().phases().size())); return; }
                island(s, args, 2, i -> { plugin.phases().setPhaseAdmin(i, id); msg(s, "admin.setphase", "island", String.valueOf(i.id()), "phase", plugin.content().phases().get(id).name()); });
            }
            case "addblocks" -> {
                if (args.length < 4) { msg(s, "usage.addblocks"); return; }
                Long n = parseLong(args[3]);
                if (n == null || n < 1 || n > 10_000_000L) { msg(s, "error.bad-number"); return; }
                island(s, args, 2, i -> { plugin.phases().addBlocksAdmin(i, n); msg(s, "admin.addblocks", "island", String.valueOf(i.id()), "amount", String.valueOf(n)); });
            }
            case "delete" -> island(s, args, 2, i -> { im().delete(i); msg(s, "admin.deleted", "island", String.valueOf(i.id())); });
            case "removemember" -> {
                if (args.length < 4) { msg(s, "usage.removemember"); return; }
                island(s, args, 2, i -> {
                    IslandMember m = i.findMember(args[3]);
                    if (m == null) { msg(s, "island.not-member", "player", args[3]); return; }
                    if (m.role == IslandRole.OWNER) { msg(s, "island.cannot-kick-owner"); return; }
                    im().removeMember(i, m.uuid);
                    msg(s, "admin.removed", "player", m.name);
                });
            }
            default -> msg(s, "usage.admin");
        }
    }

    private void island(CommandSender s, String[] args, int idx, java.util.function.Consumer<Island> action) {
        if (args.length <= idx) { msg(s, "usage.admin"); return; }
        Island i = im().find(args[idx]);
        if (i == null) { msg(s, "admin.island-not-found", "token", args[idx]); return; }
        action.accept(i);
    }

    private static Integer parseInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; } }
    private static Long parseLong(String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } }

    // ---------------------------------------------------------------- complétion

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!allowed(s)) return out;
        if (args.length == 1) {
            SUBS.stream().filter(x -> !x.equals("admin") || s.hasPermission("oneblock.mod")).forEach(out::add);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "visit", "visite" -> im().all().forEach(i -> { IslandMember o = i.member(i.owner()); if (o != null) out.add(o.name); });
                case "kick" -> { if (s instanceof Player p && im().islandOf(p.getUniqueId()) != null) im().islandOf(p.getUniqueId()).members().values().forEach(m -> out.add(m.name)); }
                case "delete" -> out.add("confirm");
                case "admin" -> { if (s.hasPermission("oneblock.mod")) out.addAll(ADMIN_SUBS); }
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && !args[1].equalsIgnoreCase("reload")) {
            im().all().forEach(i -> { IslandMember o = i.member(i.owner()); if (o != null) out.add(o.name); });
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setphase")) {
            for (int n = 1; n <= plugin.content().phases().size(); n++) out.add(String.valueOf(n));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
