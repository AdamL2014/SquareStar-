package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.GameType;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class Commands implements CommandExecutor, TabCompleter {
    private final SquareStar pl;
    public Commands(SquareStar pl) { this.pl = pl; }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        Player p = s instanceof Player pp ? pp : null;
        switch (sub) {
            case "hry", "menu" -> { if (p != null) pl.games.openMenu(p); }
            case "join" -> {
                if (p == null || a.length < 2) { s.sendMessage(U.tc(s, "&c/ss join <" + String.join("|", names()) + ">")); return true; }
                try { pl.games.join(p, GameType.valueOf(a[1].toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException ex) { pl.getLogger().log(java.util.logging.Level.WARNING, "join " + a[1], ex); U.msg(p, "&cNeznámá hra. Možnosti: &e" + String.join(", ", names())); }
            }
            case "leave" -> { if (p != null) pl.games.leave(p); }
            case "lobby", "spawn" -> { if (p != null) { if (pl.games.of(p) != null) pl.games.leave(p); else pl.lobby.send(p); } }
            case "jazyk", "lang", "language" -> { if (p != null) pl.intro.openLang(p); }
            case "udrzba", "maintenance" -> {
                if (!s.hasPermission("squarestar.admin")) { s.sendMessage(U.tc(s, "&cNa tohle nemáš práva.")); return true; }
                boolean on = a.length > 1 ? a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("zap") : !pl.motd.maintenance();
                pl.motd.setMaintenance(on);
                s.sendMessage(U.tc(s, on ? "&c&lÚdržba ZAPNUTA &7- připojí se jen tým." : "&a&lÚdržba VYPNUTA &7- server je otevřený."));
            }
            case "redstone", "rs" -> {
                if (p == null) return true;
                if (a.length > 1 && (a[1].equalsIgnoreCase("hlasovat") || a[1].equalsIgnoreCase("vote"))) pl.redstone.visit(p, 0);
                else if (a.length > 2 && (a[1].equalsIgnoreCase("pridat") || a[1].equalsIgnoreCase("add"))) pl.redstone.trust(p, a[2], true);
                else if (a.length > 2 && (a[1].equalsIgnoreCase("odebrat") || a[1].equalsIgnoreCase("remove"))) pl.redstone.trust(p, a[2], false);
                else if (a.length > 2 && (a[1].equalsIgnoreCase("navstivit") || a[1].equalsIgnoreCase("visit"))) pl.redstone.visitPlayer(p, a[2]);
                else pl.redstone.join(p);
            }
            case "top", "stats", "statistiky" -> { if (p != null) pl.stats.show(p); }
            case "novinky", "news" -> { if (p != null) pl.news.open(p); }
            case "pratele", "friends" -> { if (p != null) pl.friends.open(p); }
            case "update", "aktualizace" -> {
                if (!admin(s)) return true;
                s.sendMessage(U.tc(s, "&7Kontroluji GitHub..."));
                Bukkit.getScheduler().runTaskAsynchronously(pl, () -> pl.updater.check(s));
            }
            case "meteor" -> { if (admin(s)) pl.meteors.launch(); }
            case "intro" -> { if (p != null && pl.games.of(p) == null) pl.intro.play(p); }
            case "give" -> {
                if (!admin(s)) return true;
                if (a.length < 2) { s.sendMessage(U.tc(s, "&c/ss give <item> [hráč] [počet] &7Itemy: " + String.join(", ", pl.items.defs.keySet()))); return true; }
                Player t = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : p;
                int n = a.length >= 4 ? parse(a[3]) : 1;
                ItemStack it = pl.items.get(a[1], n);
                if (t == null || it == null) { s.sendMessage(U.tc(s, "&cŠpatný hráč nebo item.")); return true; }
                t.getInventory().addItem(it);
                s.sendMessage(U.tc(s, "&aDáno: " + a[1] + " x" + n + " -> " + t.getName()));
            }
            case "key" -> {
                if (!admin(s)) return true;
                if (a.length < 3) { s.sendMessage(U.tc(s, "&c/ss key <hráč> <" + String.join("|", pl.crates.types.keySet()) + "> [počet]")); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                ItemStack k = pl.crates.key(a[2], a.length >= 4 ? parse(a[3]) : 1);
                if (t == null || k == null) { s.sendMessage(U.tc(s, "&cŠpatný hráč nebo typ bedny.")); return true; }
                t.getInventory().addItem(k);
                s.sendMessage(U.tc(s, "&aKlíč dán hráči " + t.getName()));
            }
            case "rebuildlobby", "rebuild" -> {
                if (!admin(s)) return true;
                s.sendMessage(U.tc(s, "&ePřestavuji Mars lobby, nápis, raketu a NPC..."));
                pl.lobby.build(() -> { pl.crates.placeBlocks(); pl.lobby.spawnHolo(); pl.fx.spawnAll(); s.sendMessage(U.tc(s, "&aLobby přestavěna.")); });
            }
            case "fx" -> {
                if (!admin(s)) return true;
                pl.fx.spawnAll();
                s.sendMessage(U.tc(s, "&a3D efekty (nápis, raketa, NPC) znovu vytvořeny."));
            }
            case "lag" -> {
                double[] tps = Bukkit.getTPS();
                double mspt = Bukkit.getAverageTickTime();
                int ent = 0; for (var w : Bukkit.getWorlds()) ent += w.getEntityCount();
                long used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576, max = Runtime.getRuntime().maxMemory() / 1048576;
                s.sendMessage(U.tc(s, String.format("&6TPS: &f%.1f &7(1m) &8| &6MSPT: &f%.1f ms &8| &6RAM: &f%d/%d MB", tps[0], mspt, used, max)));
                s.sendMessage(U.tc(s, "&6Entity: &f" + ent + " &8| &6Hry: &f" + pl.games.activeGames() + " &8| &7(20 TPS a pod 50 ms = server je OK)"));
            }
            case "rank" -> {
                if (!admin(s)) return true;
                if (a.length < 3) { s.sendMessage(U.tc(s, "&c/ss rank <hráč> <" + String.join("|", Arrays.stream(Ranks.Rank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)).toList()) + ">")); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(U.tc(s, "&cHráč není online.")); return true; }
                try { Ranks.Rank r = Ranks.Rank.valueOf(a[2].toUpperCase(Locale.ROOT));
                    // OWNER smí dávat/brát jen jiný OWNER nebo konzole (admin ne)
                    boolean owner = !(s instanceof Player sp) || pl.ranks.of(sp) == Ranks.Rank.OWNER;
                    if (!owner && (r == Ranks.Rank.OWNER || pl.ranks.of(t) == Ranks.Rank.OWNER)) { s.sendMessage(U.tc(s, "&cRank OWNER může dávat nebo brát jen majitel serveru.")); return true; }
                    if (!owner && (r == Ranks.Rank.ADMIN || r == Ranks.Rank.DEVELOPER) && pl.ranks.of((Player) s) != Ranks.Rank.OWNER && pl.ranks.of((Player) s) != Ranks.Rank.ADMIN) { s.sendMessage(U.tc(s, "&cNa tento rank nemáš práva.")); return true; }
                    pl.ranks.set(t, r); s.sendMessage(U.tc(s, "&a" + t.getName() + " má rank " + r.color + r.name())); }
                catch (IllegalArgumentException ex) { s.sendMessage(U.tc(s, "&cNeznámý rank.")); }
            }
            default -> {
                s.sendMessage(U.tc(s, "&c&l✦ SquareStar &7- příkazy"));
                for (String h : Lobby.help()) s.sendMessage(U.tc(s, h));
                if (s.hasPermission("squarestar.admin")) {
                    s.sendMessage(U.tc(s, "&c/ss give <item> [hráč] [počet] &7- vesmírné itemy"));
                    s.sendMessage(U.tc(s, "&c/ss key <hráč> <typ> [počet] &7- klíče k bednám"));
                    s.sendMessage(U.tc(s, "&c/ss rebuild &7- znovu postaví Mars lobby + 3D efekty"));
                    s.sendMessage(U.tc(s, "&c/ss fx &7- obnoví jen 3D nápis, raketu a NPC"));
                    s.sendMessage(U.tc(s, "&c/ss rank <hráč> <rank> &7- nastaví rank (owner, admin...)"));
                    s.sendMessage(U.tc(s, "&c/ss lag &7- výkon serveru (TPS, MSPT, RAM)"));
                    s.sendMessage(U.tc(s, "&c/ss update &7- zkontroluje a nainstaluje aktualizaci"));
                    s.sendMessage(U.tc(s, "&c/event &7- uspořádat event"));
                }
            }
        }
        return true;
    }

    private static int parse(String s) { try { return Math.max(1, Math.min(64, Integer.parseInt(s))); } catch (NumberFormatException e) { return 1; } }
    private static boolean admin(CommandSender s) {
        if (s.hasPermission("squarestar.admin")) return true;
        s.sendMessage(U.tc(s, "&cNa tohle nemáš práva.")); return false;
    }
    private static List<String> names() { return Arrays.stream(GameType.values()).map(g -> g.name().toLowerCase(Locale.ROOT)).toList(); }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("hry", "join", "leave", "lobby", "lag", "top", "novinky", "pratele", "redstone", "jazyk"));
            if (s.hasPermission("squarestar.admin")) out.addAll(List.of("give", "key", "rebuild", "fx", "rank", "update"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("join")) out.addAll(names());
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) out.addAll(pl.items.defs.keySet());
        else if ((a.length == 3 && a[0].equalsIgnoreCase("give")) || (a.length == 2 && a[0].equalsIgnoreCase("key")))
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (a.length == 3 && a[0].equalsIgnoreCase("key")) out.addAll(pl.crates.types.keySet());
        else if (a.length == 2 && a[0].equalsIgnoreCase("redstone")) out.addAll(List.of("hlasovat", "pridat", "odebrat", "navstivit"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("redstone")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (a.length == 2 && a[0].equalsIgnoreCase("rank")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (a.length == 3 && a[0].equalsIgnoreCase("rank")) for (Ranks.Rank r : Ranks.Rank.values()) out.add(r.name().toLowerCase(Locale.ROOT));
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
