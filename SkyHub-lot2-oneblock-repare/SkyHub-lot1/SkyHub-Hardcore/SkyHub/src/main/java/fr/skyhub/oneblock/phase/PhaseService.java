package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.OneBlockPlugin;
import fr.skyhub.oneblock.island.Island;
import fr.skyhub.oneblock.island.IslandMember;
import fr.skyhub.oneblock.utils.Effects;
import fr.skyhub.oneblock.utils.ItemSpec;
import fr.skyhub.oneblock.utils.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Casse du OneBlock : comptage unique, progression, changement de phase, génération du bloc suivant. Thread principal. */
public final class PhaseService {

    private final OneBlockPlugin plugin;

    public PhaseService(OneBlockPlugin plugin) { this.plugin = plugin; }

    private Content content() { return plugin.content(); }

    /**
     * Traite une casse valide du bloc central. L'événement Bukkit d'origine est annulé par l'appelant ;
     * ce code est la seule voie de comptage : une casse = un appel = un incrément.
     */
    public void handleBreak(Island island, Player player, Block block) {
        Content c = content();
        long now = System.currentTimeMillis();
        if (now - island.lastBreakMillis < c.general().minBreakIntervalMs()) return;
        island.lastBreakMillis = now;

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (creative && !c.general().creativeDrops()) {
            block.setType(Material.AIR, false);
        } else {
            block.breakNaturally(tool, true);
            if (!creative && tool.getType().getMaxDurability() > 0) player.damageItemStack(EquipmentSlot.HAND, 1);
        }

        if (!creative || c.general().countCreative()) {
            island.setBlocks(island.blocks() + 1);
            IslandMember m = island.member(player.getUniqueId());
            if (m != null) m.contributed++;
            plugin.islands().markDirty(island);
        }

        checkAdvance(island, true);
        Phase phase = c.phases().get(island.phase());
        generate(island, block, phase);
        plugin.mobs().onBreak(island, player, phase);
    }

    // -------------------------------------------------------------- génération

    /** Place le bloc suivant selon le pool de la phase (le tirage SURPRISE déclenche l'événement puis pose le bloc de surprise). */
    public void generate(Island island, Block block, Phase phase) {
        Content c = content();
        BlockPool pool = c.pools().get(phase.id());
        BlockChoice choice = pool.table().roll(ThreadLocalRandom.current());
        switch (choice.kind()) {
            case MATERIAL -> block.setType(choice.material(), false);
            case CHEST_BONUS -> {
                block.setType(Material.CHEST, false);
                if (block.getState() instanceof Chest chest) {
                    for (ItemSpec spec : pool.bonusChest()) {
                        if (ThreadLocalRandom.current().nextDouble() < 0.7) chest.getBlockInventory().addItem(spec.roll());
                    }
                    if (chest.getBlockInventory().isEmpty()) chest.getBlockInventory().addItem(pool.bonusChest().get(0).roll());
                }
            }
            case SURPRISE -> {
                block.setType(c.surpriseBlock(), false);
                triggerSurprise(island, block.getLocation().add(0.5, 1.2, 0.5), c.surprises().roll(ThreadLocalRandom.current()));
            }
        }
    }

    private void triggerSurprise(Island island, Location at, SurpriseEvent ev) {
        World w = at.getWorld();
        Effects.burst(at, "totem_of_undying", 30, 0.6);
        Effects.sound(at, "entity.firework_rocket.twinkle", 1f, 1f);
        switch (ev.type()) {
            case DROPS -> ev.items().forEach(spec -> w.dropItemNaturally(at, spec.roll()));
            case XP -> w.spawn(at, ExperienceOrb.class, orb -> orb.setExperience(ev.xp().roll()));
            case POTION -> {
                for (IslandMember m : island.members().values()) {
                    Player p = Bukkit.getPlayer(m.uuid);
                    if (p != null && p.getWorld().equals(w) && p.getLocation().distanceSquared(at) <= 30 * 30) {
                        p.addPotionEffect(new PotionEffect(ev.effect(), ev.seconds() * 20, ev.level() - 1));
                    }
                }
            }
        }
        for (IslandMember m : island.members().values()) {
            Player p = Bukkit.getPlayer(m.uuid);
            if (p != null) plugin.messages().send(p, "phase.surprise");
        }
    }

