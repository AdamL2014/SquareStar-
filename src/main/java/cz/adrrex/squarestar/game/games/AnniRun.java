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
 * AnniRun: zkrácená Annihilation. Dva týmy, každý má Nexus (maják). Po 3 minutách se odemknou:
 * mlať do cizího Nexusu a sniž jeho životy na 0. Uprostřed jsou rudy (samy se obnovují), smrt = oživení na základně.
 */
public final class AnniRun extends Game implements org.bukkit.event.Listener {
    private static final int IX = 34, NEXUS_HP = Integer.getInteger("ss.anni.hp", 40), PHASE2 = Integer.getInteger("ss.anni.phase2", 180), PHASE3 = Integer.getInteger("ss.anni.phase3", 540);
    private static final String[] TN = {"&c&lČervení", "&9&lModří"};
    private static final Color[] TC = {Color.RED, Color.BLUE};
    private static final Material[] CONC = {Material.RED_CONCRETE, Material.BLUE_CONCRETE};
    private final Map<Player, Integer> team = new HashMap<>();
    private final int[] hp = {NEXUS_HP, NEXUS_HP};
    private final Map<Player, Long> hitAt = new HashMap<>();
    private final Set<Long> own = new HashSet<>();
    private long lastAlert;

    public AnniRun(GameManager gm, int slot) { super(gm, GameType.ANNIRUN, slot); }

    @Override public List<String> maps() { return List.of("Rudný ostrov", "Ledový ostrov"); }
    @Override public List<String> teamNames() { return List.of(TN[0], TN[1]); }
    @Override protected List<Material> teamIcons() { return List.of(Material.RED_WOOL, Material.BLUE_WOOL); }
    @Override public Double gravity() { return 0.08; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected int voidY() { return oy - 14; }
    @Override protected int timeLimit() { return 900; }
    @Override protected boolean canDrop() { return false; }
    @Override protected boolean hunger() { return true; }
    @Override protected GameMode mode() { return GameMode.SURVIVAL; }

    private int nx(int t) { return ox + (t == 0 ? -IX : IX); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        boolean ice = map == 1;
        Material floor = ice ? Material.PACKED_ICE : Material.STONE_BRICKS, top = ice ? Material.SNOW_BLOCK : Material.SMOOTH_STONE;
        Material rock = ice ? Material.BLUE_ICE : Material.STONE;
        for (int t = 0; t < 2; t++) {
            int cx = t == 0 ? -IX : IX, s = t == 0 ? 1 : -1;
            for (int x = cx - 8; x <= cx + 8; x++) for (int z = -8; z <= 8; z++) {
                boolean edge = Math.abs(x - cx) == 8 || Math.abs(z) == 8;
                b.set(ox + x, oy, oz + z, edge ? CONC[t] : top);
                b.set(ox + x, oy - 1, oz + z, floor);
                b.set(ox + x, oy - 2, oz + z, Material.POLISHED_ANDESITE);
            }
            // nexus: maják na železném podstavci, kolem ochranný rámeček
            b.set(ox + cx, oy, oz, Material.IRON_BLOCK);
            b.set(ox + cx, oy + 1, oz, Material.BEACON);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(ox + cx + d[0], oy + 1, oz + d[1], Material.CRYING_OBSIDIAN);
            // zadní zeď
            for (int z = -8; z <= 8; z++) for (int y = 1; y <= 8; y++) b.set(ox + cx - s * 9, oy + y, oz + z, y == 8 ? Material.SEA_LANTERN : (t == 0 ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS));
            // můstek ke středu (3 široký)
            for (int x = 0; x <= IX - 18; x++) for (int z = -1; z <= 1; z++) {
                int bx = cx + s * (9 + x);
                b.set(ox + bx, oy, oz + z, Material.STONE_BRICKS);
                b.set(ox + bx, oy - 1, oz + z, Material.STONE_BRICKS);
            }
        }
        // střední ostrov s rudami (obnovují se přes heal)
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            if (x * x + z * z > 150) continue;
            for (int y = -3; y <= 0; y++) {
                int r = rnd.nextInt(100);
                Material m = y == 0 ? top : r < 12 ? Material.COAL_ORE : r < 22 ? Material.IRON_ORE : r < 26 ? Material.GOLD_ORE : r < 28 && y < 0 ? Material.DIAMOND_ORE : rock;
                b.set(ox + x, oy + y, oz + z, m);
            }
            if (rnd.nextInt(6) == 0) for (int y = 1; y <= 2; y++) b.set(ox + x, oy + y, oz + z, rnd.nextBoolean() ? Material.IRON_ORE : Material.COAL_ORE);
        }
        // strom pro dřevo
        for (int y = 1; y <= 5; y++) b.set(ox, oy + y, oz + 8, Material.OAK_LOG);
        b.sphere(ox, oy + 6, oz + 8, 2, p -> { if (p[1] >= oy + 5) b.set(p[0], p[1], p[2], Material.OAK_LEAVES); });
    }

