package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Parkour na Marsu – při každé hře se vygeneruje JINÁ trať z náhodných úseků:
 * skoky, schody, úzké trámy, vlny a jízda na vznášedle (nastoupíš a ve správnou chvíli vyskočíš na plošinu).
 * Plošiny před tebou se postupně vynořují z propasti.
 */
public final class Parkour extends Game {
    /** jedna vrstva bloků: x1..x2, z1..z2 (relativně k aréně), výška y */
    private record Plat(int x1, int y, int z1, int x2, int z2, Material m) {}
    /** vznášedlo: jede po ose z mezi z0 a z1, zastavuje u nástupní plošiny */
    private static final class Ride {
        double x, y, z0, z1, s = 0; int state = 0, wait = 0, side;   // 0 čeká, 1 jede tam, 2 pauza, 3 zpět
        ArmorStand seat; BlockDisplay body, glow; Player rider; boolean spawned;
    }

    private final List<Plat> plats = new ArrayList<>();
    private final Set<Integer> shown = new HashSet<>();
    private final List<Ride> rides = new ArrayList<>();
    private final List<int[]> checkpoints = new ArrayList<>();
    private final Map<Long, Integer> cpIndex = new HashMap<>();
    private final Map<Player, Integer> progress = new HashMap<>();
    private final Map<Player, Ride> riding = new HashMap<>();
    private int minY = 0;
    private long finish;
    private BukkitTask ticker2;
    private int tick2;

    private static final double CARPET_V = 0.2;

    public Parkour(GameManager gm, int slot) { super(gm, GameType.PARKOUR, slot); }

    @Override protected Object[][] themes() {
        return new Object[][]{
                new Object[]{"Zlatá dráha", 0, Material.RED_NETHER_BRICKS, Material.GOLD_BLOCK, Material.RED_SANDSTONE, Material.YELLOW_CONCRETE, Material.RED_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.HONEYCOMB_BLOCK, Material.BLACKSTONE, Material.SMOOTH_SANDSTONE},
                new Object[]{"Lávová dráha", 0, Material.RED_SANDSTONE, Material.MAGMA_BLOCK, Material.RED_TERRACOTTA, Material.BLACKSTONE, Material.ORANGE_TERRACOTTA, Material.NETHERRACK, Material.RED_NETHER_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.BASALT}};
    }

    @Override public List<String> maps() { return List.of("Rudé kaňony", "Ledová kometa", "Neonová dráha"); }

    // ---------------- generátor trati ----------------
    private Material[] mats() {
        return map == 2
                ? new Material[]{Material.CYAN_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.PURPLE_STAINED_GLASS}
                : map == 1
                ? new Material[]{Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK, Material.LIGHT_BLUE_CONCRETE, Material.CALCITE}
                : new Material[]{Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.WHITE_CONCRETE, Material.RED_SANDSTONE, Material.BLACKSTONE};
    }
    private int cx, cy, cz;   // kurzor generátoru = střed poslední plošiny
    private void plat(int x, int y, int z, int wd, int dp, Material m) {
        int x1 = x - (wd - 1) / 2, z1 = z - (dp - 1) / 2;
        plats.add(new Plat(x1, y, z1, x1 + wd - 1, z1 + dp - 1, m));
        minY = Math.min(minY, y);
    }
    private void clampCursor() { cx = Math.max(-13, Math.min(13, cx)); cy = Math.max(-14, Math.min(18, cy)); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        mapName = allMaps().get(Math.min(map, allMaps().size() - 1)) + " #" + (1000 + rnd.nextInt(9000));
        Material[] mats = mats();
        b.fill(ox - 2, oy, oz - 2, ox + 2, oy, oz + 2, map == 1 ? Material.PACKED_ICE : map == 2 ? Material.BLACK_CONCRETE : Material.RED_NETHER_BRICKS);
        checkpoints.add(new int[]{0, 0, 0});
        cx = 0; cy = 0; cz = 0;
        int units = 0, lastCp = 0, lastKind = -1;
        final int TOTAL = 44 + rnd.nextInt(10);
        while (units < TOTAL) {
            int kind;
            do { kind = pickKind(); } while (kind == 4 && (lastKind == 4 || units < 4 || TOTAL - units < 8));
            lastKind = kind;
            switch (kind) {
                case 0 -> { int n = 4 + rnd.nextInt(4); for (int i = 0; i < n; i++) jump(mats); units += n; }
                case 1 -> { boolean up = rnd.nextInt(3) != 0; int n = 3 + rnd.nextInt(4); for (int i = 0; i < n; i++) { cz += 2; cy += up ? 1 : -1; clampCursor(); plat(cx, cy, cz, 1 + rnd.nextInt(2), 1, mats[rnd.nextInt(mats.length)]); } units += n; }
                case 2 -> { int len = 6 + rnd.nextInt(4); Material m = mats[rnd.nextInt(mats.length)]; for (int i = 0; i < len; i++) { cz++; if (i % 3 == 2) cx += rnd.nextBoolean() ? 1 : -1; clampCursor(); plat(cx, cy, cz, 1, 1, m); } units += 4; }
                case 3 -> { int n = 5 + rnd.nextInt(3); double ph = rnd.nextDouble() * 6, amp = 3 + rnd.nextInt(2); int base = cx; for (int i = 1; i <= n; i++) { cz += 3; cx = (int) Math.round(base + amp * Math.sin(ph + i * 0.95)) ; clampCursor(); plat(cx, cy, cz, 1 + rnd.nextInt(2), 1, mats[rnd.nextInt(mats.length)]); } units += n; }
                default -> { units += ride(); }
            }
            if (units - lastCp >= 10 && units < TOTAL) { checkpoint(); lastCp = units; }
        }
        // cíl
        cz += 4; clampCursor();
        plat(cx, cy, cz, 5, 5, Material.EMERALD_BLOCK);
        finish = U.pack(ox + cx, oy + cy, oz + cz);
        b.set(ox + cx, oy + cy + 1, oz + cz, Material.BEACON);
    }
    private int pickKind() { int r = rnd.nextInt(12); return r < 4 ? 0 : r < 6 ? 1 : r < 8 ? 2 : r < 10 ? 3 : 4; }

