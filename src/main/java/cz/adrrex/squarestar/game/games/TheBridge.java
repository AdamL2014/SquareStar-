package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * The Bridge: dva týmy na ostrovech spojených mostem. Skoč do cizí brány = bod. První na 5 bodů vyhrává.
 * Smrt = návrat na spawn (bloky si můžeš stavět a bourat jen vlastní).
 */
public final class TheBridge extends Game {
    private static final int GOALS = 5, IX = 26;          // ostrovy na x = ±IX
    private static final String[] TN = {"&c&lČervení", "&9&lModří"};
    private static final Color[] TC = {Color.RED, Color.BLUE};
    private static final Material[] WOOL = {Material.RED_TERRACOTTA, Material.BLUE_TERRACOTTA};
    private static final Material[] CONC = {Material.RED_CONCRETE, Material.BLUE_CONCRETE};
    private final Map<Player, Integer> team = new HashMap<>();
    private final int[] score = new int[2];
    private final Set<Long> own = new HashSet<>();        // bloky postavené hráči
    private boolean paused;                                // chvilka po bodu

    public TheBridge(GameManager gm, int slot) { super(gm, GameType.BRIDGE, slot); }

    @Override public List<String> maps() { return List.of("Písečný most", "Ledový most"); }
    @Override public List<String> teamNames() { return List.of(TN[0], TN[1]); }
    @Override protected List<Material> teamIcons() { return List.of(Material.RED_WOOL, Material.BLUE_WOOL); }
    @Override public Double gravity() { return 0.08; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected int voidY() { return oy - 14; }
    @Override protected int timeLimit() { return 900; }
    @Override protected boolean canDrop() { return false; }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        boolean ice = map == 1;
        Material floor = ice ? Material.PACKED_ICE : Material.SANDSTONE, top = ice ? Material.SNOW_BLOCK : Material.SMOOTH_SANDSTONE;
        Material bridge = ice ? Material.BLUE_ICE : Material.STONE_BRICKS;
        for (int s = -1; s <= 1; s += 2) {
            int t = s < 0 ? 0 : 1;
            int cx = s * IX;
            // ostrov 15x15
            for (int x = cx - 7; x <= cx + 7; x++) for (int z = -7; z <= 7; z++) {
                boolean edge = Math.abs(x - cx) == 7 || Math.abs(z) == 7;
                b.set(ox + x, oy, oz + z, edge ? CONC[t] : (x + z) % 2 == 0 ? top : floor);
                b.set(ox + x, oy - 1, oz + z, floor);
                b.set(ox + x, oy - 2, oz + z, Material.POLISHED_ANDESITE);
            }
            // zadní zeď (sklo) za ostrovem
            int bx = cx + s * 8;
            for (int z = -7; z <= 7; z++) for (int y = 1; y <= 10; y++) b.set(ox + bx, oy + y, oz + z, y == 10 ? Material.SEA_LANTERN : (t == 0 ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS));
            // brána (cíl) u zadní zdi: 3x3 plocha, kolem rám
            int gx = cx + s * 5;
            for (int a = -2; a <= 2; a++) for (int c = -2; c <= 2; c++) {
                boolean in = Math.abs(a) <= 1 && Math.abs(c) <= 1;
                b.set(ox + gx + a, oy, oz + c, in ? Material.PURPLE_STAINED_GLASS : Material.CRYING_OBSIDIAN);
            }
            for (int y = 1; y <= 4; y++) { b.set(ox + gx, oy + y, oz - 2, Material.END_ROD); b.set(ox + gx, oy + y, oz + 2, Material.END_ROD); }
            b.fill(ox + gx - 1, oy + 5, oz - 2, ox + gx + 1, oy + 5, oz + 2, t == 0 ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS);
            // pár krytů
            b.fill(ox + cx - s * 2, oy + 1, oz - 4, ox + cx - s * 2, oy + 2, oz - 4, CONC[t]);
            b.fill(ox + cx - s * 2, oy + 1, oz + 4, ox + cx - s * 2, oy + 2, oz + 4, CONC[t]);
        }
        // most uprostřed (5 širokých)
        for (int x = -IX + 8; x <= IX - 8; x++) for (int z = -2; z <= 2; z++) {
            b.set(ox + x, oy, oz + z, (Math.abs(z) == 2 || x % 5 == 0) ? bridge : Material.SMOOTH_STONE);
            b.set(ox + x, oy - 1, oz + z, bridge);
        }
        // kruhový "stůl" uprostřed mostu
        b.fill(ox - 2, oy + 1, oz - 2, ox - 2, oy + 1, oz + 2, Material.SEA_LANTERN);
        b.fill(ox + 2, oy + 1, oz - 2, ox + 2, oy + 1, oz + 2, Material.SEA_LANTERN);
    }

