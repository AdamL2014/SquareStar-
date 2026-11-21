package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** UFO: jednou za pár minut proletí nad kopulí lobby (postavené z blokových displejů, svítí a točí se). /ufo = vyvolat (admin). */
public final class Ufo implements CommandExecutor {
    private static final String TAG = "ss_ufo";
    private final SquareStar pl;
    private final Random rnd = new Random();
    private boolean flying;
    private long nextAt = System.currentTimeMillis() + 60_000;

    public Ufo(SquareStar pl) {
        this.pl = pl;
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (flying || System.currentTimeMillis() < nextAt) return;
            nextAt = System.currentTimeMillis() + (150 + rnd.nextInt(210)) * 1000L;   // 2,5–6 minut
            if (!pl.mars.getPlayers().isEmpty()) fly();
        }, 200, 100);
    }

    private BlockDisplay part(Location at, Material m, float sx, float sy, float sz, float rotY) {
        return pl.mars.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            // střed bloku do počátku
            d.setTransformation(new Transformation(new Vector3f(-sx / 2, -sy / 2, -sz / 2), new AxisAngle4f(rotY, 0, 1, 0), new Vector3f(sx, sy, sz), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.addScoreboardTag(TAG); d.setPersistent(false); d.setViewRange(8f);
        });
    }

    public void fly() {
        if (flying) return;
        flying = true;
        double ang = rnd.nextDouble() * Math.PI * 2;
        double R = 95, y = Lobby.Y + 48 + rnd.nextInt(10);
        Location start = new Location(pl.mars, Math.cos(ang) * R, y, Math.sin(ang) * R);
        double endAng = ang + Math.PI + (rnd.nextDouble() - 0.5) * 0.6;
        Location end = new Location(pl.mars, Math.cos(endAng) * R, y + rnd.nextInt(8) - 4, Math.sin(endAng) * R);
        // tělo: vrstvený talíř (osmiúhelník = dva čtverce otočené o 45°), kabina, světla, anténa
        List<BlockDisplay> body = new ArrayList<>();
        List<Double> offY = new ArrayList<>();
        List<BlockDisplay> lights = new ArrayList<>();
        float q = (float) (Math.PI / 4);
        Object[][] layers = {
                {Material.LIGHT_GRAY_CONCRETE, 7.2f, 0.3f, 0.0},      // obvodový límec
                {Material.WHITE_CONCRETE, 5.8f, 0.5f, 0.38},          // horní trup
                {Material.GRAY_CONCRETE, 5.2f, 0.45f, -0.38},         // spodní trup
                {Material.BLACK_CONCRETE, 3.4f, 0.4f, -0.75},         // spodek
                {Material.LIGHT_GRAY_CONCRETE, 3.0f, 0.35f, 0.85}};   // podstavec kabiny
        for (Object[] l : layers) for (int k = 0; k < 2; k++) { body.add(part(start, (Material) l[0], (float) l[1], (float) l[2], (float) l[1], k * q)); offY.add((double) (double) l[3]); }
        body.add(part(start, Material.LIME_STAINED_GLASS, 2.6f, 1.2f, 2.6f, q / 2)); offY.add(1.3);       // kabina
        body.add(part(start, Material.LIME_CONCRETE, 1.0f, 0.8f, 1.0f, 0)); offY.add(1.3);                // zářící jádro
        body.add(part(start, Material.WHITE_CONCRETE, 1.2f, 0.3f, 1.2f, q)); offY.add(1.95);             // víčko
        body.add(part(start, Material.END_ROD, 0.15f, 1.0f, 0.15f, 0)); offY.add(2.5);                    // anténa
        body.add(part(start, Material.SEA_LANTERN, 2.0f, 0.3f, 2.0f, q)); offY.add(-1.05);                // zářivý spodek
        Material[] lm = {Material.REDSTONE_BLOCK, Material.SEA_LANTERN, Material.GLOWSTONE, Material.EMERALD_BLOCK};
        for (int i = 0; i < 12; i++) lights.add(part(start, lm[i % lm.length], 0.5f, 0.5f, 0.5f, 0));
        Vector dir = end.toVector().subtract(start.toVector());
        double total = dir.length();
        Vector step = dir.clone().normalize().multiply(0.4);
        int ticks = (int) (total / 0.4);
        for (Player p : pl.mars.getPlayers()) if (pl.games.of(p) == null) U.bar(p, "&a✦ &fNad kopulí prolétá UFO! &7Podívej se na oblohu.");
        new BukkitRunnable() {
            int t = 0; double spin = 0;
            @Override public void run() {
                if (t++ >= ticks) { body.forEach(BlockDisplay::remove); lights.forEach(BlockDisplay::remove); flying = false; cancel(); return; }
                Location c = start.clone().add(step.clone().multiply(t));
                c.add(0, Math.sin(t / 9.0) * 1.2, 0);
                for (int i = 0; i < body.size(); i++) body.get(i).teleport(c.clone().add(0, offY.get(i), 0));
                spin += 0.16;
                for (int i = 0; i < lights.size(); i++) {
                    double a = spin + i * Math.PI / 6;
                    lights.get(i).teleport(c.clone().add(Math.cos(a) * 3.55, 0.05, Math.sin(a) * 3.55));
                }
                // paprsek světla dolů (zeleno-bílý kužel)
                if (t % 2 == 0) for (int k = 0; k < 6; k++) {
                    double h = 1.2 + k * 1.6, rr = 0.4 + k * 0.45, an = spin * 2 + k;
                    pl.mars.spawnParticle(Particle.DUST, c.clone().add(Math.cos(an) * rr, -h, Math.sin(an) * rr), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(150, 255, 170), 1.4f), true);
                }
                if (t % 3 == 0) pl.mars.spawnParticle(Particle.END_ROD, c.clone().add(0, -1.2, 0), 2, 0.4, 0.1, 0.4, 0.01, null, true);
                if (t % 3 == 1) pl.mars.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 0, 0), 4, 3, 0.2, 3, 0.1, null, true);
                if (t % 20 == 0) for (Player p : pl.mars.getPlayers()) {
                    double d = p.getLocation().distance(c);
                    if (d < 90) p.playSound(p.getLocation().add(c.toVector().subtract(p.getLocation().toVector()).normalize().multiply(8)), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 2.5f, 1.7f);
                }
            }
        }.runTaskTimer(pl, 1, 1);
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!s.hasPermission("squarestar.admin")) { s.sendMessage(U.c("&cNemáš oprávnění.")); return true; }
        if (flying) { s.sendMessage(U.c("&7UFO už letí.")); return true; }
        fly(); s.sendMessage(U.c("&aUFO vyletělo!"));
        return true;
    }
}
