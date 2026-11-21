package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Speedrun: Free Edition – „levná" vesmírná verze Minecraftu. Celý svět je z vlny,
 * moby jsou jen poskakující hlavy. Kdo první posbírá díly a odletí raketou, vyhrává.
 */
public final class SpeedrunFE extends Game {
    private static final int R = 46;
    // cíl: materiál vlny -> počet
    private static final Material ROCK = Material.GRAY_WOOL, GOLD = Material.YELLOW_WOOL, CRYSTAL = Material.LIGHT_BLUE_WOOL, FUEL = Material.RED_WOOL;
    private static final Object[][] GOALS = {
            {ROCK, 16, "Měsíční kámen"}, {GOLD, 8, "Vesmírné zlato"}, {CRYSTAL, 4, "Krystal"}, {FUEL, 4, "Palivo"}};
    private static final int TECH = 3;
    private final Map<Player, Map<Material, Integer>> bag = new HashMap<>();
    private final Map<Player, Integer> tech = new HashMap<>();
    private final Map<Slime, ItemDisplay> heads = new HashMap<>();
    private long startMs;

    public SpeedrunFE(GameManager gm, int slot) { super(gm, GameType.SPEEDRUN, slot); }

    @Override public List<String> maps() { return List.of("Vlněný měsíc", "Levná planeta", "Duhová planeta"); }
    @Override protected GameMode mode() { return GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected int voidY() { return oy - 25; }
    @Override protected int timeLimit() { return 600; }

    // ---------------- vlněný svět ----------------
    @Override protected void buildMap(U.BlockQueue.Builder b) {
        boolean planet = map == 1, rainbow = map == 2;
        Material base = planet ? Material.ORANGE_WOOL : rainbow ? Material.PINK_WOOL : Material.WHITE_WOOL;
        Material top = planet ? Material.RED_WOOL : Material.LIGHT_GRAY_WOOL;
        Material[] rb = {Material.PINK_WOOL, Material.MAGENTA_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.CYAN_WOOL, Material.LIME_WOOL, Material.GREEN_WOOL};
        long seed = rnd.nextLong();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R) continue;
            int h = (int) Math.round(3 + 3 * Math.sin((x + seed % 7) * 0.13) * Math.cos((z - seed % 5) * 0.11) + 2 * Math.sin((x + z) * 0.07) - Math.max(0, d - R + 8));
            int bottom = (int) -Math.max(2, (R - d) * 0.35);
            for (int y = bottom; y < h; y++) b.set(ox + x, oy + y, oz + z, y < h - 3 ? ROCK : base);
            b.set(ox + x, oy + h, oz + z, rainbow ? rb[Math.floorMod((x + z) / 4, rb.length)] : (planet && (x * 7 + z * 3) % 11 == 0) ? Material.ORANGE_WOOL : top);
        }
        // „rudy" = barevné shluky vlny pod povrchem i na povrchu
        Material[] ores = {GOLD, GOLD, CRYSTAL, FUEL, ROCK, ROCK};
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble() * (R - 10);
            int cx = ox + (int) (Math.cos(a) * r), cz = oz + (int) (Math.sin(a) * r);
            Material m = ores[i % ores.length];
            b.sphere(cx, oy + 3 + rnd.nextInt(3), cz, 1 + rnd.nextInt(2), p -> b.set(p[0], p[1], p[2], m));
        }
        // „levné stromy" z vlny
        for (int i = 0; i < 22; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 8 + rnd.nextDouble() * (R - 12);
            int cx = ox + (int) (Math.cos(a) * r), cz = oz + (int) (Math.sin(a) * r);
            b.fill(cx, oy + 4, cz, cx, oy + 9, cz, Material.BROWN_WOOL);
            b.fill(cx - 2, oy + 9, cz - 2, cx + 2, oy + 11, cz + 2, planet ? Material.PURPLE_WOOL : Material.LIME_WOOL);
        }
        // startovací rampa + raketa z vlny uprostřed
        b.fill(ox - 3, oy + 8, oz - 3, ox + 3, oy + 8, oz + 3, Material.GOLD_BLOCK);
        b.fill(ox - 1, oy + 9, oz - 1, ox + 1, oy + 15, oz + 1, Material.WHITE_WOOL);
        b.fill(ox - 1, oy + 12, oz - 1, ox + 1, oy + 12, oz + 1, Material.RED_WOOL);
        b.set(ox, oy + 16, oz, Material.RED_WOOL);
        b.fill(ox - 3, oy + 1, oz - 3, ox + 3, oy + 7, oz + 3, Material.GRAY_WOOL);
    }

    @Override public boolean canBreak(Player p, Block b) {
        Material m = b.getType();
        if (!m.name().endsWith("_WOOL") || b.getY() > oy + 7 && Math.abs(b.getX() - ox) <= 3 && Math.abs(b.getZ() - oz) <= 3) return false;
        // vlna jde rovnou do batohu (žádné itemy na zemi = žádný lag)
        for (Object[] g : GOALS) if (g[0] == m) bag.computeIfAbsent(p, x -> new HashMap<>()).merge(m, 1, Integer::sum);
        Bukkit.getScheduler().runTask(pl, () -> { b.setType(Material.AIR, false); markPlaced(b); });
        p.playSound(b.getLocation(), Sound.BLOCK_WOOL_BREAK, 1f, 1.2f);
        w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, b.getBlockData());
        return false;
    }

    // ---------------- start ----------------
    @Override protected void start() {
        int i = 0;
        for (Player p : players) {
            double a = i++ * Math.PI * 2 / players.size();
            p.teleport(loc(Math.cos(a) * 20 + 0.5, 12, Math.sin(a) * 20 + 0.5));
            ItemStack sh = new ItemStack(Material.SHEARS);
            ItemMeta m = sh.getItemMeta(); m.displayName(U.c("&e&lLevné nůžky &7(Free Edition)")); m.setUnbreakable(true); sh.setItemMeta(m);
            p.getInventory().setItem(0, sh);
            ItemStack sw = new ItemStack(Material.WOODEN_SWORD);
            ItemMeta sm = sw.getItemMeta(); sm.displayName(U.c("&6&lDřevěný meč &7(zdarma)")); sm.setUnbreakable(true); sw.setItemMeta(sm);
            p.getInventory().setItem(1, sw);
            p.getInventory().setItem(8, new ItemStack(Material.BREAD, 8));
            bag.put(p, new HashMap<>()); tech.put(p, 0);
        }
        startMs = System.currentTimeMillis();
        broadcast("&e&lSPEEDRUN: FREE EDITION! &7Svět je z vlny, moby jsou jen hlavy. Posbírej díly a odleť raketou &6uprostřed&7!");
        broadcast("&7Úkoly vidíš &fvpravo&7: rozbíjej &8šedou&7, &ežlutou&7, &bsvětle modrou &7a &cčervenou &7vlnu, zabij &d3 hlavy &7a doběhni na &6zlatou rampu&7.");
        for (int k = 0; k < 6 + players.size() * 2; k++) spawnHead();
        for (Player p : players) board(p);
    }

    // ---------------- „levní" moby: jen poskakující hlavy ----------------
    private static final Material[] HEADS = {Material.ZOMBIE_HEAD, Material.CREEPER_HEAD, Material.SKELETON_SKULL, Material.PIGLIN_HEAD};
    private void spawnHead() {
        double a = rnd.nextDouble() * Math.PI * 2, r = 10 + rnd.nextDouble() * (R - 14);
        Location l = loc(Math.cos(a) * r, 12, Math.sin(a) * r);
        Material head = HEADS[rnd.nextInt(HEADS.length)];
        Slime s = spawn(l, Slime.class, e -> {
            e.setSize(2);
            e.setInvisible(true);
            e.setSilent(true);
            e.setLootTable(null);
            Objects.requireNonNull(e.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(10);
            e.setHealth(10);
            e.customName(U.c("&7Levný mob"));
        });
        ItemDisplay d = spawn(l, ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(head));
            e.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new AxisAngle4f(), new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
        });
        s.addPassenger(d);
        heads.put(s, d);
    }

    @Override protected void second() {
        if (state != State.RUNNING) return;
        // mrtvé hlavy: zabiják dostane mimozemskou technologii
        for (var it = heads.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Slime s = e.getKey();
            if (s.isValid() && !s.isDead()) continue;
            Player k = s.getKiller();
            if (k != null && tech.containsKey(k)) { tech.merge(k, 1, Integer::sum); U.sound(k, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.6f); }
            e.getValue().remove();
            it.remove();
        }
        while (heads.size() < 6 + players.size() * 2) spawnHead();
        // kontrola cíle
        for (Player p : new ArrayList<>(alive)) {
            board(p);
            if (done(p) && p.getLocation().getY() >= oy + 8.5 && Math.abs(p.getLocation().getX() - ox) < 4 && Math.abs(p.getLocation().getZ() - oz) < 4) {
                win(p);
                return;
            }
        }
    }

    private boolean done(Player p) {
        Map<Material, Integer> m = bag.getOrDefault(p, Map.of());
        for (Object[] g : GOALS) if (m.getOrDefault((Material) g[0], 0) < (int) g[1]) return false;
        return tech.getOrDefault(p, 0) >= TECH;
    }

    private String progress(Player p) {
        Map<Material, Integer> m = bag.getOrDefault(p, Map.of());
        StringBuilder sb = new StringBuilder();
        for (Object[] g : GOALS) {
            int have = Math.min(m.getOrDefault((Material) g[0], 0), (int) g[1]);
            sb.append(have >= (int) g[1] ? "&a✔" : "&7").append(g[2]).append(" &f").append(have).append("/").append(g[1]).append("  ");
        }
        int t = Math.min(tech.getOrDefault(p, 0), TECH);
        sb.append(t >= TECH ? "&a✔" : "&7").append("Tech &f").append(t).append("/").append(TECH);
        if (done(p)) sb = new StringBuilder("&6&l➹ Máš vše! Běž na zlatou rampu k raketě!");
        return sb.toString();
    }

    /** přehledný seznam úkolů vpravo na obrazovce (sidebar) */
    private final Map<Player, org.bukkit.scoreboard.Scoreboard> boards = new HashMap<>();
    private void board(Player p) {
        var sb = boards.computeIfAbsent(p, x -> {
            var b = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(b);
            return b;
        });
        var old = sb.getObjective("sr");
        if (old != null) old.unregister();
        var o = sb.registerNewObjective("sr", org.bukkit.scoreboard.Criteria.DUMMY, U.c(cz.adrrex.squarestar.Lang.t(p, "&e&lSPEEDRUN &7Free Edition")));
        o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        Map<Material, Integer> m = bag.getOrDefault(p, Map.of());
        List<String> lines = new ArrayList<>();
        lines.add("&7Čas: &f" + fmt(time));
        lines.add(" ");
        for (Object[] g : GOALS) {
            int have = Math.min(m.getOrDefault((Material) g[0], 0), (int) g[1]);
            String color = g[0] == ROCK ? "&8" : g[0] == GOLD ? "&e" : g[0] == CRYSTAL ? "&b" : "&c";
            lines.add((have >= (int) g[1] ? "&a✔ " : color + "■ ") + "&f" + g[2] + " &7" + have + "/" + g[1]);
        }
        int t = Math.min(tech.getOrDefault(p, 0), TECH);
        lines.add((t >= TECH ? "&a✔ " : "&d☠ ") + "&fZabij hlavy &7" + t + "/" + TECH);
        lines.add("  ");
        lines.add(done(p) ? "&6&l➹ Běž k raketě!" : "&7Rozbíjej barevnou vlnu");
        int score = lines.size();
        for (String l : lines) o.getScore(cz.adrrex.squarestar.Lang.t(p, l).replace('&', '§')).setScore(score--);
    }

    private void win(Player p) {
        long ms = System.currentTimeMillis() - startMs;
        String time = String.format("%d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
        broadcast("&6&l➹ " + p.getName() + " &eodletěl domů! &7Čas speedrunu: &f" + time);
        p.setVelocity(p.getVelocity().setY(2.5));
        w.spawnParticle(Particle.FLAME, p.getLocation(), 80, 0.5, 0.2, 0.5, 0.1);
        end(List.of(p));
    }

    @Override public void onDeath(Player p, Player killer) {
        // v speedrunu se neumírá natrvalo: respawn, ale přijdeš o polovinu nasbíraných věcí
        U.reset(p, GameMode.SURVIVAL);
        Map<Material, Integer> m = bag.getOrDefault(p, new HashMap<>());
        m.replaceAll((k, v) -> v / 2);
        p.teleport(loc(rnd.nextInt(30) - 15 + 0.5, 12, rnd.nextInt(30) - 15 + 0.5));
        pl.applyGravity(p);
        U.title(p, "&c&lUMŘEL JSI", "&7Ztratil jsi půlku věcí. Běž dál!");
        start(p);
    }
    private void start(Player p) {
        ItemStack sh = new ItemStack(Material.SHEARS); ItemMeta m = sh.getItemMeta(); m.setUnbreakable(true); sh.setItemMeta(m);
        p.getInventory().setItem(0, sh);
        p.getInventory().setItem(1, new ItemStack(Material.WOODEN_SWORD));
    }

    @Override public boolean onPvp(Player victim, Player attacker, org.bukkit.event.entity.EntityDamageByEntityEvent e) { return true; }
    @Override protected void checkWin() { if (state == State.RUNNING && alive.isEmpty()) end(List.of()); }
    @Override protected boolean ownBoard() { return true; }
    @Override protected void onLeave(Player p) { boards.remove(p); p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); }
    @Override protected void onDestroy() {
        for (Player p : boards.keySet()) if (p.isOnline()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        boards.clear(); for (var e : heads.entrySet()) { e.getKey().remove(); e.getValue().remove(); } heads.clear(); }
    @Override protected String status(Player p) { return progress(p); }
    @Override protected void timeUp() {
        // nikdo nedoletěl: vyhrává ten, kdo má nejvíc posbíráno
        Player best = null; int bs = -1;
        for (Player p : alive) { int s = bag.getOrDefault(p, Map.of()).values().stream().mapToInt(i -> i).sum() + tech.getOrDefault(p, 0) * 5; if (s > bs) { bs = s; best = p; } }
        end(best == null ? List.of() : List.of(best));
    }
}
