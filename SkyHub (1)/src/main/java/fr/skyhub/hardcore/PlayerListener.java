package fr.skyhub.hardcore;

import fr.skyhub.SkyHubPlugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {
    private final SkyHubPlugin pl;

    public PlayerListener(SkyHubPlugin pl) {
        this.pl = pl;
    }

    /** Seule la blacklist (ban définitif) bloque la connexion au serveur. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (pl.data().isBlacklisted(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    Gui.MM.deserialize("<dark_red><bold>Tu es banni définitivement de ce serveur."));
        }
    }

    /** Mort dans le monde hardcore = banni du monde hardcore (pas du serveur). */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!pl.isHardcoreWorld(p.getWorld())) return;
        if (p.hasPermission("hardcore.bypass")) return;
        long until = System.currentTimeMillis() + pl.data().getHours() * 3_600_000L;
        pl.data().tempBan(p.getUniqueId(), p.getName(), until);
        Bukkit.broadcast(Gui.MM.deserialize("<dark_red>☠ <red>" + p.getName()
                + " est mort et est banni du monde hardcore pour <yellow>" + Gui.fmtHours(pl.data().getHours()) + "</yellow> !"));
    }

    /** Si le joueur est banni du hardcore, il réapparaît au hub avec le message de ban. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Long until = pl.data().activeBan(p.getUniqueId());
        if (until != null && !p.hasPermission("hardcore.bypass")
                && pl.isHardcoreWorld(e.getRespawnLocation().getWorld())) {
            e.setRespawnLocation(pl.hub().spawn());
            Bukkit.getScheduler().runTask(pl, () -> {
                pl.hub().applyLobbyState(p);
                p.sendMessage(Gui.banMessage("Tu es mort en mode hardcore.", until));
            });
        }
        Bukkit.getScheduler().runTask(pl, () -> SanctionApplier.apply(pl, p));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        SanctionApplier.apply(pl, p);
        Bukkit.getScheduler().runTask(pl, () -> guard(p));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        SanctionApplier.apply(pl, p);
        guard(p);
    }

    /** Un joueur banni qui se retrouve dans le monde hardcore est renvoyé au hub avec le message. */
    private void guard(Player p) {
        if (!p.isOnline() || !pl.isHardcoreWorld(p.getWorld()) || p.hasPermission("hardcore.bypass")) return;
        Long until = pl.data().activeBan(p.getUniqueId());
        if (until == null) return;
        p.sendMessage(Gui.banMessage("Tu es banni du monde hardcore.", until));
        pl.hub().sendToHub(p);
    }

    /** Saisie du pseudo dans le chat apres clic sur l'onglet Sanctions */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!pl.awaiting().remove(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (msg.equalsIgnoreCase("annuler")) {
                p.sendMessage(Gui.MM.deserialize("<gray>Annulé."));
                return;
            }
            Player online = Bukkit.getPlayerExact(msg);
            OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (op == null || (!op.isOnline() && !op.hasPlayedBefore())) {
                p.sendMessage(Gui.MM.deserialize("<red>Joueur introuvable : " + msg.replace("<", "")));
                return;
            }
            String name = op.getName() != null ? op.getName() : msg;
            Gui.openSanction(pl, p, op.getUniqueId(), name);
        });
    }
}