    @Override protected void afterBuild() { Bukkit.getPluginManager().registerEvents(this, pl); }
    @Override protected void onDestroy() { org.bukkit.event.HandlerList.unregisterAll(this); }

    private Location spawn(Player p) {
        int t = team.getOrDefault(p, 0), s = t == 0 ? 1 : -1;
        int idx = 0, n = 0;
        for (Player x : players) if (team.getOrDefault(x, 0) == t) { if (x == p) idx = n; n++; }
        Location l = U.center(w, nx(t) + s * 3, oy + 1, oz - 4 + (idx % 9));
        l.setDirection(new Vector(s, 0, 0));
        return l;
    }

    @Override protected void start() {
        List<Player> order = new ArrayList<>(players);
        Collections.shuffle(order, rnd);
        int[] cnt = new int[2];
        int cap = (int) Math.ceil(order.size() / 2.0);
        for (Player p : order) { Integer pick = teamPick.get(p); if (pick != null && pick < 2 && cnt[pick] < cap) { team.put(p, pick); cnt[pick]++; } }
        for (Player p : order) if (!team.containsKey(p)) { int t = cnt[0] <= cnt[1] ? 0 : 1; team.put(p, t); cnt[t]++; }
        for (Player p : players) {
            U.title(p, TN[team.get(p)], "&7Braň svůj Nexus, znič cizí!");
            p.teleport(spawn(p));
            kit(p);
        }
        broadcast("&eFáze 1 (&f3 min&e): těž rudy uprostřed a vybav se. Pak se Nexusy odemknou – &bmlať do cizího majáku&e! Každý má &c" + NEXUS_HP + " životů&e.");
    }

