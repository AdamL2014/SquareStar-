package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Dropper: padáš dlouhou šachtou, nesmíš se ničeho dotknout a musíš dopadnout do vody.
 * 5 levelů, každý jiný. Kdo první projde všechny, vyhrává.
 */
public final class Dropper extends Game {
    private static final int LEVELS = 8, H = 110, IN = 4; // vnitřek šachty = -IN..IN (9x9)
    private final Map<Player, Integer> level = new HashMap<>(), fails = new HashMap<>();
    private final Map<Player, Long> lastFail = new HashMap<>();
    private BukkitTask tick;
    private static final String[] NAMES = {"Trámy", "Díry v podlaze", "Les sloupů", "Spirála", "Kříže", "Rámy", "Asteroidy", "Finále"};

    public Dropper(GameManager gm, int slot) { super(gm, GameType.DROPPER, slot); }

    @Override public List<String> maps() { return List.of("Marsovské šachty", "Neonové šachty"); }
    @Override protected int timeLimit() { return 900; }
    @Override protected int voidY() { return oy - 30; }
    /** trochu silnější gravitace než v lobby, ať padání netrvá věčnost */
    @Override public Double gravity() { return 0.045; }

    private int sx(int lvl) { return ox + (lvl - LEVELS / 2) * 22; }

    // ---------------- stavba ----------------
    @Override protected void buildMap(U.BlockQueue.Builder b) {
        boolean neon = map == 1;
        Material wall = neon ? Material.BLACK_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
        Material stripe = neon ? Material.SEA_LANTERN : Material.SHROOMLIGHT;
        Material obst = neon ? Material.MAGENTA_CONCRETE : Material.RED_TERRACOTTA;
        Material obst2 = neon ? Material.CYAN_CONCRETE : Material.RED_SANDSTONE;
        for (int l = 0; l < LEVELS; l++) {
            int cx = sx(l), cz = oz;
            Random r = new Random(l * 7919L + map);
            // stěny + světelné pruhy
            for (int y = 0; y <= H; y++) for (int i = -IN - 1; i <= IN + 1; i++) {
                Material m = y % 10 == 0 ? stripe : wall;
                b.set(cx + i, oy + y, cz - IN - 1, m); b.set(cx + i, oy + y, cz + IN + 1, m);
                b.set(cx - IN - 1, oy + y, cz + i, m); b.set(cx + IN + 1, oy + y, cz + i, m);
            }
            // dno: voda + okraj
            b.fill(cx - IN, oy - 1, cz - IN, cx + IN, oy - 1, cz + IN, Material.SEA_LANTERN);
            b.fill(cx - IN, oy, cz - IN, cx + IN, oy + 1, cz + IN, Material.WATER);
            // horní plošina se startem
            b.fill(cx - IN - 3, oy + H + 1, cz - IN - 3, cx + IN + 3, oy + H + 1, cz + IN + 3, neon ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_RED_SANDSTONE);
            b.fill(cx - IN, oy + H + 1, cz - IN, cx + IN, oy + H + 1, cz + IN, Material.AIR);
            // překážky podle levelu – vždy projít jde (díra další vrstvy je blízko předchozí)
            int hx = 0, hz = 0, q = 0;
            for (int y = H - 14; y > 8; y--) {
                switch (l) {
                    case 0 -> { if (y % 9 == 0) { boolean xAxis = (y / 9) % 2 == 0; int off = r.nextInt(5) - 2;
                        for (int i = -IN; i <= IN; i++) { if (xAxis) b.set(cx + i, oy + y, cz + off, obst); else b.set(cx + off, oy + y, cz + i, obst); } } }
                    case 1, 7 -> { int gap = l == 7 ? 10 : 13; if (y % gap == 0) {
                        hx = Math.max(-IN + 1, Math.min(IN - 1, hx + r.nextInt(5) - 2)); hz = Math.max(-IN + 1, Math.min(IN - 1, hz + r.nextInt(5) - 2));
                        for (int i = -IN; i <= IN; i++) for (int k = -IN; k <= IN; k++) if (Math.abs(i - hx) > 1 || Math.abs(k - hz) > 1)
                            b.set(cx + i, oy + y, cz + k, l == 7 && (i + k) % 5 == 0 ? Material.MAGMA_BLOCK : ((i + k) % 2 == 0 ? obst : obst2)); } }
                    case 2 -> { if (y % 16 == 0) for (int c = 0; c < 4; c++) { int px = r.nextInt(2 * IN + 1) - IN, pz = r.nextInt(2 * IN + 1) - IN, len = 5 + r.nextInt(7);
                        for (int k = 0; k < len; k++) b.set(cx + px, oy + y - k, cz + pz, k % 4 == 0 ? stripe : obst); } }
                    case 3 -> { if (y % 12 == 0) { q = (q + 1) % 4;
                        for (int i = -IN; i <= IN; i++) for (int k = -IN; k <= IN; k++) {
                            boolean open = switch (q) { case 0 -> i >= 1 && k >= 1; case 1 -> i <= -1 && k >= 1; case 2 -> i <= -1 && k <= -1; default -> i >= 1 && k <= -1; };
                            if (!open) b.set(cx + i, oy + y, cz + k, obst2);
                        } } }
                    case 4 -> { if (y % 10 == 0) { boolean diag = (y / 10) % 2 == 0;
                        for (int i = -IN; i <= IN; i++) { if (diag) { b.set(cx + i, oy + y, cz + i, obst); b.set(cx + i, oy + y, cz - i, obst); } else { b.set(cx + i, oy + y, cz, obst); b.set(cx, oy + y, cz + i, obst); } } } }
                    case 5 -> { if (y % 12 == 0) { boolean ring = (y / 12) % 2 == 0;
                        for (int i = -IN; i <= IN; i++) for (int k = -IN; k <= IN; k++) {
                            int m = Math.max(Math.abs(i), Math.abs(k));
                            if (ring ? m >= 3 : m <= 1) b.set(cx + i, oy + y, cz + k, ring ? obst : stripe);
                        } } }
                    default -> { if (y % 5 == 0) for (int c = 0; c < 2; c++) { int px = r.nextInt(2 * IN - 1) - IN + 1, pz = r.nextInt(2 * IN - 1) - IN + 1;
                        b.fill(cx + px, oy + y, cz + pz, cx + px + r.nextInt(2), oy + y, cz + pz + r.nextInt(2), c == 0 ? Material.MAGMA_BLOCK : obst); } }
                }
            }
            // číslo levelu nad šachtou
            for (int i = 0; i <= l; i++) b.set(cx - IN - 3 + i * 2, oy + H + 2, cz - IN - 3, Material.GOLD_BLOCK);
        }
    }

