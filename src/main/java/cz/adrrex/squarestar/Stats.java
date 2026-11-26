package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Statistiky hráčů (výhry, hry, zabití, XP) + žebříčky (leaderboardy) v lobby. */
public final class Stats {
    private static final String TAG = "ss_top";
    private final SquareStar pl;
    private final File file;
    private final YamlConfiguration y;
    private boolean dirty;
    private final List<TextDisplay> boards = new ArrayList<>();

    public Stats(SquareStar pl) {
        this.pl = pl;
        file = pl.dataFile("squarestar-stats.yml");
        y = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(pl, this::save, 1200, 1200);
        Bukkit.getScheduler().runTaskTimer(pl, this::refresh, 100, 600);
    }

    private String k(Player p, String f) { y.set(p.getUniqueId() + ".name", p.getName()); return p.getUniqueId() + "." + f; }
    public void add(Player p, String field, int n) { String key = k(p, field); y.set(key, y.getInt(key) + n); dirty = true; }
    public void setCoins(Player p, int c) { y.set(k(p, "coins"), c); dirty = true; }
    public void setXp(Player p, int xp) { y.set(k(p, "xp"), xp); dirty = true; }
    public void setStr(Player p, String field, String v) { y.set(k(p, field), v); dirty = true; }
    public void setInt(Player p, String field, int v) { y.set(k(p, field), v); dirty = true; }
    /** všechna uložená data (pro web) – volat jen z hlavního vlákna */
    public YamlConfiguration raw() { return y; }
    public int get(Player p, String field) { return y.getInt(p.getUniqueId() + "." + field); }

    public void save() {
        if (!dirty) return;
        dirty = false;
        try { y.save(file); } catch (IOException e) { pl.getLogger().warning("Statistiky nelze ulozit: " + e.getMessage()); }
    }

    /** seřazený top seznam: [jméno, hodnota] */
    public List<Map.Entry<String, Integer>> top(String field, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>();
        for (String id : y.getKeys(false)) {
            int v = field.equals("level") ? Levels.levelOf(y.getInt(id + ".xp")) : y.getInt(id + "." + field);
            if (v > 0) l.add(Map.entry(y.getString(id + ".name", "?"), v));
        }
        l.sort((a, b) -> b.getValue() - a.getValue());
        return l.subList(0, Math.min(n, l.size()));
    }

    private static final String[][] BOARDS = {
            {"wins", "&6&l♛ NEJVÍC VÝHER"}, {"level", "&b&l★ NEJVYŠŠÍ LEVEL"}, {"kills", "&c&l⚔ NEJVÍC ZABITÍ"}, {"games", "&a&l✪ NEJVÍC HER"}};
    // jedna pevná tabule (neotáčí se za hráčem) uprostřed jižní části kopule, čelem ke spawnu
    private static final double CX = 0.5, CZ = -14.5;   // sever, za řadou NPC minimaher, čelem ke spawnu (jih)
    private int cur;
    private BlockDisplayHolder panel;
    private static final class BlockDisplayHolder { org.bukkit.entity.BlockDisplay d; }

