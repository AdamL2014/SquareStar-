package cz.adrrex.squarestar;

import org.bukkit.GameMode;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.util.Vector;

/** Sněhové koule a vejce odstrčí hráče (bez poškození, jen takový kámošský šťouch). */
public final class Fun implements Listener {
    @EventHandler public void hit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!(pr instanceof Snowball || pr instanceof Egg)) return;
        if (pr.getScoreboardTags().contains("ss_meteor")) return;   // meteorová granát má vlastní efekt
        if (!(e.getHitEntity() instanceof Player t) || t.getGameMode() == GameMode.SPECTATOR) return;
        if (pr.getShooter() instanceof Player s && s.equals(t)) return;
        Vector dir = pr.getVelocity().clone().setY(0);
        if (dir.lengthSquared() < 1e-4) return;
        dir.normalize().multiply(0.8).setY(0.32);
        t.setVelocity(t.getVelocity().add(dir));
        t.getWorld().playSound(t.getLocation(), org.bukkit.Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.6f);
    }
    /** vejce nevyklubou kuřata */
    @EventHandler public void egg(PlayerEggThrowEvent e) { e.setHatching(false); }
}
