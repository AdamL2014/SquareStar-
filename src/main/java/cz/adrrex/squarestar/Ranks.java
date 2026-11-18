package cz.adrrex.squarestar;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Ranky s pixel-art odznaky (resource pack font). Bez packu se ukáže barevný text. */
public final class Ranks implements Listener {
    public enum Rank {
        OWNER("&4&l", true), ADMIN("&c&l", true), MODERATOR("&e&l", false), HELPER("&a&l", false), BUILDER("&6&l", false),
        YOUTUBER("&c&l", false), STREAMER("&5&l", false), VIDRIFTER("&b&l", false), LEGENDA("&d&l", false), MVP("&b&l", false), VIP("&a&l", false), HRAC("&7", false);
        public final String color; public final boolean admin;
        Rank(String c, boolean a) { color = c; admin = a; }
        public char glyph() { return (char) (0xE000 + ordinal()); }
    }

    private static final Key FONT = Key.key("squarestar", "ranks");
    private final SquareStar pl;
    private final File file;
    private final YamlConfiguration data;
    private final Set<UUID> packed = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, PermissionAttachment> perms = new HashMap<>();

    public Ranks(SquareStar pl) {
        this.pl = pl;
        file = new File(pl.getDataFolder(), "ranks.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    public Rank of(Player p) {
        String r = data.getString("players." + p.getUniqueId());
        if (r == null) return Rank.HRAC;
        try { return Rank.valueOf(r); } catch (IllegalArgumentException e) { return Rank.HRAC; }
    }

    public void set(Player p, Rank r) {
        data.set("players." + p.getUniqueId(), r == Rank.HRAC ? null : r.name());
        data.set("names." + p.getUniqueId(), p.getName());
        try { data.save(file); } catch (IOException e) { pl.getLogger().warning("Nelze ulozit ranks.yml: " + e.getMessage()); }
        apply(p);
    }

    private Component textBadge(Rank r) {
        if (r == Rank.HRAC) return Component.empty();
        if (r == Rank.VIDRIFTER) return link(U.c("&8[&b&lVID&d&lRIFTER&8]")).append(Component.text(" "));
        return U.c("&8[" + r.color + r.name() + "&8] ");
    }
    private Component glyphBadge(Rank r) {
        if (r == Rank.HRAC) return Component.empty();
        Component g = Component.text(String.valueOf(r.glyph())).font(FONT).color(NamedTextColor.WHITE);
        return (r == Rank.VIDRIFTER ? link(g) : g).append(Component.text(" "));
    }
    /** VidRifter odznak je klikací odkaz na VidRift */
    private Component link(Component c) {
        String url = pl.getConfig().getString("ranks.vidrift-url", "https://vidriftvideo.netlify.app");
        return c.clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(U.c("&b&lVid&d&lRift\n&7Česká video platforma\n&eKlikni: &f" + url)));
    }
    private static String nameColor(Rank r) { return r == Rank.HRAC ? "&7" : r.color.replace("&l", ""); }

    public void apply(Player p) {
        Rank r = of(p);
        p.playerListName(textBadge(r).append(U.c(nameColor(r) + p.getName())));
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String tn = String.format("ss_r%02d", r.ordinal());
        Team t = sb.getTeam(tn);
        if (t == null) {
            t = sb.registerNewTeam(tn);
            t.prefix(textBadge(r));
            t.color(r == Rank.HRAC ? NamedTextColor.GRAY : NamedTextColor.WHITE);
        }
        if (!t.hasEntry(p.getName())) t.addEntry(p.getName());
        PermissionAttachment old = perms.remove(p.getUniqueId());
        if (old != null) { try { p.removeAttachment(old); } catch (IllegalArgumentException ignored) {} }
        if (r.admin) { PermissionAttachment a = p.addAttachment(pl); a.setPermission("squarestar.admin", true); perms.put(p.getUniqueId(), a); }
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        apply(e.getPlayer());
        Rank r = of(e.getPlayer());
        if (r != Rank.HRAC) e.joinMessage(U.c(r.color + r.name() + " " + nameColor(r) + e.getPlayer().getName() + " &7přiletěl na Mars!"));
    }

    @EventHandler public void quit(PlayerQuitEvent e) { packed.remove(e.getPlayer().getUniqueId()); perms.remove(e.getPlayer().getUniqueId()); }

    @EventHandler public void pack(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) packed.add(e.getPlayer().getUniqueId());
        else if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD || e.getStatus() == PlayerResourcePackStatusEvent.Status.DISCARDED) packed.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH) public void chat(AsyncChatEvent e) {
        Rank r = of(e.getPlayer());
        String name = e.getPlayer().getName();
        e.renderer((source, displayName, message, viewer) -> {
            boolean glyph = viewer instanceof Player v && packed.contains(v.getUniqueId());
            return (glyph ? glyphBadge(r) : textBadge(r)).append(U.c(nameColor(r) + name + "&8: &f")).append(message);
        });
    }
}
