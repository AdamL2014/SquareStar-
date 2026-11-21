package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Úvodní animace (kamera přeletí Mars) + výběr jazyka při prvním připojení. */
public final class Intro implements Listener {
    private final SquareStar pl;
    private final Map<UUID, BukkitRunnable> running = new HashMap<>();
    public Intro(SquareStar pl) { this.pl = pl; }

    public static final class LangHolder implements InventoryHolder {
        Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; }
    }

    // klíčové body kamery: pozice + kam se kouká
    private double[][] path() {
        double y = Lobby.Y;
        return new double[][]{
                {0, y + 140, -70,   0.5, y + 85, 30.5},
                {70, y + 95, -20,   0.5, y + 85, 30.5},
                {55, y + 40, 5,     0.5, y + 26, 44.5},
                {0, y + 30, 8,      0.5, y + 26, 44.5},
                {-25, y + 14, -5,   0, y + 4, 10},
                {0.5, y + 2.6, -4,  0.5, y + 1.6, 12},
        };
    }

    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // počkej, až se hráč načte a lobby ho teleportuje
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || pl.games.of(p) != null) return;
            if (pl.getConfig().getBoolean("lobby.intro", true)) play(p); else afterIntro(p);
        }, 30);
    }

    public void play(Player p) {
        stop(p);
        GameMode before = p.getGameMode() == GameMode.SPECTATOR ? GameMode.ADVENTURE : p.getGameMode();
        p.setGameMode(GameMode.SPECTATOR);
        double[][] k = path();
        int perSeg = 40, total = perSeg * (k.length - 1);
        U.title(p, "&6&lSQUARE&c&lSTAR", "&7Minihry na Marsu");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        BukkitRunnable r = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); running.remove(p.getUniqueId()); return; }
                if (t >= total) { finish(p, before); return; }
                int seg = t / perSeg; double f = (t % perSeg) / (double) perSeg;
                f = f * f * (3 - 2 * f); // plynulé zrychlení/zpomalení
                double[] a = k[Math.max(0, seg - 1)], b = k[seg], c = k[seg + 1], d = k[Math.min(k.length - 1, seg + 2)];
                Vector pos = new Vector(cr(a[0], b[0], c[0], d[0], f), cr(a[1], b[1], c[1], d[1], f), cr(a[2], b[2], c[2], d[2], f));
                Vector look = new Vector(b[3] + (c[3] - b[3]) * f, b[4] + (c[4] - b[4]) * f, b[5] + (c[5] - b[5]) * f);
                Location l = pos.toLocation(pl.mars);
                l.setDirection(look.subtract(pos));
                p.teleport(l);
                if (t == perSeg * 2) U.title(p, "&b&lNízká gravitace", "&7všude na Marsu");
                if (t == perSeg * 3) U.title(p, "&e&l18 miniher", "&7Bitva o postele, Války asteroidů, Stavitelská bitva a další");
                if (t % 20 == 0) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f + t / 400f);
                if (t == 2) U.bar(p, "&7Přeskočit: &eShift");
                t++;
            }
        };
        running.put(p.getUniqueId(), r);
        r.runTaskTimer(pl, 0, 1);
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private void finish(Player p, GameMode before) {
        stop(p);
        p.setGameMode(before);
        p.teleport(pl.mars.getSpawnLocation().add(0.5, 0, 0.5));
        pl.lobby.giveItems(p);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
        afterIntro(p);
    }
    private void stop(Player p) { BukkitRunnable r = running.remove(p.getUniqueId()); if (r != null) try { r.cancel(); } catch (IllegalStateException ignored) {} }

    private void afterIntro(Player p) {
        if (!Lang.chosen(p)) openLang(p);
        else U.title(p, "&6&lVítej na Marsu!", "&f" + p.getName());
    }

    public void openLang(Player p) {
        LangHolder h = new LangHolder();
        h.inv = Bukkit.createInventory(h, 9, Gui.title(9, "&f&lJazyk / Language"));
        h.inv.setItem(2, U.tag(Gui.icon("flag_cz", "&f&lČeština", "&7Klikni pro češtinu"), "ss_lang", "cs"));
        h.inv.setItem(6, U.tag(Gui.icon("flag_en", "&f&lEnglish", "&7Click for English"), "ss_lang", "en"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof LangHolder)) return;
        e.setCancelled(true);
        String l = U.tagOf(e.getCurrentItem(), "ss_lang");
        if (l == null || !(e.getWhoClicked() instanceof Player p)) return;
        Lang.set(p, l);
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
        if (pl.games.of(p) == null) pl.lobby.giveItems(p);
        U.title(p, l.equals("en") ? "&6&lWelcome to Mars!" : "&6&lVítej na Marsu!", "&f" + p.getName());
        U.msg(p, l.equals("en") ? "&aLanguage: &fEnglish &7(change: &e/ss lang&7)" : "&aJazyk: &fčeština &7(změna: &e/ss jazyk&7)");
    }

    @EventHandler public void sneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && running.containsKey(e.getPlayer().getUniqueId())) {
            Player p = e.getPlayer();
            stop(p);
            finish(p, GameMode.ADVENTURE);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        if (running.containsKey(e.getPlayer().getUniqueId())) {
            stop(e.getPlayer());
            e.getPlayer().setGameMode(GameMode.ADVENTURE);
            e.getPlayer().teleport(pl.mars.getSpawnLocation());
        }
    }
}
