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

    /** jeden záběr: klíčové body kamery (pozice + kam se kouká), délka v tikách a titulek (cs/en) */
    private record Shot(double[][] pos, double[][] look, int ticks, String cs, String en, String title, String sub) {}

    private List<Shot> shots() {
        double y = Lobby.Y;
        List<Shot> l = new ArrayList<>();
        // 1) kosmický příjezd k planetě
        l.add(new Shot(new double[][]{{0, y + 240, -200}, {0, y + 160, -125}, {10, y + 100, -72}, {16, y + 70, -45}},
                new double[][]{{0, y + 40, 20}, {0, y + 40, 20}, {0, y + 30, 10}, {0, y + 20, 5}}, 120,
                "Rok 2087. Lidstvo přistálo na Marsu…", "Year 2087. Humanity has landed on Mars…", "&6&lSQUARE&c&lSTAR", "&7Minihry na Marsu"));
        // 2) oblet kopule po kruhu (po cestě proletí UFO)
        double[][] op = new double[7][], ol = new double[7][];
        for (int i = 0; i < 7; i++) { double a2 = Math.toRadians(200 + i * 22); op[i] = new double[]{Math.cos(a2) * 64, y + 34 + i * 1.5, Math.sin(a2) * 64}; ol[i] = new double[]{0, y + 10, 0}; }
        l.add(new Shot(op, ol, 140, "Rudá planeta. Nízká gravitace.", "The red planet. Low gravity.", "", ""));
        // 3) nízký přelet kaňonem
        l.add(new Shot(new double[][]{{-90, y + 9, -62}, {-58, y + 6, -50}, {-30, y + 9, -38}, {-8, y + 5, -34}},
                new double[][]{{-60, y + 6, -52}, {-32, y + 7, -40}, {-8, y + 6, -26}, {0, y + 6, -18}}, 110,
                "Tady se nezávodí o čas. Závodí se o slávu.", "Here you don't race the clock. You race for glory.", "", ""));
        // 4) průjezd vchodem do kopule
        l.add(new Shot(new double[][]{{0.5, y + 2.4, -66}, {0.5, y + 2.4, -34}, {0.5, y + 2.6, -13}, {0.5, y + 4, -2}},
                new double[][]{{0.5, y + 3, 10}, {0.5, y + 3, 10}, {0.5, y + 3.5, 14}, {2, y + 6, 14}}, 120,
                "Vítej v kopuli SquareStar.", "Welcome to the SquareStar dome.", "", ""));
        // 5) jeřábový záběr okolo stromu a středu
        l.add(new Shot(new double[][]{{0.5, y + 4, -3}, {-12, y + 9, 6}, {-3, y + 14, 17}, {15, y + 11, 13}, {13, y + 4, 0}},
                new double[][]{{6, y + 5, 5}, {6, y + 6, 5}, {8, y + 6, 5}, {4, y + 6, 4}, {0.5, y + 3, 0}}, 120,
                "Vyber si hru a ukaž, co v tobě je!", "Pick a game and show what you've got!", "", ""));
        return l;
    }

    /** je hráč právě v úvodní animaci? (hudba lobby mezitím mlčí) */
    public boolean active(Player p) { return running.containsKey(p.getUniqueId()); }

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
        List<Shot> shots = shots();
        boolean en = Lang.en(p);
        p.stopSound(SoundCategory.RECORDS);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        p.playSound(p, Sound.MUSIC_DISC_OTHERSIDE, SoundCategory.RECORDS, 0.8f, 1f);   // „filmová“ hudba na pozadí
        BukkitRunnable r = new BukkitRunnable() {
            int t = 0, shot = 0, st = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); running.remove(p.getUniqueId()); return; }
                if (shot >= shots.size()) { finish(p, before); return; }
                Shot s = shots.get(shot);
                if (st >= s.ticks) { shot++; st = 0; return; }
                if (st == 0) {
                    if (!s.title.isEmpty()) U.title(p, s.title, s.sub);
                    if (shot == 1 && pl.ufo != null) pl.ufo.fly();          // UFO přeletí nad kopulí
                    if (shot == 2) p.playSound(p, Sound.ENTITY_PHANTOM_FLAP, SoundCategory.AMBIENT, 0.6f, 0.5f);
                }
                // střih do černé mezi záběry
                if (st == s.ticks - 8 && shot < shots.size() - 1) p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 16, 0, false, false, false));
                int n = s.pos.length - 1;
                double f = st / (double) s.ticks;
                f = f * f * (3 - 2 * f) * 0.35 + f * 0.65;            // jemné rozjetí a dojetí kamery, jinak plynulý pohyb
                double u = f * n; int seg = Math.min(n - 1, (int) u); double lf = u - seg;
                Vector pos = spline(s.pos, seg, lf), look = spline(s.look, seg, lf);
                Location loc = pos.toLocation(pl.mars);
                loc.setDirection(look.clone().subtract(pos));
                p.teleport(loc);
                // titulky: psací stroj
                String line = en ? s.en : s.cs;
                int chars = Math.min(line.length(), Math.max(0, (st - 14) * 2 / 3));
                if (chars > 0 && st < s.ticks - 10) U.bar(p, "&f&o" + line.substring(0, chars) + (chars < line.length() ? "&7▌" : ""));
                if (t % 20 == 0) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.7f + (shot * 0.1f));
                if (t == 4) U.bar(p, en ? "&7Skip: &eShift" : "&7Přeskočit: &eShift");
                t++; st++;
            }
        };
        running.put(p.getUniqueId(), r);
        r.runTaskTimer(pl, 0, 1);
    }

    private static Vector spline(double[][] k, int seg, double f) {
        double[] a = k[Math.max(0, seg - 1)], b = k[seg], c = k[seg + 1], d = k[Math.min(k.length - 1, seg + 2)];
        return new Vector(cr(a[0], b[0], c[0], d[0], f), cr(a[1], b[1], c[1], d[1], f), cr(a[2], b[2], c[2], d[2], f));
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private void finish(Player p, GameMode before) {
        stop(p);
        p.setGameMode(before);
        p.stopSound(SoundCategory.RECORDS);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
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
