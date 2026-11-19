package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Závod Mars roverů: jezdíš roverem (pod ním je loďka na ledě) po okruhu na Marsu.
 * Projeď všechny checkpointy, 3 kola, kdo první v cíli vyhrává.
 */
public final class RoverRace extends Game {
    private static final int LAPS = 3, CP = 16, HALF_W = 4;
    private final List<double[]> path = new ArrayList<>();      // body osy trati (x, z)
    private final Map<Player, ArmorStand> boats = new HashMap<>();
    private final Map<Player, double[]> phys = new HashMap<>(); // {rychlost, směr (rad), boost tiků, cooldown tiků, x, z}
    private static final double MAXV = 0.72, ACC = 0.03, BRAKE = 0.06, DRAG = 0.975;
    private final Map<Player, ItemDisplay> rovers = new HashMap<>();
    private final Map<Player, Integer> cp = new HashMap<>(), lap = new HashMap<>();
    private final List<Player> finished = new ArrayList<>();
    private final Set<Long> gate = new HashSet<>();
    private BukkitTask tick;
    private int go = -1;

    public RoverRace(GameManager gm, int slot) { super(gm, GameType.ROVER, slot); }

    @Override public List<String> maps() { return List.of("Kaňon Valles", "Olympus okruh"); }
    @Override protected int timeLimit() { return 420; }
    @Override protected int voidY() { return oy - 15; }
    @Override public boolean canExitVehicle(Player p) { return !alive.contains(p) || finished.contains(p); }

