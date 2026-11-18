package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** SkyWars na asteroidech: každý hráč má svůj asteroid s truhlou, uprostřed je velký asteroid s lepšími věcmi. */
public final class SkyWars extends Game {
    private final List<int[]> islands = new ArrayList<>();
    private final List<int[]> chests = new ArrayList<>();
    private final List<int[]> midChests = new ArrayList<>();
    private static final Material[] ROCK = {Material.END_STONE, Material.STONE, Material.ANDESITE, Material.BLACKSTONE, Material.TUFF, Material.END_STONE};
    private static final Material[] ORE = {Material.IRON_ORE, Material.COAL_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE};

    public SkyWars(GameManager gm, int slot) { super(gm, GameType.SKYWARS, slot); }

    private void asteroid(U.BlockQueue.Builder b, int cx, int cy, int cz, int r) {
        b.sphere(cx, cy, cz, r, p -> {
            if (p[1] > cy + 1) return; // plochý vršek
            Material m = p[1] == cy + 1 ? Material.RED_SAND : rnd.nextInt(14) == 0 ? ORE[rnd.nextInt(ORE.length)] : ROCK[rnd.nextInt(ROCK.length)];
            b.set(p[0], p[1], p[2], m);
        });
    }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        asteroid(b, ox, oy - 1, oz, 8);
        for (int[] c : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            b.set(ox + c[0], oy + 1, oz + c[1], Material.CHEST);
            midChests.add(new int[]{ox + c[0], oy + 1, oz + c[1]});
        }
        int n = type.max;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int cx = ox + (int) Math.round(Math.cos(a) * 30), cz = oz + (int) Math.round(Math.sin(a) * 30), cy = oy - 1 + rnd.nextInt(5) - 2;
            asteroid(b, cx, cy, cz, 4);
            islands.add(new int[]{cx, cy + 2, cz});
            int chx = cx + (int) Math.round(-Math.cos(a) * 2), chz = cz + (int) Math.round(-Math.sin(a) * 2);
            b.set(chx, cy + 2, chz, Material.CHEST);
            chests.add(new int[]{chx, cy + 2, chz});
        }
        // malé kameny jako odrazové body
        for (int i = 0; i < 10; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 12 + rnd.nextInt(12);
            asteroid(b, ox + (int) (Math.cos(a) * d), oy + rnd.nextInt(9) - 4, oz + (int) (Math.sin(a) * d), 1 + rnd.nextInt(2));
        }
    }

    @Override protected void afterBuild() { refill(); }

    private void refill() {
        for (int[] c : chests) fill(c, false, 5 + rnd.nextInt(3));
        for (int[] c : midChests) fill(c, true, 6 + rnd.nextInt(3));
    }

    private void fill(int[] c, boolean good, int count) {
        Block bl = w.getBlockAt(c[0], c[1], c[2]);
        if (!(bl.getState() instanceof Chest chest)) return;
        Inventory inv = chest.getBlockInventory();
        inv.clear();
        for (int i = 0; i < count; i++) inv.setItem(rnd.nextInt(inv.getSize()), Loot.random(rnd, good));
    }

    @Override protected GameMode mode() { return GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected boolean hunger() { return true; }
    @Override protected boolean canDrop() { return true; }
    @Override protected int timeLimit() { return 600; }

    @Override public boolean canBreak(Player p, Block b) { return b.getY() < oy + 40 && owns(b.getLocation()); }
    @Override public boolean canPlace(Player p, Block b) { return b.getY() < oy + 40; }

    @Override protected void start() {
        int i = 0;
        for (Player p : players) {
            int[] s = islands.get(i++ % islands.size());
            Location l = U.center(w, s[0], s[1], s[2]);
            l.setDirection(new Vector(ox - s[0], 0, oz - s[2]));
            p.teleport(l);
        }
        broadcast("&eOtevři truhlu na svém asteroidu! &7Uprostřed jsou lepší věci. Za 3 minuty se truhly doplní.");
    }

    @Override protected void second() {
        if (time == 180) { refill(); broadcast("&6&lTruhly byly doplněny!"); }
    }
}
