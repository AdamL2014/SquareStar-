package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** Vlastní vesmírné itemy (náhrada Oraxenu). Vzhled dodává resource pack přes item_model "squarestar:<id>". */
public final class SpaceItems implements Listener {
    public record Def(String id, Material base, String name, String[] lore, Color color) {}
    public final Map<String, Def> defs = new LinkedHashMap<>();
    private final SquareStar pl;

    public SpaceItems(SquareStar pl) {
        this.pl = pl;
        add("laser_pistol", Material.IRON_HOE, "&c&lLaserová pistole", null, "&7Pravý klik: laserový paprsek", "&7Poškození: &c5 &7| Nabíjení: &e1 s");
        add("jetpack", Material.FIREWORK_STAR, "&b&lJetpack", null, "&7Pravý klik: vystřelí tě nahoru", "&7Nabíjení: &e2 s");
        add("meteor_grenade", Material.SNOWBALL, "&6&lMeteoritový granát", null, "&7Hoď ho! Výbuch neničí mapu");
        add("gravity_boots", Material.LEATHER_BOOTS, "&d&lGravitační boty", Color.fromRGB(0x8A2BE2), "&7Obuj si je: skáčeš mnohem výš");
        add("oxygen_helmet", Material.LEATHER_HELMET, "&f&lKyslíková helma", Color.fromRGB(0xDDEEFF), "&7Dýchání pod vodou + noční vidění");
        add("plasma_sword", Material.IRON_SWORD, "&5&lPlazmový meč", null, "&7Zapálí nepřítele plazmou");
        add("moon_rock", Material.FLINT, "&7&lMěsíční kámen", null, "&7Pravý klik: krátce se vzneseš");
        add("space_ration", Material.COOKED_BEEF, "&e&lVesmírná porce", null, "&7Jídlo astronautů");
        add("star_shard", Material.AMETHYST_SHARD, "&e&l✦ Hvězdný úlomek", null, "&7Vzácný sběratelský kousek");
    }

    private void add(String id, Material m, String name, Color c, String... lore) { defs.put(id, new Def(id, m, name, lore, c)); }

    public ItemStack get(String id, int amount) {
        Def d = defs.get(id);
        if (d == null) return null;
        ItemStack it = U.item(d.base(), amount, d.name(), d.lore());
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("squarestar", id));
        if (m instanceof LeatherArmorMeta lm && d.color() != null) lm.setColor(d.color());
        it.setItemMeta(m);
        return U.tag(it, "ss_item", id);
    }

    @EventHandler public void use(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        String id = U.tagOf(it, "ss_item");
        if (id == null) return;
        Player p = e.getPlayer();
        switch (id) {
            case "laser_pistol" -> {
                e.setCancelled(true);
                if (p.hasCooldown(it)) return;
                p.setCooldown(it, 20);
                laser(p);
            }
            case "jetpack" -> {
                e.setCancelled(true);
                if (p.hasCooldown(it)) return;
                p.setCooldown(it, 40);
                Vector v = p.getLocation().getDirection().multiply(0.6).setY(0.9);
                p.setVelocity(v);
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 30, 0.2, 0.1, 0.2, 0.05);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 0.8f);
            }
            case "moon_rock" -> {
                e.setCancelled(true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));
                it.setAmount(it.getAmount() - 1);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1.5f);
            }
            default -> {}
        }
    }

    private void laser(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, 30, FluidCollisionMode.NEVER, true, 0.3,
                en -> en instanceof LivingEntity && en != p && !(en instanceof ArmorStand));
        double dist = r == null ? 30 : r.getHitPosition().distance(eye.toVector());
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.0f);
        for (double d = 0.5; d < dist; d += 0.4) p.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        p.getWorld().playSound(eye, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 2f);
        if (r != null && r.getHitEntity() instanceof LivingEntity le) le.damage(5, p);
    }

    @EventHandler public void throwGrenade(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Snowball s && "meteor_grenade".equals(U.tagOf(s.getItem(), "ss_item"))) s.addScoreboardTag("ss_meteor");
    }

    @EventHandler public void grenade(ProjectileHitEvent e) {
        if (!e.getEntity().getScoreboardTags().contains("ss_meteor")) return;
        Location l = e.getEntity().getLocation();
        Entity src = e.getEntity().getShooter() instanceof Entity en ? en : null;
        l.getWorld().createExplosion(l, 2.2f, false, false, src);
        l.getWorld().spawnParticle(Particle.LAVA, l, 20, 0.5, 0.5, 0.5);
    }

    @EventHandler(ignoreCancelled = true) public void plasma(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && "plasma_sword".equals(U.tagOf(p.getInventory().getItemInMainHand(), "ss_item"))) {
            e.getEntity().setFireTicks(50);
            e.getEntity().getWorld().spawnParticle(Particle.WITCH, e.getEntity().getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        }
    }
}
