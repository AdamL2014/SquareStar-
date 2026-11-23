package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.SquareStar;
import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Neon Beat (jako Beat Saber): kostky letí tunelem v rytmu skutečné minecraftové hudby (disky).
 * Červená = levé tlačítko, modrá = pravé. Zamiř na kostku a klikni, když je u bílé čáry. TNT netrefuj!
 * Noty jsou vygenerované z analýzy zvuku každého disku (beat/*.csv), takže sedí na hudbu.
 */
public final class Beat extends Game {
    private static final int FLIGHT = 100, PRE = 110, MINGAP = 18;
    private static final double D = 28.0, IDEAL = 4.5, MIN_DZ = 1.2, MAX_DZ = 7.5, CONE = 0.94;
    private static final double[] COLX = {-2.25, -0.75, 0.75, 2.25};
    private static final double[] ROWY = {1.5, 2.5, 3.5};
    private static final int HIT_TICKS = (int) Math.round(FLIGHT * (D - IDEAL) / D);   // za kolik ticků od spawnu je kostka v ideální zóně

    /** {id, název, ikona} */
    private static final String[][] SONGS = {
            {"cat", "Cat", "MUSIC_DISC_CAT"}, {"pigstep", "Pigstep", "MUSIC_DISC_PIGSTEP"}, {"otherside", "Otherside", "MUSIC_DISC_OTHERSIDE"},
            {"relic", "Relic", "MUSIC_DISC_RELIC"}, {"5", "5", "MUSIC_DISC_5"}, {"creator", "Creator", "MUSIC_DISC_CREATOR"},
            {"precipice", "Precipice", "MUSIC_DISC_PRECIPICE"}, {"tears", "Tears", "MUSIC_DISC_TEARS"}, {"lava_chicken", "Lava Chicken", "MUSIC_DISC_LAVA_CHICKEN"},
            {"chirp", "Chirp", "MUSIC_DISC_CHIRP"}, {"far", "Far", "MUSIC_DISC_FAR"}, {"mall", "Mall", "MUSIC_DISC_MALL"},
            {"strad", "Strad", "MUSIC_DISC_STRAD"}, {"stal", "Stal", "MUSIC_DISC_STAL"}, {"ward", "Ward", "MUSIC_DISC_WARD"},
            {"wait", "Wait", "MUSIC_DISC_WAIT"}, {"blocks", "Blocks", "MUSIC_DISC_BLOCKS"}, {"mellohi", "Mellohi", "MUSIC_DISC_MELLOHI"},
            {"13", "13", "MUSIC_DISC_13"}, {"11", "11", "MUSIC_DISC_11"}, {"creator_music_box", "Creator (Music Box)", "MUSIC_DISC_CREATOR_MUSIC_BOX"}};
    private static final int[] SONG_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private record Note(int hit, int col, int row, int kind) {}   // kind 0 červená, 1 modrá, 2 bomba
    private record Chart(double bpm, double first, double len, int notes, List<Note> list) {}
    private static final Map<String, Chart> CHARTS = new HashMap<>();

    private static Chart chart(SquareStar pl, String id) {
        return CHARTS.computeIfAbsent(id, k -> {
            try (InputStream in = pl.getResource("beat/" + k + ".csv")) {
                if (in == null) return null;
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String[] h = r.readLine().split(",");
                List<Note> l = new ArrayList<>(); String ln;
                int prev = -1000; double half = 600.0 / Double.parseDouble(h[0]), f0 = Double.parseDouble(h[1]) * 20;
                while ((ln = r.readLine()) != null) { String[] c = ln.split(","); if (c.length == 4) { int hit = (int) Math.round(Math.round((Integer.parseInt(c[0]) - f0) / half) * half + f0); if (hit - prev < MINGAP) continue; prev = hit; { int kd = Integer.parseInt(c[3]); l.add(new Note(hit, Integer.parseInt(c[1]), Integer.parseInt(c[2]), kd == 2 ? 2 : 0)); } } }
                return new Chart(Double.parseDouble(h[0]), Double.parseDouble(h[1]), Double.parseDouble(h[2]), l.size(), l);
            } catch (Exception e) { return null; }
        });
    }

    private static final class Live { Note n; BlockDisplay d; List<BlockDisplay> extra = new ArrayList<>(); int spawn; boolean done; double wx, ww, cx, cy; List<BlockDisplay> all() { List<BlockDisplay> a = new ArrayList<>(); a.add(d); a.addAll(extra); return a; } }
    private static final class St {
        int score, combo, best, energy = 100, hits, misses, idx, hudC = -1, hudM = -1; double sx; String last = ""; TextDisplay comboTd, multTd;
        final List<Live> live = new ArrayList<>();
    }
    public static final class SongHolder implements InventoryHolder {
        Inventory inv; final Beat game; SongHolder(Beat g) { game = g; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private static boolean registered;
    private final Map<Player, St> st = new LinkedHashMap<>();
    private final Map<Player, String> votes = new HashMap<>();
    private final Map<Integer, List<int[]>> lights = new HashMap<>();   // pilíře pro světelnou show
    private String song = "cat"; private Chart ch; private double beatLen = 9; private int beatIdx; private int t, ci, endTick, beatTicks, firstBeat, wi; private final List<int[]> walls = new ArrayList<>();   // {tick příletu, strana}
    private BukkitTask task;

    public Beat(GameManager gm, int slot) {
        super(gm, GameType.BEAT, slot);
        if (!registered) { registered = true; Bukkit.getPluginManager().registerEvents(new Ui(pl), pl); }
    }

    @Override public List<String> maps() { return List.of("Neonový tunel"); }
    @Override protected int timeLimit() { return 900; }
    @Override protected int voidY() { return oy - 40; }
    private double sx(int idx) { return ox + 0.5 + idx * 11; }

    private static String songName(String id) { for (String[] s : SONGS) if (s[0].equals(id)) return s[1]; return id; }

    // ---------------- výběr písničky ----------------
    @Override protected void onJoin(Player p) {
        ItemStack it = U.tag(U.item(Material.MUSIC_DISC_CAT, 1, "&d&lVyber písničku &7(pravý klik)", "&7Hudba z minecraftových disků"), "ss_beat", "songs");
        var m = it.getItemMeta(); m.addItemFlags(org.bukkit.inventory.ItemFlag.values()); it.setItemMeta(m);
        p.getInventory().setItem(4, it);
    }

    void openSongs(Player p) {
        SongHolder h = new SongHolder(this);
        h.inv = Bukkit.createInventory(h, 54, U.c("&d&lNeon Beat &8» &fVyber písničku"));
        ItemStack bg = U.item(Material.BLACK_STAINED_GLASS_PANE, 1, "&r ");
        for (int i = 0; i < 54; i++) h.inv.setItem(i, bg);
        for (int i = 0; i < SONGS.length; i++) {
            String[] s = SONGS[i]; Chart c = chart(pl, s[0]);
            Material mat; try { mat = Material.valueOf(s[2]); } catch (IllegalArgumentException e) { mat = Material.MUSIC_DISC_13; }
            long n = votes.values().stream().filter(v -> v.equals(s[0])).count();
            double dens = c == null ? 0 : c.notes / Math.max(1, c.len);
            String diff = dens < 1.0 ? "&aLehká" : dens < 1.5 ? "&eStřední" : dens < 1.8 ? "&6Těžší" : "&cTěžká";
            ItemStack it = U.item(mat, 1, "&d&l" + s[1], c == null ? "&cchybí chart" : "&7Délka: &f" + (int) c.len / 60 + ":" + String.format("%02d", (int) c.len % 60),
                    "&7Tempo: &f" + (c == null ? 0 : Math.round(c.bpm)) + " BPM", "&7Kostek: &f" + (c == null ? 0 : c.notes), "&7Obtížnost: " + diff, "&7Hlasů: &a" + n,
                    s[0].equals(votes.get(p)) ? "&a✔ Tvůj hlas" : "&eKlikni pro hlas");
            var m = it.getItemMeta(); m.addItemFlags(org.bukkit.inventory.ItemFlag.values()); if (s[0].equals(votes.get(p))) m.setEnchantmentGlintOverride(true); it.setItemMeta(m);
            h.inv.setItem(SONG_SLOTS[i], U.tag(it, "ss_song", s[0]));
        }
        h.inv.setItem(49, U.tag(U.item(Material.ENDER_EYE, 1, "&7&lNáhodná písnička"), "ss_song", "-"));
        p.openInventory(h.inv);
    }

    void pick(Player p, String id) {
        if (state != State.WAITING && state != State.COUNTDOWN && state != State.BUILDING) { U.msg(p, "&cHra už začala."); p.closeInventory(); return; }
        if (id.equals("-")) votes.remove(p); else votes.put(p, id);
        U.msg(p, id.equals("-") ? "&7Písničku vybere hra." : "&aHlasuješ pro &d" + songName(id));
        U.sound(p, Sound.UI_BUTTON_CLICK, 1.5f);
        openSongs(p);
    }

    private void chooseSong() {
        Map<String, Integer> cnt = new HashMap<>();
        for (String v : votes.values()) cnt.merge(v, 1, Integer::sum);
        String best = null;
        for (var e : cnt.entrySet()) if (best == null || e.getValue() > cnt.get(best) || (e.getValue().equals(cnt.get(best)) && rnd.nextBoolean())) best = e.getKey();
        if (best == null) best = SONGS[rnd.nextInt(SONGS.length)][0];
        song = best; ch = chart(pl, song);
        if (ch == null) { song = "cat"; ch = chart(pl, song); }
    }

    // ---------------- stavba: otevřený neonový tunel (nic nad hráčem) ----------------
    /** Tunel jako v Beat Saberu: uzavřená červená chodba, bránami tvaru „domečku“ (obdélník + střecha) a svítícími čarami na podlaze. */
    @Override protected void buildMap(U.BlockQueue.Builder b) {
        int cx = (int) Math.floor(sx(0));
        lights.clear();
        final int W = 7, z0 = oz - 84, z1 = oz + 4;
        List<int[]> li = new ArrayList<>();
        // podlaha
        b.fill(cx - W, oy - 2, z0, cx + W, oy - 2, z1, Material.BLACK_CONCRETE);        // propast po stranách
        b.fill(cx - 3, oy, z0, cx + 3, oy, z1, Material.BLACK_CONCRETE);                // „most“, po kterém jedou kostky
        b.fill(cx - 3, oy - 1, z0, cx + 3, oy - 1, z1, Material.RED_NETHER_BRICKS);
        // stěny + střecha (špičatá)
        b.fill(cx - W, oy - 1, z0, cx - W, oy + 9, z1, Material.RED_NETHER_BRICKS);
        b.fill(cx + W, oy - 1, z0, cx + W, oy + 9, z1, Material.RED_NETHER_BRICKS);
        int[] hh = new int[2 * W + 1];
        for (int dx = -W; dx <= W; dx++) {
            hh[dx + W] = oy + 9 + (int) Math.round((W - Math.abs(dx)) * 4.0 / W);
            b.fill(cx + dx, hh[dx + W] - 1, z0, cx + dx, hh[dx + W], z1, Material.RED_NETHER_BRICKS);
        }
        // zadní zeď (za hráčem) a koncová zeď s rozžhaveným středem
        for (int dx = -W; dx <= W; dx++) {
            b.fill(cx + dx, oy + 1, z1, cx + dx, hh[dx + W], z1, Material.BLACK_CONCRETE);
            if (Math.abs(dx) < W) b.fill(cx + dx, oy + 1, z0 - 1, cx + dx, hh[dx + W] - 2, z0 - 1, Material.RED_CONCRETE);
        }
        // vodorovný pruh po stěnách
        b.fill(cx - W, oy + 5, z0, cx - W, oy + 5, z1 - 1, Material.RED_TERRACOTTA);
        b.fill(cx + W, oy + 5, z0, cx + W, oy + 5, z1 - 1, Material.RED_TERRACOTTA);
        // brány: obrys „domečku“ z jasných čar (každých 8 bloků)
        for (int z = oz - 8; z >= z0; z -= 8) {
            for (int y = 1; y <= 8; y++) for (int sd : new int[]{-W, W}) { b.set(cx + sd, oy + y, z, Material.RED_CONCRETE); li.add(new int[]{cx + sd, oy + y, z}); }
            for (int dx = -W; dx <= W; dx++) { int y = hh[dx + W] - 1; b.set(cx + dx, y, z, Material.RED_CONCRETE); li.add(new int[]{cx + dx, y, z}); }
        }
        // světelné čáry na podlaze (v dálce se sbíhají)
        for (int x : new int[]{-3, -1, 1, 3}) for (int z = z0; z <= z1 - 1; z++) { b.set(cx + x, oy, z, Material.RED_CONCRETE); li.add(new int[]{cx + x, oy, z}); }
        // hranice zóny zásahu
        b.fill(cx - 3, oy, oz - 3, cx + 3, oy, oz - 3, Material.PINK_CONCRETE);
        // neviditelné bariéry kolem hráče: hýbe se jen do stran
        b.fill(cx - 4, oy + 1, oz - 1, cx - 4, oy + 9, oz + 2, Material.BARRIER);
        b.fill(cx + 4, oy + 1, oz - 1, cx + 4, oy + 9, oz + 2, Material.BARRIER);
        b.fill(cx - 4, oy + 1, oz - 1, cx + 4, oy + 9, oz - 1, Material.BARRIER);
        b.fill(cx - 4, oy + 1, oz + 2, cx + 4, oy + 9, oz + 2, Material.BARRIER);
        b.fill(cx - 4, oy + 9, oz - 1, cx + 4, oy + 9, oz + 2, Material.BARRIER);
        // neviditelná světla, ať je chodba osvětlená
        BlockData lit = Bukkit.createBlockData("minecraft:light[level=11]");
        for (int z = oz + 2; z >= z0; z -= 4) b.set(cx, oy + 11, z, lit);
        lights.put(0, li);
    }

    // ---------------- průběh ----------------
    @Override protected void start() {
        chooseSong(); t = 0; ci = 0; wi = 0; beatIdx = 0; st.clear(); walls.clear();
        beatLen = 1200.0 / ch.bpm; beatTicks = Math.max(6, (int) Math.round(beatLen)); firstBeat = PRE + (int) Math.round(ch.first * 20);
        endTick = PRE + (int) Math.round(ch.len * 20) + 50;
        for (int b = 12, k = 0; (int) Math.round(firstBeat + b * beatLen) < endTick - 60; b += 14, k++) walls.add(new int[]{(int) Math.round(firstBeat + b * beatLen), k % 3});
        int i = 0;
        for (Player p : new ArrayList<>(players)) {
            St s = new St(); s.idx = i++; s.sx = sx(s.idx); st.put(p, s);
            p.teleport(new Location(w, s.sx, oy + 1, oz + 0.5, 180, 0));
            makeHud(p, s);
            p.setWalkSpeed(0.2f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 250, false, false, false));
            ItemStack sw = cz.adrrex.squarestar.Cosmetics.model(Material.DIAMOND_SWORD, "saber_red", "&c&lSvětelný meč", "&7Klikni (levé i pravé) na červenou kostku", "&7Pohybuj se &fA/D &7a uhýbej zdem", "&4TNT netrefuj!");
            var m = sw.getItemMeta(); m.setUnbreakable(true); m.addItemFlags(org.bukkit.inventory.ItemFlag.values()); sw.setItemMeta(m);
            p.getInventory().setItem(0, sw); p.getInventory().setHeldItemSlot(0);
        }
        broadcast("&d&lNEON BEAT! &7Hraje &d" + songName(song) + " &8(" + Math.round(ch.bpm) + " BPM, " + ch.notes + " kostek)&7. &cČervené kostky seknout (levé nebo pravé tlačítko), &fA/D &7uhýbej zdem. &4TNT netrefuj!");
        task = Bukkit.getScheduler().runTaskTimer(pl, this::loop, 1, 1);
    }

    private String key() { return "minecraft:music_disc." + song; }
    private double zNow(Live l) { double fr = Math.max(0, Math.min(1.3, (t - l.spawn - 1) / (double) FLIGHT)); return oz + 0.5 - D + D * fr; }
    private double dz(Live l) { return oz + 0.5 - zNow(l); }

    private void loop() {
        if (state != State.RUNNING) return;
        if (t == PRE) for (Player p : st.keySet()) p.playSound(p.getLocation(), key(), SoundCategory.RECORDS, 1.0f, 1f);
        // světelná show na beat
        if (t >= firstBeat && t >= firstBeat + Math.round(beatIdx * beatLen)) pulse(beatIdx++);
        // nové kostky
        while (ci < ch.list.size() && PRE + ch.list.get(ci).hit - HIT_TICKS <= t) {
            Note n = ch.list.get(ci++);
            for (var e : st.entrySet()) if (alive.contains(e.getKey())) spawnNote(e.getKey(), e.getValue(), n);
        }
        while (wi < walls.size() && walls.get(wi)[0] - FLIGHT <= t) {
            int[] w_ = walls.get(wi++);
            for (var e : st.entrySet()) if (alive.contains(e.getKey())) spawnWall(e.getKey(), e.getValue(), w_[1]);
        }

        for (var e : new ArrayList<>(st.entrySet())) {
            Player p = e.getKey(); St s = e.getValue(); updateHud(s);
            if (!p.isOnline()) continue;
            for (Live l : new ArrayList<>(s.live)) {
                if (!l.d.isValid()) { for (var x_ : l.extra) x_.remove(); s.live.remove(l); continue; }
                move(l);
                double dz = dz(l);
                if (l.n == null) {          // zeď
                    if (t % 2 == 0 && dz < 14 && dz > 0) for (int q = 0; q < 2; q++) w.spawnParticle(Particle.ELECTRIC_SPARK, l.wx + (rnd.nextDouble() - .5) * l.ww, oy + 1 + rnd.nextDouble() * 4, zNow(l) + 0.3, 0, 0, 0, 0.5, 0.2);
                    if (!l.done && dz < 0.5) {
                        l.done = true;
                        if (alive.contains(p) && Math.abs(p.getLocation().getX() - l.wx) < l.ww / 2 + 0.3) {
                            s.energy -= 15; s.combo = 0; s.last = "&4Zeď!"; s.misses++;
                            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.6f, 1.6f);
                            p.sendActionBar(U.c(status(p))); checkEnergy(p, s);
                        }
                    }
                    if (dz < -3) { l.d.remove(); s.live.remove(l); }
                    continue;
                }
                if (!l.done && dz < MIN_DZ) { l.done = true; if (l.n.kind != 2) miss(p, s, "&cMimo!"); }
                if (dz < -3) { l.d.remove(); for (var x_ : l.extra) x_.remove(); s.live.remove(l); }
            }
            if (alive.contains(p)) {
                Location pl_ = p.getLocation();
                if (Math.abs(pl_.getX() - s.sx) > 3.6 || Math.abs(pl_.getZ() - (oz + 0.5)) > 1.4 || pl_.getY() > oy + 4) p.teleport(new Location(w, Math.max(s.sx - 3.4, Math.min(s.sx + 3.4, pl_.getX())), oy + 1, oz + 0.5, pl_.getYaw(), pl_.getPitch()));
                if (t % 4 == 0) p.sendActionBar(U.c(status(p)));
            }
        }
        if (t > endTick) finish();
        t++;
    }

    private void pulse(int beat) {
        boolean sw = (beat / 8) % 2 == 1;           // po 8 dobách se strany prohodí (jako v originále)
        BlockData red = Bukkit.createBlockData(Material.RED_CONCRETE), dim = Bukkit.createBlockData(Material.RED_TERRACOTTA), flash = Bukkit.createBlockData(Material.PINK_CONCRETE);
        BlockData left = sw ? dim : red, right = sw ? red : dim;
        for (var e : st.entrySet()) {
            Player p = e.getKey(); if (!p.isOnline()) continue;
            List<int[]> li = lights.get(e.getValue().idx); if (li == null) continue;
            double cx = e.getValue().sx; Map<Location, BlockData> ch_ = new HashMap<>();
            for (int[] c : li) { boolean l_ = c[0] < cx; ch_.put(new Location(w, c[0], c[1], c[2]), (Math.floorMod((oz - c[2]) / 6 + beat, 5) == 0) ? flash : (c[1] > oy + 10 ? ((beat % 2 == 0) ? left : right) : (l_ ? left : right))); }
            p.sendMultiBlockChange(ch_);
        }
    }

    /** díl kostky; pohybuje se posouváním entity každý tick (ne interpolací – ta se v klientu zasekává) */
    private BlockDisplay part(Player p, Location l, Material m, Vector3f off, Vector3f size) {
        BlockDisplay d = spawn(l, BlockDisplay.class, x -> {
            x.setVisibleByDefault(false);
            x.setBlock(m.createBlockData());
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(3f);
            x.setTeleportDuration(2);
            x.setTransformation(new Transformation(off, new Quaternionf(), size, new Quaternionf()));
        });
        p.showEntity(pl, d);
        return d;
    }

    private void move(Live l) {
        double z = zNow(l);
        for (BlockDisplay x : l.all()) if (x.isValid()) x.teleport(new Location(w, l.cx, l.cy, z));
    }

    private void spawnNote(Player p, St s, Note n) {
        Location l = new Location(w, s.sx + COLX[n.col], oy + ROWY[n.row], oz + 0.5 - D);
        float h = 0.31f;
        Live lv = new Live(); lv.n = n; lv.spawn = t; lv.cx = l.getX(); lv.cy = l.getY();
        if (n.kind == 2) {
            lv.d = part(p, l, Material.TNT, new Vector3f(-h, -h, -h), new Vector3f(h * 2, h * 2, h * 2));
        } else {
            Material glow = n.kind == 0 ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS;
            lv.d = part(p, l, glow, new Vector3f(-h, -h, -h), new Vector3f(h * 2, h * 2, h * 2));                       // zářící obrys
            lv.extra.add(part(p, l, Material.BLACK_CONCRETE, new Vector3f(-h + 0.05f, -h + 0.05f, -h + 0.05f), new Vector3f(h * 2 - 0.1f, h * 2 - 0.1f, h * 2 - 0.1f))); // tmavé jádro
            lv.extra.add(part(p, l, Material.WHITE_CONCRETE, new Vector3f(-0.17f, -0.04f, -h - 0.015f), new Vector3f(0.34f, 0.08f, 0.03f)));                      // bílá šipka
            lv.extra.add(part(p, l, Material.WHITE_CONCRETE, new Vector3f(-0.04f, -0.17f, -h - 0.015f), new Vector3f(0.08f, 0.34f, 0.03f)));
        }
        s.live.add(lv);
    }

    // ---------------- efekty jako v Beat Saberu ----------------
    private static final Particle.DustOptions REDD = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.4f), BLUED = new Particle.DustOptions(Color.fromRGB(60, 120, 255), 1.4f);

    /** paprsky jisker rozletující se do všech stran + rozříznutá kostka */
    private void cutFx(Location c, int kind, Vector look) {
        for (int i = 0; i < 10; i++) {      // jen pár jisker, ať nezakrývají výhled
            Vector v = new Vector(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            w.spawnParticle(Particle.ELECTRIC_SPARK, c, 0, v.getX(), v.getY(), v.getZ(), 0.25 + rnd.nextDouble() * 0.2);
        }
        w.spawnParticle(Particle.DUST, c, 5, .15, .15, .15, 0, REDD);
        Material m = Material.RED_CONCRETE;
        boolean horiz = rnd.nextBoolean();
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            final int sg = sgn;
            float h = 0.31f;
            Vector3f size = horiz ? new Vector3f(h * 2, h, h * 2) : new Vector3f(h, h * 2, h * 2);
            Vector3f off = horiz ? new Vector3f(-h, sg > 0 ? 0 : -h, -h) : new Vector3f(sg > 0 ? 0 : -h, -h, -h);
            BlockDisplay d = w.spawn(c, BlockDisplay.class, x -> {
                x.setPersistent(false); x.setBlock(m.createBlockData()); x.setBrightness(new Display.Brightness(15, 15));
                x.setInterpolationDuration(8); x.setInterpolationDelay(0);
                x.setTransformation(new Transformation(off, new Quaternionf(), size, new Quaternionf()));
            });
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!d.isValid()) return;
                Vector3f mv = horiz ? new Vector3f(0, sg * 0.9f, -1.5f) : new Vector3f(sg * 0.9f, 0, -1.5f);
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(new Vector3f(off).add(mv), new Quaternionf().rotateZ(sg * 1.0f), new Vector3f(size).mul(0.25f), new Quaternionf()));
            }, 1L);
            Bukkit.getScheduler().runTaskLater(pl, d::remove, 9L);
        }
    }

    /** červená světelná stopa meče – dole u pravé ruky, mimo střed výhledu */
    private void saberTrail(Player p, int kind) {
        Location eye = p.getEyeLocation(); Vector look = eye.getDirection().normalize();
        Vector side = look.clone().crossProduct(new Vector(0, 1, 0)).normalize(); Vector up2 = side.clone().crossProduct(look).normalize();
        Location mid = eye.clone().add(look.clone().multiply(1.6)).add(side.clone().multiply(-0.55)).add(up2.clone().multiply(-0.55));
        double ang = Math.PI * (0.15 + 0.2 * rnd.nextDouble());
        Vector dir = side.clone().multiply(-Math.cos(ang)).add(up2.clone().multiply(Math.sin(ang)));
        for (double k = -0.6; k <= 0.6; k += 0.12) p.spawnParticle(Particle.DUST, mid.clone().add(dir.clone().multiply(k)), 1, 0, 0, 0, 0, REDD);
    }

    /** plovoucí skóre u zásahu */
    private void popup(Location c, String txt) {
        org.bukkit.entity.TextDisplay td = w.spawn(c.clone().add(0, 0.4, 0), org.bukkit.entity.TextDisplay.class, x -> {
            x.setPersistent(false); x.text(U.c(txt)); x.setBillboard(Display.Billboard.CENTER); x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            x.setShadowed(true); x.setInterpolationDuration(10); x.setInterpolationDelay(0);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
        });
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (td.isValid()) { td.setInterpolationDelay(0); td.setTransformation(new Transformation(new Vector3f(0, 0.6f, 0), new Quaternionf(), new Vector3f(0.4f, 0.4f, 0.4f), new Quaternionf())); } }, 1L);
        Bukkit.getScheduler().runTaskLater(pl, td::remove, 10L);
    }

    /** zeď: uhni do strany (0 = levá polovina, 1 = pravá, 2 = střed) */
    private void spawnWall(Player p, St s, int side) {
        double ww = side == 2 ? 2.6 : 3.2, wx = s.sx + (side == 0 ? -1.4 : side == 1 ? 1.4 : 0);
        Location l = new Location(w, wx, oy + 1, oz + 0.5 - D);
        Live lv = new Live(); lv.spawn = t; lv.wx = wx; lv.ww = ww; lv.cx = wx; lv.cy = oy + 1;
        lv.d = part(p, l, Material.RED_STAINED_GLASS, new Vector3f((float) -ww / 2, 0, -0.2f), new Vector3f((float) ww, 4f, 0.4f));
        s.live.add(lv);
        p.sendActionBar(U.c("&c&l⚠ ZEĎ! &7uhni do strany"));
    }

    /** čáry rychlosti letící tunelem k hráči */
    private void speedLines(Player p, St s) {
        for (int i = 0; i < 2; i++) {
            Location l = new Location(w, s.sx + (rnd.nextDouble() - .5) * 9, oy + 1 + rnd.nextDouble() * 5, oz + 0.5 - D + rnd.nextDouble() * 6);
            p.spawnParticle(Particle.DUST, l, 0, 0, 0, 0, 0, REDD);
        }
    }

    private void miss(Player p, St s, String msg) {
        s.misses++; s.combo = 0; s.energy -= 4; s.last = msg;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.6f, 0.5f);
        p.sendActionBar(U.c(status(p)));
        checkEnergy(p, s);
    }

    private static int mult(int combo) { return 1 + Math.min(combo, 70) / 10; }

    /** panely jako v Beat Saberu: vlevo COMBO, vpravo násobič ×N s ukazatelem do dalšího */
    private void makeHud(Player p, St s) {
        double y = oy + 3.6, z = oz - 7.5;
        s.comboTd = hudText(p, new Location(w, s.sx - 4.6, y, z), 1.7f);
        s.multTd = hudText(p, new Location(w, s.sx + 4.6, y, z), 1.7f);
        s.hudC = s.hudM = -1; updateHud(s);
    }
    private TextDisplay hudText(Player p, Location l, float sc) {
        TextDisplay td = w.spawn(l, TextDisplay.class, x -> {
            x.setPersistent(false); x.setVisibleByDefault(false); x.setBillboard(Display.Billboard.CENTER);
            x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); x.setBrightness(new Display.Brightness(15, 15)); x.setViewRange(2f);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
            x.text(U.c("&7"));
        });
        p.showEntity(pl, td);
        return td;
    }
    private void updateHud(St s) {
        if (s.comboTd == null) return;
        int m = mult(s.combo);
        if (s.hudC != s.combo) {
            s.hudC = s.combo;
            s.comboTd.text(U.c("&f&lCOMBO\n" + (s.combo >= 10 ? "&c&l" : "&f&l") + s.combo));
        }
        if (s.hudM != m * 100 + (s.combo % 10)) {
            s.hudM = m * 100 + (s.combo % 10);
            int f = m >= 8 ? 10 : s.combo % 10;
            s.multTd.text(U.c("&c&l×" + m + "\n&c" + "▮".repeat(f) + "&8" + "▮".repeat(10 - f)));
        }
    }
    private void clearHud(St s) {
        if (s.comboTd != null) s.comboTd.remove();
        if (s.multTd != null) s.multTd.remove();
        s.comboTd = s.multTd = null;
    }

    private void checkEnergy(Player p, St s) {
        if (s.energy > 0 || !alive.contains(p)) return;
        s.energy = 0;
        for (Live l : s.live) { l.d.remove(); for (var x_ : l.extra) x_.remove(); }
        s.live.clear(); clearHud(s);
        p.setWalkSpeed(0.2f); p.stopSound(key(), SoundCategory.RECORDS);
        U.title(p, "&c&lDošla energie", "&7Skóre: &e" + s.score);
        eliminate(p, null);
    }

    @Override public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer(); St s = st.get(p);
        if (s == null || !alive.contains(p)) return;
        int kind; Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) kind = 0;
        else if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && e.getHand() == EquipmentSlot.HAND) kind = 0;
        else return;
        e.setCancelled(true);
        swing(p, s, kind);
    }

    private Vector look_(Player p) { return p.getEyeLocation().getDirection(); }

    private void swing(Player p, St s, int kind) {
        saberTrail(p, kind);
        Location eye = p.getEyeLocation(); Vector look = eye.getDirection();
        Live best = null; double bdot = -2;
        for (Live l : s.live) {
            if (l.done || l.n == null) continue;
            double dz = dz(l);
            if (dz < MIN_DZ || dz > MAX_DZ) continue;
            Vector to = new Vector(s.sx + COLX[l.n.col], oy + ROWY[l.n.row], zNow(l)).subtract(eye.toVector());
            double dot = look.dot(to.normalize());
            if (dot < CONE) continue;
            if (dot > bdot + 0.004 || (Math.abs(dot - bdot) <= 0.004 && best != null && dz < dz(best))) { bdot = dot; best = l; }
        }
        if (System.getProperty("ssdebug") != null) pl.getLogger().info("BEAT swing kind=" + kind + " live=" + s.live.size() + " best=" + (best != null));
        if (best == null) return;
        double bdz = dz(best);
        Location pos = new Location(w, s.sx + COLX[best.n.col], oy + ROWY[best.n.row], zNow(best));
        Note n = best.n;
        best.done = true; best.d.remove(); for (var x_ : best.extra) x_.remove(); s.live.remove(best);
        if (n.kind == 2) {
            w.spawnParticle(Particle.EXPLOSION, pos, 1);
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.7f, 1.4f);
            s.combo = 0; s.energy -= 10; s.last = "&4TNT!"; s.misses++;
            p.sendActionBar(U.c(status(p))); checkEnergy(p, s); return;
        }
        if (n.kind != kind) {
            w.spawnParticle(Particle.SMOKE, pos, 12, .15, .15, .15, .02);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.7f, 1.2f);
            s.combo = 0; s.energy -= 4; s.last = "&cŠpatná barva!"; s.misses++;
            p.sendActionBar(U.c(status(p))); checkEnergy(p, s); return;
        }
        double err = Math.abs(bdz - IDEAL);
        int base; String rate;
        if (err <= 1.5) { base = 100; rate = "&6&lPERFEKT"; } else if (err <= 2.8) { base = 70; rate = "&a&lDobře"; } else { base = 40; rate = "&7OK"; }
        s.combo++; s.best = Math.max(s.best, s.combo); s.hits++;
        int mult = mult(s.combo);
        s.score += base * mult; s.energy = Math.min(100, s.energy + 1); s.last = rate;
        cutFx(pos, n.kind, look_(p));
        popup(pos, (n.kind == 0 ? "&c&l" : "&9&l") + (base * mult));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.MASTER, 0.8f, 1.4f);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 0.5f, n.kind == 0 ? 1.2f : 1.6f);
        p.sendActionBar(U.c(status(p)));
    }

    @Override protected String status(Player p) {
        St s = st.get(p);
        if (s == null) return "&dNeon Beat";
        int f = Math.max(0, Math.min(10, (s.energy + 9) / 10));
        return "&6Skóre &f" + s.score + " &8| &bKombo &fx" + s.combo + " &8| &c❤ &a" + "▮".repeat(f) + "&8" + "▮".repeat(10 - f) + (s.last.isEmpty() ? "" : " &8| " + s.last);
    }

    @Override protected List<String> boardLines(Player p) {
        List<String> l = super.boardLines(p);
        if (state == State.RUNNING) {
            St s = st.get(p);
            l.add(Math.max(0, l.size() - 4), "&fPísnička: &d" + songName(song));
            if (s != null) l.add(Math.max(0, l.size() - 4), "&fSkóre: &e" + s.score);
        }
        return l;
    }

    private void finish() {
        if (state != State.RUNNING) return;
        for (Player p : st.keySet()) p.stopSound(key(), SoundCategory.RECORDS);
        List<Map.Entry<Player, St>> rank = new ArrayList<>(st.entrySet());
        rank.sort((a, b) -> Integer.compare(b.getValue().score, a.getValue().score));
        broadcast("&d&lVýsledky Neon Beat &7(" + songName(song) + ")&d&l:");
        int i = 1;
        for (var e : rank) {
            St s = e.getValue(); int tot = s.hits + s.misses;
            broadcast("&e" + i++ + ". &f" + e.getKey().getName() + " &8- &6" + s.score + " &7(přesnost " + (tot == 0 ? 0 : s.hits * 100 / tot) + " %, nejlepší kombo " + s.best + (alive.contains(e.getKey()) ? "" : ", &cvyřazen&7") + ")");
        }
        Player top = null;
        for (var e : rank) if (alive.contains(e.getKey())) { top = e.getKey(); break; }
        end(top == null ? List.of() : List.of(top));
    }

    @Override public void onDeath(Player p, Player killer) { p.setWalkSpeed(0.2f); }
    @Override protected void onLeave(Player p) { p.setWalkSpeed(0.2f); p.removePotionEffect(PotionEffectType.JUMP_BOOST); if (ch != null) p.stopSound(key(), SoundCategory.RECORDS); votes.remove(p); }
    @Override protected void timeUp() { finish(); }
    @Override protected void checkWin() { if (state == State.RUNNING && alive.isEmpty()) finish(); }
    @Override protected void onDestroy() { if (task != null) task.cancel(); for (Player p : players) { p.setWalkSpeed(0.2f); p.removePotionEffect(PotionEffectType.JUMP_BOOST); if (ch != null) p.stopSound(key(), SoundCategory.RECORDS); } }

    /** menu výběru písničky (klik na item / v inventáři) */
    private static final class Ui implements Listener {
        private final SquareStar pl; Ui(SquareStar pl) { this.pl = pl; }
        @EventHandler public void use(PlayerInteractEvent e) {
            if (!"songs".equals(U.tagOf(e.getItem(), "ss_beat"))) return;
            if (pl.games.of(e.getPlayer()) instanceof Beat b) { e.setCancelled(true); if (e.getHand() == EquipmentSlot.HAND) b.openSongs(e.getPlayer()); }
        }
        @EventHandler public void click(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof SongHolder h)) return;
            e.setCancelled(true);
            String id = U.tagOf(e.getCurrentItem(), "ss_song");
            if (id != null && e.getWhoClicked() instanceof Player p) h.game.pick(p, id);
        }
    }
}
