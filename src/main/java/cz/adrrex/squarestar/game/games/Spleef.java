package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Spleef (kopání sněhu) a TNT Run (bloky mizí pod nohama) – 3 patra. */
public final class Spleef extends Game {
    private final boolean tnt;
    private final Material floor;
    private final Set<Long> falling = new HashSet<>();
    private static final int R = 14;

    public Spleef(GameManager gm, int slot, boolean tnt) {
        super(gm, tnt ? GameType.TNTRUN : GameType.SPLEEF, slot);
        this.tnt = tnt;
        this.floor = tnt ? Material.TNT : Material.SNOW_BLOCK;
    }

    @Override public List<String> maps() { return tnt ? List.of("Kruhová patra", "Hvězdná patra") : List.of("Sněžný měsíc", "Čtvercový krystal"); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        for (int layer = 0; layer < 3; layer++) {
            int y = oy - layer * 10;
            for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                boolean in;
                if (map == 1 && tnt) { // hvězda: 5 cípů
                    double a = Math.atan2(z, x), star = R * (0.62 + 0.38 * Math.abs(Math.cos(2.5 * a)));
                    in = d <= star;
                } else if (map == 1) in = Math.abs(x) <= R - 1 && Math.abs(z) <= R - 1 && !(Math.abs(x) <= 1 && Math.abs(z) <= 1 && layer == 0);
                else in = d <= R;
                if (in) b.set(ox + x, y, oz + z, floor);
                else if (d <= R + 1.2 && layer == 0) b.set(ox + x, y - 1, oz + z, map == 1 ? Material.AMETHYST_BLOCK : Material.RED_NETHER_BRICKS);
            }
        }
    }

    @Override protected int voidY() { return oy - 28; }
    @Override protected GameMode mode() { return tnt ? GameMode.ADVENTURE : GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.KNOCKBACK; }
    @Override protected int timeLimit() { return 300; }

    @Override protected void start() {
        for (Player p : players) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * (R - 3);
            p.teleport(new Location(w, ox + Math.cos(a) * d + 0.5, oy + 1, oz + Math.sin(a) * d + 0.5));
            if (!tnt) {
                ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
                ItemMeta m = shovel.getItemMeta(); m.setUnbreakable(true); m.addEnchant(Enchantment.EFFICIENCY, 5, true); shovel.setItemMeta(m);
                p.getInventory().addItem(shovel);
            }
        }
        broadcast(tnt ? "&eBloky pod tebou mizí! Běhej a skákej, nepadej dolů!" : "&eKopej sníh pod soupeři! Za každý blok dostaneš sněhovou kouli.");
    }

    @Override public boolean canBreak(Player p, Block b) {
        if (tnt || b.getType() != Material.SNOW_BLOCK) return false;
        b.setType(Material.AIR, false);
        p.getInventory().addItem(new ItemStack(Material.SNOWBALL));
        return false; // blok jsme odstranili sami (bez dropu)
    }

    @Override public void onBlockDamage(BlockDamageEvent e) {
        if (!tnt && e.getBlock().getType() == Material.SNOW_BLOCK) e.setInstaBreak(true);
    }

    @Override public void onProjectileHit(ProjectileHitEvent e) {
        if (!tnt && e.getEntity() instanceof Snowball && e.getHitBlock() != null && e.getHitBlock().getType() == Material.SNOW_BLOCK && owns(e.getHitBlock().getLocation()))
            e.getHitBlock().setType(Material.AIR, false);
    }

    @Override public void onMove(Player p, Location to) {
        if (!tnt || time < 3) return;
        for (double dx : new double[]{-0.3, 0.3}) for (double dz : new double[]{-0.3, 0.3}) {
            Block b = w.getBlockAt((int) Math.floor(to.getX() + dx), (int) Math.floor(to.getY() - 0.5), (int) Math.floor(to.getZ() + dz));
            if (b.getType() != Material.TNT) continue;
            long k = U.pack(b.getX(), b.getY(), b.getZ());
            if (!falling.add(k)) continue;
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (b.getType() == Material.TNT) b.setType(Material.AIR, false); }, 8);
        }
    }

    @Override protected void second() {
        // TNT Run: kdo stojí na místě, tomu se blok taky rozpadne
        if (tnt) for (Player p : alive) onMove(p, p.getLocation());
    }
}