    // ---------------- týmy / spawn ----------------
    private Location spawn(Player p) {
        int t = team.getOrDefault(p, 0), s = t == 0 ? -1 : 1;
        int idx = 0, n = 0;
        for (Player x : players) if (team.getOrDefault(x, 0) == t) { if (x == p) idx = n; n++; }
        Location l = U.center(w, ox + s * IX - s * 1, oy + 1, oz - 3 + (idx % 7));
        l.setDirection(new Vector(-s, 0, 0));
        return l;
    }

    @Override protected void start() {
        List<Player> order = new ArrayList<>(players);
        Collections.shuffle(order, rnd);
        int[] cnt = new int[2];
        int cap = (int) Math.ceil(order.size() / 2.0);
        // nejdřív hráči s výběrem týmu, pak doplnit
        for (Player p : order) { Integer pick = teamPick.get(p); if (pick != null && pick < 2 && cnt[pick] < cap) { team.put(p, pick); cnt[pick]++; } }
        for (Player p : order) if (!team.containsKey(p)) { int t = cnt[0] <= cnt[1] ? 0 : 1; team.put(p, t); cnt[t]++; }
        for (Player p : players) {
            int t = team.get(p);
            U.title(p, TN[t], "&7Skoč do &ccizí brány&7! První na " + GOALS + " bodů vyhrává.");
            p.teleport(spawn(p));
            kit(p);
        }
        broadcast("&eSkoč do &dfialové brány&e na soupeřově ostrově. Brání se mečem a bloky! První na &6" + GOALS + " bodů&e vyhrává.");
    }

