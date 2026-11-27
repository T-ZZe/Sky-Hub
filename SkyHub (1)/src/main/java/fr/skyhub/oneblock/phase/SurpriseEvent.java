package fr.skyhub.oneblock.phase;

import fr.skyhub.oneblock.utils.ItemSpec;
import fr.skyhub.oneblock.utils.Ranges;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public record SurpriseEvent(Type type, List<ItemSpec> items, Ranges xp, PotionEffectType effect, int seconds, int level) {
    public enum Type { DROPS, XP, POTION }
}
