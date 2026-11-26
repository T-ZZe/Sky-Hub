package fr.skyhub.npc;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NpcCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBS = List.of("mod", "trade", "item", "teleport", "move", "remove", "list");
    private static final List<String> COLORS = List.of("white", "gray", "dark_gray", "black", "red", "dark_red", "gold", "yellow",
            "green", "dark_green", "aqua", "dark_aqua", "blue", "dark_blue", "light_purple", "dark_purple", "#ff8800");
    private static final int MAX_TRADES = 12;

    private final CustomNpcManager npcs;

    public NpcCommand(CustomNpcManager npcs) {
        this.npcs = npcs;
    }

    private static void msg(CommandSender s, String m) {
        s.sendMessage(MM.deserialize(m));
    }

    private static void help(CommandSender s) {
        msg(s, "<gold><bold>/npc</bold> <gray>- PNJ (OP)");
        msg(s, "<yellow>/npc <mob> <interface|interaction> <nom> [couleur]</yellow> <gray>- crée un PNJ là où tu es");
        msg(s, "<gray>  interface : trade (<yellow>/npc trade <nom> add</yellow> : objet obtenu en main, prix en main secondaire)");
        msg(s, "<gray>  interaction : <yellow>/npc item <nom> [once]</yellow> (objet en main reçu) ou <yellow>/npc teleport <nom></yellow> (téléporte à ta position)");
        msg(s, "<yellow>/npc mod ffa</yellow> <gray>- Breeze qui propose de changer de zone PvP");
        msg(s, "<yellow>/npc move|remove <nom></yellow>  |  <yellow>/npc list");
    }

    private static boolean isSpawnableMob(EntityType t) {
        String k = t.getKey().getKey();
        return t.isSpawnable() && t.isAlive() && !k.equals("armor_stand") && !k.equals("player");
    }

    private static Location facing(Player p) {
        Location l = p.getLocation().clone();
        l.setYaw(l.getYaw() + 180f); // le PNJ regarde le joueur
        l.setPitch(0f);
        return l;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("skyhub.admin")) {
            msg(p, "<red>Permission refusée.");
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "mod" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("ffa")) {
                    msg(p, "<yellow>/npc mod ffa");
                    return true;
                }
                CustomNpc existing = npcs.get("mod-ffa");
                if (existing != null) {
                    npcs.move(existing, facing(p));
                    msg(p, "<green>Le Breeze « Changer de PvP » a été déplacé ici.");
                    return true;
                }
                CustomNpc n = npcs.create("mod-ffa", EntityType.BREEZE, CustomNpc.Kind.FFA_MOD, "gold", facing(p));
                n.display = "Changer de PvP";
                npcs.refresh(n);
                msg(p, "<green>Breeze posé : un clic ouvre le choix de la zone PvP (UHC, Mace, Crystal, Cart PvP, Pot).");
            }
            case "list" -> {
                if (npcs.all().isEmpty()) {
                    msg(p, "<gray>Aucun PNJ.");
                    return true;
                }
                for (CustomNpc n : npcs.all()) {
                    msg(p, "<gray>- <white>" + n.key + "</white> (" + n.type.getKey().getKey() + ", " + n.kind.name().toLowerCase(Locale.ROOT)
                            + ", " + n.world + ")");
                }
            }
            case "trade", "item", "teleport", "move", "remove" -> {
                if (a.length < 2) {
                    help(p);
                    return true;
                }
                CustomNpc n = npcs.get(a[1]);
                if (n == null) {
                    msg(p, "<red>PNJ introuvable : " + a[1].replace("<", ""));
                    return true;
                }
                handleOnNpc(p, sub, n, a);
            }
            default -> create(p, a);
        }
        return true;
    }

    /** /npc <mob> <interface|interaction> <nom> [couleur] */
    private void create(Player p, String[] a) {
        if (a.length < 3) {
            help(p);
            return;
        }
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(a[0].toLowerCase(Locale.ROOT)));
        if (type == null || !isSpawnableMob(type)) {
            msg(p, "<red>Mob inconnu : " + a[0].replace("<", "") + " <gray>(ex : villager, zombie, breeze, wither_skeleton...)");
            return;
        }
        CustomNpc.Kind kind;
        if (a[1].equalsIgnoreCase("interface")) kind = CustomNpc.Kind.INTERFACE;
        else if (a[1].equalsIgnoreCase("interaction")) kind = CustomNpc.Kind.INTERACTION;
        else {
            msg(p, "<red>Type : interface ou interaction.");
            return;
        }
        String name = a[2];
        if (!CustomNpcManager.validName(name)) {
            msg(p, "<red>Nom invalide (lettres, chiffres, _ et - ; le _ devient un espace à l'affichage).");
            return;
        }
        if (npcs.get(name) != null) {
            msg(p, "<red>Un PNJ nommé " + name + " existe déjà.");
            return;
        }
        String color = a.length >= 4 ? a[3] : "white";
        if (CustomNpcManager.parseColor(color) == null) {
            msg(p, "<red>Couleur inconnue (ex : red, dark_red, gold, aqua, #ff8800).");
            return;
        }
        npcs.create(name, type, kind, color, facing(p));
        if (kind == CustomNpc.Kind.INTERFACE) {
            msg(p, "<green>PNJ <white>" + name + "</white> créé. <gray>Ajoute des échanges : tiens l'objet vendu en main, le prix en main secondaire, puis <yellow>/npc trade " + name + " add");
        } else {
            msg(p, "<green>PNJ <white>" + name + "</white> créé. <gray>Choisis son action : <yellow>/npc item " + name + "</yellow> (objet en main) ou <yellow>/npc teleport " + name + "</yellow>.");
        }
    }

    private void handleOnNpc(Player p, String sub, CustomNpc n, String[] a) {
        switch (sub) {
            case "remove" -> {
                npcs.remove(n);
                msg(p, "<green>PNJ <white>" + n.key + "</white> supprimé.");
            }
            case "move" -> {
                npcs.move(n, facing(p));
                msg(p, "<green>PNJ <white>" + n.key + "</white> déplacé ici.");
            }
            case "teleport" -> {
                if (n.kind != CustomNpc.Kind.INTERACTION) {
                    msg(p, "<red>Seuls les PNJ « interaction » peuvent téléporter.");
                    return;
                }
                Location l = p.getLocation();
                n.tpWorld = l.getWorld().getName();
                n.tx = l.getX();
                n.ty = l.getY();
                n.tz = l.getZ();
                n.tyaw = l.getYaw();
                n.tpitch = l.getPitch();
                n.action = CustomNpc.Action.TELEPORT;
                npcs.save();
                msg(p, "<green>Un clic sur <white>" + n.key + "</white> téléporte maintenant ici.");
            }
            case "item" -> {
                if (n.kind != CustomNpc.Kind.INTERACTION) {
                    msg(p, "<red>Seuls les PNJ « interaction » donnent un objet.");
                    return;
                }
                ItemStack held = p.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    msg(p, "<red>Tiens l'objet à donner dans ta main principale.");
                    return;
                }
                n.item = held.clone();
                n.action = CustomNpc.Action.ITEM;
                n.once = a.length >= 3 && a[2].equalsIgnoreCase("once");
                n.given.clear();
                npcs.save();
                msg(p, "<green>Un clic sur <white>" + n.key + "</white> donne cet objet"
                        + (n.once ? " (une seule fois par joueur)." : " (à chaque clic)."));
            }
            case "trade" -> {
                if (n.kind != CustomNpc.Kind.INTERFACE) {
                    msg(p, "<red>Seuls les PNJ « interface » ont des échanges.");
                    return;
                }
                String op = a.length >= 3 ? a[2].toLowerCase(Locale.ROOT) : "";
                if (op.equals("clear")) {
                    n.trades.clear();
                    npcs.save();
                    msg(p, "<green>Échanges supprimés.");
                } else if (op.equals("add")) {
                    ItemStack result = p.getInventory().getItemInMainHand();
                    ItemStack cost = p.getInventory().getItemInOffHand();
                    if (result.getType().isAir() || cost.getType().isAir()) {
                        msg(p, "<red>Objet vendu en main principale ET prix en main secondaire.");
                        return;
                    }
                    if (n.trades.size() >= MAX_TRADES) {
                        msg(p, "<red>Maximum " + MAX_TRADES + " échanges.");
                        return;
                    }
                    n.trades.add(new ItemStack[]{cost.clone(), result.clone()});
                    npcs.save();
                    msg(p, "<green>Échange ajouté : " + cost.getAmount() + "x " + cost.getType().name().toLowerCase(Locale.ROOT)
                            + " → " + result.getAmount() + "x " + result.getType().name().toLowerCase(Locale.ROOT)
                            + " <gray>(" + n.trades.size() + " au total)");
                } else {
                    msg(p, "<yellow>/npc trade " + n.key + " <add|clear>");
                }
            }
            default -> {
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("skyhub.admin")) return out;
        String cur = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            out.addAll(SUBS);
            for (EntityType t : Registry.ENTITY_TYPE) if (isSpawnableMob(t)) out.add(t.getKey().getKey());
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "mod" -> out.add("ffa");
                case "trade", "item", "teleport", "move", "remove" -> {
                    for (CustomNpc n : npcs.all()) out.add(n.key);
                }
                default -> out.addAll(List.of("interface", "interaction"));
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("trade")) {
            out.addAll(List.of("add", "clear"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("item")) {
            out.add("once");
        } else if (a.length == 4 && !SUBS.contains(a[0].toLowerCase(Locale.ROOT))) {
            out.addAll(COLORS);
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