    // -------------------------------------------------------------- progression

    /** Passe les phases dont le seuil est atteint. Ne recule jamais ; rewards une seule fois par phase (rewardedPhase). */
    public void checkAdvance(Island island, boolean announce) {
        PhaseRegistry reg = content().phases();
        Phase target = reg.phaseFor(island.blocks());
        if (target.id() <= island.phase()) return;
        int from = island.phase();
        List<Phase> reached = new ArrayList<>();
        for (int id = from + 1; id <= target.id(); id++) reached.add(reg.get(id));
        island.setPhase(target.id());
        plugin.islands().markDirty(island);
        for (Phase p : reached) {
            if (p.id() > island.rewardedPhase()) { grantRewards(island, p); island.setRewardedPhase(p.id()); }
        }
        applyBiome(island, target);
        if (announce) announcePhase(island, target);
    }

    /** Commande admin : fixe la phase et aligne le compteur sur son seuil (pas de récompenses). */
    public void setPhaseAdmin(Island island, int phaseId) {
        Phase p = content().phases().get(phaseId);
        island.setBlocks(p.threshold());
        island.setPhase(p.id());
        island.setRewardedPhase(Math.max(island.rewardedPhase(), p.id()));
        plugin.islands().markDirty(island);
        applyBiome(island, p);
        Block b = plugin.islands().world().getBlockAt(island.centerX(), island.oneBlockY(), island.centerZ());
        generate(island, b, p);
    }

    public void addBlocksAdmin(Island island, long amount) {
        island.setBlocks(island.blocks() + amount);
        plugin.islands().markDirty(island);
        checkAdvance(island, true);
    }

    private void grantRewards(Island island, Phase phase) {
        for (IslandMember m : island.members().values()) {
            Player p = Bukkit.getPlayer(m.uuid);
            if (p == null) continue;
            for (String r : phase.rewards()) {
                try { Rewards.grant(p, r); }
                catch (RuntimeException e) { plugin.getLogger().warning("Récompense '" + r + "' (phase " + phase.id() + ") impossible pour " + p.getName() + " : " + e.getMessage()); }
            }
        }
    }

    private void announcePhase(Island island, Phase phase) {
        World w = plugin.islands().world();
        Location at = island.oneBlockLocation(w).add(0.5, 1.5, 0.5);
        Effects.burst(at, phase.particle(), 80, 1.2);
        Component title = Messages.mm(phase.color() + "<bold>" + phase.name());
        for (IslandMember m : island.members().values()) {
            Player p = Bukkit.getPlayer(m.uuid);
            if (p == null) continue;
            p.showTitle(Title.title(title, plugin.messages().get("phase.subtitle", "phase", String.valueOf(phase.id()))));
            plugin.messages().send(p, "phase.reached", "phase", phase.name(), "id", String.valueOf(phase.id()));
            Effects.sound(p.getLocation(), phase.sound(), 1f, 1f);
        }
    }

    /** Biome de l'île, par cellules de 4x4x4 autour du centre (appelé uniquement aux changements de phase). */
    public void applyBiome(Island island, Phase phase) {
        NamespacedKey key = NamespacedKey.fromString(phase.biome());
        Biome biome = key == null ? null : Registry.BIOME.get(key);
        if (biome == null) return;
        World w = plugin.islands().world();
        int r = Math.min(plugin.content().islands().protectionRadius(), 48);
        int y0 = Math.max(w.getMinHeight(), island.oneBlockY() - 24), y1 = Math.min(w.getMaxHeight() - 1, island.oneBlockY() + 56);
        for (int x = island.centerX() - r; x <= island.centerX() + r; x += 4)
            for (int z = island.centerZ() - r; z <= island.centerZ() + r; z += 4)
                for (int y = y0; y <= y1; y += 4) w.setBiome(x, y, z, biome);
    }
}
