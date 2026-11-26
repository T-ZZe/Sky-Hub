package fr.skyhub.kit;

import fr.skyhub.SkyHubPlugin;
import fr.skyhub.ffa.FfaMode;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Toutes les interfaces des kits : éditeur joueur, éditeur admin, personnalisation d'armure, shulker. */
public final class KitGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Disposition (comme sur les captures) : 3 lignes x 7 colonnes d'objets, boutons dans la colonne de droite
    static final int[] CELLS = buildCells();
    static final int SLOT_SAVE = 8;
    static final int[] SLOT_TAB = {17, 26, 35};
    static final int SLOT_KIT = 44;
    static final int SLOT_LOAD = 45;
    static final int SLOT_CLEAR = 53;
    static final int[] PREVIEW = {47, 48, 49, 50};        // casque, plastron, jambières, bottes
    static final int[] PREVIEW_INV = {39, 38, 37, 36};
    static final int SLOT_OFFHAND = 51;

    private static final String[] TAB_NAMES = {"Équipement", "Utilitaire", "Nourriture & potions"};
    private static final Material[] TAB_ICONS = {Material.DIAMOND_SWORD, Material.ENDER_PEARL, Material.POTION};

    private static final int[] ROW_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private static final String[] PATTERNS = {"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout",
            "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"};
    private static final String[] TRIM_MATS = {"quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald",
            "diamond", "lapis", "amethyst", "resin"};
    private static final String[] TRIM_ICONS = {"QUARTZ", "IRON_INGOT", "NETHERITE_INGOT", "REDSTONE", "COPPER_INGOT",
            "GOLD_INGOT", "EMERALD", "DIAMOND", "LAPIS_LAZULI", "AMETHYST_SHARD", "RESIN_BRICK"};

    private final SkyHubPlugin plugin;
    private final KitManager kits;

    public KitGui(SkyHubPlugin plugin, KitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    // ------------------------------------------------------------------ helpers

    private static int[] buildCells() {
        int[] a = new int[21];
        int i = 0;
        for (int r = 1; r <= 3; r++) {
            for (int c = 1; c <= 7; c++) a[i++] = r * 9 + c;
        }
        return a;
    }

    static int cellIndex(int slot) {
        for (int i = 0; i < CELLS.length; i++) {
            if (CELLS[i] == slot) return i;
        }
        return -1;
    }

    private static Component c(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.lore(l);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glint(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.empty());
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private static void fillPane(Inventory inv) {
        ItemStack p = pane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, p);
    }

    private static boolean isShulker(ItemStack it) {
        return it != null && Tag.SHULKER_BOXES.isTagged(it.getType());
    }

    private static boolean empty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    private static String pretty(String k) {
        String s = k.replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Material mat(String name, Material fallback) {
        Material m = Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static void equip(Player p, ItemStack armor) {
        PlayerInventory pi = p.getInventory();
        switch (KitRules.armorIndex(armor.getType())) {
            case 39 -> pi.setHelmet(armor);
            case 38 -> pi.setChestplate(armor);
            case 37 -> pi.setLeggings(armor);
            default -> pi.setBoots(armor);
        }
        p.updateInventory();
    }

    // ------------------------------------------------------------------ dispatch

    void click(InventoryClickEvent e, KitHolder h, Player p) {
        switch (h.kind) {
            case EDITOR -> editorClick(e, h, p);
            case ADMIN -> adminClick(e, h, p);
            case SHULKER -> shulkerClick(e, h, p);
            default -> customClick(e, h, p);
        }
    }

    void drag(InventoryDragEvent e, KitHolder h) {
        int size = e.getView().getTopInventory().getSize();
        switch (h.kind) {
            case ADMIN -> {
                for (int s : e.getRawSlots()) {
                    if (s < size && cellIndex(s) < 0) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
            case SHULKER -> {
                if (isShulker(e.getOldCursor())) {
                    for (int s : e.getRawSlots()) {
                        if (s < size) {
                            e.setCancelled(true);
                            return;
                        }
                    }
                }
            }
            default -> {
                for (int s : e.getRawSlots()) {
                    if (s < size) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    void close(KitHolder h, Player p) {
        switch (h.kind) {
            case EDITOR -> p.setItemOnCursor(null);
            case ADMIN -> {
                captureAdmin(h);
                kits.savePools(h.zone, h.adminPools);
                p.sendMessage(MM.deserialize("<green>Objets de la zone <white>" + h.zone.label + "</white> sauvegardés."));
            }
            case SHULKER -> saveShulker(h, p);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ éditeur joueur

    void openEditor(Player p, FfaMode zone, int tab) {
        KitHolder h = new KitHolder(KitHolder.Kind.EDITOR);
        h.zone = zone;
        h.tab = tab;
        h.inv = Bukkit.createInventory(h, 54,
                MM.deserialize("<dark_red>✂ <red><bold>Kit Editor</bold></red> <gray>(" + zone.label + ")"));
        renderEditor(p, h);
        p.openInventory(h.inv);
    }

    private void renderEditor(Player p, KitHolder h) {
        Inventory inv = h.inv;
        int kit = kits.selected(p.getUniqueId());
        fillPane(inv);
        ItemStack[] tab = kits.getPools(h.zone)[h.tab];
        for (int i = 0; i < CELLS.length; i++) {
            if (!empty(tab[i])) inv.setItem(CELLS[i], tab[i].clone());
        }
        inv.setItem(SLOT_SAVE, icon(Material.LIME_BANNER, "<green><bold>Sauvegarder (kit " + kit + ")",
                "<gray>Enregistre ton inventaire comme", "<gray>kit " + kit + " de la zone " + h.zone.label, "", "<yellow>▶ Clique"));
        for (int t = 0; t < 3; t++) {
            ItemStack b = icon(TAB_ICONS[t], "<aqua><bold>" + TAB_NAMES[t],
                    t == h.tab ? "<green>Onglet affiché" : "<yellow>▶ Clique pour afficher");
            if (t == h.tab) glint(b);
            inv.setItem(SLOT_TAB[t], b);
        }
        inv.setItem(SLOT_KIT, icon(Material.NAME_TAG, "<gold><bold>Kit sélectionné : " + kit,
                "<gray>Clique pour changer (1, 2, 3)"));
        inv.setItem(SLOT_LOAD, icon(Material.CHEST, "<green><bold>Charger le kit " + kit,
                "<gray>Équipe le kit " + kit + " de cette zone"));
        inv.setItem(SLOT_CLEAR, icon(Material.BARRIER, "<red><bold>Vider l'inventaire"));
        inv.setItem(46, icon(Material.LEATHER_HORSE_ARMOR, "<gray>Armure équipée", "<dark_gray>Shift-clic = personnaliser"));
        refreshPreview(p, inv);
    }

    private void refreshPreview(Player p, Inventory inv) {
        PlayerInventory pi = p.getInventory();
        for (int i = 0; i < 4; i++) inv.setItem(PREVIEW[i], orPane(pi.getItem(PREVIEW_INV[i])));
        inv.setItem(SLOT_OFFHAND, orPane(pi.getItemInOffHand()));
    }

    private static ItemStack orPane(ItemStack it) {
        return empty(it) ? pane() : it.clone();
    }

    private void editorClick(InventoryClickEvent e, KitHolder h, Player p) {
        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        // Inventaire du joueur
        if (clicked != top) {
            ItemStack cur = e.getCurrentItem();
            if (e.isShiftClick() && !empty(cur)) {
                int bottomSlot = e.getSlot();
                if (KitRules.isArmor(cur.getType())) {
                    e.setCancelled(true);
                    later(() -> startCustom(p, h.zone, false, null, bottomSlot, h.tab));
                    return;
                }
                if (isShulker(cur)) {
                    e.setCancelled(true);
                    later(() -> openShulker(p, bottomSlot));
                    return;
                }
            }
            if (e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
            return;
        }

        e.setCancelled(true);
        int slot = e.getSlot();
        UUID id = p.getUniqueId();

        if (slot == SLOT_SAVE) {
            int n = kits.selected(id);
            String err = kits.saveKit(p, h.zone, n);
            if (err != null) {
                p.sendMessage(MM.deserialize("<red>Kit non sauvegardé : <white>" + err
                        + "</white> n'est pas autorisé dans la zone " + h.zone.label + "."));
            } else {
                p.sendMessage(MM.deserialize("<green>Kit <white>" + n + "</white> (" + h.zone.label + ") sauvegardé."));
            }
            return;
        }
        for (int t = 0; t < 3; t++) {
            if (slot == SLOT_TAB[t]) {
                h.tab = t;
                renderEditor(p, h);
                return;
            }
        }
        if (slot == SLOT_KIT) {
            kits.setSelected(id, kits.selected(id) % KitManager.MAX_KITS + 1);
            renderEditor(p, h);
            return;
        }
        if (slot == SLOT_LOAD) {
            int n = kits.selected(id);
            int r = kits.loadKit(p, h.zone, n);
            if (r < 0) {
                p.sendMessage(MM.deserialize("<red>Tu n'as pas encore de kit " + n + " pour la zone " + h.zone.label + "."));
            } else {
                p.sendMessage(MM.deserialize("<green>Kit <white>" + n + "</white> équipé."
                        + (r > 0 ? " <gray>(" + r + " objet(s) retiré(s), plus autorisé(s))" : "")));
                refreshPreview(p, top);
            }
            return;
        }
        if (slot == SLOT_CLEAR) {
            p.getInventory().clear();
            p.updateInventory();
            refreshPreview(p, top);
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (slot == PREVIEW[i]) {
                final int idx = PREVIEW_INV[i];
                ItemStack worn = p.getInventory().getItem(idx);
                if (e.isShiftClick() && !empty(worn) && KitRules.isArmor(worn.getType())) {
                    later(() -> startCustom(p, h.zone, false, null, idx, h.tab));
                }
                return;
            }
        }

        int ci = cellIndex(slot);
        if (ci < 0) return;
        ItemStack pool = kits.getPools(h.zone)[h.tab][ci];
        if (empty(pool)) return;

        ItemStack give = pool.clone();
        boolean armor = KitRules.isArmor(give.getType());
        if (armor) KitRules.bind(give);

        if (armor && e.isShiftClick()) {
            later(() -> startCustom(p, h.zone, true, give, -1, h.tab));
            return;
        }
        if (armor) {
            equip(p, give);
            refreshPreview(p, top);
            return;
        }
        if (e.isShiftClick()) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(give);
            if (!left.isEmpty()) p.sendMessage(MM.deserialize("<red>Ton inventaire est plein."));
            return;
        }
        ItemStack cursor = e.getCursor();
        if (!empty(cursor)) {
            p.setItemOnCursor(null);
            return;
        }
        p.setItemOnCursor(give);
    }

    // ------------------------------------------------------------------ éditeur admin (/kit create)

    void openAdmin(Player p, FfaMode zone) {
        KitHolder h = new KitHolder(KitHolder.Kind.ADMIN);
        h.zone = zone;
        h.tab = 0;
        h.adminPools = KitManager.copy(kits.getPools(zone));
        h.inv = Bukkit.createInventory(h, 54,
                MM.deserialize("<dark_red>✂ <red><bold>Kit Create</bold></red> <gray>(" + zone.label + ")"));
        renderAdmin(h);
        p.openInventory(h.inv);
        p.sendMessage(MM.deserialize("<yellow>Mode création : <white>" + zone.label
                + "</white>. Mets / retire les objets dans les cases, ferme pour sauvegarder."));
    }

    private void renderAdmin(KitHolder h) {
        Inventory inv = h.inv;
        fillPane(inv);
        ItemStack[] tab = h.adminPools[h.tab];
        for (int i = 0; i < CELLS.length; i++) inv.setItem(CELLS[i], tab[i] == null ? null : tab[i].clone());
        inv.setItem(SLOT_SAVE, icon(Material.LIME_BANNER, "<green><bold>Sauvegarder et fermer", "<yellow>▶ Clique"));
        for (int t = 0; t < 3; t++) {
            ItemStack b = icon(TAB_ICONS[t], "<aqua><bold>" + TAB_NAMES[t],
                    t == h.tab ? "<green>Onglet affiché" : "<yellow>▶ Clique pour afficher");
            if (t == h.tab) glint(b);
            inv.setItem(SLOT_TAB[t], b);
        }
        inv.setItem(SLOT_KIT, icon(Material.REDSTONE, "<gold><bold>Objets par défaut", "<gray>Remet cet onglet par défaut"));
        inv.setItem(SLOT_CLEAR, icon(Material.BARRIER, "<red><bold>Vider l'onglet"));
    }

    private void captureAdmin(KitHolder h) {
        for (int i = 0; i < CELLS.length; i++) {
            ItemStack it = h.inv.getItem(CELLS[i]);
            h.adminPools[h.tab][i] = empty(it) ? null : it.clone();
        }
    }

    private void adminClick(InventoryClickEvent e, KitHolder h, Player p) {
        Inventory top = e.getView().getTopInventory();
        if (e.getClickedInventory() != top) {
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
            return;
        }
        int slot = e.getSlot();
        if (cellIndex(slot) >= 0) return; // case libre
        e.setCancelled(true);
        if (slot == SLOT_SAVE) {
            p.closeInventory();
            return;
        }
        for (int t = 0; t < 3; t++) {
            if (slot == SLOT_TAB[t]) {
                captureAdmin(h);
                h.tab = t;
                renderAdmin(h);
                return;
            }
        }
        if (slot == SLOT_KIT) {
            h.adminPools[h.tab] = KitDefaults.pool(h.zone)[h.tab];
            renderAdmin(h);
            return;
        }
        if (slot == SLOT_CLEAR) {
            h.adminPools[h.tab] = new ItemStack[KitManager.TAB_SLOTS];
            renderAdmin(h);
        }
    }

    // ------------------------------------------------------------------ personnalisation d'armure

    private void startCustom(Player p, FfaMode zone, boolean template, ItemStack working, int invIndex, int editorTab) {
        CustomSession s = new CustomSession();
        s.zone = zone;
        s.template = template;
        s.working = working == null ? null : working.clone();
        s.invIndex = invIndex;
        s.editorTab = editorTab;
        openMain(p, s);
    }

    private ItemStack current(Player p, CustomSession s) {
        return s.template ? s.working : p.getInventory().getItem(s.invIndex);
    }

    private void store(Player p, CustomSession s, ItemStack it) {
        if (s.template) {
            s.working = it;
        } else {
            p.getInventory().setItem(s.invIndex, it);
            p.updateInventory();
        }
    }

    private void backToEditor(Player p, CustomSession s) {
        later(() -> openEditor(p, s.zone, s.editorTab));
    }

    private KitHolder customHolder(KitHolder.Kind kind, CustomSession s, int size, String title) {
        KitHolder h = new KitHolder(kind);
        h.zone = s.zone;
        h.custom = s;
        h.inv = Bukkit.createInventory(h, size, MM.deserialize(title));
        return h;
    }

    private void openMain(Player p, CustomSession s) {
        KitHolder h = customHolder(KitHolder.Kind.ARMOR, s, 27, "<gold><bold>Armure</bold></gold> <gray>- personnalisation");
        renderMain(p, h);
        p.openInventory(h.inv);
    }

    private void renderMain(Player p, KitHolder h) {
        Inventory inv = h.inv;
        CustomSession s = h.custom;
        fillPane(inv);
        inv.setItem(18, icon(Material.ARROW, "<red>◀ Retour"));
        ItemStack cur = current(p, s);
        if (empty(cur) || !KitRules.isArmor(cur.getType())) {
            inv.setItem(13, icon(Material.BARRIER, "<red>Aucune armure ici"));
            return;
        }
        inv.setItem(4, cur.clone());
        inv.setItem(11, icon(cur.getType(), "<aqua><bold>Changer le type", "<gray>Cuir, fer, diamant, netherite...", "", "<yellow>▶ Clique"));
        inv.setItem(13, icon(Material.ENCHANTED_BOOK, "<light_purple><bold>Enchantements", "<gray>Modifier les enchantements", "", "<yellow>▶ Clique"));
        inv.setItem(15, icon(mat("SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE", Material.PAPER), "<green><bold>Trims", "<gray>Motif et matériau", "", "<yellow>▶ Clique"));
        if (s.template) {
            inv.setItem(22, icon(Material.EMERALD, "<green><bold>✔ Prendre cette pièce", "<gray>Équipe la pièce personnalisée"));
        }
    }

    private void openType(Player p, CustomSession s) {
        KitHolder h = customHolder(KitHolder.Kind.TYPE, s, 27, "<aqua><bold>Type d'armure");
        renderType(p, h);
        p.openInventory(h.inv);
    }

    private void renderType(Player p, KitHolder h) {
        Inventory inv = h.inv;
        fillPane(inv);
        inv.setItem(22, icon(Material.ARROW, "<red>◀ Retour"));
        ItemStack cur = current(p, h.custom);
        if (empty(cur)) return;
        List<Material> types = typeList(h, cur);
        for (int i = 0; i < types.size() && i < 14; i++) {
            Material m = types.get(i);
            int slot = i < 7 ? 10 + i : 19 + (i - 7);
            ItemStack ic = icon(m, "<white>" + pretty(m.name()),
                    m == cur.getType() ? "<green>Type actuel" : "<yellow>▶ Clique pour choisir");
            if (m == cur.getType()) glint(ic);
            inv.setItem(slot, ic);
        }
    }

    private List<Material> typeList(KitHolder h, ItemStack cur) {
        return KitRules.armorTypes(h.zone, cur.getType());
    }

    private void openEnch(Player p, CustomSession s) {
        KitHolder h = customHolder(KitHolder.Kind.ENCH, s, 45, "<light_purple><bold>Enchantements");
        renderEnch(p, h);
        p.openInventory(h.inv);
    }

    private List<Enchantment> applicable(ItemStack it) {
        List<Enchantment> l = new ArrayList<>();
        for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (KitRules.isCurse(e)) continue;
            if (e.canEnchantItem(it)) l.add(e);
        }
        l.sort(Comparator.comparing(e -> e.getKey().getKey()));
        return l;
    }

    private void renderEnch(Player p, KitHolder h) {
        Inventory inv = h.inv;
        fillPane(inv);
        inv.setItem(40, icon(Material.ARROW, "<red>◀ Retour"));
        ItemStack cur = current(p, h.custom);
        if (empty(cur)) return;
        List<Enchantment> list = applicable(cur);
        for (int i = 0; i < list.size() && i < ROW_SLOTS.length; i++) {
            Enchantment en = list.get(i);
            int lvl = cur.getEnchantmentLevel(en);
            ItemStack ic = icon(lvl > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
                    (lvl > 0 ? "<light_purple>" : "<gray>") + pretty(en.getKey().getKey()) + " <white>" + lvl + "/" + en.getMaxLevel(),
                    "<gray>Clic gauche : niveau +1", "<gray>Clic droit : niveau -1");
            if (lvl > 0) glint(ic);
            inv.setItem(ROW_SLOTS[i], ic);
        }
    }

    private void openTrim(Player p, CustomSession s) {
        KitHolder h = customHolder(KitHolder.Kind.TRIM, s, 27, "<green><bold>Trims");
        renderTrim(p, h);
        p.openInventory(h.inv);
    }

    private void renderTrim(Player p, KitHolder h) {
        Inventory inv = h.inv;
        CustomSession s = h.custom;
        fillPane(inv);
        inv.setItem(22, icon(Material.ARROW, "<red>◀ Retour"));
        ItemStack cur = current(p, s);
        if (!empty(cur)) inv.setItem(4, cur.clone());
        String pat = PATTERNS[s.patIdx];
        String tm = TRIM_MATS[s.matIdx];
        inv.setItem(10, icon(mat(pat.toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE", Material.PAPER),
                "<yellow>Motif : <white>" + pretty(pat), "<gray>Clic gauche : suivant", "<gray>Clic droit : précédent"));
        inv.setItem(12, icon(mat(TRIM_ICONS[s.matIdx], Material.PAPER),
                "<yellow>Matériau : <white>" + pretty(tm), "<gray>Clic gauche : suivant", "<gray>Clic droit : précédent"));
        inv.setItem(14, icon(Material.LIME_DYE, "<green><bold>Appliquer le trim", "<gray>" + pretty(pat) + " / " + pretty(tm)));
        inv.setItem(16, icon(Material.RED_DYE, "<red><bold>Retirer le trim"));
    }

    private void customClick(InventoryClickEvent e, KitHolder h, Player p) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        CustomSession s = h.custom;
        ItemStack cur = current(p, s);

        switch (h.kind) {
            case ARMOR -> {
                if (slot == 18) {
                    backToEditor(p, s);
                    return;
                }
                if (empty(cur) || !KitRules.isArmor(cur.getType())) return;
                if (slot == 11) later(() -> openType(p, s));
                else if (slot == 13) later(() -> openEnch(p, s));
                else if (slot == 15) later(() -> openTrim(p, s));
                else if (slot == 22 && s.template) {
                    equip(p, KitRules.bind(cur.clone()));
                    backToEditor(p, s);
                }
            }
            case TYPE -> {
                if (slot == 22) {
                    later(() -> openMain(p, s));
                    return;
                }
                if (empty(cur)) return;
                int i = slot >= 10 && slot <= 16 ? slot - 10 : (slot >= 19 && slot <= 25 ? 7 + slot - 19 : -1);
                List<Material> types = typeList(h, cur);
                if (i < 0 || i >= types.size()) return;
                ItemStack n = new ItemStack(types.get(i));
                n.addUnsafeEnchantments(cur.getEnchantments());
                if (cur.getItemMeta() instanceof ArmorMeta om && om.hasTrim() && n.getItemMeta() instanceof ArmorMeta nm) {
                    nm.setTrim(om.getTrim());
                    n.setItemMeta(nm);
                }
                KitRules.bind(n);
                store(p, s, n);
                renderType(p, h);
            }
            case ENCH -> {
                if (slot == 40) {
                    later(() -> openMain(p, s));
                    return;
                }
                if (empty(cur)) return;
                int idx = -1;
                for (int i = 0; i < ROW_SLOTS.length; i++) {
                    if (ROW_SLOTS[i] == slot) idx = i;
                }
                List<Enchantment> list = applicable(cur);
                if (idx < 0 || idx >= list.size()) return;
                Enchantment en = list.get(idx);
                int lvl = cur.getEnchantmentLevel(en);
                int max = en.getMaxLevel();
                int next = e.isRightClick() ? Math.max(0, lvl - 1) : (lvl >= max ? 0 : lvl + 1);
                if (next == 0) {
                    cur.removeEnchantment(en);
                } else {
                    for (Enchantment other : new ArrayList<>(cur.getEnchantments().keySet())) {
                        if (!other.equals(en) && en.conflictsWith(other)) cur.removeEnchantment(other);
                    }
                    cur.addUnsafeEnchantment(en, next);
                }
                KitRules.bind(cur);
                store(p, s, cur);
                renderEnch(p, h);
            }
            case TRIM -> {
                if (slot == 22) {
                    later(() -> openMain(p, s));
                    return;
                }
                if (slot == 10) {
                    s.patIdx = (s.patIdx + (e.isRightClick() ? PATTERNS.length - 1 : 1)) % PATTERNS.length;
                    renderTrim(p, h);
                } else if (slot == 12) {
                    s.matIdx = (s.matIdx + (e.isRightClick() ? TRIM_MATS.length - 1 : 1)) % TRIM_MATS.length;
                    renderTrim(p, h);
                } else if (slot == 14 || slot == 16) {
                    if (empty(cur) || !(cur.getItemMeta() instanceof ArmorMeta am)) return;
                    if (slot == 16) {
                        am.setTrim(null);
                    } else {
                        TrimPattern pat = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(PATTERNS[s.patIdx]));
                        TrimMaterial tmat = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(TRIM_MATS[s.matIdx]));
                        if (pat == null || tmat == null) {
                            p.sendMessage(MM.deserialize("<red>Ce trim n'est pas disponible sur cette version."));
                            return;
                        }
                        am.setTrim(new ArmorTrim(tmat, pat));
                    }
                    cur.setItemMeta(am);
                    store(p, s, cur);
                    renderTrim(p, h);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ shulker

    void openShulker(Player p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        if (!isShulker(it)) return;
        if (!(it.getItemMeta() instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof ShulkerBox sb)) return;
        KitHolder h = new KitHolder(KitHolder.Kind.SHULKER);
        h.shulkerIndex = slot;
        h.inv = Bukkit.createInventory(h, 27, MM.deserialize("<light_purple><bold>Shulker box"));
        h.inv.setContents(sb.getInventory().getContents());
        p.openInventory(h.inv);
    }

    private void saveShulker(KitHolder h, Player p) {
        ItemStack[] contents = h.inv.getContents();
        ItemStack it = p.getInventory().getItem(h.shulkerIndex);
        if (!isShulker(it) || !(it.getItemMeta() instanceof BlockStateMeta bsm)
                || !(bsm.getBlockState() instanceof ShulkerBox sb)) {
            // la shulker a bougé : on rend le contenu au joueur
            for (ItemStack c : contents) {
                if (empty(c)) continue;
                for (ItemStack left : p.getInventory().addItem(c).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
            }
            return;
        }
        sb.getInventory().setContents(contents);
        bsm.setBlockState(sb);
        it.setItemMeta(bsm);
        p.getInventory().setItem(h.shulkerIndex, it);
        p.updateInventory();
    }

    private void shulkerClick(InventoryClickEvent e, KitHolder h, Player p) {
        Inventory top = e.getView().getTopInventory();
        if (e.getClickedInventory() != top) {
            if (e.getClickedInventory() == p.getInventory() && e.getSlot() == h.shulkerIndex) {
                e.setCancelled(true);
                return;
            }
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() == h.shulkerIndex) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick() && isShulker(e.getCurrentItem())) e.setCancelled(true);
            return;
        }
        if (isShulker(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY) {
            int b = e.getHotbarButton();
            if (b >= 0 && (b == h.shulkerIndex || isShulker(p.getInventory().getItem(b)))) e.setCancelled(true);
        }
    }
}
