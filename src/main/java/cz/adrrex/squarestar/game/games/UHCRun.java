package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * UHC Run: rychlé UHC. Sbíráš suroviny na marťanském terénu, rudy se samy taví a padají dvojnásobně,
 * lístky dávají jablka. První 2 minuty je klid (bez PvP), od 7. minuty se hrací plocha zmenšuje.
 */
public final class UHCRun extends Game implements Listener {
    private static final int R = 42;                       // poloměr mapy
    private static final int GRACE = 120, SHRINK_FROM = 420, LIMIT = 720, MIN_R = 14;
    private final int[][] hgt = new int[2 * R + 1][2 * R + 1];
    private double ph1, ph2, ph3, ph4;
    private int radius = R;

    public UHCRun(GameManager gm, int slot) { super(gm, GameType.UHCRUN, slot); }

    @Override public List<String> maps() { return List.of("Zelené údolí", "Sněžné kopce"); }
    @Override protected GameMode mode() { return GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected boolean hunger() { return true; }
    @Override protected boolean canDrop() { return true; }
    @Override protected int timeLimit() { return LIMIT; }
    @Override protected int voidY() { return oy - 30; }

    // ---------------- mapa ----------------
    private int height(int x, int z) {
        double v = Math.sin(x * 0.11 + ph1) * 2.4 + Math.cos(z * 0.09 + ph2) * 2.4 + Math.sin((x + z) * 0.05 + ph3) * 3.0 + Math.cos((x - z) * 0.19 + ph4) * 1.2;
        double edge = Math.max(Math.abs(x), Math.abs(z)) / (double) R;
        v -= Math.max(0, edge - 0.85) * 18;                 // okraje klesají k vodě
        return oy + 3 + (int) Math.round(v);
    }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        ph1 = rnd.nextDouble() * 6.28; ph2 = rnd.nextDouble() * 6.28; ph3 = rnd.nextDouble() * 6.28; ph4 = rnd.nextDouble() * 6.28;
        boolean snow = map == 1;
        Material top = snow ? Material.SNOW_BLOCK : Material.GRASS_BLOCK, sub = snow ? Material.SNOW_BLOCK : Material.DIRT;
        Material leaf = snow ? Material.SPRUCE_LEAVES : Material.OAK_LEAVES, log = snow ? Material.SPRUCE_LOG : Material.OAK_LOG;
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) > R) continue;
            int h = height(dx, dz); hgt[dx + R][dz + R] = h;
            int x = ox + dx, z = oz + dz;
            for (int y = oy - 8; y <= h; y++) {
                Material m;
                if (y == h) m = h <= oy ? Material.SAND : top;
                else if (y >= h - 3) m = h <= oy ? Material.SAND : sub;
                else {
                    int r = rnd.nextInt(400);
                    m = r < 9 ? Material.COAL_ORE : r < 15 ? Material.IRON_ORE : r < 18 && y < oy ? Material.GOLD_ORE : r < 19 && y < oy - 2 ? Material.DIAMOND_ORE : r < 21 ? Material.GRAVEL : Material.STONE;
                }
                b.set(x, y, z, m);
            }
            for (int y = h + 1; y <= oy; y++) b.set(x, y, z, Material.WATER);   // jezírka v nížinách
        }
        for (int i = 0; i < 38; i++) {
            int dx = rnd.nextInt(2 * R - 14) - R + 7, dz = rnd.nextInt(2 * R - 14) - R + 7;
            int h = hgt[dx + R][dz + R];
            if (h <= oy + 1 || Math.hypot(dx, dz) < 6) continue;
            int th = 4 + rnd.nextInt(2), x = ox + dx, z = oz + dz;
            for (int y = h + 1; y <= h + th; y++) b.set(x, y, z, log);
            for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) for (int ly = th - 1; ly <= th + 1; ly++) {
                if (lx == 0 && lz == 0 && ly < th) continue;
                if (Math.abs(lx) + Math.abs(lz) + Math.max(0, ly - th) * 2 > 3) continue;
                b.set(x + lx, h + ly, z + lz, leaf);
            }
        }
    }

    @Override protected void afterBuild() { Bukkit.getPluginManager().registerEvents(this, pl); }
    @Override protected void onDestroy() { HandlerList.unregisterAll(this); }

    // ---------------- pravidla ----------------
    private boolean inside(Block b) { return owns(b.getLocation()) && Math.abs(b.getX() - ox) <= R && Math.abs(b.getZ() - oz) <= R && b.getY() >= oy - 8; }
    @Override public boolean canBreak(Player p, Block b) { return inside(b); }
    @Override public boolean canPlace(Player p, Block b) { return inside(b) && b.getY() < oy + 40; }
    @Override public boolean onPvp(Player victim, Player attacker, EntityDamageByEntityEvent e) {
        if (time < GRACE) { U.msg(attacker, "&7Ještě je klid zbraní! &eZbývá " + (GRACE - time) + " s."); return false; }
        return true;
    }

    /** auto-smelt (cutclean), dvojité rudy, jablka z lístků */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drops(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (state != State.RUNNING || !alive.contains(p) || e.getBlock().getWorld() != w) return;
        Material m = e.getBlock().getType();
        ItemStack drop = switch (m) {
            case IRON_ORE -> new ItemStack(Material.IRON_INGOT, 2);
            case GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 2);
            case COAL_ORE -> new ItemStack(Material.COAL, 2);
            case DIAMOND_ORE -> new ItemStack(Material.DIAMOND, rnd.nextInt(3) == 0 ? 2 : 1);
            case SAND -> new ItemStack(Material.GLASS, 1);
            case GRAVEL -> new ItemStack(Material.FLINT, 1);
            case OAK_LEAVES, SPRUCE_LEAVES -> rnd.nextInt(100) < 45 ? new ItemStack(Material.APPLE, 1) : null;
            case OAK_LOG, SPRUCE_LOG -> new ItemStack(Material.OAK_PLANKS, 4);
            default -> null;
        };
        if (drop == null) return;
        e.setDropItems(false);
        Location l = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        w.dropItemNaturally(l, drop);
        if (m == Material.IRON_ORE || m == Material.GOLD_ORE || m == Material.DIAMOND_ORE) e.setExpToDrop(3);
    }

    // ---------------- průběh ----------------
    @Override protected void start() {
        int n = players.size(), i = 0;
        for (Player p : players) {
            double a = i++ * Math.PI * 2 / Math.max(1, n);
            int dx = 0, dz = 0, h = hgt[R][R];
            for (int r : new int[]{28, 24, 32, 20, 16, 10}) {            // první místo na suchu (ne ve vodě)
                int cx = (int) Math.round(Math.cos(a) * r), cz = (int) Math.round(Math.sin(a) * r);
                if (hgt[cx + R][cz + R] > oy + 1) { dx = cx; dz = cz; h = hgt[cx + R][cz + R]; break; }
            }
            for (int cy = 1; cy <= 3; cy++) w.getBlockAt(ox + dx, h + cy, oz + dz).setType(Material.AIR, false);   // ať nespawneš v kmeni
            Location l = U.center(w, ox + dx, h + 1, oz + dz);
            l.setDirection(new Vector(-dx, 0, -dz));
            p.teleport(l);
            kit(p);
        }
        broadcast("&eSbírej suroviny, rudy se &6samy taví&e a padají dvojnásobně! &7Prvních &f" + (GRACE / 60) + " min&7 je klid zbraní. Od &f" + (SHRINK_FROM / 60) + ". minuty&7 se mapa zmenšuje.");
    }

    private void kit(Player p) {
        var inv = p.getInventory();
        inv.clear();
        ItemStack pick = unb(Material.IRON_PICKAXE); pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3); inv.addItem(pick);
        ItemStack axe = unb(Material.IRON_AXE); axe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3); inv.addItem(axe);
        ItemStack sh = unb(Material.IRON_SHOVEL); sh.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3); inv.addItem(sh);
        inv.addItem(unb(Material.STONE_SWORD));
        inv.addItem(new ItemStack(Material.COOKED_BEEF, 16));
        inv.addItem(new ItemStack(Material.CRAFTING_TABLE, 1));
        inv.addItem(new ItemStack(Material.WATER_BUCKET, 1));
    }
    private static ItemStack unb(Material m) { ItemStack it = new ItemStack(m); ItemMeta im = it.getItemMeta(); im.setUnbreakable(true); it.setItemMeta(im); return it; }

    @Override protected void second() {
        if (time == GRACE) broadcast("&c&lKlid zbraní skončil! &7PvP je zapnuté.");
        if (time == SHRINK_FROM - 20) broadcast("&6Za 20 sekund se začne mapa zmenšovat!");
        if (time >= SHRINK_FROM) {
            radius = (int) Math.round(R - (R - MIN_R) * Math.min(1.0, (time - SHRINK_FROM) / (double) (LIMIT - SHRINK_FROM)));
            for (Player p : alive) {
                int d = Math.max(Math.abs(p.getLocation().getBlockX() - ox), Math.abs(p.getLocation().getBlockZ() - oz));
                if (d > radius) { p.damage(2.0); U.sound(p, Sound.ENTITY_PLAYER_HURT, 1f); p.sendActionBar(U.c("&c&l⚠ Jsi mimo hrací plochu!")); }
            }
        }
    }

    @Override protected String status(Player p) {
        String ph = time < GRACE ? "&aklid &7(" + (GRACE - time) + " s)" : time < SHRINK_FROM ? "&cPvP" : "&6zmenšuje se &7(" + radius + ")";
        return "&7Naživu: &a" + alive.size() + " &8| " + ph + " &8| &e" + fmt(LIMIT - time);
    }
}
