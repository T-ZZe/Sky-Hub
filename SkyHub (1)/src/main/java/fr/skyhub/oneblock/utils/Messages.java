package fr.skyhub.oneblock.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Messages MiniMessage configurables (messages.yml). Les placeholders s'écrivent &lt;nom&gt; et se passent par paires clé/valeur. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private volatile FileConfiguration cfg;

    public void reload(FileConfiguration config) { this.cfg = config; }

    private static TagResolver[] resolvers(String prefix, String... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("placeholders: nombre impair d'arguments");
        List<TagResolver> list = new ArrayList<>();
        list.add(Placeholder.parsed("prefix", prefix));
        for (int i = 0; i < kv.length; i += 2) list.add(Placeholder.unparsed(kv[i], kv[i + 1]));
        return list.toArray(new TagResolver[0]);
    }

    public String raw(String key) {
        String s = cfg == null ? null : cfg.getString(key);
        return s == null ? "<red>Message manquant: " + key : s;
    }

    public Component get(String key, String... kv) {
        String prefix = cfg == null ? "" : cfg.getString("prefix", "");
        return MM.deserialize(raw(key), resolvers(prefix, kv));
    }

    public List<Component> list(String key, String... kv) {
        String prefix = cfg == null ? "" : cfg.getString("prefix", "");
        List<String> lines = cfg == null ? List.of() : cfg.getStringList(key);
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(MM.deserialize(l, resolvers(prefix, kv)));
        return out;
    }

    public void send(CommandSender to, String key, String... kv) { to.sendMessage(get(key, kv)); }

    /** Convertit un texte MiniMessage arbitraire (couleur de phase, etc.). */
    public static Component mm(String text) { return MM.deserialize(text); }
}