    private void kit(Player p) {
        int t = team.getOrDefault(p, 0);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, unb(Material.IRON_SWORD));
        ItemStack bow = unb(Material.BOW); inv.setItem(1, bow);
        inv.setItem(2, unb(Material.IRON_PICKAXE));
        inv.setItem(3, new ItemStack(CONC[t], 64));
        inv.setItem(4, new ItemStack(CONC[t], 64));
        inv.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 2));
        inv.setItem(9, new ItemStack(Material.ARROW, 1));
        inv.setHelmet(leather(Material.LEATHER_HELMET, t)); inv.setChestplate(leather(Material.LEATHER_CHESTPLATE, t));
        inv.setLeggings(leather(Material.LEATHER_LEGGINGS, t)); inv.setBoots(leather(Material.LEATHER_BOOTS, t));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, false));
        p.setFireTicks(0);
    }

    private static ItemStack leather(Material m, int t) {
        ItemStack it = new ItemStack(m); LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta(); lm.setColor(TC[t]); lm.setUnbreakable(true); it.setItemMeta(lm); return it;
    }
    private static ItemStack unb(Material m) { ItemStack it = new ItemStack(m); ItemMeta im = it.getItemMeta(); im.setUnbreakable(true); it.setItemMeta(im); return it; }

    // ---------------- pravidla bloků ----------------
    @Override public boolean canPlace(Player p, Block b) {
        int rx = b.getX() - ox, ry = b.getY() - oy;
        if (ry < -1 || ry > 14 || Math.abs(rx) > IX + 9 || Math.abs(b.getZ() - oz) > 12) return false;
        // brány a nejbližší okolí nejde zastavět
        for (int s = -1; s <= 1; s += 2) { int gx = s * (IX + 5); if (Math.abs(rx - gx) <= 3 && Math.abs(b.getZ() - oz) <= 3 && ry <= 6) return false; }
        own.add(U.pack(b.getX(), b.getY(), b.getZ()));
        return true;
    }
    @Override public boolean canBreak(Player p, Block b) { return own.contains(U.pack(b.getX(), b.getY(), b.getZ())); }

    // ---------------- hra ----------------
    @Override public void onMove(Player p, Location to) {
        if (paused) return;
        int t = team.getOrDefault(p, 0), other = 1 - t;
        // brána soupeře
        int s = other == 0 ? -1 : 1, gx = ox + s * (IX + 5);
        if (Math.abs(to.getX() - (gx + 0.5)) <= 1.6 && Math.abs(to.getZ() - (oz + 0.5)) <= 1.6 && to.getY() >= oy && to.getY() <= oy + 3) score(p, t);
    }

    private void score(Player p, int t) {
        paused = true;
        score[t]++;
        broadcast(TN[t] + " &7– &e" + p.getName() + " &7dal bod! &8(&c" + score[0] + " &7: &9" + score[1] + "&8)");
        for (Player x : players) { U.sound(x, Sound.ENTITY_PLAYER_LEVELUP, 1.2f); U.title(x, TN[t], "&f" + score[0] + " &7: &f" + score[1]); }
        if (score[t] >= GOALS) {
            end(players.stream().filter(x -> team.getOrDefault(x, -1) == t).toList());
            return;
        }
        // reset kola: smaž bloky hráčů, všichni na spawn
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (state != State.RUNNING) return;
            clearOwn();
            for (Player x : players) { if (!x.isOnline()) continue; x.teleport(spawn(x)); x.setHealth(x.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()); kit(x); }
            paused = false;
        }, 40);
    }

    private void clearOwn() {
        for (long k : own) {
            int x = (int) (k >> 38), z = (int) ((k >> 12) & 0x3FFFFFF), y = (int) (k & 0xFFF);
            if ((x & 0x2000000) != 0) x |= ~0x3FFFFFF;
            if ((z & 0x2000000) != 0) z |= ~0x3FFFFFF;
            w.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        own.clear();
    }

    @Override public void onDeath(Player p, Player killer) {
        if (!alive.contains(p)) return;
        if (killer != null && killer != p && pl.stats != null) pl.stats.add(killer, "kills", 1);
        broadcast("&c☠ " + p.getName() + (killer != null && killer != p ? " &7byl zabit hráčem &c" + killer.getName() : " &7spadl"));
        U.reset(p, GameMode.ADVENTURE);
        pl.applyGravity(p);
        p.teleport(spawn(p));
        kit(p);
        lastHit.remove(p);
    }

    @Override public boolean onPvp(Player victim, Player attacker, org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        return !paused && team.getOrDefault(victim, 0) != team.getOrDefault(attacker, 1);
    }

    @Override protected void second() {
        if (time % 4 == 0) for (Player p : alive) if (!p.getInventory().contains(Material.ARROW)) p.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
    }

    @Override protected void checkWin() {
        if (state != State.RUNNING) return;
        long t0 = alive.stream().filter(p -> team.getOrDefault(p, 0) == 0).count(), t1 = alive.size() - t0;
        if (t0 == 0 || t1 == 0) {
            int win = t0 > 0 ? 0 : 1;
            end(players.stream().filter(p -> team.getOrDefault(p, -1) == win).toList());
        }
    }

    @Override protected GameMode mode() { return GameMode.SURVIVAL; }

    @Override protected String status(Player p) {
        return TN[team.getOrDefault(p, 0)] + " &8| &c" + score[0] + " &7:&9 " + score[1] + " &8| &7Do " + GOALS + " &8| &e" + fmt(timeLimit() - time);
    }

    @Override protected void timeUp() {
        if (score[0] == score[1]) { end(List.of()); return; }
        int t = score[0] > score[1] ? 0 : 1;
        end(players.stream().filter(p -> team.getOrDefault(p, -1) == t).toList());
    }
}
