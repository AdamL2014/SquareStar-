package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

/** Parkour nad propastí. Díky nízké gravitaci jsou skoky dlouhé. */
public final class Parkour extends Game {
    private final List<int[]> checkpoints = new ArrayList<>();
    private final Map<Long, Integer> cpIndex = new HashMap<>();
    private final Map<Player, Integer> progress = new HashMap<>();
    private int minY = 0;
    private long finish;

    public Parkour(GameManager gm, int slot) { super(gm, GameType.PARKOUR, slot); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        b.fill(ox - 2, oy, oz - 2, ox + 2, oy, oz + 2, Material.RED_NETHER_BRICKS);
        checkpoints.add(new int[]{0, 0, 0});
        Material[] mats = {Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.WHITE_CONCRETE, Material.RED_SANDSTONE, Material.BLACKSTONE};
        int x = 0, y = 0, z = 3;
        for (int i = 1; i <= 50; i++) {
            z += 3 + rnd.nextInt(3);
            x += rnd.nextInt(5) - 2;
            x = Math.max(-12, Math.min(12, x));
            y += rnd.nextInt(10) < 2 ? 2 : rnd.nextInt(3) - 1;
            y = Math.max(-14, Math.min(18, y));
            minY = Math.min(minY, y);
            if (i % 10 == 0 && i < 50) {
                b.fill(ox + x - 1, oy + y, oz + z - 1, ox + x + 1, oy + y, oz + z + 1, Material.GOLD_BLOCK);
                int idx = checkpoints.size();
                checkpoints.add(new int[]{x, y, z});
                for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) cpIndex.put(U.pack(ox + x + a, oy + y, oz + z + c), idx);
                z += 1;
            } else if (i == 50) {
                b.fill(ox + x - 2, oy + y, oz + z - 2, ox + x + 2, oy + y, oz + z + 2, Material.EMERALD_BLOCK);
                b.set(ox + x, oy + y + 1, oz + z, Material.BEACON);
                finish = U.pack(ox + x, oy + y, oz + z);
            } else {
                Material m = mats[rnd.nextInt(mats.length)];
                b.set(ox + x, oy + y, oz + z, m);
                if (rnd.nextInt(4) == 0) b.set(ox + x + (rnd.nextBoolean() ? 1 : -1), oy + y, oz + z, m);
            }
        }
    }

    @Override protected int voidY() { return oy + minY - 12; }
    @Override protected int timeLimit() { return 360; }

    @Override protected void start() {
        for (Player p : players) { progress.put(p, 0); p.teleport(cp(0)); }
        broadcast("&eDoskákej na &asmaragdovou plošinu&e! Zlaté bloky jsou &6checkpointy&e.");
    }

    private Location cp(int i) { int[] c = checkpoints.get(i); return U.center(w, ox + c[0], oy + c[1] + 1, oz + c[2]).setDirection(new org.bukkit.util.Vector(0, 0, 1)); }

    @Override public void onMove(Player p, Location to) {
        Block below = to.clone().subtract(0, 0.2, 0).getBlock();
        Material m = below.getType();
        if (m == Material.GOLD_BLOCK) {
            Integer idx = cpIndex.get(U.pack(below.getX(), below.getY(), below.getZ()));
            if (idx != null && idx > progress.getOrDefault(p, 0)) {
                progress.put(p, idx);
                U.title(p, "&6Checkpoint " + idx, "&7Super, jen tak dál!");
                U.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.4f);
            }
        } else if (m == Material.EMERALD_BLOCK || m == Material.BEACON) {
            broadcast("&a&l" + p.getName() + " &adoskákal do cíle za &e" + fmt(time) + "&a!");
            end(List.of(p));
        }
    }

    @Override public void onVoid(Player p) { p.teleport(cp(progress.getOrDefault(p, 0))); p.setFallDistance(0); U.sound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1f); }

    @Override protected void checkWin() { if (state == State.RUNNING && alive.isEmpty()) end(List.of()); }

    @Override protected void timeUp() {
        Player best = null; int bp = -1;
        for (Player p : alive) { int v = progress.getOrDefault(p, 0); if (v > bp) { bp = v; best = p; } }
        end(best == null ? List.of() : List.of(best));
    }

    @Override protected String status() {
        return "&7Čas: &e" + fmt(time) + " &8| &7Checkpointy: &6" + (checkpoints.size() - 1) + " &8| &7Cíl: &asmaragdy";
    }
}