    private TextDisplay text(Location l, float scale, TextDisplay.TextAlignment al, int lineWidth) {
        return pl.mars.spawn(l, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.FIXED); d.setRotation(0f, 0f);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setAlignment(al);
            d.setLineWidth(lineWidth);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.5f);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            d.addScoreboardTag(TAG);
            d.setPersistent(false);
        });
    }

    public void spawnBoards() {
        for (Entity e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        boards.clear();
        double cy = Lobby.Y + 8.5, face = 0;   // yaw 0 = čelem k jihu (ke spawnu)
        // panel (zasklená tabule)
        panel = new BlockDisplayHolder();
        panel.d = pl.mars.spawn(new Location(pl.mars, CX, cy, CZ - 0.08, (float) face, 0), org.bukkit.entity.BlockDisplay.class, d -> {
            d.setRotation(0f, 0f); d.setViewRange(2.5f);
            d.setBlock(org.bukkit.Material.BLACK_CONCRETE.createBlockData());
            d.setPersistent(false); d.addScoreboardTag(TAG);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-4.1f, -2.9f, -0.05f), new org.joml.AxisAngle4f(), new org.joml.Vector3f(8.2f, 5.4f, 0.1f), new org.joml.AxisAngle4f()));
        });
        float sc = 1.2f;
        // pozn.: čelem k severu je pravá strana diváka = -X
        TextDisplay title = text(new Location(pl.mars, CX, cy + 1.95, CZ + 0.05, (float) face, 0), 1.5f, TextDisplay.TextAlignment.CENTER, 300);
        TextDisplay names = text(new Location(pl.mars, CX - 1.2, cy - 0.35, CZ + 0.05, (float) face, 0), sc, TextDisplay.TextAlignment.LEFT, 400);
        TextDisplay vals = text(new Location(pl.mars, CX + 3.0, cy - 0.35, CZ + 0.05, (float) face, 0), sc, TextDisplay.TextAlignment.RIGHT, 200);
        TextDisplay timer = text(new Location(pl.mars, CX + 2.8, cy - 2.68, CZ + 0.05, (float) face, 0), 0.9f, TextDisplay.TextAlignment.CENTER, 200);
        boards.add(title); boards.add(names); boards.add(vals); boards.add(timer);
        for (TextDisplay d : boards) Holo.dual(d, "");
        refresh();
        // tenká lišta dole: plní se a když se vyplní, tabule přepne na další kategorii
        var track = pl.mars.spawn(new Location(pl.mars, CX, cy - 2.3, CZ + 0.06, 0f, 0f), org.bukkit.entity.BlockDisplay.class, d -> {
            d.setBlock(org.bukkit.Material.GRAY_CONCRETE.createBlockData()); d.setPersistent(false); d.addScoreboardTag(TAG);
            d.setBrightness(new Display.Brightness(15, 15)); d.setViewRange(2.5f);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-3.8f, 0f, 0f), new org.joml.AxisAngle4f(), new org.joml.Vector3f(7.6f, 0.07f, 0.03f), new org.joml.AxisAngle4f()));
        });
        fill = pl.mars.spawn(new Location(pl.mars, CX, cy - 2.3, CZ + 0.09, 0f, 0f), org.bukkit.entity.BlockDisplay.class, d -> {
            d.setBlock(org.bukkit.Material.WHITE_CONCRETE.createBlockData()); d.setPersistent(false); d.addScoreboardTag(TAG);
            d.setBrightness(new Display.Brightness(15, 15)); d.setViewRange(2.5f);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-3.8f, 0f, 0f), new org.joml.AxisAngle4f(), new org.joml.Vector3f(0.001f, 0.07f, 0.03f), new org.joml.AxisAngle4f()));
        });
        cyc = 0; Holo.setText(timer, "&7⏱ &f" + CYCLE / 20 + " s");
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            cyc += 4; phase++;
            if (boards.size() >= 4 && boards.get(0).isValid()) {
                if (step < rows.size() + 5) { step++; renderRows(); } else if (CYCLE - cyc <= 16) renderRows();
                if (cyc % 8 == 0) renderTitle();
            }
            if (boards.size() >= 4 && boards.get(3).isValid() && cyc % 20 == 0) Holo.setText(boards.get(3), "&7⏱ &f" + Math.max(1, (CYCLE - cyc) / 20) + " s");
            if (cyc >= CYCLE) { cyc = 0; cur = (cur + 1) % BOARDS.length; refresh(); }
            if (fill != null && fill.isValid()) {
                float w = Math.max(0.001f, 7.6f * cyc / CYCLE);
                fill.setInterpolationDelay(0); fill.setInterpolationDuration(4);
                fill.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-3.8f, 0f, 0f), new org.joml.AxisAngle4f(), new org.joml.Vector3f(w, 0.07f, 0.03f), new org.joml.AxisAngle4f()));
            }
        }, 4, 4);
    }
    private org.bukkit.scheduler.BukkitTask task;
    private org.bukkit.entity.BlockDisplay fill;
    private int cyc;
    private static final int CYCLE = 300;   // 15 s

    private List<Map.Entry<String, Integer>> rows = List.of();
    private int step;       // krok animace fade-in (po řádcích)
    private int phase;      // posun záblesku v nadpisu

    public void refresh() {
        if (boards.size() < 4) return;
        for (TextDisplay d : boards) if (!d.isValid()) { spawnBoards(); return; }
        rows = top(BOARDS[cur][0], 10);
        step = 0;
        renderTitle();
        renderRows();
    }

    /** nadpis: název kategorie + „ŽEBŘÍČEK“ s běžícím světelným zábleskem */
    private void renderTitle() {
        String[] cat = BOARDS[cur];
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < BOARDS.length; i++) ind.append(i == cur ? "&f● " : "&8● ");
        String word = "ŽEBŘÍČEK", sub;
        StringBuilder sb = new StringBuilder();
        int n = word.length(), pos = phase % (n + 6) - 3;
        for (int i = 0; i < n; i++) {
            int d = Math.abs(i - pos);
            sb.append(d == 0 ? "&f&l" : d == 1 ? "&e" : "&7").append(word.charAt(i));
        }
        sub = sb.toString();
        Holo.setText(boards.get(0), cat[1] + "\n" + sub + " &8| " + ind.toString().trim());
    }

    private static int lerp(int from, int to, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) (((from >> 16) & 255) * (1 - f) + ((to >> 16) & 255) * f), g = (int) (((from >> 8) & 255) * (1 - f) + ((to >> 8) & 255) * f), bl = (int) ((from & 255) * (1 - f) + (to & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }
    private static final int DARK = 0x161616;

    /** jména, hlavy i čísla se plynule „vyjasní“ z tmavé (fade-in, jeden řádek po druhém) a před přepnutím zase zhasnou (fade-out) */
    private void renderRows() {
        Component names = Component.empty(), vals = Component.empty();
        int[] medal = {0xFFAA00, 0xFFFFFF, 0xFF5555};
        var t = rows;
        double out = Math.min(1.0, Math.max(0.0, (CYCLE - cyc) / 16.0));   // posledních 16 ticků zhasíná
        if (t.isEmpty()) { names = U.c("&7Zatím nikdo\n&7buď první!"); vals = U.c(" "); }
        for (int j = 0; j < t.size(); j++) {
            double f = Math.min(1.0, Math.max(0.0, (step - j) / 4.0)) * out;
            String name = t.get(j).getKey();
            Component rank = Component.text("#" + (j + 1), TextColor.color(lerp(DARK, j < 3 ? medal[j] : 0xAAAAAA, f))).decoration(TextDecoration.BOLD, j < 3);
            Component head = pl.skins.head(name, null).color(TextColor.color(lerp(DARK, 0xFFFFFF, f)));
            Component nm = Component.text(" " + name, TextColor.color(lerp(DARK, j == 0 ? 0xFFAA00 : 0xFFFFFF, f))).decoration(TextDecoration.BOLD, j == 0);
            names = names.append(Component.empty().append(rank).append(Component.text(" ")).append(head).append(nm));
            vals = vals.append(Component.text(String.valueOf(t.get(j).getValue()), TextColor.color(lerp(DARK, 0xFFFF55, f))));
            if (j < t.size() - 1) { names = names.append(Component.newline()); vals = vals.append(Component.newline()); }
        }
        Holo.setComponents(boards.get(1), names, names);
        Holo.setComponents(boards.get(2), vals, vals);
    }

    /** /ss top – osobní statistiky v chatu */
    public void show(Player p) {
        U.msg(p, "&6&lTvoje statistiky:");
        ChatFree.send(p, U.c(Lang.t(p, "&7Výhry: &a" + get(p, "wins") + " &8| &7Hry: &e" + get(p, "games") + " &8| &7Zabití: &c" + get(p, "kills") + " &8| &7Level: &b" + pl.levels.level(p))));
    }
}
