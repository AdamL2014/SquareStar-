package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/** 3D meteority: jednou za čas přeletí přes oblohu nad lobby (otáčející se balvan, oheň, kouř) a dopadnou za obzorem. */
public final class Meteors {
    private final SquareStar pl;
    private final Random rnd = new Random();
    private int wait = 600;

    public Meteors(SquareStar pl) {
        this.pl = pl;
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_meteor")) e.remove();
        if (pl.getConfig().getBoolean("lobby.fx.meteors", true)) Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (--wait > 0 || pl.mars.getPlayers().isEmpty()) return;
            wait = 900 + rnd.nextInt(900);       // každých 45–90 s
            launch();
        }, 200, 1);
    }

    public void launch() {
        double a = rnd.nextDouble() * Math.PI * 2;
        Location from = new Location(pl.mars, Math.cos(a) * 110, Lobby.Y + 85 + rnd.nextInt(20), Math.sin(a) * 110);
        double b = a + Math.PI + (rnd.nextDouble() - 0.5) * 1.2;
        Location to = new Location(pl.mars, Math.cos(b) * 75, Lobby.Y - 4, Math.sin(b) * 75);
        float size = 3f + rnd.nextFloat() * 2.5f;
        ItemStack it = new ItemStack(Material.PAPER);
        var m = it.getItemMeta(); m.setItemModel(new NamespacedKey("squarestar", "meteor_rock")); it.setItemMeta(m);
        ItemDisplay d = pl.mars.spawn(from, ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setPersistent(false);
            x.addScoreboardTag("ss_meteor");
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(8f);                     // vidět zdaleka
            x.setTeleportDuration(2);
            x.setInterpolationDuration(2);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size), new Quaternionf()));
        });
        int ticks = 110;
        float[] spin = {0};
        Vector3f axis = new Vector3f(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()).normalize();
        Bukkit.getScheduler().runTaskTimer(pl, task -> {
            int t = (int) spin[0]++;
            if (!d.isValid()) { task.cancel(); return; }
            if (t >= ticks) {
                task.cancel();
                Location l = d.getLocation();
                d.remove();
                pl.mars.spawnParticle(Particle.EXPLOSION_EMITTER, l, 2, 1, 1, 1, 0, null, true);
                pl.mars.spawnParticle(Particle.LAVA, l, 40, 3, 2, 3, 0, null, true);
                pl.mars.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l.clone().add(0, 2, 0), 30, 2, 2, 2, 0.05, null, true);
                pl.mars.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 6f, 0.5f);
                return;
            }
            double k = (t + 1) / (double) ticks;
            // lehce zakřivená dráha (gravitace)
            Location l = from.clone().add(to.clone().subtract(from).toVector().multiply(k));
            l.add(0, Math.sin(k * Math.PI) * 10 - k * k * 6, 0);
            if (t % 2 == 0) {
                d.teleport(l);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(t * 0.15f, axis)), new Vector3f(size), new Quaternionf()));
            }
            pl.mars.spawnParticle(Particle.FLAME, l, 12, size * 0.25, size * 0.25, size * 0.25, 0.04, null, true);
            pl.mars.spawnParticle(Particle.LARGE_SMOKE, l, 4, size * 0.2, size * 0.2, size * 0.2, 0.02, null, true);
            if (t % 3 == 0) pl.mars.spawnParticle(Particle.LAVA, l, 2, 0.5, 0.5, 0.5, 0, null, true);
            if (t == 10) for (var p : pl.mars.getPlayers()) p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.AMBIENT, 0.6f, 0.4f);
        }, 1, 1);
    }
}
