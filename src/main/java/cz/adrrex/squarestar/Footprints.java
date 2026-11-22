package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;

/** Stopy na marsovském povrchu v lobby: kroky ti na chvíli ztmaví zem (jen vizuálně, svět se nemění). */
public final class Footprints {
    private static final Set<Material> SURFACE = EnumSet.of(Material.RED_SAND, Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.BROWN_TERRACOTTA);
    private static final long LIFE = 160;   // 8 s
    private final SquareStar pl;
    private final Map<UUID, Location> last = new HashMap<>();
    private final Set<Long> active = new HashSet<>();
    private boolean left;

    public Footprints(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 40, 3);
    }

    private static Material dark(Material m) {
        return m == Material.BROWN_TERRACOTTA ? Material.BLACK_TERRACOTTA : m == Material.RED_SAND ? Material.BROWN_TERRACOTTA : Material.BROWN_TERRACOTTA;
    }

    private void tick() {
        left = !left;
        for (Player p : pl.mars.getPlayers()) {
            if (pl.games.of(p) != null || p.getGameMode() == GameMode.SPECTATOR || p.isFlying() || p.isInsideVehicle() || p.isGliding() || p.isInvisible() || !p.isOnGround()) continue;
            Location now = p.getLocation(), prev = last.get(p.getUniqueId());
            if (prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) < 0.64) continue;
            last.put(p.getUniqueId(), now);
            // levá/pravá noha: lehce vedle osy pohybu
            double yaw = Math.toRadians(now.getYaw());
            double off = left ? 0.18 : -0.18;
            Block b = now.clone().add(-Math.sin(yaw + Math.PI / 2) * -off, -0.1, Math.cos(yaw + Math.PI / 2) * -off).getBlock();
            if (!SURFACE.contains(b.getType())) b = now.clone().add(0, -0.1, 0).getBlock();
            Material base = b.getType();
            if (!SURFACE.contains(base) || !b.getRelative(0, 1, 0).isPassable()) continue;
            long key = (((long) b.getX() & 0x3FFFFFF) << 38) | (((long) b.getZ() & 0x3FFFFFF) << 12) | ((long) b.getY() & 0xFFF);
            if (!active.add(key)) continue;
            final Block fb = b;
            List<Player> viewers = new ArrayList<>();
            for (Player o : pl.mars.getPlayers()) if (o.getLocation().distanceSquared(now) < 48 * 48) viewers.add(o);
            BlockData dd = dark(base).createBlockData();
            for (Player o : viewers) o.sendBlockChange(fb.getLocation(), dd);
            pl.mars.spawnParticle(Particle.BLOCK, now.clone().add(0, 0.1, 0), 4, 0.15, 0.02, 0.15, 0.02, base.createBlockData());
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                active.remove(key);
                BlockData real = fb.getBlockData();
                for (Player o : viewers) if (o.isOnline() && o.getWorld() == pl.mars) o.sendBlockChange(fb.getLocation(), real);
            }, LIFE);
        }
        last.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }
}
