package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

/** Sidebar vpravo v lobby: rank, level, mince, denní úkoly, odměna, parta, kolik lidí hraje. */
public final class LobbyBoard implements Listener {
    private final SquareStar pl;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private int tick;

    public LobbyBoard(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::update, 60, 40);
    }

    private void update() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean lobby = p.getWorld() == pl.mars && pl.games.of(p) == null && !pl.intro.active(p);
            Scoreboard sb = boards.get(p.getUniqueId());
            if (!lobby) {
                if (sb != null) { boards.remove(p.getUniqueId()); if (p.getScoreboard() == sb) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); }
                continue;
            }
            // jiná část pluginu (hra) má vlastní tabuli -> nesahat
            if (sb != null && p.getScoreboard() != sb && p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) continue;
            boolean fresh = sb == null;
            if (fresh) { sb = Bukkit.getScoreboardManager().getNewScoreboard(); boards.put(p.getUniqueId(), sb); }
            cz.adrrex.squarestar.game.Game.copyRankTeams(sb);   // ranky v tabu a nad hlavou musí sedět i s vlastní tabulí
            if (p.getScoreboard() != sb) p.setScoreboard(sb);
            draw(p, sb);
        }
    }

    private void draw(Player p, Scoreboard sb) {
        var old = sb.getObjective("ssl");
        if (old != null) old.unregister();
        var o = sb.registerNewObjective("ssl", org.bukkit.scoreboard.Criteria.DUMMY, U.c(Lang.t(p, "&6&l✦ &e&lSQUARE&6&lSTAR &6&l✦")));
        o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        List<String> l = new ArrayList<>();
        Ranks.Rank r = pl.ranks.of(p);
        int on = Bukkit.getOnlinePlayers().size();
        boolean en = Lang.en(p);
        String w = en ? (on == 1 ? "player" : "players") : (on == 1 ? "hráč" : (on >= 2 && on <= 4) ? "hráči" : "hráčů");
        l.add("&a● &f&l" + on + " &7" + w + " online");
        l.add("&7" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " &8Lobby");
        l.add(" ");
        l.add("&fRank: " + r.color + cap(r.name()));
        l.add("&fLevel: &b✦" + pl.levels.level(p));
        l.add("&fMince: &6" + pl.coins.get(p) + " " + Coins.ICON);
        l.add("  ");
        if (pl.progress != null) {
            l.add("&fDenní úkoly: &a" + pl.progress.questsDone(p) + "&7/3");
            l.add("&fOdměna: " + (pl.progress.dailyReady(p) ? "&a&lpřipravená! &7/odmena" : "&7zítra"));
            l.add("&fÚspěchy: &e" + pl.progress.achCount(p));
        }
        l.add("   ");
        if (pl.party != null && pl.party.of(p) != null && pl.party.of(p).members.size() > 1) l.add("&fParta: &9" + pl.party.of(p).members.size() + " hráčů");
        l.add("&fVe hrách: &e" + pl.games.activePlayers());
        l.add("    ");
        l.add("&e&lSQUARESTAR &7na Marsu");
        int sc = l.size();
        for (String s : l) o.getScore(Lang.t(p, s).replace('&', '§')).setScore(sc--);
    }

    private static String cap(String s) { return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT); }

    @EventHandler public void quit(PlayerQuitEvent e) { boards.remove(e.getPlayer().getUniqueId()); }
}
