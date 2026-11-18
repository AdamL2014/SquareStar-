package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;

/** Build Battle: každý staví téma na svém pozemku, pak se hlasuje. */
public final class BuildBattle extends Game {
    private static final int HALF = 8, HEIGHT = 24, SPACING = 40, VOTE_TIME = 15;
    private static final String[] THEMES = {"Raketa", "Mimozemšťan", "Dům na Marsu", "Robot", "Hrad", "Drak", "Pizza", "Strom", "Auto", "Loď",
            "Hamburger", "Sopka", "Satelit", "UFO", "Zmrzlina", "Kočka", "Meteorit", "Astronaut", "Pirátská loď", "Dort", "Maják", "Houba", "Dinosaurus", "Herní konzole"};
    private static final Material[] VOTE_MAT = {Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.GREEN_TERRACOTTA};
    private static final String[] VOTE_NAME = {"&4Hrozné", "&cSlabé", "&eDobré", "&aSuper", "&2&lÚžasné"};

    private final List<Player> owners = new ArrayList<>();
    private final Map<Player, int[]> plots = new HashMap<>();
    private final Map<Player, Integer> score = new HashMap<>();
    private final Map<Player, Integer> curVotes = new HashMap<>();
    private String theme;
    private int buildTime, showIdx = -1;

