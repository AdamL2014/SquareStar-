package cz.adrrex.squarestar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Hráči nevidí ani nespustí „serverové“ příkazy (/plugins, /version, /bukkit:..., /paper...).
 * Vidí jen příkazy serveru SquareStar. Majitel (OWNER / op) vidí všechno.
 */
public final class CmdGuard implements Listener {
    private final SquareStar pl;
    public CmdGuard(SquareStar pl) { this.pl = pl; }

    /** příkazy, které hráči smí vidět a psát */
    private static final Set<String> PLAYER = Set.of("ss", "squarestar", "hry", "games", "play", "hrat", "spawn", "lobby", "hub", "l", "emote", "e", "emoty",
            "pritel", "friend", "friends", "f", "pratele", "event", "fly", "letat", "novinky", "news", "changelog", "msg", "tell", "w", "m", "whisper", "zprava",
            "r", "reply", "odpoved", "ping", "pravidla", "rules", "discord", "dc", "afk", "online", "players", "hraci", "report", "nahlasit", "helpop", "pomoc",
            "stats", "statistiky", "top", "kosmetika", "cosmetics", "emoji", "jazyk", "lang", "language", "help", "napoveda", "list", "hudba", "music", "mince", "coins", "obchod", "shop", "penize", "advent", "kalendar", "vanoce", "ai", "adrrexai", "ask", "ptej",
            "profil", "profile", "odmena", "daily", "ukoly", "quests", "uspechy", "achievements", "parta", "party", "pc");
    private static final Set<String> SECRET = Set.of("plugins", "pl", "version", "ver", "about", "icanhasbukkit", "?", "paper", "spigot", "bukkit", "timings", "spark", "geyser", "floodgate", "viaversion", "viaver", "vvbukkit", "viabackwards", "grim", "grimac");
    /** zakázané pro úplně všechny včetně majitele (příkaz hostingu, který hráče vyhodí z SquareStaru) */
    private static final Set<String> ALL_BLOCKED = Set.of("mk");

    private boolean owner(Player p) { return p.isOp() || pl.ranks.of(p) == Ranks.Rank.OWNER; }
    private boolean staff(Player p) { return owner(p) || pl.ranks.of(p).admin || p.hasPermission("squarestar.helper"); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void cmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        String base = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        if (ALL_BLOCKED.contains(base)) { e.setCancelled(true); U.msg(p, "&fZpět do lobby se vrátíš příkazem &e/spawn&f."); return; }   // i pro majitele
        if (owner(p)) return;
        if (!label.contains(":") && (base.equals("help") || base.equals("napoveda") || base.equals("?"))) { e.setCancelled(true); help(p); return; }
        boolean blocked = label.contains(":") || SECRET.contains(base) || (!staff(p) && !PLAYER.contains(base));
        if (!blocked) return;
        e.setCancelled(true);
        if (SECRET.contains(base) || label.contains(":"))
            U.msg(p, "&fTento server běží na &6&lvlastních pluginech &fna míru od &bAdrrexStudio&f ✦ &7(SquareStar)");
        else U.msg(p, "&cTenhle příkaz neexistuje. &7Nápověda: &e/ss");
    }

    /**
     * Hlášky „Sending you to… / You are now connected… / run the command /mk“ posílá proxy hostingu (ne tento plugin), takže je nejde vypnout odsud.
     * Po připojení proto chat na chvíli „odsuneme“ prázdnými řádky, ať hráče nerušejí.
     */
    @EventHandler public void joinClear(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) for (int i = 0; i < 30; i++) ChatFree.send(p, Component.empty()); }, 25);
    }

    /** vlastní nápověda místo výpisu všech příkazů serveru */
    public static void help(Player p) {
        String[] l = {"&6&l✦ SQUARESTAR – PŘÍKAZY ✦",
                "&e/hry &7– menu miniher  &8| &e/ss znovu &7– hrát znovu", "&e/profil &7– profil, &e/odmena &7– denní odměna, &e/ukoly &7– denní úkoly, &e/uspechy",
                "&e/parta <hráč> &7– hrát s kamarády  &8| &e/pc &7– chat party", "&e/spawn &7nebo &e/lobby &7– zpět na Mars", "&e/ss leave &7– opustit hru",
                "&e/pritel &7– přátelé  &8| &e/msg &7a &e/r &7– soukromé zprávy",
                "&e/kosmetika &7– stopy, čepice, balónky  &8| &e/emote &7– emoty",
                "&e/stats &7– statistiky  &8| &e/novinky &7– co je nové",
                "&e/event join &7– připojit se k eventu  &8| &e/fly &7– létání (VIP+)",
                "&e/obchod &7– obchod za mince  &8| &e/advent &7– adventní kalendář",
                "&e/hudba &7– hudba zap/vyp  &8| &e/ping &8| &e/online &8| &e/afk &8| &e/pravidla &8| &e/discord &8| &e/jazyk",
                "&e/report <hráč> <důvod> &7– nahlásit  &8| &e/helpop &7– zeptat se týmu"};
        for (String s : l) ChatFree.send(p, U.c(Lang.t(p, s)));
    }

    /** tab-doplňování: hráč uvidí jen svoje příkazy */
    @EventHandler public void tab(PlayerCommandSendEvent e) {
        Player p = e.getPlayer();
        e.getCommands().removeIf(c -> ALL_BLOCKED.contains(c.toLowerCase(Locale.ROOT)));
        if (owner(p)) return;
        boolean staff = staff(p);
        e.getCommands().removeIf(c -> {
            String b = c.toLowerCase(Locale.ROOT);
            return b.contains(":") || SECRET.contains(b) || (!staff && !PLAYER.contains(b));
        });
    }
}
