package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Parta (party): kamarádi hrají spolu. Vůdce vybere hru a celá parta jde s ním do stejné hry.
 * /parta <hráč> | prijmout | odejit | vyhodit <hráč> | rozpustit | chat <zpráva>   (+ /pc <zpráva>)
 */
public final class Party implements CommandExecutor, TabCompleter, Listener {
    public static final int MAX = 8;
    private final SquareStar pl;
    public Party(SquareStar pl) { this.pl = pl; }

    public static final class P { public UUID leader; public final LinkedHashSet<UUID> members = new LinkedHashSet<>(); }
    private final Map<UUID, P> of = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();      // pozvaný -> vůdce
    private final Map<UUID, Long> inviteTime = new HashMap<>();

    public P of(Player p) { return of.get(p.getUniqueId()); }
    public boolean isLeader(Player p) { P x = of(p); return x != null && x.leader.equals(p.getUniqueId()); }
    /** online členové party kromě vůdce */
    public List<Player> followers(Player leader) {
        P x = of(leader);
        if (x == null || !x.leader.equals(leader.getUniqueId())) return List.of();
        List<Player> l = new ArrayList<>();
        for (UUID u : x.members) { if (u.equals(x.leader)) continue; Player m = Bukkit.getPlayer(u); if (m != null) l.add(m); }
        return l;
    }

    private void tell(P x, String s) { for (UUID u : x.members) { Player m = Bukkit.getPlayer(u); if (m != null) U.msg(m, "&9&lPARTA &8» &7" + s); } }