    private void jump(Material[] mats) {
        cz += 3 + rnd.nextInt(3);
        cx += rnd.nextInt(5) - 2;
        cy += rnd.nextInt(10) < 2 ? 2 : rnd.nextInt(3) - 1;
        clampCursor();
        Material m = mats[rnd.nextInt(mats.length)];
        plat(cx, cy, cz, rnd.nextInt(4) == 0 ? 2 : 1, 1, m);
    }

    private void checkpoint() {
        cz += 4; clampCursor();
        plat(cx, cy, cz, 3, 3, Material.GOLD_BLOCK);
        int idx = checkpoints.size();
        checkpoints.add(new int[]{cx, cy, cz});
        for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) cpIndex.put(U.pack(ox + cx + a, oy + cy, oz + cz + c), idx);
        cz += 1;
    }

    /** vznášedlo: nástupní plošina, vznášedlo jezdí v ose z, přistávací plošina je vedle trasy */
    private int ride() {
        int L = 18 + rnd.nextInt(8);
        cz += 4; clampCursor();
        plat(cx, cy, cz, 3, 3, Material.CYAN_CONCRETE);       // nástupní plošina
        Ride r = new Ride();
        r.x = cx; r.y = cy; r.z0 = cz + 3.2; r.z1 = cz + 3.2 + L;
        r.side = cx > 4 ? -1 : cx < -4 ? 1 : rnd.nextBoolean() ? 1 : -1;
        rides.add(r);
        int lz = (int) (r.z0 + L * (0.45 + rnd.nextDouble() * 0.2));
        int lx = cx + r.side * 5;
        lx = Math.max(-16, Math.min(16, lx));
        r.side = lx > cx ? 1 : -1;
        cy += rnd.nextInt(3) - 1; clampCursor();
        cx = lx; cz = lz;
        plat(cx, cy, cz, 5, 5, Material.LIME_CONCRETE);       // přistávací plošina (široká, ať je okno na skok)
        r.z1 = lz + 4;                                         // vznášedlo nejede dál než kousek za plošinu
        cz += 1;
        return 9;
    }

    @Override protected int voidY() { return oy + minY - 14; }
    @Override protected int timeLimit() { return 420; }

    // ---------------- hra ----------------
    @Override protected void start() {
        for (Player p : players) { progress.put(p, 0); p.teleport(cp(0)); }
        broadcast("&eDoskákej na &asmaragdovou plošinu&e! Zlaté bloky jsou &6checkpointy&e. &bVznášedlo&e: nastup a vyskoč u zelené plošiny!");
        ticker2 = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 2, 2);
    }

    private Location cp(int i) { int[] c = checkpoints.get(i); return U.center(w, ox + c[0], oy + c[1] + 1, oz + c[2]).setDirection(new Vector(0, 0, 1)); }

    @Override protected void onDestroy() { dead = true; if (ticker2 != null) ticker2.cancel(); }

    private void tick() {
        if (state != State.RUNNING && state != State.ENDING) return;
        tick2++;
        // plošiny před hráči se vynoří z propasti
        if (state == State.RUNNING) {
            int n = 0;
            for (int i = 0; i < plats.size() && n < 8; i++) {
                if (shown.contains(i)) continue;
                Plat pt = plats.get(i);
                double px = ox + (pt.x1 + pt.x2) / 2.0, py = oy + pt.y, pz = oz + (pt.z1 + pt.z2) / 2.0;
                boolean near = false; double minD = 1e9;
                for (Player p : alive) {
                    Location l = p.getLocation();
                    if (Math.abs(l.getZ() - pz) < 40 && Math.abs(l.getX() - px) < 40 && Math.abs(l.getY() - py) < 40) { near = true; minD = Math.min(minD, Math.hypot(l.getX() - px, l.getZ() - pz)); }
                }
                // blízko hráče je plošina rovnou pevná (jinak by hráč proběhl animací a propadl se); daleko se hezky vynoří
                if (near) { shown.add(i); reveal(pt, minD < 20); n++; }
            }
        }
        for (Ride r : rides) tickRide(r);
    }

    /** animace: plošina vyjede z propasti, pak se z ní stanou skutečné bloky */
    private void reveal(Plat pt, boolean instant) {
        int wd = pt.x2 - pt.x1 + 1, dp = pt.z2 - pt.z1 + 1;
        Location base = new Location(w, ox + pt.x1, oy + pt.y, oz + pt.z1);
        if (instant) {
            for (int x = pt.x1; x <= pt.x2; x++) for (int z = pt.z1; z <= pt.z2; z++) {
                Block bl = w.getBlockAt(ox + x, oy + pt.y, oz + z);
                if (bl.getType().isAir()) { bl.setType(pt.m, false); markPlaced(bl); }
            }
            for (Ride r : rides) if (!r.spawned && Math.abs(r.x - (pt.x1 + pt.x2) / 2.0) < 1.5 && Math.abs(r.y - pt.y) < 1 && r.z0 - (pt.z2) < 4.5 && r.z0 > pt.z2) spawnRide(r);
            return;
        }
        BlockDisplay d = spawn(base, BlockDisplay.class, e -> {
            e.setBlock(pt.m.createBlockData());
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(0, -9, 0), new AxisAngle4f(), new Vector3f(wd, 1, dp), new AxisAngle4f()));
        });
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0); d.setInterpolationDuration(16);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(wd, 1, dp), new AxisAngle4f()));
            w.playSound(base, Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.5f, 0.7f + rnd.nextFloat() * 0.5f);
            w.spawnParticle(Particle.CLOUD, base.clone().add(wd / 2.0, -2, dp / 2.0), 6, wd / 2.0, 1.5, dp / 2.0, 0.02);
        }, 3);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (dead) return;
            for (int x = pt.x1; x <= pt.x2; x++) for (int z = pt.z1; z <= pt.z2; z++) {
                Block bl = w.getBlockAt(ox + x, oy + pt.y, oz + z);
                if (bl.getType().isAir()) { bl.setType(pt.m, false); markPlaced(bl); }
            }
            if (d.isValid()) d.remove();
        }, 21);
        // vznášedlo patřící k této plošině se objeví spolu s ní
        for (Ride r : rides) if (!r.spawned && Math.abs(r.x - (pt.x1 + pt.x2) / 2.0) < 1.5 && Math.abs(r.y - pt.y) < 1 && r.z0 - (pt.z2) < 4.5 && r.z0 > pt.z2) spawnRide(r);
    }
    private boolean dead;

    private void spawnRide(Ride r) {
        r.spawned = true;
        Location l = new Location(w, ox + r.x + 0.5, oy + r.y + 1, oz + r.z0);
        r.seat = spawn(l, ArmorStand.class, e -> { e.setInvisible(true); e.setSmall(true); e.setGravity(false); e.setInvulnerable(true); e.setSilent(true); e.setBasePlate(false); });
        r.body = spawn(l.clone().add(-1.5, -0.45, -1.5), BlockDisplay.class, e -> {
            e.setBlock(Material.CYAN_CONCRETE.createBlockData()); e.setBrightness(new Display.Brightness(15, 15)); e.setTeleportDuration(2);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(3, 0.45f, 3), new AxisAngle4f()));
        });
        r.glow = spawn(l.clone().add(-1.2, -0.02, -1.2), BlockDisplay.class, e -> {
            e.setBlock(Material.SEA_LANTERN.createBlockData()); e.setBrightness(new Display.Brightness(15, 15)); e.setTeleportDuration(2);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.4f, 0.1f, 2.4f), new AxisAngle4f()));
        });
        w.spawnParticle(Particle.END_ROD, l, 20, 1.2, 0.3, 1.2, 0.05);
    }

    private void moveRide(Ride r) {
        double z = r.z0 + r.s;
        Location l = new Location(w, ox + r.x + 0.5, oy + r.y + 1, oz + z);
        r.seat.teleport(l);
        r.body.teleport(l.clone().add(-1.5, -0.45, -1.5));
        r.glow.teleport(l.clone().add(-1.2, -0.02, -1.2));
    }

    private void tickRide(Ride r) {
        if (!r.spawned || r.seat == null || !r.seat.isValid()) return;
        if (r.rider != null && (!r.rider.isOnline() || !r.seat.getPassengers().contains(r.rider))) { riding.remove(r.rider); r.rider = null; }
        // nástup: hráč přijde k vznášedlu, když čeká na startu
        if (r.rider == null && r.state == 0) {
            for (Player p : alive) {
                if (riding.containsKey(p)) continue;
                Location pl2 = p.getLocation(), c = r.seat.getLocation();
                if (Math.abs(pl2.getX() - c.getX()) < 1.7 && Math.abs(pl2.getZ() - c.getZ()) < 1.7 && Math.abs(pl2.getY() - c.getY()) < 1.6) {
                    r.rider = p; riding.put(p, r); r.seat.addPassenger(p); r.wait = 0;
                    U.title(p, "&b✦ Vznášedlo", "&7Vyskoč (mezerník / Shift) u zelené plošiny!");
                    U.sound(p, Sound.BLOCK_BEACON_ACTIVATE, 1.6f);
                    break;
                }
            }
        }
        // vyskočení (mezerník)
        if (r.rider != null && r.rider.getCurrentInput().isJump()) jumpOff(r.rider, r);
        switch (r.state) {
            case 0 -> { r.wait += 2; if ((r.rider != null && r.wait >= 30) || r.wait >= 140) r.state = 1; }
            case 1 -> { r.s += CARPET_V * 2; if (r.s >= r.z1 - r.z0) { r.s = r.z1 - r.z0; r.state = 2; r.wait = 0; } }
            case 2 -> { r.wait += 2; if (r.wait >= 30) r.state = 3; }
            case 3 -> { r.s -= CARPET_V * 2; if (r.s <= 0) { r.s = 0; r.state = 0; r.wait = 0; } }
        }
        moveRide(r);
        if (tick2 % 4 == 0) w.spawnParticle(Particle.END_ROD, r.seat.getLocation().add(0, -0.4, 0), 1, 1.0, 0.05, 1.0, 0.01);
    }

    private void jumpOff(Player p, Ride r) {
        if (r.rider != p) return;
        double vz = r.state == 1 ? CARPET_V : r.state == 3 ? -CARPET_V : 0;
        r.seat.removePassenger(p);
        r.rider = null; riding.remove(p);
        Bukkit.getScheduler().runTask(pl, () -> p.setVelocity(new Vector(r.side * 0.2, 0.46, vz)));
        U.sound(p, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f);
    }

    /** Shift = sesednutí z vznášedla -> také skok */
    @Override public boolean canExitVehicle(Player p) {
        Ride r = riding.get(p);
        if (r != null) {
            double vz = r.state == 1 ? CARPET_V : r.state == 3 ? -CARPET_V : 0;
            riding.remove(p); if (r.rider == p) r.rider = null;
            Bukkit.getScheduler().runTask(pl, () -> p.setVelocity(new Vector(r.side * 0.2, 0.46, vz)));
        }
        return true;
    }

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

    @Override public void onVoid(Player p) {
        Ride r = riding.remove(p);
        if (r != null && r.rider == p) r.rider = null;
        p.teleport(cp(progress.getOrDefault(p, 0))); p.setFallDistance(0); U.sound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1f);
    }

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