    // ---------------- trať ----------------
    private void makePath() {
        path.clear();
        boolean olympus = map == 1;
        int n = 720;
        for (int i = 0; i < n; i++) {
            double t = i * Math.PI * 2 / n;
            double r = olympus ? 46 + 12 * Math.sin(2 * t) + 6 * Math.cos(5 * t) : 44 + 10 * Math.sin(3 * t);
            path.add(new double[]{Math.cos(t) * r * 1.25, Math.sin(t) * r});
        }
    }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        makePath();
        Set<Long> track = new HashSet<>();
        for (double[] p : path)
            for (int dx = -HALF_W; dx <= HALF_W; dx++) for (int dz = -HALF_W; dz <= HALF_W; dz++)
                if (dx * dx + dz * dz <= HALF_W * HALF_W + 1) track.add(key((int) Math.round(p[0]) + dx, (int) Math.round(p[1]) + dz));
        Set<Long> center = new HashSet<>();
        for (int i = 0; i < path.size(); i++) if ((i / 10) % 2 == 0) { double[] p = path.get(i); center.add(key((int) Math.round(p[0]), (int) Math.round(p[1]))); }
        boolean olympus = map == 1;
        // marsovská pláň
        for (int x = -78; x <= 78; x++) for (int z = -64; z <= 64; z++) {
            long k = key(x, z);
            if (track.contains(k)) {
                b.set(ox + x, oy, oz + z, center.contains(k) ? Material.YELLOW_CONCRETE : (((x * 7 + z * 13) & 7) == 0 ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE));
            } else {
                boolean border = track.contains(key(x + 1, z)) || track.contains(key(x - 1, z)) || track.contains(key(x, z + 1)) || track.contains(key(x, z - 1));
                b.set(ox + x, oy, oz + z, (x * 31 + z * 17) % 7 == 0 ? Material.RED_SANDSTONE : (olympus ? Material.TERRACOTTA : Material.RED_SAND));
                if (border) {
                    b.set(ox + x, oy + 1, oz + z, ((x + z) & 3) == 0 ? Material.SHROOMLIGHT : (olympus ? Material.ORANGE_TERRACOTTA : Material.RED_TERRACOTTA));
                    b.set(ox + x, oy + 2, oz + z, Material.RED_STAINED_GLASS_PANE);
                }
            }
            b.set(ox + x, oy - 1, oz + z, Material.RED_SANDSTONE);
        }
        // skály a krátery okolo
        for (int i = 0; i < 26; i++) {
            int x = rnd.nextInt(150) - 75, z = rnd.nextInt(124) - 62;
            if (near(x, z, track, 6)) continue;
            int r = 2 + rnd.nextInt(3);
            b.sphere(ox + x, oy, oz + z, r, p -> b.set(p[0], p[1], p[2], rnd.nextInt(3) == 0 ? Material.GRANITE : Material.RED_SANDSTONE));
        }
        // startovní / cílová čára (šachovnice)
        double[] s = path.get(0);
        for (int dz = -HALF_W; dz <= HALF_W; dz++) for (int dx = 0; dx <= 1; dx++)
            b.set(ox + (int) Math.round(s[0]) + dx, oy, oz + (int) Math.round(s[1]) + dz, ((dz + dx) & 1) == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE);
        // brána nad startem
        int sx = ox + (int) Math.round(s[0]), sz = oz + (int) Math.round(s[1]);
        b.fill(sx, oy + 1, sz - HALF_W - 1, sx, oy + 7, sz - HALF_W - 1, Material.IRON_BLOCK);
        b.fill(sx, oy + 1, sz + HALF_W + 1, sx, oy + 7, sz + HALF_W + 1, Material.IRON_BLOCK);
        b.fill(sx, oy + 7, sz - HALF_W - 1, sx, oy + 7, sz + HALF_W + 1, Material.SEA_LANTERN);
        // startovní zátaras (sklo), zmizí po odpočtu
        for (int dz = -HALF_W; dz <= HALF_W; dz++) { b.set(sx + 2, oy + 1, sz + dz, Material.LIME_STAINED_GLASS); gate.add(key(sx + 2, sz + dz)); }
    }
    private boolean near(int x, int z, Set<Long> t, int r) {
        for (int dx = -r; dx <= r; dx += 2) for (int dz = -r; dz <= r; dz += 2) if (t.contains(key(x + dx, z + dz))) return true;
        return false;
    }

    // ---------------- start ----------------
    @Override protected void start() {
        if (path.isEmpty()) makePath();
        double[] s = path.get(0), s2 = path.get(path.size() - 8);
        Vector back = new Vector(s2[0] - s[0], 0, s2[1] - s[1]).normalize(); // dozadu od čáry
        Vector side = new Vector(-back.getZ(), 0, back.getX());
        float yaw = (float) Math.toDegrees(Math.atan2(back.getX(), -back.getZ()));
        int i = 0;
        for (Player p : players) {
            int row = i / 3, col = i % 3 - 1;
            Location l = loc(s[0] + 0.5, 1, s[1] + 0.5).add(back.clone().multiply(3 + row * 3.2)).add(side.clone().multiply(col * 2.6));
            l.setYaw(yaw);
            ArmorStand boat = w.spawn(l, ArmorStand.class, e -> {
                e.setInvisible(true); e.setSmall(true); e.setBasePlate(false); e.setInvulnerable(true);
                e.setPersistent(false); e.setSilent(true); e.setRotation(yaw, 0);
            });
            boat.addPassenger(p);
            phys.put(p, new double[]{0, Math.toRadians(yaw), 0, 0, l.getX(), l.getZ()});
            boats.put(p, boat);
            rovers.put(p, spawn(l, ItemDisplay.class, d -> {
                ItemStack it = new ItemStack(Material.PAPER);
                ItemMeta m = it.getItemMeta(); m.setItemModel(new NamespacedKey("squarestar", "rover")); it.setItemMeta(m);
                d.setItemStack(it);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(0, 0.85f, 0), new Quaternionf(), new Vector3f(1.7f, 1.7f, 1.7f), new Quaternionf()));
                d.setTeleportDuration(1);
                d.setBrightness(new Display.Brightness(15, 15));
            }));
            cp.put(p, 0); lap.put(p, 1);
            i++;
        }
        go = 4;
        tick = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1, 1);
    }

    private void tick() {
        if (state != State.RUNNING) return;
        for (Player p : new ArrayList<>(alive)) {
            ArmorStand b = boats.get(p);
            ItemDisplay d = rovers.get(p);
            if (b == null || !b.isValid()) continue;
            if (!b.getPassengers().contains(p) && !finished.contains(p)) b.addPassenger(p);
            drive(p, b);
            if (d != null && d.isValid()) { Location l = b.getLocation(); l.setYaw((float) Math.toDegrees(phys.get(p)[1])); l.setPitch(0); d.teleport(l); }
            if (go < 0) progress(p, b.getLocation());
            if (Math.abs(phys.get(p)[0]) > 0.3) w.spawnParticle(Particle.DUST, b.getLocation().add(0, 0.3, 0), 2, 0.4, 0.1, 0.4, 0, new Particle.DustOptions(Color.fromRGB(190, 80, 40), 1.4f));
        }
    }

    /** řízení roveru: W plyn, S brzda/couvání, A/D zatáčení, mezerník = turbo */
    private void drive(Player p, ArmorStand st) {
        double[] f = phys.get(p);
        if (f == null) return;
        boolean frozen = go >= 0 || finished.contains(p);
        org.bukkit.Input in = p.getCurrentInput();
        if (time % 2 == 0 && System.getProperty("ssdebug") != null) pl.getLogger().info("ROVER " + p.getName() + " in=" + in + " v=" + f[0] + " frozen=" + frozen + " at=" + st.getLocation().toVector());
        double v = f[0];
        // náraz do zdi: rover se skoro nepohnul, přestože jel rychle
        Location cur = st.getLocation();
        double moved = Math.hypot(cur.getX() - f[4], cur.getZ() - f[5]);
        if (Math.abs(v) > 0.25 && moved < Math.abs(v) * 0.35) { v *= 0.35; w.playSound(cur, Sound.ENTITY_IRON_GOLEM_HURT, 0.4f, 1.6f); }
        f[4] = cur.getX(); f[5] = cur.getZ();
        if (!frozen) {
            if (in.isForward()) v += ACC;
            else if (in.isBackward()) v -= v > 0 ? BRAKE : ACC * 0.6;
            else v *= DRAG;
            if (in.isJump() && f[3] <= 0) { f[2] = 40; f[3] = 160; U.sound(p, Sound.ENTITY_BLAZE_SHOOT, 1.4f); U.bar(p, "&6&l🔥 TURBO!"); }
        } else v = 0;
        double max = f[2] > 0 ? MAXV * 1.5 : MAXV;
        v = Math.max(-0.25, Math.min(max, v));
        if (f[2] > 0) { f[2]--; v = Math.max(v, MAXV * 1.2); w.spawnParticle(Particle.FLAME, cur.clone().add(Math.sin(f[1]) * 1.6, 0.5, -Math.cos(f[1]) * 1.6), 4, 0.15, 0.1, 0.15, 0.02); }
        if (f[3] > 0) f[3]--;
        // zatáčení (citlivější při nízké rychlosti, obrácené při couvání)
        double steer = Math.toRadians(4.2) * Math.max(0.35, Math.min(1, Math.abs(v) / MAXV));
        if (!frozen && Math.abs(v) > 0.02) {
            if (in.isLeft()) f[1] -= steer * Math.signum(v);
            if (in.isRight()) f[1] += steer * Math.signum(v);
        }
        f[0] = v;
        double dx = -Math.sin(f[1]) * v, dz = Math.cos(f[1]) * v;
        st.setVelocity(new Vector(dx, Math.min(0, st.getVelocity().getY()), dz));
        st.setRotation((float) Math.toDegrees(f[1]), 0);
    }

    private void progress(Player p, Location at) {
        int next = cp.getOrDefault(p, 0) + 1;
        int idx = (next % CP) * path.size() / CP;
        double[] t = path.get(idx);
        if (at.distanceSquared(loc(t[0], 1, t[1])) > (HALF_W + 3) * (HALF_W + 3)) return;
        if (next % CP == 0) {
            int l = lap.merge(p, 1, Integer::sum);
            cp.put(p, 0);
            if (l > LAPS) { finish(p); return; }
            U.title(p, l == LAPS ? "&c&lPOSLEDNÍ KOLO!" : "&e&lKolo " + l + "/" + LAPS, "");
            U.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
        } else {
            cp.put(p, next);
            U.sound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.8f);
        }
    }

    private void finish(Player p) {
        finished.add(p);
        int place = finished.size();
        broadcast("&6&l🏁 " + p.getName() + " &edojel do cíle jako &f" + place + ". &7(" + fmt(time) + ")");
        U.title(p, place == 1 ? "&6&l1. MÍSTO!" : "&e&l" + place + ". místo", "&7Čas: &f" + fmt(time));
        w.spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
        if (place == 1 || finished.size() >= alive.size()) end(List.of(finished.get(0)));
    }

    @Override protected void second() {
        if (go > 0) {
            for (Player p : players) { U.title(p, go == 1 ? "&a&lJEĎ!" : "&e&l" + (go - 1), ""); U.sound(p, go == 1 ? Sound.ENTITY_FIREWORK_ROCKET_LAUNCH : Sound.BLOCK_NOTE_BLOCK_BASS, go == 1 ? 1f : 1.2f); }
            go--;
            if (go == 0) {
                for (long k : gate) w.getBlockAt((int) (k >> 32), oy + 1, (int) k).setType(Material.AIR, false);
                go = -1;
            }
        }
    }

    @Override protected String status(Player p) {
        int pos = 1;
        double me = score(p);
        for (Player o : alive) if (o != p && score(o) > me) pos++;
        return (go >= 0 ? "&7W = plyn, A/D = zatáčení, S = brzda, Mezerník = turbo &8| " : "") + "&6Kolo &f" + Math.min(LAPS, lap.getOrDefault(p, 1)) + "/" + LAPS + " &8| &eCheckpoint &f" + cp.getOrDefault(p, 0) + "/" + CP + " &8| &bPozice &f" + pos + "/" + alive.size() + " &8| &7" + fmt(time);
    }
    private double score(Player p) { return lap.getOrDefault(p, 1) * 100 + cp.getOrDefault(p, 0); }

    @Override public void onDeath(Player p, Player killer) {
        // spadl z trati: zpátky na poslední checkpoint
        ArmorStand b = boats.get(p);
        int ci = (cp.getOrDefault(p, 0) % CP) * path.size() / CP;
        double[] t = path.get(ci), t2 = path.get((ci + 5) % path.size());
        Location l = loc(t[0] + 0.5, 1.2, t[1] + 0.5);
        U.reset(p, GameMode.ADVENTURE);
        double head = Math.atan2(-(t2[0] - t[0]), t2[1] - t[1]);
        double[] f = phys.get(p); if (f != null) { f[0] = 0; f[1] = head; f[4] = l.getX(); f[5] = l.getZ(); }
        if (b != null && b.isValid()) { b.eject(); b.teleport(l); p.teleport(l); b.addPassenger(p); }
        else p.teleport(l);
    }

    @Override protected void timeUp() {
        Player best = null;
        for (Player p : alive) if (best == null || score(p) > score(best)) best = p;
        end(finished.isEmpty() ? (best == null ? List.of() : List.of(best)) : List.of(finished.get(0)));
    }

    @Override protected void onLeave(Player p) {
        ArmorStand b = boats.remove(p); if (b != null) b.remove();
        ItemDisplay d = rovers.remove(p); if (d != null) d.remove();
    }

    @Override protected void onDestroy() {
        if (tick != null) tick.cancel();
        for (ArmorStand b : boats.values()) b.remove();
        for (ItemDisplay d : rovers.values()) d.remove();
        boats.clear(); rovers.clear();
    }
}
