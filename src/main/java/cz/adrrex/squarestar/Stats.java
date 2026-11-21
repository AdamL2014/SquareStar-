package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
            {"wins", "&6&l🏆 NEJVÍC VÝHER", "-6.5", "13"},
            {"level", "&b&l⭐ NEJVYŠŠÍ LEVEL", "-2", "15"},
            {"kills", "&c&l⚔ NEJVÍC ZABITÍ", "3", "15"},
            {"games", "&a&l🎮 NEJVÍC HER", "7.5", "13"}};

    public void spawnBoards() {
        for (Entity e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        boards.clear();
        for (String[] b : BOARDS) {
            Location l = new Location(pl.mars, Double.parseDouble(b[2]) + 0.5, Lobby.Y + 3.6, Double.parseDouble(b[3]) + 0.5);
            boards.add(pl.mars.spawn(l, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBackgroundColor(Color.fromARGB(170, 40, 10, 5));
                d.setShadowed(true);
                d.setLineWidth(220);
                d.addScoreboardTag(TAG);
                d.setPersistent(false);
            }));
        }
        refresh();
    }

    public void refresh() {
        if (boards.isEmpty()) return;
        String[] medal = {"&6&l1.", "&f&l2.", "&c&l3."};
        for (int i = 0; i < BOARDS.length && i < boards.size(); i++) {
            TextDisplay d = boards.get(i);
            if (!d.isValid()) { spawnBoards(); return; }
            StringBuilder s = new StringBuilder(BOARDS[i][1]).append("\n&8▬▬▬▬▬▬▬▬▬▬▬▬\n");
            var t = top(BOARDS[i][0], 10);
            if (t.isEmpty()) s.append("&7Zatím nikdo – buď první!\n");
            for (int j = 0; j < t.size(); j++)
                s.append(j < 3 ? medal[j] : "&7" + (j + 1) + ".").append(" &f").append(t.get(j).getKey()).append(" &8- &e").append(t.get(j).getValue()).append('\n');
            s.append("&8▬▬▬▬▬▬▬▬▬▬▬▬");
            d.text(U.c(s.toString()));
        }
    }

    /** /ss top – osobní statistiky v chatu */
    public void show(Player p) {
        U.msg(p, "&6&lTvoje statistiky:");
        p.sendMessage(U.c(Lang.t(p, "&7Výhry: &a" + get(p, "wins") + " &8| &7Hry: &e" + get(p, "games") + " &8| &7Zabití: &c" + get(p, "kills") + " &8| &7Level: &b" + pl.levels.level(p))));
    }
}