    public BuildBattle(GameManager gm, int slot) {
        super(gm, GameType.BUILDBATTLE, slot);
        buildTime = gm.pl.getConfig().getInt("build-battle.build-seconds", 300);
    }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        owners.clear(); owners.addAll(players);
        int n = owners.size();
        for (int i = 0; i < n; i++) {
            // pozemky v mřížce (max 4 v řadě), ne v jedné dlouhé čáře
            int cols = Math.min(n, 4), rowsN = (n + 3) / 4, col = i % 4, row = i / 4;
            int inRow = row == rowsN - 1 ? n - row * 4 : cols;
            int cx = ox + (int) Math.round((col - (inRow - 1) / 2.0) * SPACING), cz = oz + (int) Math.round((row - (rowsN - 1) / 2.0) * SPACING);
            plots.put(owners.get(i), new int[]{cx, cz});
            for (int x = -HALF; x <= HALF; x++) for (int z = -HALF; z <= HALF; z++) {
                b.set(cx + x, oy, cz + z, (x + z) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE);
                b.set(cx + x, oy - 1, cz + z, Material.RED_TERRACOTTA);
            }
            for (int i2 = -HALF - 1; i2 <= HALF + 1; i2++) for (int y = 0; y <= HEIGHT + 1; y++) {
                Material m = y == 0 ? Material.RED_NETHER_BRICKS : Material.BARRIER;
                b.set(cx + i2, oy + y, cz - HALF - 1, m); b.set(cx + i2, oy + y, cz + HALF + 1, m);
                b.set(cx - HALF - 1, oy + y, cz + i2, m); b.set(cx + HALF + 1, oy + y, cz + i2, m);
            }
            b.fill(cx - HALF, oy + HEIGHT + 1, cz - HALF, cx + HALF, oy + HEIGHT + 1, cz + HALF, Material.BARRIER);
        }
        theme = THEMES[rnd.nextInt(THEMES.length)];
    }

    private boolean inside(int[] c, Block b) {
        return Math.abs(b.getX() - c[0]) <= HALF && Math.abs(b.getZ() - c[1]) <= HALF && b.getY() >= oy && b.getY() <= oy + HEIGHT;
    }
    private boolean building() { return time < buildTime; }

    @Override protected GameMode mode() { return GameMode.CREATIVE; }
    @Override protected int voidY() { return oy - 10; }
    @Override protected int timeLimit() { return buildTime + VOTE_TIME * Math.max(1, owners.size()) + 30; }
    @Override public boolean canPlace(Player p, Block b) { int[] c = plots.get(p); return building() && c != null && inside(c, b); }
    @Override public boolean canBreak(Player p, Block b) { return canPlace(p, b); }
    @Override public boolean allowFlow(Block to) { for (int[] c : plots.values()) if (inside(c, to)) return true; return false; }
    @Override protected void checkWin() { if (state == State.RUNNING && alive.isEmpty()) end(List.of()); }
    @Override public void onVoid(Player p) { int[] c = plots.get(p); p.teleport(U.center(w, c != null ? c[0] : ox, oy + 2, c != null ? c[1] : oz)); }

    @Override protected void start() {
        for (Player p : players) {
            int[] c = plots.get(p);
            if (c != null) p.teleport(U.center(w, c[0], oy + 1, c[1]));
            U.title(p, "&6&lTéma: &e" + theme, "&7Máš " + buildTime / 60 + " minut na stavbu!");
        }
        broadcast("&eTéma: &6&l" + theme + " &7| Stav jen na svém pozemku. Máš kreativ!");
    }

    @Override protected void second() {
        int left = buildTime - time;
        if (left == 60 || left == 10) broadcast("&eZbývá &c" + left + " s &ena stavbu!");
        if (time < buildTime) return;
        if ((time - buildTime) % VOTE_TIME == 0) nextPlot();
    }

    private void nextPlot() {
        if (showIdx >= 0 && showIdx < owners.size()) {
            Player o = owners.get(showIdx);
            score.merge(o, curVotes.values().stream().mapToInt(Integer::intValue).sum(), Integer::sum);
        }
        curVotes.clear();
        showIdx++;
        while (showIdx < owners.size() && !players.contains(owners.get(showIdx))) showIdx++;
        if (showIdx >= owners.size()) { finish(); return; }
        Player o = owners.get(showIdx);
        int[] c = plots.get(o);
        for (Player p : players) {
            if (p.getGameMode() == GameMode.CREATIVE || showIdx == 0) {
                U.reset(p, GameMode.ADVENTURE);
                p.setAllowFlight(true); p.setFlying(true);
                for (int i = 0; i < 5; i++) p.getInventory().setItem(i, U.tag(U.item(VOTE_MAT[i], i + 1, VOTE_NAME[i] + " &7(" + (i + 1) + " b.)"), "ss_bb", String.valueOf(i + 1)));
            }
            p.teleport(new Location(w, c[0] + 0.5, oy + 10, c[1] - HALF + 0.5, 0, 25));
            p.setAllowFlight(true); p.setFlying(true);
            U.title(p, "&eStavba: &6" + o.getName(), "&7Téma: " + theme + " &8| &7Hlasuj itemy v ruce");
        }
    }

    @Override public void onInteract(PlayerInteractEvent e) {
        String v = U.tagOf(e.getItem(), "ss_bb");
        if (v == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (showIdx < 0 || showIdx >= owners.size()) return;
        if (owners.get(showIdx) == p) { U.msg(p, "&cZa svou stavbu hlasovat nemůžeš."); return; }
        int pts = Integer.parseInt(v);
        curVotes.put(p, pts);
        U.bar(p, "&7Tvůj hlas: " + VOTE_NAME[pts - 1]);
        U.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f + pts * 0.25f);
    }

    private void finish() {
        List<Map.Entry<Player, Integer>> list = new ArrayList<>(score.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        broadcast("&6&l===== VÝSLEDKY =====");
        for (int i = 0; i < Math.min(5, list.size()); i++) broadcast("&e#" + (i + 1) + " &f" + list.get(i).getKey().getName() + " &7- &a" + list.get(i).getValue() + " bodů");
        if (list.isEmpty()) { end(List.of()); return; }
        int best = list.get(0).getValue();
        Player win = list.get(0).getKey();
        int[] c = plots.get(win);
        for (Player p : players) p.teleport(new Location(w, c[0] + 0.5, oy + 10, c[1] - HALF + 0.5, 0, 25));
        end(list.stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).filter(players::contains).toList());
    }

    @Override protected void timeUp() { finish(); }

    @Override protected String status() {
        if (time < buildTime) return "&6Téma: &e" + theme + " &8| &7Stavba: &e" + fmt(buildTime - time);
        return "&6Téma: &e" + theme + " &8| &7Hlasování: &e" + (showIdx + 1) + "/" + owners.size();
    }
}
