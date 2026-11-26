package cz.adrrex.squarestar;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Režim bez chatu: hráč, který má v Minecraftu chat skrytý (Nastavení → Chat → Chat: Skrytý),
 * nevidí žádné zprávy. Proto se každá zpráva pluginu navíc ukáže v liště nad hotbarem (po řadě),
 * a všechny funkce jdou ovládat přes itemy a menu (Profil → Party, Emoty, Herna, Event, Přátelé, Jazyk…).
 */
public final class ChatFree implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<UUID, ArrayDeque<Component>> Q = new HashMap<>();
    private static final Set<UUID> told = new HashSet<>();
    private static boolean started;
    private static final boolean DEBUG = false;

    public static boolean hidden(Player p) {
        try { return p.getClientOption(ClientOption.CHAT_VISIBILITY) == ClientOption.ChatVisibility.HIDDEN; }
        catch (Throwable t) { return false; }
    }

    /** místo p.sendMessage(...): pošle do chatu a když je chat skrytý, i do lišty */
    public static void send(CommandSender s, Component c) {
        s.sendMessage(c);
        if (s instanceof Player p) { if (DEBUG) Bukkit.getLogger().info("[CF] " + p.getName() + " opt=" + p.getClientOption(ClientOption.CHAT_VISIBILITY)); if (hidden(p)) push(p, c); }
    }
    public static void send(CommandSender s, String legacy) { send(s, U.c(legacy)); }

    private static void push(Player p, Component c) {
        String t = PLAIN.serialize(c).replace('\n', ' ').trim();
        if (t.isEmpty() || t.replaceAll("[\\s\\-=_━═─│|•·*]+", "").isEmpty()) return;
        ArrayDeque<Component> q = Q.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        if (!q.isEmpty() && PLAIN.serialize(q.peekLast()).equals(t)) return;
        q.add(Component.text(t.length() > 120 ? t.substring(0, 117) + "…" : t, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        while (q.size() > 6) q.poll();
    }

    public static void start(SquareStar pl) {
        if (started) return;
        started = true;
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (var it = Q.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null || e.getValue().isEmpty()) { if (p == null) it.remove(); continue; }
                p.sendActionBar(e.getValue().poll());
            }
        }, 40, 50);   // každé 2,5 s další zpráva
    }

    @EventHandler public void options(PlayerClientOptionsChangeEvent e) {
        if (!e.hasChatVisibilityChanged()) return;
        Player p = e.getPlayer();
        if (e.getChatVisibility() == ClientOption.ChatVisibility.HIDDEN && told.add(p.getUniqueId())) {
            boolean en = Lang.en(p);
            U.title(p, en ? "&e&lChat is hidden" : "&e&lChat je skrytý", en ? "&7Everything works from items and menus – open your Profile" : "&7Vše funguje přes itemy a menu – otevři Profil");
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { Q.remove(e.getPlayer().getUniqueId()); told.remove(e.getPlayer().getUniqueId()); }
}