    private void invite(Player p, Player t) {
        if (t == p) { U.msg(p, "&cSám sebe pozvat nemůžeš."); return; }
        P x = of(p);
        if (x != null && !x.leader.equals(p.getUniqueId())) { U.msg(p, "&cZvát může jen vůdce party."); return; }
        if (of(t) != null) { U.msg(p, "&c" + t.getName() + " už je v jiné partě."); return; }
        if (x != null && x.members.size() >= MAX) { U.msg(p, "&cParta je plná (max " + MAX + ")."); return; }
        if (x == null) { x = new P(); x.leader = p.getUniqueId(); x.members.add(p.getUniqueId()); of.put(p.getUniqueId(), x); }
        invites.put(t.getUniqueId(), p.getUniqueId());
        inviteTime.put(t.getUniqueId(), System.currentTimeMillis());
        U.msg(p, "&aPozvánka poslána hráči &e" + t.getName() + "&a. &7(platí 60 s)");
        t.sendMessage(U.c(U.PREFIX + Lang.t(t, "&e" + p.getName() + " &7tě zve do party! "))
                .append(U.c(Lang.t(t, "&a&l[PŘIJMOUT]")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/parta prijmout"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(U.c(Lang.t(t, "&eKlikni a přidej se"))))));
        U.sound(t, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
    }

    private void accept(Player p) {
        UUID lid = invites.remove(p.getUniqueId());
        Long at = inviteTime.remove(p.getUniqueId());
        if (lid == null || at == null || System.currentTimeMillis() - at > 60_000) { U.msg(p, "&cNemáš žádnou platnou pozvánku."); return; }
        P x = of.get(lid);
        if (x == null || !x.leader.equals(lid)) { U.msg(p, "&cTa parta už neexistuje."); return; }
        if (of(p) != null) { U.msg(p, "&cUž jsi v partě. &7/parta odejit"); return; }
        if (x.members.size() >= MAX) { U.msg(p, "&cParta je plná."); return; }
        x.members.add(p.getUniqueId());
        of.put(p.getUniqueId(), x);
        tell(x, "&a" + p.getName() + " &7se přidal do party! &8(" + x.members.size() + "/" + MAX + ")");
    }

    public void leave(Player p, boolean quiet) {
        P x = of.remove(p.getUniqueId());
        if (x == null) { if (!quiet) U.msg(p, "&7Nejsi v žádné partě."); return; }
        x.members.remove(p.getUniqueId());
        if (!quiet) U.msg(p, "&7Opustil jsi partu.");
        if (x.members.size() <= 1) { disband(x, "&7Parta se rozpadla – zůstal v ní jen jeden hráč."); return; }
        if (x.leader.equals(p.getUniqueId())) {
            x.leader = x.members.iterator().next();
            Player nl = Bukkit.getPlayer(x.leader);
            tell(x, "&e" + p.getName() + " &7odešel. Nový vůdce: &6" + (nl == null ? "?" : nl.getName()));
        } else tell(x, "&e" + p.getName() + " &7odešel z party.");
    }

    private void disband(P x, String why) {
        tell(x, why);
        for (UUID u : x.members) of.remove(u);
        of.remove(x.leader);
        x.members.clear();
    }

    private void info(Player p) {
        P x = of(p);
        if (x == null) {
            U.msg(p, "&9&lParta &7– hrajte spolu! Vůdce vybere hru a všichni jdou s ním.");
            U.msg(p, "&e/parta <hráč> &7pozvat  &8| &e/parta prijmout");
            U.msg(p, "&e/parta odejit &8| &e/parta vyhodit <hráč> &8| &e/parta rozpustit &8| &e/pc <zpráva>");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID u : x.members) {
            Player m = Bukkit.getPlayer(u);
            String n = m == null ? Bukkit.getOfflinePlayer(u).getName() : m.getName();
            names.add((u.equals(x.leader) ? "&6♛ " : "&f") + n + (m == null ? " &8(offline)" : ""));
        }
        U.msg(p, "&9&lTvoje parta &8(" + x.members.size() + "/" + MAX + "): " + String.join("&7, ", names));
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        invites.remove(e.getPlayer().getUniqueId());
        if (of(e.getPlayer()) != null) leave(e.getPlayer(), true);
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Jen pro hrace."); return true; }
        if (c.getName().equals("pc")) { chat(p, String.join(" ", a)); return true; }
        if (a.length == 0) { info(p); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "prijmout", "accept" -> accept(p);
            case "odejit", "leave" -> leave(p, false);
            case "rozpustit", "disband" -> { if (!isLeader(p)) U.msg(p, "&cRozpustit může jen vůdce."); else disband(of(p), "&c" + p.getName() + " rozpustil partu."); }
            case "vyhodit", "kick" -> {
                if (!isLeader(p) || a.length < 2) { U.msg(p, "&c/parta vyhodit <hráč> &7(jen vůdce)"); return true; }
                P x = of(p);
                UUID t = null;
                for (UUID u : x.members) if (a[1].equalsIgnoreCase(Bukkit.getOfflinePlayer(u).getName())) t = u;
                if (t == null || t.equals(p.getUniqueId())) { U.msg(p, "&cTen hráč v partě není."); return true; }
                x.members.remove(t); of.remove(t);
                Player tp = Bukkit.getPlayer(t);
                if (tp != null) U.msg(tp, "&cByl jsi vyhozen z party.");
                tell(x, "&e" + a[1] + " &7byl vyhozen.");
                if (x.members.size() <= 1) disband(x, "&7Parta se rozpadla.");
            }
            case "chat" -> chat(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            case "info", "list" -> info(p);
            default -> {
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) U.msg(p, "&cHráč &e" + a[0] + " &cnení online.");
                else invite(p, t);
            }
        }
        return true;
    }

    private void chat(Player p, String m) {
        P x = of(p);
        if (x == null) { U.msg(p, "&cNejsi v partě."); return; }
        if (m.isBlank()) { U.msg(p, "&c/pc <zpráva>"); return; }
        String clean = m.replace("&", "").replace("§", "");
        for (UUID u : x.members) { Player t = Bukkit.getPlayer(u); if (t != null) t.sendMessage(U.c("&9&lPARTA &8» &f" + p.getName() + "&7: &f").append(net.kyori.adventure.text.Component.text(clean))); }
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (c.getName().equals("pc") || a.length != 1) return List.of();
        List<String> out = new ArrayList<>(List.of("prijmout", "odejit", "vyhodit", "rozpustit", "chat", "info"));
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
    }
}
