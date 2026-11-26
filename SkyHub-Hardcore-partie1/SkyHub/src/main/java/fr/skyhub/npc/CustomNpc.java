package fr.skyhub.npc;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Un PNJ créé avec /npc. */
public final class CustomNpc {
    public enum Kind { INTERFACE, INTERACTION, FFA_MOD }

    public enum Action { NONE, ITEM, TELEPORT }

    public final String key;
    public String display;
    public String color = "white";
    public EntityType type = EntityType.VILLAGER;
    public Kind kind = Kind.INTERACTION;

    public String world = "world";
    public double x, y, z;
    public float yaw, pitch;

    /** Échanges : [0] = prix, [1] = objet obtenu. */
    public final List<ItemStack[]> trades = new ArrayList<>();

    public Action action = Action.NONE;
    public ItemStack item;
    public boolean once;
    public final Set<UUID> given = ConcurrentHashMap.newKeySet();

    public String tpWorld;
    public double tx, ty, tz;
    public float tyaw, tpitch;

    public CustomNpc(String key) {
        this.key = key;
        this.display = key;
    }
}
