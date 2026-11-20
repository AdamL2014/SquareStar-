package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.TextDisplay;
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
    // ---- Mario Kart režim: itemy, drift, boost pady ----
    private final Map<Player, Integer> spin = new HashMap<>(), star = new HashMap<>(), drift = new HashMap<>();
    private final Map<Player, String> held = new HashMap<>();
    private final List<double[]> boxes = new ArrayList<>();          // {x, z, respawnTick}
    private final List<Display[]> boxEnt = new ArrayList<>();
    private final List<Object[]> hazards = new ArrayList<>();       // {typ, ItemDisplay/BlockDisplay, x, z, vx, vz, ticks, owner, target}
    private final Set<Long> pads = new HashSet<>();
    private int ticks = 0;
    private final Map<Player, ItemDisplay> rovers = new HashMap<>();
    private final Map<Player, Integer> cp = new HashMap<>(), lap = new HashMap<>();
    private final List<Player> finished = new ArrayList<>();
    private final Set<Long> gate = new HashSet<>();
    private BukkitTask tick;
    private int go = -1;

    public RoverRace(GameManager gm, int slot) { super(gm, GameType.ROVER, slot); }

    @Override public List<String> maps() { return List.of("Kaňon Valles", "Olympus okruh", "Fobos sprint"); }
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
            double r = map == 2 ? 40 + 14 * Math.sin(4 * t) + 4 * Math.cos(7 * t) : olympus ? 46 + 12 * Math.sin(2 * t) + 6 * Math.cos(5 * t) : 44 + 10 * Math.sin(3 * t);
            path.add(new double[]{Math.cos(t) * r * (map == 2 ? 1.35 : 1.25), Math.sin(t) * r * (map == 2 ? 0.95 : 1)});
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
        // boost pady (šipky) a místa pro „?" boxy
        pads.clear(); boxes.clear();
        for (int k = 1; k < 6; k++) {
            int idx = k * path.size() / 6;
            double[] p = path.get(idx), q = path.get((idx + 3) % path.size());
            double nx = -(q[1] - p[1]), nz = q[0] - p[0], len = Math.hypot(nx, nz); nx /= len; nz /= len;
            if (k % 2 == 1) { // řada boxů napříč tratí
                for (int j = -1; j <= 1; j++) boxes.add(new double[]{p[0] + nx * j * 2.6 + 0.5, p[1] + nz * j * 2.6 + 0.5, 0});
            } else {          // boost pad
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int bx = (int) Math.round(p[0] + nx * 1.5) + dx, bz = (int) Math.round(p[1] + nz * 1.5) + dz;
                    b.set(ox + bx, oy, oz + bz, Material.MAGENTA_GLAZED_TERRACOTTA); pads.add(key(ox + bx, oz + bz));
                }
            }
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
        ticks++;
        tickBoxes();
        tickHazards();
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
        int sp = spin.getOrDefault(p, 0);
        if (sp > 0) {
            spin.put(p, sp - 1);
            f[1] += 0.55; v *= 0.85;
            if (sp % 4 == 0) w.spawnParticle(Particle.CRIT, cur.clone().add(0, 1, 0), 6, 0.4, 0.3, 0.4, 0.1);
            f[0] = v;
            st.setVelocity(new Vector(-Math.sin(f[1]) * v, Math.min(0, st.getVelocity().getY()), Math.cos(f[1]) * v));
            st.setRotation((float) Math.toDegrees(f[1]), 0);
            return;
        }
        int stT = star.getOrDefault(p, 0);
        if (stT > 0) { star.put(p, stT - 1); f[2] = Math.max(f[2], 2);
            w.spawnParticle(Particle.DUST, cur.clone().add(0, 1, 0), 4, 0.6, 0.4, 0.6, 0, new Particle.DustOptions(Color.fromRGB(java.awt.Color.HSBtoRGB((ticks % 30) / 30f, 1, 1) & 0xFFFFFF), 1.6f));
            for (Player o : alive) if (o != p && star.getOrDefault(o, 0) == 0 && boats.containsKey(o) && boats.get(o).getLocation().distanceSquared(cur) < 3) hit(o, 30, "&e⭐ " + p.getName() + " tě rozbil hvězdou!");
        }
        // boost pad pod rovrem
        if (pads.contains(key(cur.getBlockX(), cur.getBlockZ())) && f[2] < 20) { f[2] = 25; U.sound(p, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.6f); }
        // drift: Shift + zatáčení = ostřejší zatáčka a jiskry, po puštění mini turbo
        boolean drifting = !frozen && in.isSneak() && (in.isLeft() || in.isRight()) && v > 0.35;
        int dr = drift.getOrDefault(p, 0);
        if (drifting) {
            drift.put(p, dr + 1);
            Color c = dr > 45 ? Color.ORANGE : dr > 20 ? Color.AQUA : Color.WHITE;
            w.spawnParticle(Particle.DUST, cur.clone().add(Math.sin(f[1]) * 1.2, 0.2, -Math.cos(f[1]) * 1.2), 3, 0.3, 0.05, 0.3, 0, new Particle.DustOptions(c, 1.3f));
        } else if (dr > 0) {
            drift.put(p, 0);
            if (dr > 20) { f[2] = Math.max(f[2], dr > 45 ? 30 : 16); U.sound(p, Sound.ENTITY_BLAZE_SHOOT, dr > 45 ? 1.8f : 1.4f); U.bar(p, dr > 45 ? "&6&l🔥 SUPER DRIFT TURBO!" : "&b&lDrift turbo!"); }
        }
        if (!frozen) {
            if (in.isForward()) v += ACC;
            else if (in.isBackward()) v -= v > 0 ? BRAKE : ACC * 0.6;
            else v *= DRAG;
            if (in.isJump() && f[3] <= 0) { f[2] = 35; f[3] = 300; U.sound(p, Sound.ENTITY_BLAZE_SHOOT, 1.4f); U.bar(p, "&6&l🔥 TURBO!"); }
        } else v = 0;
        double max = (f[2] > 0 ? MAXV * 1.5 : MAXV) * (star.getOrDefault(p, 0) > 0 ? 1.15 : 1);
        v = Math.max(-0.25, Math.min(max, v));
        if (f[2] > 0) { f[2]--; v = Math.max(v, MAXV * 1.2); w.spawnParticle(Particle.FLAME, cur.clone().add(Math.sin(f[1]) * 1.6, 0.5, -Math.cos(f[1]) * 1.6), 4, 0.15, 0.1, 0.15, 0.02); }
        if (f[3] > 0) f[3]--;
        // zatáčení (citlivější při nízké rychlosti, obrácené při couvání)
        double steer = Math.toRadians(4.2) * Math.max(0.35, Math.min(1, Math.abs(v) / MAXV)) * (drifting ? 1.6 : 1);
        if (!frozen && Math.abs(v) > 0.02) {
            if (in.isLeft()) f[1] -= steer * Math.signum(v);
            if (in.isRight()) f[1] += steer * Math.signum(v);
        }
        f[0] = v;
        double dx = -Math.sin(f[1]) * v, dz = Math.cos(f[1]) * v;
        st.setVelocity(new Vector(dx, Math.min(0, st.getVelocity().getY()), dz));
        st.setRotation((float) Math.toDegrees(f[1]), 0);
    }

    // ================= „?" boxy =================
    private void tickBoxes() {
        if (boxEnt.isEmpty()) for (double[] bx : boxes) boxEnt.add(spawnBox(bx));
        for (int i = 0; i < boxes.size(); i++) {
            double[] bx = boxes.get(i);
            Display[] e = boxEnt.get(i);
            boolean visible = bx[2] <= ticks;
            if (visible && e[0] != null && e[0].isValid() && ticks % 2 == 0) {
                e[0].setInterpolationDelay(0); e[0].setInterpolationDuration(2);
                e[0].setTransformation(new Transformation(new Vector3f(-0.45f, -0.45f, -0.45f), new Quaternionf().rotateY(ticks * 0.08f).rotateX(0.4f), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
            }
            if (!visible) continue;
            Location c = loc(bx[0], 1.4, bx[1]);
            for (Player p : alive) {
                ArmorStand st = boats.get(p);
                if (st == null || st.getLocation().distanceSquared(c) > 3.2) continue;
                bx[2] = ticks + 60;
                for (Display d : e) if (d != null) d.setViewRange(0f);
                Bukkit.getScheduler().runTaskLater(pl, () -> { for (Display d : e) if (d != null && d.isValid()) d.setViewRange(1f); }, 60);
                w.spawnParticle(Particle.FIREWORK, c, 15, 0.3, 0.3, 0.3, 0.1);
                if (!held.containsKey(p)) giveItem(p);
                break;
            }
        }
    }
    private Display[] spawnBox(double[] bx) {
        Location c = loc(bx[0], 1.4, bx[1]);
        BlockDisplay cube = spawn(c, BlockDisplay.class, d -> { d.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData()); d.setBrightness(new Display.Brightness(15, 15)); });
        TextDisplay q = spawn(c.clone().add(0, -0.15, 0), TextDisplay.class, d -> {
            d.text(U.c("&e&l?")); d.setBillboard(Display.Billboard.CENTER); d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); d.setSeeThrough(true);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
        });
        return new Display[]{cube, q};
    }

    // ================= itemy =================
    private static final Object[][] ITEMS = {
            {"mushroom", Material.RED_MUSHROOM, "&c&lTurbo houba", "Pravý klik = turbo"},
            {"banana", Material.YELLOW_DYE, "&e&lBanán", "Pravý klik = polož za sebe"},
            {"green", Material.SLIME_BALL, "&a&lZelený krunýř", "Pravý klik = vystřel rovně"},
            {"red", Material.RED_DYE, "&c&lČervený krunýř", "Pravý klik = navede se na hráče před tebou"},
            {"star", Material.NETHER_STAR, "&6&l⭐ Hvězda", "Pravý klik = 6 s nezranitelnost a rychlost"},
            {"meteor", Material.MAGMA_CREAM, "&4&l☄ Meteorit", "Pravý klik = zasáhne všechny před tebou"}};
    private void giveItem(Player p) {
        // horší pozice = lepší itemy (jako v Mario Kartu)
        int pos = 1; for (Player o : alive) if (o != p && score(o) > score(p)) pos++;
        double back = alive.size() <= 1 ? 0.5 : (pos - 1) / (double) (alive.size() - 1);
        int[] weights = back < 0.34 ? new int[]{3, 5, 5, 1, 0, 0} : back < 0.67 ? new int[]{4, 3, 3, 3, 1, 0} : new int[]{5, 1, 2, 4, 2, 1};
        int sum = 0; for (int x : weights) sum += x;
        int r = rnd.nextInt(sum), i = 0; while (r >= weights[i]) r -= weights[i++];
        Object[] it = ITEMS[i];
        held.put(p, (String) it[0]);
        if (System.getProperty("ssdebug") != null) pl.getLogger().info("KART item " + it[0] + " -> " + p.getName());
        p.getInventory().setItem(0, U.tag(U.item((Material) it[1], 1, (String) it[2], "&7" + it[3]), "ss_kart", (String) it[0]));
        p.getInventory().setHeldItemSlot(0);
        U.title(p, "", it[2] + " &7(pravý klik)");
        U.sound(p, Sound.ENTITY_ITEM_PICKUP, 1.4f);
    }

    @Override public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        String id = U.tagOf(e.getItem(), "ss_kart");
        if (id == null || go >= 0 || !held.containsKey(p)) return;
        e.setCancelled(true);
        held.remove(p);
        p.getInventory().setItem(0, null);
        useItem(p, id);
    }
    @Override public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        String id = U.tagOf(p.getInventory().getItemInMainHand(), "ss_kart");
        if (id == null || go >= 0 || !held.containsKey(p)) return;
        e.setCancelled(true); held.remove(p); p.getInventory().setItem(0, null); useItem(p, id);
    }

    private void useItem(Player p, String id) {
        if (System.getProperty("ssdebug") != null) pl.getLogger().info("KART use " + id);
        double[] f = phys.get(p);
        ArmorStand st = boats.get(p);
        if (f == null || st == null) return;
        Location at = st.getLocation();
        double fx = -Math.sin(f[1]), fz = Math.cos(f[1]);
        switch (id) {
            case "mushroom" -> { f[2] = 40; U.sound(p, Sound.ENTITY_GENERIC_EAT, 1.4f); }
            case "star" -> { star.put(p, 120); U.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 2f); broadcast("&6⭐ " + p.getName() + " &emá hvězdu!"); }
            case "banana" -> hazards.add(new Object[]{"banana", item(at.clone().add(-fx * 2.4, 0.3, -fz * 2.4), Material.YELLOW_DYE, 1.3f), at.getX() - fx * 2.4, at.getZ() - fz * 2.4, 0.0, 0.0, 1200, p, null});
            case "green" -> hazards.add(new Object[]{"green", shell(at.clone().add(fx * 2, 0.4, fz * 2), Material.LIME_CONCRETE), at.getX() + fx * 2, at.getZ() + fz * 2, fx * 1.05, fz * 1.05, 100, p, null});
            case "red" -> {
                Player target = null; double best = Double.MAX_VALUE, me = score(p);
                for (Player o : alive) if (o != p && score(o) > me && score(o) - me < best) { best = score(o) - me; target = o; }
                if (target == null) for (Player o : alive) if (o != p && (target == null || boats.get(o).getLocation().distanceSquared(at) < boats.get(target).getLocation().distanceSquared(at))) target = o;
                hazards.add(new Object[]{"red", shell(at.clone().add(fx * 2, 0.4, fz * 2), Material.RED_CONCRETE), at.getX() + fx * 2, at.getZ() + fz * 2, fx, fz, 160, p, target});
            }
            case "meteor" -> {
                double me = score(p);
                for (Player o : alive) if (o != p && score(o) > me) { hit(o, 35, "&4☄ Meteorit od " + p.getName() + "!"); w.strikeLightningEffect(boats.get(o).getLocation()); }
                broadcast("&4☄ " + p.getName() + " &cposlal meteorit na všechny před sebou!");
            }
        }
        U.sound(p, Sound.ENTITY_SNOWBALL_THROW, 1.2f);
    }
    private ItemDisplay item(Location l, Material m, float scale) {
        return spawn(l, ItemDisplay.class, d -> { d.setItemStack(new ItemStack(m)); d.setBillboard(Display.Billboard.VERTICAL); d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf())); });
    }
    private BlockDisplay shell(Location l, Material m) {
        return spawn(l, BlockDisplay.class, d -> { d.setBlock(m.createBlockData()); d.setTeleportDuration(1); d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(-0.35f, 0, -0.35f), new Quaternionf(), new Vector3f(0.7f, 0.5f, 0.7f), new Quaternionf())); });
    }

    private void tickHazards() {
        for (Iterator<Object[]> it = hazards.iterator(); it.hasNext(); ) {
            Object[] h = it.next();
            Display d = (Display) h[1];
            int life = (int) h[6] - 1; h[6] = life;
            double x = (double) h[2], z = (double) h[3], vx = (double) h[4], vz = (double) h[5];
            if (h[0].equals("red") && h[8] instanceof Player t && boats.containsKey(t)) {
                Location tl = boats.get(t).getLocation();
                double dx = tl.getX() - x, dz = tl.getZ() - z, len = Math.max(0.01, Math.hypot(dx, dz));
                vx = vx * 0.7 + dx / len * 1.25 * 0.3; vz = vz * 0.7 + dz / len * 1.25 * 0.3;
                double l2 = Math.hypot(vx, vz); vx = vx / l2 * 1.15; vz = vz / l2 * 1.15;
                h[4] = vx; h[5] = vz;
            }
            if (vx != 0 || vz != 0) {
                double nx = x + vx, nz = z + vz;
                if (w.getBlockAt((int) Math.floor(nx), oy + 1, (int) Math.floor(nz)).getType().isSolid()) {
                    if (h[0].equals("green")) { // odraz od zdi
                        if (w.getBlockAt((int) Math.floor(nx), oy + 1, (int) Math.floor(z)).getType().isSolid()) vx = -vx; else vz = -vz;
                        h[4] = vx; h[5] = vz; nx = x + vx; nz = z + vz;
                    } else { life = 0; }
                }
                x = nx; z = nz; h[2] = x; h[3] = z;
                if (d.isValid()) d.teleport(new Location(w, x, oy + 1.25, z));
                if (ticks % 2 == 0) w.spawnParticle(Particle.CLOUD, new Location(w, x, oy + 1.3, z), 1, 0, 0, 0, 0);
            }
            boolean done = life <= 0;
            for (Player o : alive) {
                ArmorStand st = boats.get(o);
                if (st == null || (o == h[7] && (int) h[6] > (h[0].equals("banana") ? 1170 : 90))) continue; // chvíli nezasáhne vlastníka
                Location ol = st.getLocation();
                if (Math.hypot(ol.getX() - x, ol.getZ() - z) < 1.5) {
                    if (star.getOrDefault(o, 0) == 0) hit(o, h[0].equals("banana") ? 26 : 38, h[0].equals("banana") ? "&eUklouzl jsi po banánu!" : "&cZásah krunýřem!");
                    done = true; break;
                }
            }
            if (done) { if (d.isValid()) d.remove(); it.remove(); }
        }
    }

    private void hit(Player o, int ticksSpin, String msg) {
        if (spin.getOrDefault(o, 0) > 0) return;
        spin.put(o, ticksSpin);
        U.title(o, "&c&lAU!", msg);
        U.sound(o, Sound.ENTITY_ITEM_BREAK, 0.7f);
        ArmorStand st = boats.get(o);
        if (st != null) w.spawnParticle(Particle.EXPLOSION, st.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
    }

    private void progress(Player p, Location at) {
        int next = cp.getOrDefault(p, 0) + 1;
        int idx = (next % CP) * path.size() / CP;
        double[] t = path.get(idx);
        if (at.distanceSquared(loc(t[0], 1, t[1])) > (HALF_W + 3) * (HALF_W + 3)) return;
        if (System.getProperty("ssdebug") != null) pl.getLogger().info("KART cp " + next + " lap " + lap.get(p));
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
        return (go >= 0 ? "&7W plyn, A/D zatáčení, Shift+A/D drift, Mezerník turbo, pravý klik = item &8| " : (held.containsKey(p) ? "&dItem: pravý klik &8| " : "")) + "&6Kolo &f" + Math.min(LAPS, lap.getOrDefault(p, 1)) + "/" + LAPS + " &8| &eCheckpoint &f" + cp.getOrDefault(p, 0) + "/" + CP + " &8| &bPozice &f" + pos + "/" + alive.size() + " &8| &7" + fmt(time);
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
        for (Object[] h : hazards) ((Display) h[1]).remove();
        for (Display[] e : boxEnt) for (Display d : e) if (d != null) d.remove();
        hazards.clear(); boxEnt.clear();
        for (ItemDisplay d : rovers.values()) d.remove();
        boats.clear(); rovers.clear();
    }
}
