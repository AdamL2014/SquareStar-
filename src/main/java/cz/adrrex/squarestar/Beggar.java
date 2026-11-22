package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Vesmírný bezdomovec v lobby: žebrá o mince. Za 5 mincí dá hráči holuba na hlavu; druhý holub mu sedí na rameni. */
public final class Beggar implements Listener {
    private static final String TAG = "ss_beggar";
    private static final double X = -5.5, Z = 4.5;
    private final SquareStar pl;
    private Mannequin npc;
    private ItemDisplay pigeon;
    private ArmorStand seat;
    private static final double SIT_DY = -0.05;   // posadí hráče „zadkem“ na podlahu
    private final Map<UUID, Long> asked = new HashMap<>();
    private final Random rnd = new Random();
    private int tick;
    private static final String[] BEG = {
            "&6Hej, kámo... nemáš pár mincí? &7Mám hlad jak černá díra.",
            "&6Pssst! Pět mincí a dostaneš mýho holuba!",
            "&6Moje raketa mi ujela... pomůžeš mi? &e(pravý klik)",
            "&6Za 5 mincí ti dám kámoše na hlavu. &7Je hodnej!",
            "&6Prosím, jen drobné na kyslík... &7*kručí mu v břiše*",
            "&6Holub říká: &eGrrr-uuu! &6To znamená 5 mincí!"};

    public Beggar(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskLater(pl, this::spawn, 60);
        Bukkit.getScheduler().runTaskTimer(pl, this::loop, 100, 5);
    }

    private void spawn() {
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        double fy = Lobby.Y + 1;                                           // povrch podlahy
        Location l = new Location(pl.mars, X, fy + SIT_DY, Z);
        l.setDirection(new org.bukkit.util.Vector(-X, 0, -Z));
        seat = pl.mars.spawn(l, ArmorStand.class, a -> {
            a.setPersistent(false); a.addScoreboardTag(TAG); a.setInvisible(true); a.setMarker(true); a.setGravity(false); a.setSilent(true);
        });
        npc = pl.mars.spawn(l, Mannequin.class, m -> {
            LobbyFx.npc(m, "&6&lVesmírný bezdomovec", "&eKlikni = dej mu 5 mincí", "beggar");
            m.addScoreboardTag(TAG);
            var eq = m.getEquipment();
            eq.setHelmet(null); eq.setChestplate(null); eq.setLeggings(null); eq.setBoots(null);
            m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile()
                    .skinPatch(sp -> sp.body(net.kyori.adventure.key.Key.key("squarestar", "entity/bezdomovec"))).build());
        });
        seat.addPassenger(npc);                                            // sedí (jízdní póza)
        npc.setRotation(l.getYaw(), 0);
        if (System.getProperty("ssdebug") != null) Bukkit.getScheduler().runTaskLater(pl, () -> pl.getLogger().info("BEGGAR seat y=" + seat.getLocation().getY() + " npc y=" + npc.getLocation().getY()), 20);
        double yaw = Math.toRadians(l.getYaw());
        // kalíšek s drobnými před ním
        Location cup = new Location(pl.mars, X, fy, Z).add(-Math.sin(yaw) * 0.85, 0.02, Math.cos(yaw) * 0.85);
        pl.mars.spawn(cup, ItemDisplay.class, d -> {
            d.setPersistent(false); d.addScoreboardTag(TAG);
            d.setItemStack(new ItemStack(Material.BOWL));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 0.16f, 0), new Quaternionf().rotateX((float) Math.toRadians(-90)), new Vector3f(0.45f, 0.45f, 0.45f), new Quaternionf()));
        });
        for (int i = 0; i < 4; i++) {
            final int k = i;
            pl.mars.spawn(cup.clone().add((k % 2 - .5) * 0.1, 0.1 + k * 0.012, (k / 2 - .5) * 0.1), ItemDisplay.class, d -> {
                d.setPersistent(false); d.addScoreboardTag(TAG);
                d.setItemStack(new ItemStack(Material.GOLD_NUGGET));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.toRadians(-90)).rotateZ(k * 1.3f), new Vector3f(0.18f, 0.18f, 0.18f), new Quaternionf()));
            });
        }
        // holub na rameni (pravé rameno)
        Location pgl = new Location(pl.mars, X, fy + 0.8, Z).add(Math.cos(yaw) * 0.32 * -1, 0, Math.sin(yaw) * 0.32 * -1);
        pigeon = pl.mars.spawn(pgl, ItemDisplay.class, d -> {
            d.setPersistent(false); d.addScoreboardTag(TAG);
            d.setItemStack(Cosmetics.model(Material.PAPER, "pet_pigeon", "Holub"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(14, 14));
            d.setInterpolationDuration(10); d.setInterpolationDelay(0);
            d.setRotation(l.getYaw(), 0);
            d.setTransformation(tr(0, 0, 0));
        });
    }

    private Transformation tr(float bob, float tilt, float peck) {
        return new Transformation(new Vector3f(0, 0.18f + bob, 0), new Quaternionf().rotateZ(tilt).rotateX(peck), new Vector3f(0.42f, 0.42f, 0.42f), new Quaternionf());
    }

    private void loop() {
        tick++;
        if (npc == null || !npc.isValid()) { if (tick % 40 == 0) spawn(); return; }
        // holub se houpe a občas klove
        if (pigeon != null && pigeon.isValid() && tick % 2 == 0) {
            int ph = (tick / 2) % 12;
            float peck = ph == 5 || ph == 6 ? 0.55f : 0f;
            pigeon.setInterpolationDelay(0); pigeon.setInterpolationDuration(2);
            pigeon.setTransformation(tr((float) Math.sin(tick * 0.2) * 0.01f, (float) Math.sin(tick * 0.09) * 0.08f, peck));
        }
        // žebrá na hráče v okolí
        if (tick % 60 == 0) {
            long now = System.currentTimeMillis();
            for (Player p : pl.mars.getPlayers()) {
                if (p.getLocation().distanceSquared(npc.getLocation()) > 36) continue;
                if (now - asked.getOrDefault(p.getUniqueId(), 0L) < 14000) continue;
                asked.put(p.getUniqueId(), now);
                boolean has = pl.cosmetics.hasPigeon(p);
                U.bar(p, has ? "&6Díky za holuba, kámo! &7Příště ti dám víc!" : BEG[rnd.nextInt(BEG.length)]);
                p.playSound(npc.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 0.7f);
            }
        }
    }

    @EventHandler public void click(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (pl.cosmetics.hasPigeon(p)) {
            U.msg(p, "&6Bezdomovec: &eHolub tě miluje! Už mi nic nedlužíš, kámo. &7(Holuba máš v kosmetice → čepice)");
            p.playSound(p.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 1f, 1.2f);
            return;
        }
        if (!pl.coins.pay(p, 5)) { U.msg(p, "&6Bezdomovec: &eNemáš ani 5 mincí? Tak se vrať, až něco vyhraješ..."); return; }
        pl.cosmetics.giveBeggarPet(p);
        U.msg(p, "&6Bezdomovec: &eDíky, kámo! Tady máš mýho holuba. &aPet: Holub &7je na tvé hlavě!");
        U.title(p, "&e&l🕊 Holub!", "&7Nový pet na hlavě");
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.2, 0), 8, .3, .2, .3, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 1f, 1.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    @EventHandler public void hit(EntityDamageEvent e) { if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true); }
}