    private void kit(Player p) {
        int t = team.getOrDefault(p, 0);
        var inv = p.getInventory();
        inv.clear();
        inv.addItem(unb(Material.STONE_SWORD));
        ItemStack pick = unb(Material.IRON_PICKAXE); pick.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, 2); inv.addItem(pick);
        inv.addItem(unb(Material.IRON_AXE));
        inv.addItem(new ItemStack(Material.COOKED_BEEF, 12));
        inv.addItem(new ItemStack(CONC[t], 48));
        inv.addItem(new ItemStack(Material.CRAFTING_TABLE, 1));
        inv.addItem(new ItemStack(Material.FURNACE, 1));
        inv.setHelmet(leather(Material.LEATHER_HELMET, t)); inv.setChestplate(leather(Material.LEATHER_CHESTPLATE, t));
        inv.setLeggings(leather(Material.LEATHER_LEGGINGS, t)); inv.setBoots(leather(Material.LEATHER_BOOTS, t));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 4, false, false));
        p.setFireTicks(0);
    }
    private static ItemStack leather(Material m, int t) {
        ItemStack it = new ItemStack(m); LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta(); lm.setColor(TC[t]); lm.setUnbreakable(true); it.setItemMeta(lm); return it;
    }
    private static ItemStack unb(Material m) { ItemStack it = new ItemStack(m); ItemMeta im = it.getItemMeta(); im.setUnbreakable(true); it.setItemMeta(im); return it; }

    // ---------------- bloky ----------------
    private boolean mid(Block b) { return Math.abs(b.getX() - ox) <= 13 && Math.abs(b.getZ() - oz) <= 13 && b.getY() >= oy - 3 && b.getY() <= oy + 8; }
    @Override public boolean canBreak(Player p, Block b) {
        long k = U.pack(b.getX(), b.getY(), b.getZ());
        if (own.contains(k)) return true;
        Material m = b.getType();
        return mid(b) && m != Material.AIR && m != Material.BEDROCK;
    }
    @Override public boolean canPlace(Player p, Block b) {
        int rx = b.getX() - ox, ry = b.getY() - oy;
        if (ry < -1 || ry > 16 || Math.abs(rx) > IX + 9 || Math.abs(b.getZ() - oz) > 12) return false;
        for (int t = 0; t < 2; t++) if (Math.abs(b.getX() - nx(t)) <= 2 && Math.abs(b.getZ() - oz) <= 2 && ry <= 3) return false;
        own.add(U.pack(b.getX(), b.getY(), b.getZ()));
        return true;
    }

    /** rudy se samy taví (jako v UHC Run) */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void drops(org.bukkit.event.block.BlockBreakEvent e) {
        if (state != State.RUNNING || !alive.contains(e.getPlayer()) || e.getBlock().getWorld() != w) return;
        ItemStack d = switch (e.getBlock().getType()) {
            case IRON_ORE -> new ItemStack(Material.IRON_INGOT, 1);
            case GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 1);
            case COAL_ORE -> new ItemStack(Material.COAL, 1);
            case DIAMOND_ORE -> new ItemStack(Material.DIAMOND, 1);
            default -> null;
        };
        if (d == null) return;
        e.setDropItems(false);
        w.dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), d);
    }

    // ---------------- nexus ----------------
    @Override public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.BEACON) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!alive.contains(p)) return;
        int mine = team.getOrDefault(p, 0);
        for (int t = 0; t < 2; t++) {
            if (b.getX() != nx(t) || b.getZ() != oz) continue;
            if (t == mine) { U.msg(p, "&cSvůj Nexus ničit nemůžeš!"); return; }
            if (time < PHASE2) { U.msg(p, "&7Nexus je ještě zamčený &8(odemkne se za " + (PHASE2 - time) + " s)"); return; }
            long now = System.currentTimeMillis();
            if (now - hitAt.getOrDefault(p, 0L) < 450) return;
            hitAt.put(p, now);
            hp[t] -= time >= PHASE3 ? 2 : 1;
            b.getWorld().spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 1, 0.5), 12, 0.4, 0.4, 0.4, 0.2);
            U.sound(p, Sound.BLOCK_ANVIL_HIT, 1.4f);
            if (now - lastAlert > 4000) {
                lastAlert = now;
                for (Player m : players) if (team.getOrDefault(m, -1) == t) { U.sound(m, Sound.BLOCK_BEACON_DEACTIVATE, 1f); m.sendActionBar(U.c("&c&l⚠ Tvůj Nexus je pod útokem! &7(" + Math.max(0, hp[t]) + ")")); }
            }
            if (hp[t] <= 0) {
                hp[t] = 0;
                broadcast(TN[t] + " &7Nexus byl &c&lZNIČEN&7 hráčem &e" + p.getName() + "&7!");
                end(players.stream().filter(x -> team.getOrDefault(x, -1) == mine).toList());
            }
            return;
        }
    }

    @Override public void onDeath(Player p, Player killer) {
        if (!alive.contains(p)) return;
        if (killer != null && killer != p && pl.stats != null) pl.stats.add(killer, "kills", 1);
        broadcast("&c☠ " + p.getName() + (killer != null && killer != p ? " &7byl zabit hráčem &c" + killer.getName() : " &7spadl"));
        U.reset(p, GameMode.SURVIVAL);
        pl.applyGravity(p);
        p.teleport(spawn(p));
        kit(p);
        lastHit.remove(p);
    }

    @Override public boolean onPvp(Player victim, Player attacker, org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        return team.getOrDefault(victim, 0) != team.getOrDefault(attacker, 1);
    }

    @Override protected void second() {
        if (time == PHASE2) broadcast("&6&lFáze 2! &eNexusy jsou odemčené – mlať do cizího majáku!");
        if (time == PHASE3) broadcast("&c&lFáze 3! &eÚtoky na Nexus ubírají &c2 životy&e!");
    }

    @Override protected void checkWin() {
        if (state != State.RUNNING) return;
        // hráči se oživují, takže hra končí jen zničením Nexusu / vypršením času / odchodem celého týmu
        long t0 = players.stream().filter(p -> team.getOrDefault(p, 0) == 0).count(), t1 = players.size() - t0;
        if (t0 == 0 || t1 == 0) { int win = t0 > 0 ? 0 : 1; end(players.stream().filter(p -> team.getOrDefault(p, -1) == win).toList()); }
    }

    @Override protected String status(Player p) {
        String ph = time < PHASE2 ? "&7Nexus za &f" + (PHASE2 - time) + " s" : "&eútok!";
        return TN[team.getOrDefault(p, 0)] + " &8| &c" + hp[0] + " &7Nexus&9 " + hp[1] + " &8| " + ph + " &8| &e" + fmt(timeLimit() - time);
    }

    @Override protected void timeUp() {
        if (hp[0] == hp[1]) { end(List.of()); return; }
        int t = hp[0] > hp[1] ? 0 : 1;
        end(players.stream().filter(p -> team.getOrDefault(p, -1) == t).toList());
    }
}
