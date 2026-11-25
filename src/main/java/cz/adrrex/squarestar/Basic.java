package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Běžné serverové příkazy: msg/r, ping, pravidla, discord, afk, online, report, helpop, staff chat, vanish, gm, bc, clearchat, heal, tphere. */
public final class Basic implements CommandExecutor, TabCompleter, Listener {
    private final SquareStar pl;
    private final Map<UUID, UUID> lastMsg = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();
    public static final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Long> reportCd = new HashMap<>();

    public Basic(SquareStar pl) { this.pl = pl; }

    public static final String[] PLAYER_CMDS = {"msg", "r", "ping", "pravidla", "discord", "afk", "online", "report", "helpop", "stats", "kosmetika", "jazyk", "hry", "emoji"};
    public static final String[] STAFF_CMDS = {"sc", "vanish", "gm", "bc", "clearchat", "heal", "tphere"};

    private boolean staff(CommandSender s) { return !(s instanceof Player p) || p.isOp() || pl.ranks.of(p).admin || p.hasPermission("squarestar.helper"); }
    private boolean admin(CommandSender s) { return !(s instanceof Player p) || p.isOp() || p.hasPermission("squarestar.admin"); }
    private void staffMsg(String m) { for (Player o : Bukkit.getOnlinePlayers()) if (staff(o)) o.sendMessage(U.c(m)); Bukkit.getConsoleSender().sendMessage(U.c(m)); }
    private static String join(String[] a, int from) { return String.join(" ", Arrays.copyOfRange(a, from, a.length)); }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        Player p = s instanceof Player pp ? pp : null;
        String n = c.getName();
        if (Arrays.asList(STAFF_CMDS).contains(n) && !staff(s)) { s.sendMessage(U.tc(s, "&cTenhle příkaz je jen pro tým serveru.")); return true; }
        switch (n) {
            case "msg" -> {
                if (a.length < 2) { s.sendMessage(U.tc(s, "&e/msg <hráč> <zpráva>")); return true; }
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null || (vanished.contains(t.getUniqueId()) && !staff(s))) { s.sendMessage(U.tc(s, "&cHráč není online.")); return true; }
                pm(s, t, join(a, 1));
            }
            case "r" -> {
                if (p == null) return true;
                Player t = lastMsg.containsKey(p.getUniqueId()) ? Bukkit.getPlayer(lastMsg.get(p.getUniqueId())) : null;
                if (t == null) { U.msg(p, "&cNemáš komu odpovědět."); return true; }
                if (a.length == 0) { U.msg(p, "&e/r <zpráva>"); return true; }
                pm(p, t, join(a, 0));
            }
            case "ping" -> {
                Player t = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : p;
                if (t == null) return true;
                int ms = t.getPing();
                s.sendMessage(U.tc(s, "&7Ping " + (t == p ? "" : t.getName() + " ") + ": " + (ms < 80 ? "&a" : ms < 180 ? "&e" : "&c") + ms + " ms"));
            }
            case "pravidla" -> {
                for (String r : pl.getConfig().getStringList("rules").isEmpty() ? List.of(
                        "&6&l✦ PRAVIDLA SQUARESTAR ✦",
                        "&e1. &fBuď slušný – žádné nadávky, urážky ani spam.",
                        "&e2. &fŽádné cheaty, hacky, makra ani X-ray.",
                        "&e3. &fNezneužívej chyby (bugy) – nahlas je přes &e/report&f.",
                        "&e4. &fŽádná reklama na jiné servery.",
                        "&e5. &fNesdílej osobní údaje (adresa, telefon...).",
                        "&e6. &fPoslouchej tým serveru.",
                        "&7Porušení = varování, kick nebo ban.") : pl.getConfig().getStringList("rules"))
                    s.sendMessage(U.tc(s, p == null ? r : Lang.t(p, r)));
            }
            case "discord" -> {
                String d = pl.getConfig().getString("links.discord", "");
                s.sendMessage(U.tc(s, d.isEmpty() ? "&7Discord zatím nemáme – sleduj &bvidriftvideo.netlify.app" : "&9&lDiscord: &f" + d)
                        .clickEvent(d.isEmpty() ? null : net.kyori.adventure.text.event.ClickEvent.openUrl(d)));
            }
            case "afk" -> { if (p != null) setAfk(p, !afk.contains(p.getUniqueId())); }
            case "online" -> {
                List<String> names = new ArrayList<>();
                for (Player o : Bukkit.getOnlinePlayers()) if (!vanished.contains(o.getUniqueId()) || staff(s))
                    names.add(pl.ranks.of(o).color + o.getName() + (afk.contains(o.getUniqueId()) ? " &7(AFK)" : ""));
                s.sendMessage(U.tc(s, "&6&lOnline (" + names.size() + "): &r" + String.join("&7, ", names)));
            }
            case "report" -> {
                if (p == null || a.length < 2) { s.sendMessage(U.tc(s, "&e/report <hráč> <důvod>")); return true; }
                long now = System.currentTimeMillis();
                if (now - reportCd.getOrDefault(p.getUniqueId(), 0L) < 60_000) { U.msg(p, "&cDalší report můžeš poslat za minutu."); return true; }
                reportCd.put(p.getUniqueId(), now);
                staffMsg("&c&l⚑ REPORT &f" + p.getName() + " &7nahlásil &c" + a[0] + "&7: &f" + join(a, 1));
                for (Player o : Bukkit.getOnlinePlayers()) if (staff(o)) o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1, 1);
                U.msg(p, "&aDíky! Report jsme poslali týmu serveru.");
            }
            case "helpop" -> {
                if (p == null || a.length == 0) { s.sendMessage(U.tc(s, "&e/helpop <otázka>")); return true; }
                staffMsg("&b&l? HELPOP &f" + p.getName() + "&7: &f" + join(a, 0));
                U.msg(p, "&aZpráva odeslána týmu. Brzy ti někdo odpoví.");
            }
            case "stats" -> { if (p != null) pl.stats.show(p); }
            case "kosmetika" -> { if (p != null && pl.games.of(p) == null) pl.cosmetics.open(p); }
            case "emoji" -> s.sendMessage(U.c(Emojis.list()));
            case "jazyk" -> { if (p != null) pl.intro.openLang(p); }
            case "hry" -> { if (p != null) pl.games.openMenu(p); }
            // ---------- tým ----------
            case "sc" -> {
                if (a.length == 0) { s.sendMessage(U.tc(s, "&e/sc <zpráva> &7– chat jen pro tým")); return true; }
                staffMsg("&d&l[TÝM] &f" + s.getName() + "&7: &d" + join(a, 0));
            }
            case "vanish" -> {
                if (p == null) return true;
                boolean on = vanished.add(p.getUniqueId());
                if (!on) vanished.remove(p.getUniqueId());
                for (Player o : Bukkit.getOnlinePlayers()) if (o != p && !staff(o)) { if (on) o.hidePlayer(pl, p); else o.showPlayer(pl, p); }
                U.msg(p, on ? "&a&lNeviditelný &7– hráči tě nevidí." : "&7Jsi zase viditelný.");
            }
            case "gm" -> {
                if (!admin(s) || p == null) { s.sendMessage(U.tc(s, "&cJen pro admina.")); return true; }
                GameMode m = switch (a.length == 0 ? "" : a[0]) { case "1", "c" -> GameMode.CREATIVE; case "2", "a" -> GameMode.ADVENTURE; case "3", "sp" -> GameMode.SPECTATOR; default -> GameMode.SURVIVAL; };
                p.setGameMode(m); U.msg(p, "&aRežim: &e" + m.name().toLowerCase(Locale.ROOT));
            }
            case "bc" -> {
                if (!admin(s)) { s.sendMessage(U.tc(s, "&cJen pro admina.")); return true; }
                if (a.length == 0) { s.sendMessage(U.tc(s, "&e/bc <zpráva>")); return true; }
                String m = join(a, 0);
                for (Player o : Bukkit.getOnlinePlayers()) {
                    o.sendMessage(U.c("")); o.sendMessage(U.tc(o, "&c&l⚑ OZNÁMENÍ &8» &f" + m)); o.sendMessage(U.c(""));
                    o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.2f);
                }
            }
            case "clearchat" -> {
                for (Player o : Bukkit.getOnlinePlayers()) { if (!staff(o)) for (int i = 0; i < 100; i++) o.sendMessage(U.c("")); }
                U.all("&7Chat vyčistil &e" + s.getName());
            }
            case "heal" -> {
                Player t = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : p;
                if (!admin(s) || t == null) return true;
                var at = t.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                t.setHealth(at == null ? 20 : at.getValue()); t.setFoodLevel(20); t.setFireTicks(0);
                U.msg(t, "&aJsi vyléčený ❤");
            }
            case "tphere" -> {
                Player t = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : null;
                if (p == null || t == null) { s.sendMessage(U.tc(s, "&e/tphere <hráč>")); return true; }
                t.teleport(p); U.msg(p, "&a" + t.getName() + " teleportován k tobě.");
            }
        }
        return true;
    }

    private void pm(CommandSender from, Player to, String msg) {
        String fn = from.getName();
        from.sendMessage(U.tc(from, "&d&l✉ &7Ty &8→ &e" + to.getName() + "&8: &f" + msg));
        to.sendMessage(U.tc(to, "&d&l✉ &e" + fn + " &8→ &7Ty&8: &f" + msg));
        to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
        if (from instanceof Player f) { lastMsg.put(to.getUniqueId(), f.getUniqueId()); lastMsg.put(f.getUniqueId(), to.getUniqueId()); }
        if (afk.contains(to.getUniqueId())) from.sendMessage(U.tc(from, "&7(" + to.getName() + " je AFK)"));
    }

    private void setAfk(Player p, boolean on) {
        if (on == afk.contains(p.getUniqueId())) return;
        if (on) afk.add(p.getUniqueId()); else afk.remove(p.getUniqueId());
        U.all(on ? "&7" + p.getName() + " je teď &eAFK" : "&7" + p.getName() + " už není AFK");
    }
    @EventHandler public void move(PlayerMoveEvent e) {
        if (afk.contains(e.getPlayer().getUniqueId()) && e.hasChangedBlock()) setAfk(e.getPlayer(), false);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { afk.remove(e.getPlayer().getUniqueId()); vanished.remove(e.getPlayer().getUniqueId()); lastMsg.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent e) {
        if (staff(e.getPlayer())) return;
        for (UUID u : vanished) { Player v = Bukkit.getPlayer(u); if (v != null) e.getPlayer().hidePlayer(pl, v); }
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (c.getName().equals("gm") && a.length == 1) return List.of("0", "1", "2", "3");
        if (a.length == 1 && List.of("msg", "ping", "report", "heal", "tphere").contains(c.getName()))
            return Bukkit.getOnlinePlayers().stream().filter(o -> !vanished.contains(o.getUniqueId()) || staff(s)).map(Player::getName)
                    .filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
