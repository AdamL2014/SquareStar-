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
 * UHC Run: rychlé UHC ve skutečném (čerstvě vygenerovaném) vanilla overworldu. Rudy se samy taví a padají dvojnásobně,
 * lístky dávají jablka. Prvních 3 minuty je klid (bez PvP), pak se začne zmenšovat world border. Po hře se svět smaže.
 */
public final class UHCRun extends Game implements Listener {
    private static final int START_SIZE = 500, END_SIZE = 40;                       // průměr borderu
    private static final int GRACE = Integer.getInteger("ss.uhc.grace", 180);
    private static final int SHRINK_FROM = Integer.getInteger("ss.uhc.shrink", 600), SHRINK_FOR = Integer.getInteger("ss.uhc.shrinkfor", 720);
    private static final int LIMIT = SHRINK_FROM + SHRINK_FOR + 180;

    public UHCRun(GameManager gm, int slot) { super(gm, GameType.UHCRUN, slot); }

    @Override protected World pickWorld() { return pl.newUhcWorld(); }
    @Override public List<String> maps() { return List.of("Náhodný overworld"); }
    @Override protected GameMode mode() { return GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected boolean hunger() { return true; }
    @Override protected boolean canDrop() { return true; }
    @Override public Double gravity() { return 0.08; }
    @Override protected int timeLimit() { return LIMIT; }
    @Override protected int voidY() { return w.getMinHeight() - 10; }
    @Override protected int waitY() { return 300; }
    @Override protected Location specSpot() { return U.center(w, 0, 140, 0); }
    @Override public boolean owns(Location l) { return l.getWorld() == w; }

    @Override protected void buildMap(U.BlockQueue.Builder b) { /* mapu generuje Minecraft */ }
    @Override protected void afterBuild() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        WorldBorder wb = w.getWorldBorder();
        wb.setCenter(0, 0); wb.setSize(START_SIZE); wb.setDamageAmount(2.0); wb.setDamageBuffer(1.0); wb.setWarningDistance(12);
    }
    @Override protected void onDestroy() {
        HandlerList.unregisterAll(this);
        World dead = w;
        Bukkit.getScheduler().runTaskLater(pl, () -> pl.deleteUhcWorld(dead), 200);   // až se dočistí čekárna a všichni odejdou
    }

    // ---------------- pravidla ----------------
    @Override public boolean canBreak(Player p, Block b) { return true; }
    @Override public boolean canPlace(Player p, Block b) { return b.getY() < w.getMaxHeight() - 2; }
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
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT, 2);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 2);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT, 3);
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL, 2);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND, rnd.nextInt(3) == 0 ? 2 : 1);
            case SAND -> new ItemStack(Material.GLASS, 1);
            case GRAVEL -> new ItemStack(Material.FLINT, 1);
            default -> Tag.LEAVES.isTagged(m) && rnd.nextInt(100) < 45 ? new ItemStack(Material.APPLE, 1) : null;
        };
        if (drop == null) return;
        e.setDropItems(false);
        w.dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), drop);
        if (m.name().endsWith("_ORE")) e.setExpToDrop(3);
    }

    // ---------------- průběh ----------------
    @Override protected void start() {
        int n = players.size(), i = 0;
        for (Player p : players) {
            double a = i++ * Math.PI * 2 / Math.max(1, n) + rnd.nextDouble() * 0.3;
            Location l = null;
            for (int t = 0; t < 80 && l == null; t++) {                       // první místo na souši (ne ve vodě/lávě)
                double r = 70 + rnd.nextInt(150);
                int x = (int) Math.round(Math.cos(a + t * 0.11) * r), z = (int) Math.round(Math.sin(a + t * 0.11) * r);
                int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Material under = w.getBlockAt(x, y, z).getType();
                if (y < 62 || under == Material.WATER || under == Material.LAVA || under == Material.ICE || Tag.LEAVES.isTagged(under)) continue;
                l = U.center(w, x, y + 1, z);
            }
            if (l == null) l = U.center(w, 0, w.getHighestBlockYAt(0, 0) + 1, 0);
            l.setDirection(new Vector(-l.getX(), 0, -l.getZ()));
            p.teleport(l);
            kit(p);
        }
        w.setTime(6000);
        broadcast("&eSbírej suroviny v &aoverworldu&e – rudy se &6samy taví&e a padají dvojnásobně! &7Prvních &f" + (GRACE >= 60 ? GRACE / 60 + " min" : GRACE + " s") + "&7 je klid zbraní. Za &f" + (SHRINK_FROM >= 60 ? SHRINK_FROM / 60 + " min" : SHRINK_FROM + " s") + "&7 se začne zmenšovat hrací plocha.");
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
        if (time == SHRINK_FROM - 30) broadcast("&6Za 30 sekund se začne zmenšovat hrací plocha!");
        if (time == SHRINK_FROM) {
            w.getWorldBorder().setSize(END_SIZE, SHRINK_FOR);
            broadcast("&c&lBorder se zmenšuje! &7Drž se uvnitř, jinak dostaneš poškození.");
        }
    }

    @Override protected String status(Player p) {
        String ph = time < GRACE ? "&aklid &7(" + (GRACE - time) + " s)" : time < SHRINK_FROM ? "&cPvP" : "&6zmenšuje se";
        return "&7Naživu: &a" + alive.size() + " &8| " + ph + " &8| &7Border: &f" + (int) (w.getWorldBorder().getSize() / 2) + " &8| &e" + fmt(LIMIT - time);
    }
}
