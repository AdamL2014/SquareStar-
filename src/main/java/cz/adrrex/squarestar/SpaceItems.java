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
    private final java.util.Map<java.util.UUID, Integer> jetUses = new java.util.HashMap<>();
    public record Def(String id, Material base, String name, String[] lore, Color color) {}
    public final Map<String, Def> defs = new LinkedHashMap<>();
    private final SquareStar pl;

    public SpaceItems(SquareStar pl) {
        this.pl = pl;
        add("laser_pistol", Material.IRON_HOE, "&c&lLaserová pistole", null, "&7Pravý klik: laserový paprsek", "&7Poškození: &c5 &7| Nabíjení: &e1 s");
        add("jetpack", Material.FIREWORK_STAR, "&b&lJetpack", null, "&7Pravý klik: vystřelí tě nahoru", "&7Nabíjení: &e2 s", "&7Ve hrách: 3× pak pauza &e14 s");
        add("meteor_grenade", Material.SNOWBALL, "&6&lMeteoritový granát", null, "&7Hoď ho! Výbuch neničí mapu");
        add("gravity_boots", Material.LEATHER_BOOTS, "&d&lGravitační boty", Color.fromRGB(0x8A2BE2), "&7Obuj si je: skáčeš mnohem výš");
        add("oxygen_helmet", Material.LEATHER_HELMET, "&f&lKyslíková helma", Color.fromRGB(0xDDEEFF), "&7Dýchání pod vodou + noční vidění");
        add("plasma_sword", Material.IRON_SWORD, "&5&lPlazmový meč", null, "&7Zapálí nepřítele plazmou");
        add("moon_rock", Material.FLINT, "&7&lMěsíční kámen", null, "&7Pravý klik: krátce se vzneseš");
        add("space_ration", Material.COOKED_BEEF, "&e&lVesmírná porce", null, "&7Jídlo astronautů");
        add("star_shard", Material.AMETHYST_SHARD, "&e&l✦ Hvězdný úlomek", null, "&7Vzácný sběratelský kousek");
        add("black_hole", Material.ECHO_SHARD, "&5&l◉ Mini černá díra", null, "&7Pravý klik: hodíš ji před sebe", "&7Přitáhne a podrží hráče!", "&7Zničíš ji úderem, jinak zmizí za &e20 s");
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
                boolean inGame = pl.games.of(p) != null;
                double up = 0.9;
                if (inGame) {
                    // ve hrách (PvP, BedWars...): 3 použití, pak 14 s pauza; a žádné létání do nebe
                    int n = jetUses.merge(p.getUniqueId(), 1, Integer::sum);
                    if (n >= 3) { jetUses.remove(p.getUniqueId()); p.setCooldown(it, 280); U.bar(p, "&c&lJetpack se přehřál! &7Pauza &e14 s"); }
                    else { p.setCooldown(it, 40); U.bar(p, "&bJetpack: &e" + (3 - n) + "&7× do přehřátí"); }
                    var hit = p.getWorld().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), 40, org.bukkit.FluidCollisionMode.NEVER, true);
                    double ground = hit == null ? 40 : p.getLocation().getY() - hit.getHitPosition().getY();
                    up = ground > 7 ? 0.12 : 0.55;           // vysoko nad zemí už nevyletí
                    if (ground > 7) U.bar(p, "&cMoc vysoko – jetpack už víc nevyletí!");
                } else p.setCooldown(it, 40);
                Vector v = p.getLocation().getDirection().multiply(0.6).setY(up);
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
            case "black_hole" -> {
                e.setCancelled(true);
                if (p.hasCooldown(it)) return;
                p.setCooldown(it, 40);
                it.setAmount(it.getAmount() - 1);
                blackHole(p);
            }
            default -> {}
        }
    }

    // ---------------- mini černá díra ----------------
    private final Map<UUID, Object[]> holes = new HashMap<>(); // interaction id -> {ItemDisplay, Interaction, owner, task}
    private void blackHole(Player p) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), 9, FluidCollisionMode.NEVER, true);
        Location at = r != null ? r.getHitPosition().toLocation(p.getWorld()).subtract(eye.getDirection().multiply(0.8)) : eye.clone().add(eye.getDirection().multiply(8));
        at.add(0, 0.6, 0);
        World w = at.getWorld();
        ItemDisplay core = w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(get("black_hole", 1));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(1.8f, 1.8f, 1.8f), new org.joml.Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15)); d.setPersistent(false); d.setTeleportDuration(1);
        });
        Interaction hit = w.spawn(at.clone().add(0, -0.8, 0), Interaction.class, i -> { i.setInteractionWidth(1.6f); i.setInteractionHeight(1.6f); i.setResponsive(true); i.setPersistent(false); i.addScoreboardTag("ss_blackhole"); });
        w.playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.6f);
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        holes.put(hit.getUniqueId(), new Object[]{core, hit, p.getUniqueId(), task});
        int[] t = {0};
        task[0] = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            t[0]++;
            if (!core.isValid() || t[0] > 400) { collapse(hit.getUniqueId(), false); return; }
            // otáčení + víření částic
            core.setInterpolationDelay(0); core.setInterpolationDuration(2);
            core.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf().rotateY(t[0] * 0.25f).rotateX(0.3f), new org.joml.Vector3f(1.8f, 1.8f, 1.8f), new org.joml.Quaternionf()));
            for (int k = 0; k < 3; k++) {
                double a = t[0] * 0.4 + k * 2.1, rr = 2.5 - (t[0] % 20) * 0.1;
                w.spawnParticle(Particle.PORTAL, at.clone().add(Math.cos(a) * rr, Math.sin(a * 0.5) * 0.4, Math.sin(a) * rr), 2, 0, 0, 0, 0.3);
            }
            if (t[0] % 10 == 0) w.spawnParticle(Particle.REVERSE_PORTAL, at, 12, 0.3, 0.3, 0.3, 0.05);
            if (t[0] % 30 == 0) w.playSound(at, Sound.BLOCK_PORTAL_AMBIENT, 0.6f, 0.5f);
            // přitahování hráčů (kromě toho, kdo ji hodil)
            for (Player o : w.getPlayers()) {
                if (o.getUniqueId().equals(p.getUniqueId()) || o.getGameMode() == GameMode.SPECTATOR) continue;
                double d = o.getLocation().distance(at);
                if (d > 9) continue;
                Vector to = at.toVector().subtract(o.getLocation().toVector().add(new Vector(0, 0.9, 0)));
                if (d < 1.6) { o.setVelocity(to.multiply(0.35)); o.setFallDistance(0); if (t[0] % 20 == 0) U.bar(o, "&5&l◉ Drží tě černá díra! &7Udeř do ní!"); }
                else o.setVelocity(o.getVelocity().multiply(0.6).add(to.normalize().multiply(0.18 + 0.5 / Math.max(1.5, d))));
            }
        }, 1, 1);
    }
    private void collapse(UUID id, boolean byHit) {
        Object[] h = holes.remove(id);
        if (h == null) return;
        ItemDisplay core = (ItemDisplay) h[0]; Interaction hit = (Interaction) h[1];
        org.bukkit.scheduler.BukkitTask t = ((org.bukkit.scheduler.BukkitTask[]) h[3])[0];
        if (t != null) t.cancel();
        Location l = core.getLocation();
        l.getWorld().spawnParticle(byHit ? Particle.EXPLOSION : Particle.SMOKE, l, byHit ? 3 : 20, 0.4, 0.4, 0.4, 0.05);
        l.getWorld().playSound(l, byHit ? Sound.BLOCK_GLASS_BREAK : Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
        core.remove(); hit.remove();
    }
    /** zničení úderem (levý i pravý klik) */
    @EventHandler public void hitHole(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (e.getAttacked().getScoreboardTags().contains("ss_blackhole")) { e.setCancelled(true); collapse(e.getAttacked().getUniqueId(), true); }
    }
    @EventHandler public void clickHole(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("ss_blackhole")) { e.setCancelled(true); collapse(e.getRightClicked().getUniqueId(), true); }
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
