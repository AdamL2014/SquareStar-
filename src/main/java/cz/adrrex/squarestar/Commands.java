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
                if (p == null || a.length < 2) { s.sendMessage(U.c("&c/ss join <" + String.join("|", names()) + ">")); return true; }
                try { pl.games.join(p, GameType.valueOf(a[1].toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException ex) { U.msg(p, "&cNeznámá hra. Možnosti: &e" + String.join(", ", names())); }
            }
            case "leave" -> { if (p != null) pl.games.leave(p); }
            case "lobby", "spawn" -> { if (p != null) { if (pl.games.of(p) != null) pl.games.leave(p); else pl.lobby.send(p); } }
            case "give" -> {
                if (!admin(s)) return true;
                if (a.length < 2) { s.sendMessage(U.c("&c/ss give <item> [hráč] [počet] &7Itemy: " + String.join(", ", pl.items.defs.keySet()))); return true; }
                Player t = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : p;
                int n = a.length >= 4 ? parse(a[3]) : 1;
                ItemStack it = pl.items.get(a[1], n);
                if (t == null || it == null) { s.sendMessage(U.c("&cŠpatný hráč nebo item.")); return true; }
                t.getInventory().addItem(it);
                s.sendMessage(U.c("&aDáno: " + a[1] + " x" + n + " -> " + t.getName()));
            }
            case "key" -> {
                if (!admin(s)) return true;
                if (a.length < 3) { s.sendMessage(U.c("&c/ss key <hráč> <" + String.join("|", pl.crates.types.keySet()) + "> [počet]")); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                ItemStack k = pl.crates.key(a[2], a.length >= 4 ? parse(a[3]) : 1);
                if (t == null || k == null) { s.sendMessage(U.c("&cŠpatný hráč nebo typ bedny.")); return true; }
                t.getInventory().addItem(k);
                s.sendMessage(U.c("&aKlíč dán hráči " + t.getName()));
            }
            case "rebuildlobby" -> {
                if (!admin(s)) return true;
                pl.lobby.build(() -> { pl.crates.placeBlocks(); pl.lobby.spawnHolo(); s.sendMessage(U.c("&aLobby přestavěna.")); });
            }
            default -> {
                s.sendMessage(U.c("&c&l✦ SquareStar &7- příkazy"));
                for (String h : Lobby.help()) s.sendMessage(U.c(h));
                if (s.hasPermission("squarestar.admin")) {
                    s.sendMessage(U.c("&c/ss give <item> [hráč] [počet] &7- vesmírné itemy"));
                    s.sendMessage(U.c("&c/ss key <hráč> <typ> [počet] &7- klíče k bednám"));
                    s.sendMessage(U.c("&c/ss rebuildlobby &7- znovu postaví Mars lobby"));
                }
            }
        }
        return true;
    }

    private static int parse(String s) { try { return Math.max(1, Math.min(64, Integer.parseInt(s))); } catch (NumberFormatException e) { return 1; } }
    private static boolean admin(CommandSender s) {
        if (s.hasPermission("squarestar.admin")) return true;
        s.sendMessage(U.c("&cNa tohle nemáš práva.")); return false;
    }
    private static List<String> names() { return Arrays.stream(GameType.values()).map(g -> g.name().toLowerCase(Locale.ROOT)).toList(); }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("hry", "join", "leave", "lobby"));
            if (s.hasPermission("squarestar.admin")) out.addAll(List.of("give", "key", "rebuildlobby"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("join")) out.addAll(names());
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) out.addAll(pl.items.defs.keySet());
        else if ((a.length == 3 && a[0].equalsIgnoreCase("give")) || (a.length == 2 && a[0].equalsIgnoreCase("key")))
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (a.length == 3 && a[0].equalsIgnoreCase("key")) out.addAll(pl.crates.types.keySet());
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
