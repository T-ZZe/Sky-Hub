package fr.skyhub.kit;

import fr.skyhub.SkyHubPlugin;
import fr.skyhub.ffa.FfaMode;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Données des kits FFA : objets de chaque zone, 3 kits par joueur et par zone,
 * stuff sauvegardé par zone, zone actuelle du joueur. Fichier : kit-data.yml.
 */
public final class KitManager {
    public static final int TABS = 3;
    public static final int TAB_SLOTS = 21;
    public static final int MAX_KITS = 3;

    private final SkyHubPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;
    private final Map<FfaMode, ItemStack[][]> poolCache = new EnumMap<>(FfaMode.class);
    private final Map<UUID, Integer> selected = new HashMap<>();
    /** Zone quittée dont le stuff est encore dans l'inventaire (changement de zone en cours). */
    private final Map<UUID, FfaMode> pendingOld = new HashMap<>();

    public KitManager(SkyHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kit-data.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder kit-data.yml : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ sérialisation

    private static String enc(ItemStack it) {
        if (it == null || it.getType().isAir()) return "";
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack dec(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<String> encAll(ItemStack[] items) {
        List<String> l = new ArrayList<>();
        for (ItemStack it : items) l.add(enc(it));
        return l;
    }

    private static ItemStack[] decAll(List<String> l, int size) {
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < size && i < l.size(); i++) out[i] = dec(l.get(i));
        return out;
    }

    static ItemStack[][] copy(ItemStack[][] src) {
        ItemStack[][] out = new ItemStack[src.length][];
        for (int t = 0; t < src.length; t++) {
            out[t] = new ItemStack[src[t].length];
            for (int i = 0; i < src[t].length; i++) out[t][i] = src[t][i] == null ? null : src[t][i].clone();
        }
        return out;
    }

    // ------------------------------------------------------------------ zone du joueur

    public boolean isFfa(World w) {
        return plugin.ffa().isFfa(w);
    }

    public FfaMode getMode(UUID id) {
        return FfaMode.parse(cfg.getString("players." + id + ".mode"));
    }

    public void setMode(UUID id, FfaMode m) {
        cfg.set("players." + id + ".mode", m.key);
        save();
    }

    // ------------------------------------------------------------------ objets de chaque zone

    public ItemStack[][] getPools(FfaMode z) {
        ItemStack[][] cached = poolCache.get(z);
        if (cached != null) return cached;
        ItemStack[][] p;
        if (cfg.contains("pools." + z.key)) {
            p = new ItemStack[TABS][];
            for (int t = 0; t < TABS; t++) {
                p[t] = decAll(cfg.getStringList("pools." + z.key + ".tab" + t), TAB_SLOTS);
            }
        } else {
            p = KitDefaults.pool(z);
        }
        poolCache.put(z, p);
        return p;
    }

    public void savePools(FfaMode z, ItemStack[][] pools) {
        for (int t = 0; t < TABS; t++) cfg.set("pools." + z.key + ".tab" + t, encAll(pools[t]));
        save();
        poolCache.put(z, copy(pools));
    }

    // ------------------------------------------------------------------ validation

    /** Renvoie le nom du premier objet interdit dans cette zone, ou null si tout est autorisé. */
    public String check(FfaMode z, ItemStack[] contents) {
        ItemStack[][] pools = getPools(z);
        for (ItemStack it : contents) {
            if (it == null || it.getType().isAir()) continue;
            if (!ok(z, it, pools, false)) return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        int lim = KitRules.totemLimit(z);
        if (lim >= 0) {
            int total = 0;
            for (ItemStack it : contents) total += totems(it);
            if (total > lim) return "plus de " + lim + " totem" + (lim > 1 ? "s" : "");
        }
        return null;
    }

    /** Nombre de totems dans un objet (shulker comprise). */
    private static int totems(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        if (it.getType() == org.bukkit.Material.TOTEM_OF_UNDYING) return it.getAmount();
        if (Tag.SHULKER_BOXES.isTagged(it.getType())
                && it.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox sb) {
            int n = 0;
            for (ItemStack c : sb.getInventory().getContents()) n += totems(c);
            return n;
        }
        return 0;
    }

    private boolean ok(FfaMode z, ItemStack it, ItemStack[][] pools, boolean inShulker) {
        if (KitRules.banned(z, it.getType())) return false;
        if (Tag.SHULKER_BOXES.isTagged(it.getType())) {
            if (inShulker) return false;
            if (it.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox sb) {
                for (ItemStack c : sb.getInventory().getContents()) {
                    if (c != null && !c.getType().isAir() && !ok(z, c, pools, true)) return false;
                }
            }
            return true;
        }
        if (KitRules.isArmor(it.getType())) return true; // armures personnalisables (type, enchantements, trims)
        ItemStack n = norm(it);
        for (ItemStack[] tab : pools) {
            for (ItemStack p : tab) {
                if (p == null || p.getType().isAir()) continue;
                if (p.getAmount() >= it.getAmount() && norm(p).isSimilar(n)) return true;
            }
        }
        return false;
    }

    private static ItemStack norm(ItemStack it) {
        ItemStack c = it.clone();
        c.setAmount(1);
        if (c.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d && d.hasDamage()) {
            d.setDamage(0);
            c.setItemMeta(d);
        }
        return c;
    }

    // ------------------------------------------------------------------ kits 1 à 3

    private String base(UUID id, FfaMode z) {
        return "players." + id + "." + z.key;
    }

    public int selected(UUID id) {
        return selected.getOrDefault(id, 1);
    }

    public void setSelected(UUID id, int n) {
        selected.put(id, Math.max(1, Math.min(MAX_KITS, n)));
    }

    public boolean hasKit(UUID id, FfaMode z, int n) {
        return !cfg.getStringList(base(id, z) + ".kit" + n).isEmpty();
    }

    public int last(UUID id, FfaMode z) {
        return cfg.getInt(base(id, z) + ".last", 0);
    }

    /** Sauvegarde l'inventaire comme kit n de la zone. Renvoie null si OK, sinon le nom de l'objet interdit. */
    public String saveKit(Player p, FfaMode z, int n) {
        ItemStack[] c = p.getInventory().getContents();
        String err = check(z, c);
        if (err != null) return err;
        String b = base(p.getUniqueId(), z);
        cfg.set(b + ".kit" + n, encAll(c));
        cfg.set(b + ".last", n);
        save();
        return null;
    }

    /** Équipe le kit n. Renvoie -1 s'il n'existe pas, sinon le nombre d'objets retirés (plus autorisés). */
    public int loadKit(Player p, FfaMode z, int n) {
        String b = base(p.getUniqueId(), z);
        List<String> l = cfg.getStringList(b + ".kit" + n);
        if (l.isEmpty()) return -1;
        PlayerInventory inv = p.getInventory();
        ItemStack[] kit = decAll(l, inv.getSize());
        ItemStack[][] pools = getPools(z);
        ItemStack[] c = new ItemStack[inv.getSize()];
        int removed = 0;
        int lim = KitRules.totemLimit(z);
        int totalTotems = 0;
        for (int i = 0; i < kit.length; i++) {
            ItemStack it = kit[i];
            if (it == null || it.getType().isAir()) continue;
            int t = totems(it);
            if (!ok(z, it, pools, false) || (lim >= 0 && totalTotems + t > lim)) {
                removed++;
                continue;
            }
            totalTotems += t;
            c[i] = KitRules.bind(it);
        }
        inv.setContents(c);
        cfg.set(b + ".last", n);
        save();
        p.updateInventory();
        return removed;
    }

    // ------------------------------------------------------------------ stuff par zone

    /** Changement de zone : l'ancien stuff reste dans l'inventaire (0,5 s) et sera sauvegardé à la fin. */
    public void beginSwitch(UUID id, FfaMode old) {
        pendingOld.putIfAbsent(id, old);
    }

    /** Zone dont le stuff est actuellement dans l'inventaire du joueur. */
    public FfaMode stuffZone(UUID id) {
        FfaMode old = pendingOld.get(id);
        return old != null ? old : getMode(id);
    }

    public void clearPending(UUID id) {
        pendingOld.remove(id);
    }

    /** Fin du délai : sauvegarde du stuff de l'ancienne zone, puis récupération de celui de la nouvelle. */
    public void finishSwitch(Player p, FfaMode next) {
        FfaMode old = pendingOld.remove(p.getUniqueId());
        if (old != null) saveStuff(p, old);
        restoreStuff(p, next);
    }

    public void saveStuff(Player p, FfaMode z) {
        cfg.set(base(p.getUniqueId(), z) + ".stuff", encAll(p.getInventory().getContents()));
        save();
    }

    public void restoreStuff(Player p, FfaMode z) {
        PlayerInventory inv = p.getInventory();
        List<String> l = cfg.getStringList(base(p.getUniqueId(), z) + ".stuff");
        if (l.isEmpty()) {
            inv.clear();
        } else {
            ItemStack[] s = decAll(l, inv.getSize());
            ItemStack[] c = new ItemStack[inv.getSize()];
            System.arraycopy(s, 0, c, 0, Math.min(s.length, c.length));
            inv.setContents(c);
        }
        p.updateInventory();
    }
}
