package fr.skyffa.claim;

import fr.skyffa.SkyFfaPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Zones protégées (/claim) : règles de pose / casse / wind charge, sauvegarde de la zone,
 * reconstruction automatique et disparition des blocs posés après un délai.
 */
public final class ClaimManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private final SkyFfaPlugin plugin;
    private final File dir;
    private final File file;
    private final NamespacedKey wandKey;

    private final Map<String, Claim> claims = new ConcurrentHashMap<>();
    private final Map<String, Snapshot> snaps = new ConcurrentHashMap<>();
    /** claim -> (position du bloc -> moment d'expiration en ms, Long.MAX_VALUE = jamais) */
    private final Map<String, Map<Long, Long>> placed = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    private BukkitTask ticker;
    private boolean dirty;
    private int tickCount;

    public ClaimManager(SkyFfaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "claims");
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        this.wandKey = new NamespacedKey(plugin, "claim_wand");
    }

    // ================================================================ config
    private int perTick() {
        return Math.max(1000, plugin.getConfig().getInt("claims.blocks-per-tick", 10000));
    }

    private long maxVolume() {
        return Math.max(1000, plugin.getConfig().getLong("claims.max-volume", 2_000_000L));
    }

    private File snapFile(Claim c) {
        return new File(dir, c.key() + ".snap");
    }

    private File placedFile(Claim c) {
        return new File(dir, c.key() + ".placed");
    }

    // ================================================================ chargement / sauvegarde
    public void start() {
        dir.mkdirs();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = cfg.getConfigurationSection("claims");
        if (s != null) {
            for (String name : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(name);
                if (c == null) continue;
                Claim cl = new Claim(name, c.getString("world", "world"),
                        c.getInt("min.x"), c.getInt("min.y"), c.getInt("min.z"),
                        c.getInt("max.x"), c.getInt("max.y"), c.getInt("max.z"));
                cl.place = c.getBoolean("flags.place");
                cl.breakAllowed = c.getBoolean("flags.break");
                cl.windcharge = c.getBoolean("flags.windcharge");
                cl.pvp = c.getBoolean("flags.pvp", true);
                cl.drop = c.getBoolean("flags.drop", true);
                cl.expireSec = c.getInt("expire");
                cl.pearlSec = c.getInt("pearl");
                cl.restoreSec = c.getInt("restore");
                if (cl.restoreSec > 0) cl.nextRestore = System.currentTimeMillis() + cl.restoreSec * 1000L;
                claims.put(cl.key(), cl);
                loadPlaced(cl);
            }
        }
        // lecture des sauvegardes de zones hors du thread principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Claim c : claims.values()) {
                File f = snapFile(c);
                if (!f.exists()) continue;
                try {
                    snaps.put(c.key(), Snapshot.read(f));
                } catch (IOException e) {
                    plugin.getLogger().warning("Sauvegarde illisible pour la zone " + c.name + " : " + e.getMessage());
                }
            }
        });
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        savePlaced();
        saveClaims();
    }

    public synchronized void saveClaims() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Claim c : claims.values()) {
            String b = "claims." + c.name + ".";
            cfg.set(b + "world", c.world);
            cfg.set(b + "min.x", c.minX);
            cfg.set(b + "min.y", c.minY);
            cfg.set(b + "min.z", c.minZ);
            cfg.set(b + "max.x", c.maxX);
            cfg.set(b + "max.y", c.maxY);
            cfg.set(b + "max.z", c.maxZ);
            cfg.set(b + "flags.place", c.place);
            cfg.set(b + "flags.break", c.breakAllowed);
            cfg.set(b + "flags.windcharge", c.windcharge);
            cfg.set(b + "flags.pvp", c.pvp);
            cfg.set(b + "flags.drop", c.drop);
            cfg.set(b + "expire", c.expireSec);
            cfg.set(b + "pearl", c.pearlSec);
            cfg.set(b + "restore", c.restoreSec);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire claims.yml : " + e.getMessage());
        }
    }

    private void loadPlaced(Claim c) {
        File f = placedFile(c);
        if (!f.exists()) return;
        Map<Long, Long> m = placed.computeIfAbsent(c.key(), k -> new ConcurrentHashMap<>());
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length == 2) m.put(Long.parseLong(p[0]), Long.parseLong(p[1]));
            }
        } catch (IOException | NumberFormatException e) {
            plugin.getLogger().warning("Blocs posés illisibles pour " + c.name + " : " + e.getMessage());
        }
    }

    private synchronized void savePlaced() {
        if (!dirty) return;
        dir.mkdirs();
        for (Claim c : claims.values()) {
            File f = placedFile(c);
            Map<Long, Long> m = placed.get(c.key());
            if (m == null || m.isEmpty()) {
                f.delete();
                continue;
            }
            try (PrintWriter w = new PrintWriter(new FileWriter(f))) {
                for (var e : m.entrySet()) w.println(e.getKey() + "," + e.getValue());
            } catch (IOException ex) {
                plugin.getLogger().warning("Impossible d'écrire " + f.getName() + " : " + ex.getMessage());
            }
        }
        dirty = false;
    }

    // ================================================================ accès
    public Claim get(String name) {
        return claims.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Claim> all() {
        return claims.values();
    }

    public boolean hasSnapshot(Claim c) {
        return snaps.containsKey(c.key());
    }

    /** Zone la plus petite qui contient la position. */
    public Claim at(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return at(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public Claim at(World w, int x, int y, int z) {
        Claim best = null;
        for (Claim c : claims.values()) {
            if (c.contains(w, x, y, z) && (best == null || c.volume() < best.volume())) best = c;
        }
        return best;
    }

    public Claim at(Block b) {
        return at(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    // ================================================================ baguette / sélection
    public ItemStack wand() {
        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = it.getItemMeta();
        m.displayName(MM.deserialize("<!italic><gold><bold>Baguette de claim"));
        m.lore(List.of(
                MM.deserialize("<!italic><gray>Clic gauche : position 1"),
                MM.deserialize("<!italic><gray>Clic droit : position 2"),
                MM.deserialize("<!italic><dark_gray>OP + créatif uniquement")));
        m.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isWand(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void setPos(UUID id, int which, Location l) {
        (which == 1 ? pos1 : pos2).put(id, l.getBlock().getLocation());
    }

    public Location[] selection(UUID id) {
        Location a = pos1.get(id), b = pos2.get(id);
        return a == null || b == null ? null : new Location[]{a, b};
    }

    // ================================================================ création / suppression
    /** Retourne un message d'erreur, ou null si la zone est créée. */
    public String create(String name, Location a, Location b) {
        if (!NAME.matcher(name).matches()) return "Nom invalide (lettres, chiffres, _ et - uniquement).";
        if (get(name) != null) return "Une zone nommée " + name + " existe déjà.";
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return "Les deux positions doivent être dans le même monde.";
        Claim c = new Claim(name, a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
        if (c.volume() > maxVolume()) return "Zone trop grande (" + c.volume() + " blocs, max " + maxVolume() + ").";
        claims.put(c.key(), c);
        saveClaims();
        return null;
    }

    public void delete(Claim c) {
        claims.remove(c.key());
        snaps.remove(c.key());
        placed.remove(c.key());
        snapFile(c).delete();
        placedFile(c).delete();
        saveClaims();
    }

    // ================================================================ blocs posés par les joueurs
    public void track(Claim c, Block b) {
        long exp = c.expireSec > 0 ? System.currentTimeMillis() + c.expireSec * 1000L : Long.MAX_VALUE;
        placed.computeIfAbsent(c.key(), k -> new ConcurrentHashMap<>()).put(b.getBlockKey(), exp);
        dirty = true;
    }

    public boolean isTracked(Claim c, Block b) {
        Map<Long, Long> m = placed.get(c.key());
        return m != null && m.containsKey(b.getBlockKey());
    }

    public void untrack(Claim c, Block b) {
        Map<Long, Long> m = placed.get(c.key());
        if (m != null && m.remove(b.getBlockKey()) != null) dirty = true;
    }

    public void setExpire(Claim c, int sec) {
        c.expireSec = sec;
        Map<Long, Long> m = placed.get(c.key());
        if (m != null) {
            long exp = sec > 0 ? System.currentTimeMillis() + sec * 1000L : Long.MAX_VALUE;
            m.replaceAll((k, v) -> exp);
            dirty = true;
        }
        saveClaims();
    }

    public void setPearl(Claim c, int sec) {
        c.pearlSec = sec;
        saveClaims();
    }

    public void setRestore(Claim c, int sec) {
        c.restoreSec = sec;
        c.nextRestore = sec > 0 ? System.currentTimeMillis() + sec * 1000L : 0;
        saveClaims();
    }

    // ================================================================ sauvegarde de la zone
    /** Copie tous les blocs de la zone (par paquets pour ne pas lagger). */
    public boolean saveSnapshot(Claim c, CommandSender notify) {
        World w = Bukkit.getWorld(c.world);
        if (w == null || c.busy) return false;
        c.busy = true;
        final int sx = c.maxX - c.minX + 1, sy = c.maxY - c.minY + 1, sz = c.maxZ - c.minZ + 1;
        final int total = sx * sy * sz;
        final int[] data = new int[total];
        final Map<String, Integer> index = new HashMap<>();
        final List<String> pal = new ArrayList<>();
        final int per = perTick();

        new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                int end = Math.min(total, idx + per);
                for (; idx < end; idx++) {
                    int z = idx % sz;
                    int y = (idx / sz) % sy;
                    int x = idx / (sz * sy);
                    String s = w.getBlockAt(c.minX + x, c.minY + y, c.minZ + z).getBlockData().getAsString();
                    Integer i = index.get(s);
                    if (i == null) {
                        i = pal.size();
                        pal.add(s);
                        index.put(s, i);
                    }
                    data[idx] = i;
                }
                if (idx >= total) {
                    cancel();
                    Snapshot snap = new Snapshot(c.minX, c.minY, c.minZ, sx, sy, sz, pal.toArray(new String[0]), data);
                    snaps.put(c.key(), snap);
                    // l'état actuel devient la référence : plus rien de "posé"
                    Map<Long, Long> m = placed.get(c.key());
                    if (m != null) m.clear();
                    dirty = true;
                    c.busy = false;
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            dir.mkdirs();
                            snap.write(snapFile(c));
                        } catch (IOException e) {
                            plugin.getLogger().warning("Impossible d'écrire la sauvegarde de " + c.name + " : " + e.getMessage());
                        }
                    });
                    if (notify != null) notify.sendMessage(MM.deserialize("<green>Zone <white>" + c.name
                            + "</white> sauvegardée (" + total + " blocs, " + pal.size() + " types)."));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ================================================================ reconstruction
    public boolean restore(Claim c, CommandSender notify) {
        Snapshot sn = snaps.get(c.key());
        World w = Bukkit.getWorld(c.world);
        if (sn == null || w == null || c.busy) return false;
        c.busy = true;
        final BlockData[] pal = sn.palette();
        final int total = sn.data.length;
        final int per = perTick();
        evacuate(c, w);

        new BukkitRunnable() {
            int idx = 0;
            int t = 0;

            @Override
            public void run() {
                if (t++ % 5 == 0) evacuate(c, w);
                int end = Math.min(total, idx + per);
                for (; idx < end; idx++) {
                    int z = idx % sn.sz;
                    int y = (idx / sn.sz) % sn.sy;
                    int x = idx / (sn.sz * sn.sy);
                    Block b = w.getBlockAt(sn.minX + x, sn.minY + y, sn.minZ + z);
                    BlockData want = pal[sn.data[idx]];
                    if (!b.getBlockData().equals(want)) b.setBlockData(want, false);
                }
                if (idx >= total) {
                    cancel();
                    Map<Long, Long> m = placed.get(c.key());
                    if (m != null) m.clear();
                    dirty = true;
                    c.busy = false;
                    evacuate(c, w);
                    for (Player p : w.getPlayers()) {
                        if (c.contains(w, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())) {
                            p.sendActionBar(MM.deserialize("<green>La zone " + c.name + " a été reconstruite !"));
                        }
                    }
                    if (notify != null) notify.sendMessage(MM.deserialize("<green>Zone <white>" + c.name + "</white> reconstruite."));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * Pendant une reconstruction : les cristaux d'Ender de la zone disparaissent et les joueurs qui se trouvent
     * dans la zone sont téléportés juste au-dessus (ils ne restent pas coincés dans les blocs).
     */
    private void evacuate(Claim c, World w) {
        for (org.bukkit.entity.EnderCrystal cr : w.getEntitiesByClass(org.bukkit.entity.EnderCrystal.class)) {
            Location l = cr.getLocation();
            if (c.contains(w, l.getBlockX(), l.getBlockY(), l.getBlockZ())) cr.remove();
        }
        int y = Math.min(c.maxY + 2, w.getMaxHeight() - 1);
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            if (!c.contains(w, l.getBlockX(), l.getBlockY(), l.getBlockZ())) continue;
            p.teleportAsync(new Location(w, l.getX(), y, l.getZ(), l.getYaw(), l.getPitch()));
            p.setFallDistance(0);
            p.sendActionBar(MM.deserialize("<yellow>La zone " + c.name + " se reconstruit : tu as été téléporté au-dessus."));
        }
    }

    // ================================================================ boucle 1 fois par seconde
    private void tick() {
        long now = System.currentTimeMillis();
        for (Claim c : claims.values()) {
            if (c.busy) continue;
            World w = Bukkit.getWorld(c.world);
            if (w == null) continue;
            if (c.restoreSec > 0 && snaps.containsKey(c.key()) && now >= c.nextRestore) {
                c.nextRestore = now + c.restoreSec * 1000L;
                restore(c, null);
                continue;
            }
            if (c.expireSec > 0) expireDue(c, w, now);
        }
        if (++tickCount % 60 == 0) savePlaced();
    }

    /** Retire les blocs posés dont le délai est dépassé (remet le bloc d'origine, sinon de l'air). */
    private void expireDue(Claim c, World w, long now) {
        Map<Long, Long> m = placed.get(c.key());
        if (m == null || m.isEmpty()) return;
        Snapshot sn = snaps.get(c.key());
        int n = 0;
        for (Iterator<Map.Entry<Long, Long>> it = m.entrySet().iterator(); it.hasNext() && n < 2000; ) {
            var e = it.next();
            if (e.getValue() > now) continue;
            Block b = w.getBlockAtKey(e.getKey());
            BlockData orig = sn != null ? sn.at(b.getX(), b.getY(), b.getZ()) : null;
            if (orig == null) orig = Bukkit.createBlockData(Material.AIR);
            if (!b.getBlockData().equals(orig)) b.setBlockData(orig, false);
            it.remove();
            dirty = true;
            n++;
        }
    }
}
