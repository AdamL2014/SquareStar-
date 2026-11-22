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
        file = new File(pl.getDataFolder(), "squarestar-stats.yml");
        y = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(pl, this::save, 1200, 1200);
        Bukkit.getScheduler().runTaskTimer(pl, this::refresh, 100, 600);
    }

    private String k(Player p, String f) { y.set(p.getUniqueId() + ".name", p.getName()); return p.getUniqueId() + "." + f; }
    public void add(Player p, String field, int n) { String key = k(p, field); y.set(key, y.getInt(key) + n); dirty = true; }
    public void setCoins(Player p, int c) { y.set(k(p, "coins"), c); dirty = true; }
    public void setXp(Player p, int xp) { y.set(k(p, "xp"), xp); dirty = true; }
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
    private static final double CX = 0.5, CZ = 16.5;
    private int cur;
    private BlockDisplayHolder panel;
    private static final class BlockDisplayHolder { org.bukkit.entity.BlockDisplay d; }

    private TextDisplay text(Location l, float scale, TextDisplay.TextAlignment al, int lineWidth) {
        return pl.mars.spawn(l, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.FIXED); d.setRotation(180f, 0f);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setAlignment(al);
            d.setLineWidth(lineWidth);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            d.addScoreboardTag(TAG);
            d.setPersistent(false);
        });
    }

    public void spawnBoards() {
        for (Entity e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        boards.clear();
        double cy = Lobby.Y + 5.5, face = 180;   // yaw 180 = čelem k severu (ke spawnu)
        // panel (zasklená tabule)
        panel = new BlockDisplayHolder();
        panel.d = pl.mars.spawn(new Location(pl.mars, CX, cy, CZ + 0.08, (float) face, 0), org.bukkit.entity.BlockDisplay.class, d -> {
            d.setRotation(180f, 0f);
            d.setBlock(org.bukkit.Material.BLUE_STAINED_GLASS.createBlockData());
            d.setPersistent(false); d.addScoreboardTag(TAG);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-4.1f, -2.5f, -0.05f), new org.joml.AxisAngle4f(), new org.joml.Vector3f(8.2f, 5.0f, 0.1f), new org.joml.AxisAngle4f()));
        });
        float sc = 1.2f;
        // pozn.: čelem k severu je pravá strana diváka = -X
        TextDisplay title = text(new Location(pl.mars, CX, cy + 1.95, CZ - 0.05, (float) face, 0), 1.5f, TextDisplay.TextAlignment.CENTER, 300);
        TextDisplay names = text(new Location(pl.mars, CX + 1.2, cy - 0.35, CZ - 0.05, (float) face, 0), sc, TextDisplay.TextAlignment.LEFT, 400);
        TextDisplay vals = text(new Location(pl.mars, CX - 3.0, cy - 0.35, CZ - 0.05, (float) face, 0), sc, TextDisplay.TextAlignment.RIGHT, 200);
        boards.add(title); boards.add(names); boards.add(vals);
        for (TextDisplay d : boards) Holo.dual(d, "");
        refresh();
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(pl, () -> { cur = (cur + 1) % BOARDS.length; refresh(); }, 300, 300);
    }
    private org.bukkit.scheduler.BukkitTask task;

    public void refresh() {
        if (boards.size() < 3) return;
        for (TextDisplay d : boards) if (!d.isValid()) { spawnBoards(); return; }
        String[] cat = BOARDS[cur];
        var t = top(cat[0], 10);
        String dots = "&8" + "●".repeat(BOARDS.length).substring(0, 0);
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < BOARDS.length; i++) ind.append(i == cur ? "&f● " : "&8● ");
        String head = cat[1] + "\n&7ŽEBŘÍČEK &8| " + ind.toString().trim();
        Holo.setText(boards.get(0), head);
        Component names = Component.empty(), vals = Component.empty();
        String[] medal = {"&6&l#1", "&f&l#2", "&c&l#3"};
        if (t.isEmpty()) { names = U.c("&7Zatím nikdo\n&7buď první!"); vals = U.c(" "); }
        for (int j = 0; j < t.size(); j++) {
            String name = t.get(j).getKey();
            Component row = U.c(j < 3 ? medal[j] : "&7#" + (j + 1)).append(Component.text(" ")).append(pl.skins.head(name, null)).append(U.c(" &f" + name));
            names = names.append(row); vals = vals.append(U.c("&e" + t.get(j).getValue()));
            if (j < t.size() - 1) { names = names.append(Component.newline()); vals = vals.append(Component.newline()); }
        }
        Holo.setComponents(boards.get(1), names, names);
        Holo.setComponents(boards.get(2), vals, vals);
    }

    /** /ss top – osobní statistiky v chatu */
    public void show(Player p) {
        U.msg(p, "&6&lTvoje statistiky:");
        p.sendMessage(U.c(Lang.t(p, "&7Výhry: &a" + get(p, "wins") + " &8| &7Hry: &e" + get(p, "games") + " &8| &7Zabití: &c" + get(p, "kills") + " &8| &7Level: &b" + pl.levels.level(p))));
    }
}
