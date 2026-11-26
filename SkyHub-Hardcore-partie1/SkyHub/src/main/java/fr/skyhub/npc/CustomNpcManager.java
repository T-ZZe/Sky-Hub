package fr.skyhub.npc;

import fr.skyhub.SkyHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** PNJ personnalisés : /npc <mob> <interface|interaction> <nom> <couleur>, /npc mod ffa. */
public final class CustomNpcManager implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private final SkyHubPlugin plugin;
    private final File file;
    private final NamespacedKey npcKey;
    private final Map<String, CustomNpc> npcs = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CustomNpcManager(SkyHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-npcs.yml");
        this.npcKey = new NamespacedKey(plugin, "custom_npc");
    }

    // ================================================================ stockage
    private static String enc(ItemStack it) {
        return it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack dec(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception e) {
            return null;
        }
    }

    public void start() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = cfg.getConfigurationSection("npcs");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(k);
                if (c == null) continue;
                CustomNpc n = new CustomNpc(k);
                n.display = c.getString("display", k);
                n.color = c.getString("color", "white");
                EntityType t = org.bukkit.Registry.ENTITY_TYPE.get(
                        NamespacedKey.minecraft(c.getString("entity", "villager").toLowerCase(Locale.ROOT)));
                n.type = t != null ? t : EntityType.VILLAGER;
                try {
                    n.kind = CustomNpc.Kind.valueOf(c.getString("kind", "INTERACTION"));
                } catch (IllegalArgumentException e) {
                    n.kind = CustomNpc.Kind.INTERACTION;
                }
                n.world = c.getString("world", "world");
                n.x = c.getDouble("x");
                n.y = c.getDouble("y");
                n.z = c.getDouble("z");
                n.yaw = (float) c.getDouble("yaw");
                n.pitch = (float) c.getDouble("pitch");
                List<String> costs = c.getStringList("trades.cost");
                List<String> results = c.getStringList("trades.result");
                for (int i = 0; i < Math.min(costs.size(), results.size()); i++) {
                    ItemStack cost = dec(costs.get(i)), result = dec(results.get(i));
                    if (cost != null && result != null) n.trades.add(new ItemStack[]{cost, result});
                }
                try {
                    n.action = CustomNpc.Action.valueOf(c.getString("action", "NONE"));
                } catch (IllegalArgumentException e) {
                    n.action = CustomNpc.Action.NONE;
                }
                n.item = dec(c.getString("item"));
                n.once = c.getBoolean("once");
                for (String u : c.getStringList("given")) n.given.add(UUID.fromString(u));
                n.tpWorld = c.getString("tp.world");
                n.tx = c.getDouble("tp.x");
                n.ty = c.getDouble("tp.y");
                n.tz = c.getDouble("tp.z");
                n.tyaw = (float) c.getDouble("tp.yaw");
                n.tpitch = (float) c.getDouble("tp.pitch");
                npcs.put(k.toLowerCase(Locale.ROOT), n);
            }
        }
        for (World w : Bukkit.getWorlds()) spawnWorld(w);
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (CustomNpc n : npcs.values()) {
            String b = "npcs." + n.key + ".";
            cfg.set(b + "display", n.display);
            cfg.set(b + "color", n.color);
            cfg.set(b + "entity", n.type.getKey().getKey());
            cfg.set(b + "kind", n.kind.name());
            cfg.set(b + "world", n.world);
            cfg.set(b + "x", n.x);
            cfg.set(b + "y", n.y);
            cfg.set(b + "z", n.z);
            cfg.set(b + "yaw", (double) n.yaw);
            cfg.set(b + "pitch", (double) n.pitch);
            List<String> costs = new ArrayList<>(), results = new ArrayList<>();
            for (ItemStack[] t : n.trades) {
                costs.add(enc(t[0]));
                results.add(enc(t[1]));
            }
            cfg.set(b + "trades.cost", costs);
            cfg.set(b + "trades.result", results);
            cfg.set(b + "action", n.action.name());
            cfg.set(b + "item", enc(n.item));
            cfg.set(b + "once", n.once);
            cfg.set(b + "given", n.given.stream().map(UUID::toString).toList());
            if (n.tpWorld != null) {
                cfg.set(b + "tp.world", n.tpWorld);
                cfg.set(b + "tp.x", n.tx);
                cfg.set(b + "tp.y", n.ty);
                cfg.set(b + "tp.z", n.tz);
                cfg.set(b + "tp.yaw", (double) n.tyaw);
                cfg.set(b + "tp.pitch", (double) n.tpitch);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire custom-npcs.yml : " + e.getMessage());
        }
    }

    // ================================================================ accès
    public static boolean validName(String n) {
        return NAME.matcher(n).matches();
    }

    public CustomNpc get(String name) {
        return npcs.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomNpc> all() {
        return npcs.values();
    }

    public static TextColor parseColor(String s) {
        if (s == null) return null;
        if (s.startsWith("#")) return TextColor.fromHexString(s);
        return NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
    }

    private Component nameOf(CustomNpc n) {
        TextColor c = parseColor(n.color);
        return Component.text(n.display.replace('_', ' '), c != null ? c : NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD);
    }

    // ================================================================ création / spawn
    public CustomNpc create(String name, EntityType type, CustomNpc.Kind kind, String color, Location loc) {
        CustomNpc n = new CustomNpc(name);
        n.display = name;
        n.type = type;
        n.kind = kind;
        n.color = color;
        place(n, loc);
        npcs.put(name.toLowerCase(Locale.ROOT), n);
        save();
        spawn(n);
        return n;
    }

    private void place(CustomNpc n, Location loc) {
        n.world = loc.getWorld().getName();
        n.x = loc.getX();
        n.y = loc.getY();
        n.z = loc.getZ();
        n.yaw = loc.getYaw();
        n.pitch = 0f;
    }

    public void move(CustomNpc n, Location loc) {
        removeEntities(n);
        place(n, loc);
        save();
        spawn(n);
    }

    public void remove(CustomNpc n) {
        removeEntities(n);
        npcs.remove(n.key.toLowerCase(Locale.ROOT));
        save();
    }

    private Location locationOf(CustomNpc n) {
        World w = Bukkit.getWorld(n.world);
        return w == null ? null : new Location(w, n.x, n.y, n.z, n.yaw, n.pitch);
    }

    private void removeEntities(CustomNpc n) {
        Location loc = locationOf(n);
        if (loc == null) return;
        Chunk ch = loc.getChunk();
        for (Entity e : ch.getEntities()) {
            String k = e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
            if (n.key.equals(k)) e.remove();
        }
    }

    public void spawn(CustomNpc n) {
        Location loc = locationOf(n);
        if (loc == null) return;
        removeEntities(n);
        Entity e;
        try {
            e = loc.getWorld().spawnEntity(loc, n.type);
        } catch (Exception ex) {
            plugin.getLogger().warning("Impossible de faire apparaître le PNJ " + n.key + " : " + ex.getMessage());
            return;
        }
        e.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, n.key);
        e.customName(nameOf(n));
        e.setCustomNameVisible(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent(true);
        if (e instanceof LivingEntity le) {
            le.setAI(false);
            le.setRemoveWhenFarAway(false);
            le.setCollidable(false);
        }
    }

    private void spawnWorld(World w) {
        for (CustomNpc n : npcs.values()) {
            if (n.world.equals(w.getName())) spawn(n);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> spawnWorld(e.getWorld()));
    }

    /** Rafraîchit le nom / la couleur d'un PNJ déjà posé. */
    public void refresh(CustomNpc n) {
        save();
        spawn(n);
    }

    // ================================================================ interaction
    private String keyOf(Entity e) {
        return e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        String k = keyOf(e.getRightClicked());
        if (k == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        handle(e.getPlayer(), k);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        String k = keyOf(e.getEntity());
        if (k == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) handle(p, k);
    }

    private void handle(Player p, String key) {
        CustomNpc n = npcs.get(key.toLowerCase(Locale.ROOT));
        if (n == null) return;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        cooldowns.put(p.getUniqueId(), now);

        switch (n.kind) {
            case FFA_MOD -> plugin.ffa().openMenu(p);
            case INTERFACE -> {
                if (n.trades.isEmpty()) {
                    p.sendMessage(MM.deserialize("<gray>Ce PNJ n'a aucun échange pour le moment."));
                    return;
                }
                Merchant m = Bukkit.createMerchant(nameOf(n));
                List<MerchantRecipe> recipes = new ArrayList<>();
                for (ItemStack[] t : n.trades) {
                    MerchantRecipe r = new MerchantRecipe(t[1].clone(), Integer.MAX_VALUE);
                    r.addIngredient(t[0].clone());
                    recipes.add(r);
                }
                m.setRecipes(recipes);
                p.openMerchant(m, true);
            }
            case INTERACTION -> interact(p, n);
        }
    }

    private void interact(Player p, CustomNpc n) {
        switch (n.action) {
            case ITEM -> {
                if (n.item == null) return;
                if (n.once && n.given.contains(p.getUniqueId())) {
                    p.sendMessage(MM.deserialize("<red>Tu as déjà reçu cet objet."));
                    return;
                }
                var left = p.getInventory().addItem(n.item.clone());
                left.values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                if (n.once) {
                    n.given.add(p.getUniqueId());
                    save();
                }
                p.sendMessage(MM.deserialize("<green>Tu as reçu un objet !"));
            }
            case TELEPORT -> {
                World w = n.tpWorld == null ? null : Bukkit.getWorld(n.tpWorld);
                if (w == null) {
                    p.sendMessage(MM.deserialize("<red>La destination n'est pas disponible."));
                    return;
                }
                p.teleportAsync(new Location(w, n.tx, n.ty, n.tz, n.tyaw, n.tpitch));
            }
            case NONE -> p.sendMessage(MM.deserialize("<gray>Ce PNJ n'a pas encore d'action."));
        }
    }
}
