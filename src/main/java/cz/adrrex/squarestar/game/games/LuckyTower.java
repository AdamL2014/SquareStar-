package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Lucky Tower: každý stojí na vysoké věži a každé 4 sekundy dostane náhodný item. Poslední na nohou vyhrává. */
public final class LuckyTower extends Game {
    private final List<int[]> tops = new ArrayList<>();
    private static final int EVERY = 4;

    public LuckyTower(GameManager gm, int slot) { super(gm, GameType.LUCKYTOWER, slot); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        int n = type.max;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = ox + (int) Math.round(Math.cos(a) * 11), z = oz + (int) Math.round(Math.sin(a) * 11);
            for (int y = oy - 30; y <= oy; y++) b.set(x, y, z, y == oy ? Material.CRYING_OBSIDIAN : Material.BEDROCK);
            tops.add(new int[]{x, oy + 1, z});
        }
        // měsíc dole jako dekorace (pád = vyřazení)
        b.sphere(ox, oy - 45, oz, 5, p -> b.set(p[0], p[1], p[2], Material.RED_TERRACOTTA));
    }

    @Override protected int voidY() { return oy - 32; }
    @Override protected GameMode mode() { return GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected boolean canDrop() { return true; }
    @Override protected int timeLimit() { return 420; }
    @Override public boolean canBreak(Player p, Block b) { return b.getY() < oy + 40 && b.getType() != Material.BEDROCK; }
    @Override public boolean canPlace(Player p, Block b) { return b.getY() < oy + 40; }

    @Override protected void start() {
        int i = 0;
        for (Player p : players) {
            int[] t = tops.get(i++ % tops.size());
            Location l = U.center(w, t[0], t[1], t[2]);
            l.setDirection(new Vector(ox - t[0], 0, oz - t[2]));
            p.teleport(l);
        }
        broadcast("&eKaždé &6" + EVERY + " s &edostaneš náhodný item. Postav most a shoď ostatní!");
    }

    @Override protected void second() {
        if (time % EVERY != 0) return;
        for (Player p : alive) {
            ItemStack it = Loot.random(rnd, time > 120);
            for (ItemStack left : p.getInventory().addItem(it).values()) w.dropItemNaturally(p.getLocation(), left);
            U.sound(p, Sound.ENTITY_ITEM_PICKUP, 1.2f);
        }
    }
}