    // ---------------- průběh ----------------
    @Override protected void start() {
        for (Player p : players) { level.put(p, 0); fails.put(p, 0); toTop(p); }
        broadcast("&b&lDROPPER! &7Skoč do šachty, nenaraz do ničeho a dopadni do &bvody&7. " + LEVELS + " levelů!");
        tick = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 2, 2);
    }

    private void toTop(Player p) {
        int l = level.getOrDefault(p, 0);
        Location t = loc(0, 0, 0);
        t = new Location(w, sx(l) + 0.5, oy + H + 2, oz - IN - 1.5, 0, 50);
        p.teleport(t);
        p.setFallDistance(0);
        U.title(p, "&b&lLevel " + (l + 1) + "/" + LEVELS, "&7" + NAMES[l]);
    }

    private void tick() {
        if (state != State.RUNNING) return;
        for (Player p : new ArrayList<>(alive)) {
            Location l = p.getLocation();
            int lv = level.getOrDefault(p, 0);
            // dopad do vody = level splněn
            if (l.getBlock().getType() == Material.WATER && l.getY() < oy + 3) {
                lv++;
                level.put(p, lv);
                U.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.4f);
                w.spawnParticle(Particle.SPLASH, l, 40, 0.6, 0.3, 0.6, 0.2);
                if (lv >= LEVELS) { broadcast("&6&l" + p.getName() + " &eprošel všechny levely! &7(chyb: " + fails.get(p) + ", čas " + fmt(time) + ")"); end(List.of(p)); return; }
                broadcast("&a" + p.getName() + " &7prošel level &f" + lv + "&7!");
                toTop(p);
                continue;
            }
            // náraz: stojí na něčem uvnitř šachty (pod horní plošinou)
            if (l.getY() < oy + H && p.isOnGround()) {
                Block below = l.clone().add(0, -0.2, 0).getBlock();
                if (below.getType().isSolid() && System.currentTimeMillis() - lastFail.getOrDefault(p, 0L) > 800) fail(p);
            }
        }
    }

    private void fail(Player p) {
        if (System.getProperty("ssdebug") != null) pl.getLogger().info("DROP fail " + p.getLocation().toVector() + " below=" + p.getLocation().clone().add(0, -0.2, 0).getBlock().getType());
        lastFail.put(p, System.currentTimeMillis());
        fails.merge(p, 1, Integer::sum);
        U.sound(p, Sound.ENTITY_ITEM_BREAK, 0.8f);
        w.spawnParticle(Particle.SMOKE, p.getLocation(), 15, 0.3, 0.3, 0.3, 0.02);
        U.bar(p, "&c✖ Náraz! &7Znovu od začátku levelu.");
        toTop(p);
    }

    @Override public void onDeath(Player p, Player killer) { U.reset(p, GameMode.ADVENTURE); pl.applyGravity(p); fail(p); }

    @Override protected String status(Player p) {
        return "&bLevel &f" + Math.min(LEVELS, level.getOrDefault(p, 0) + 1) + "/" + LEVELS + " &8| &cNárazy &f" + fails.getOrDefault(p, 0) + " &8| &7" + fmt(time);
    }

    @Override protected void timeUp() {
        Player best = null;
        for (Player p : alive) if (best == null || level.get(p) > level.get(best) || (level.get(p).equals(level.get(best)) && fails.get(p) < fails.get(best))) best = p;
        end(best == null ? List.of() : List.of(best));
    }
    @Override protected void checkWin() { if (state == State.RUNNING && alive.isEmpty()) end(List.of()); }
    @Override protected void onDestroy() { if (tick != null) tick.cancel(); }
}
