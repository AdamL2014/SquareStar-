package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * /event – majitel (OWNER) nebo admin uspořádá event: vybere minihru, max. počet hráčů a gravitaci,
 * pak ho oznámí všem (kliknutelná zpráva), hráči se připojí přes /event join a majitel ho spustí.
 */
public final class EventManager implements CommandExecutor, TabCompleter, Listener {
    private final SquareStar pl;
    private GameType type = GameType.PVP;
    private int max = 10;
    private boolean lowGravity = true;
    private Game game;
    private String host;

    public EventManager(SquareStar pl) { this.pl = pl; }

    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    private boolean allowed(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        var r = pl.ranks.of(p);
        return p.isOp() || r == Ranks.Rank.OWNER || r == Ranks.Rank.ADMIN || r == Ranks.Rank.DEVELOPER;
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("join") || sub.equals("pripojit")) {
            if (!(s instanceof Player p)) return true;
            if (game == null || !game.joinable()) { U.msg(p, "&cTeď neběží žádný event, ke kterému se dá připojit."); return true; }
            if (!pl.games.joinEvent(p, game)) U.msg(p, "&cNejdřív opusť hru: &e/ss leave");
            else U.msg(p, "&d&l★ &aJsi v eventu! &7Počkej, až ho " + host + " spustí.");
            return true;
        }
        if (!allowed(s)) { s.sendMessage(U.c("&cEventy může pořádat jen majitel nebo admin. &7Připojit se: &e/event join")); return true; }
        switch (sub) {
            case "", "menu" -> { if (s instanceof Player p) open(p); else s.sendMessage(U.c("&7/event start|stop|join")); }
            case "start" -> start(s);
            case "stop" -> stop(s);
            default -> s.sendMessage(U.c("&7/event &8- menu, &7/event start&8, &7/event stop&8, &7/event join"));
        }
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (a.length != 1) return List.of();
        List<String> o = allowed(s) ? List.of("join", "start", "stop", "menu") : List.of("join");
        return o.stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
    }

    // ---------------- menu ----------------
    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Gui.title(54, Lang.t(p, "&f&l★ Pořádání eventu")));
        GameType[] t = GameType.values();
        for (int i = 0; i < t.length && i < 27; i++) {
            ItemStack it = Gui.skin(p, U.item(t[i].icon, 1, (t[i] == type ? "&a&l✔ " : "&e") + t[i].display, "&7" + t[i].desc, "", t[i] == type ? "&aVybráno" : "&eKlikni pro výběr"), "icon_" + t[i].name().toLowerCase(Locale.ROOT));
            if (t[i] == type) { var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(i, U.tag(it, "ss_ev", "type:" + t[i].name()));
        }
        h.inv.setItem(37, U.tag(U.item(Material.RED_CONCRETE, 1, "&c&l- 1 hráč", "&7Shift = -5"), "ss_ev", "minus"));
        h.inv.setItem(38, U.item(Material.PLAYER_HEAD, Math.max(1, Math.min(64, max)), "&f&lMax. hráčů: &e" + max, "&7Kolik lidí se může připojit"));
        h.inv.setItem(39, U.tag(U.item(Material.LIME_CONCRETE, 1, "&a&l+ 1 hráč", "&7Shift = +5"), "ss_ev", "plus"));
        h.inv.setItem(41, U.tag(U.item(lowGravity ? Material.FEATHER : Material.ANVIL, 1, "&b&lGravitace: " + (lowGravity ? "&aNízká (Mars)" : "&cNormální (Země)"), "&7Klikni pro přepnutí"), "ss_ev", "gravity"));
        boolean running = game != null;
        h.inv.setItem(43, U.tag(U.item(running ? Material.ORANGE_CONCRETE : Material.BELL, 1, running ? "&6&l▶ SPUSTIT EVENT" : "&d&l✦ OZNÁMIT EVENT",
                running ? "&7Přihlášeno: &e" + game.players.size() + "&7/" + max : "&7Pošle všem pozvánku", running ? "&7Klikni = start hry" : "&7Hráči se připojí přes &e/event join"), "ss_ev", running ? "start" : "announce"));
        if (running) h.inv.setItem(49, U.tag(U.item(Material.BARRIER, 1, "&c&lZrušit event"), "ss_ev", "stop"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_ev");
        if (t == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        if (t.startsWith("type:")) { if (game == null) { type = GameType.valueOf(t.substring(5)); max = Math.min(max, 50); } else U.msg(p, "&cEvent už běží – nejdřív ho zruš."); }
        else switch (t) {
            case "minus" -> max = Math.max(1, max - (e.isShiftClick() ? 5 : 1));
            case "plus" -> max = Math.min(100, max + (e.isShiftClick() ? 5 : 1));
            case "gravity" -> lowGravity = !lowGravity;
            case "announce" -> announce(p);
            case "start" -> { p.closeInventory(); start(p); return; }
            case "stop" -> { p.closeInventory(); stop(p); return; }
        }
        open(p);
    }

    private void announce(Player p) {
        if (game != null) return;
        game = pl.games.createEvent(type, max, lowGravity ? null : 0.08);
        if (game == null) { U.msg(p, "&cVšechny arény jsou plné, zkus to za chvíli."); return; }
        host = p.getName();
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.sendMessage(U.c(""));
            o.sendMessage(U.c(Lang.t(o, "&d&l★ EVENT ★ &f" + host + " &7pořádá event: &6&l" + type.display)));
            o.sendMessage(U.c(Lang.t(o, "&7Max. hráčů: &e" + max + " &8| &7Gravitace: " + (lowGravity ? "&anízká" : "&cnormální"))));
            o.sendMessage(U.c(Lang.t(o, "&a&l[ KLIKNI A PŘIPOJ SE ]")).clickEvent(ClickEvent.runCommand("/event join"))
                    .hoverEvent(HoverEvent.showText(U.c("&e/event join"))));
            o.sendMessage(U.c(""));
            U.title(o, "&d&l★ EVENT ★", "&6" + type.display + " &7– napiš &e/event join");
            o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
        }
        if (!pl.games.joinEvent(p, game)) U.msg(p, "&7(Ty jsi zůstal mimo – hraješ jinou hru.)");
    }

    private void start(CommandSender s) {
        if (game == null) { s.sendMessage(U.c("&cŽádný event. Otevři menu: &e/event")); return; }
        if (game.players.size() < game.type.min) { s.sendMessage(U.c("&cMálo hráčů: &e" + game.players.size() + "&c (min. " + game.type.min + ")")); return; }
        pl.games.startEvent(game);
        U.all("&d&l★ EVENT &6" + game.type.display + " &dzačíná! &7(" + game.players.size() + " hráčů)");
        game = null;
    }

    private void stop(CommandSender s) {
        if (game == null) { s.sendMessage(U.c("&7Žádný event neběží.")); return; }
        for (Player p : new ArrayList<>(game.players)) pl.games.leave(p);
        game = null;
        U.all("&c&l★ Event byl zrušen.");
    }
}
