package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.*;

/**
 * Vesmírní zombíci: tým brání reaktor měsíční základny před vlnami mimozemských zombíků.
 * Přežijete 10 vln = výhra všech. Reaktor zničen nebo padnou všichni = prohra.
 */
public final class Zombies extends Game {
    private static final int R = 22, WAVES = 10, REACTOR_HP = 300;
    private int wave = 0, reactor = REACTOR_HP, breakLeft = 8;
    private final List<Mob> mobs = new ArrayList<>();
    private final Set<Player> down = new HashSet<>();
    private BossBar bar;

    public Zombies(GameManager gm, int slot) { super(gm, GameType.ZOMBIES, slot); }

    @Override public List<String> maps() { return List.of("Měsíční základna", "Kráter Tycho"); }
    @Override protected GameMode mode() { return GameMode.ADVENTURE; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected int voidY() { return oy - 20; }
    @Override protected int timeLimit() { return 1500; }
    @Override protected boolean hunger() { return false; }

    // ---------------- mapa ----------------
    @Override protected void buildMap(U.BlockQueue.Builder b) {
        boolean tycho = map == 1;
        Material floor = tycho ? Material.SMOOTH_STONE : Material.LIGHT_GRAY_CONCRETE;
        Material floor2 = tycho ? Material.ANDESITE : Material.GRAY_CONCRETE;
        for (int x = -R - 12; x <= R + 12; x++) for (int z = -R - 12; z <= R + 12; z++) {
            double d = Math.sqrt(x * x + z * z);
            boolean gate = Math.abs(x) <= 2 || Math.abs(z) <= 2;
            if (d <= R + 0.5 || (gate && d <= R + 11)) {
                b.set(ox + x, oy, oz + z, d < 4 ? Material.POLISHED_DEEPSLATE : ((x + z) % 4 == 0 ? floor2 : floor));
                b.set(ox + x, oy - 1, oz + z, Material.STONE);
            }
            // zeď s průchody (brány), svítidla nahoře
            if (d > R - 0.5 && d <= R + 0.5 && !gate) {
                for (int y = 1; y <= 4; y++) b.set(ox + x, oy + y, oz + z, y == 4 ? Material.SEA_LANTERN : (tycho ? Material.STONE_BRICKS : Material.WHITE_CONCRETE));
            }
        }
        // reaktor uprostřed
        b.fill(ox - 1, oy + 1, oz - 1, ox + 1, oy + 1, oz + 1, Material.IRON_BLOCK);
        b.set(ox, oy + 2, oz, Material.BEACON);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.fill(ox + c[0], oy + 1, oz + c[1], ox + c[0], oy + 3, oz + c[1], Material.END_ROD);
        // spawn plošiny mimozemšťanů za branami
        for (int[] g : gates()) b.fill(ox + g[0] - 2, oy, oz + g[1] - 2, ox + g[0] + 2, oy, oz + g[1] + 2, Material.CRYING_OBSIDIAN);
        // pár krytů
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int cx = ox + (int) Math.round(Math.cos(a) * 12), cz = oz + (int) Math.round(Math.sin(a) * 12);
            b.fill(cx, oy + 1, cz, cx + 1, oy + 2, cz, tycho ? Material.COBBLED_DEEPSLATE : Material.CYAN_TERRACOTTA);
        }
    }
    private static int[][] gates() { return new int[][]{{0, R + 10}, {0, -R - 10}, {R + 10, 0}, {-R - 10, 0}}; }

    // ---------------- průběh ----------------
    @Override protected void start() {
        bar = BossBar.bossBar(U.c("&b&lReaktor"), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
        int i = 0;
        for (Player p : players) {
            p.teleport(loc(Math.cos(i * 1.3) * 5 + 0.5, 1, Math.sin(i * 1.3) * 5 + 0.5));
            i++;
            kit(p);
            p.showBossBar(bar);
        }
        broadcast("&a&lBraňte reaktor! &7Mimozemští zombíci přijdou branami. Přežijte &e" + WAVES + " vln&7!");
        breakLeft = 8;
    }

    private void kit(Player p) {
        var inv = p.getInventory();
        inv.clear();
        inv.setItem(0, unb(new ItemStack(Material.IRON_SWORD)));
        inv.setItem(1, unb(new ItemStack(Material.BOW)));
        inv.setItem(2, new ItemStack(Material.COOKED_BEEF, 16));
        inv.setItem(8, new ItemStack(Material.ARROW, 32));
        inv.setChestplate(unb(new ItemStack(Material.IRON_CHESTPLATE)));
        inv.setHelmet(pl.items.get("oxygen_helmet", 1));
        if (wave >= 3) inv.setItem(3, pl.items.get("laser_pistol", 1));
        if (wave >= 6) inv.setItem(4, pl.items.get("plasma_sword", 1));
    }
    private static ItemStack unb(ItemStack it) { ItemMeta m = it.getItemMeta(); m.setUnbreakable(true); it.setItemMeta(m); return it; }

    @Override protected void second() {
        if (state != State.RUNNING) return;
        mobs.removeIf(m -> !m.isValid() || m.isDead());
        // přestávka mezi vlnami
        if (mobs.isEmpty()) {
            if (breakLeft == 8 && wave > 0) waveCleared();
            if (breakLeft-- <= 0) { nextWave(); breakLeft = 8; }
            else if (wave < WAVES) for (Player p : players) U.bar(p, "&eDalší vlna za &f" + (breakLeft + 1) + " s &8| &bReaktor: &f" + reactor + "/" + REACTOR_HP);
        }
        // mobové jdou na reaktor, když u nich není hráč
        Location core = loc(0.5, 1, 0.5);
        for (Mob m : mobs) {
            Player near = null; double best = 64;
            for (Player p : alive) { double d = p.getLocation().distanceSquared(m.getLocation()); if (d < best) { best = d; near = p; } }
            if (near != null) m.setTarget(near);
            else { m.setTarget(null); m.getPathfinder().moveTo(core, 1.1); }
            if (m.getLocation().distanceSquared(core) < 9) {
                int dmg = m.getPersistentDataContainer().has(U.key("zboss")) ? 12 : 3;
                reactor -= dmg;
                w.spawnParticle(Particle.ELECTRIC_SPARK, core.clone().add(0, 1.5, 0), 15, 0.5, 0.5, 0.5, 0.2);
                w.playSound(core, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.6f);
            }
        }
        if (bar != null) {
            bar.progress(Math.max(0, reactor) / (float) REACTOR_HP);
            bar.name(U.c("&b&lReaktor &f" + Math.max(0, reactor) + " ❤ &8| &eVlna " + wave + "/" + WAVES + " &8| &cZombíci: &f" + mobs.size()));
            bar.color(reactor < REACTOR_HP / 3 ? BossBar.Color.RED : reactor < REACTOR_HP * 2 / 3 ? BossBar.Color.YELLOW : BossBar.Color.BLUE);
        }
        if (reactor <= 0) {
            broadcast("&c&lREAKTOR ZNIČEN! &7Mimozemšťané vyhráli.");
            w.createExplosion(core, 0f, false, false);
            end(List.of());
        }
    }

    private void waveCleared() {
        if (wave >= WAVES) {
            broadcast("&a&lZÁKLADNA ZACHRÁNĚNA! &7Přežili jste všech " + WAVES + " vln!");
            end(new ArrayList<>(players));
            return;
        }
        for (Player p : players) {
            U.title(p, "&a&lVlna " + wave + " zvládnuta!", "&7Odměny jsou v inventáři");
            U.sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.4f);
        }
        // oživit padlé + odměny
        for (Player p : new ArrayList<>(down)) {
            if (!p.isOnline()) continue;
            U.reset(p, GameMode.ADVENTURE);
            p.teleport(loc(0.5, 1, 3.5));
            alive.add(p);
            pl.applyGravity(p);
            kit(p);
            U.msg(p, "&aOživen! &7Zpátky do boje.");
        }
        down.clear();
        for (Player p : alive) {
            p.getInventory().addItem(new ItemStack(Material.ARROW, 16), new ItemStack(Material.GOLDEN_APPLE, 1));
            if (wave == 3 || wave == 6) kit(p);
            p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
        }
        reactor = Math.min(REACTOR_HP, reactor + 25);
    }

    private void nextWave() {
        wave++;
        boolean boss = wave % 5 == 0;
        int n = 3 + wave * 2 + players.size() * 2;
        for (Player p : players) {
            U.title(p, boss ? "&4&l☠ VLNA " + wave + " ☠" : "&c&lVLNA " + wave, boss ? "&cPřichází mimozemský obr!" : "&7" + n + " zombíků");
            U.sound(p, boss ? Sound.ENTITY_WITHER_SPAWN : Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f);
        }
        int[][] g = gates();
        for (int i = 0; i < n; i++) {
            int[] gg = g[i % g.length];
            Location l = loc(gg[0] + rnd.nextInt(3) - 1 + 0.5, 1, gg[1] + rnd.nextInt(3) - 1 + 0.5);
            int kind = wave >= 4 && rnd.nextInt(4) == 0 ? 2 : wave >= 2 && rnd.nextInt(3) == 0 ? 1 : 0;
            mobs.add(alien(l, kind, false));
        }
        if (boss) mobs.add(alien(loc(0.5, 1, R + 8.5), 0, true));
    }

    /** kind: 0 = zombík s helmou, 1 = rychlý husk, 2 = střelec (stray) */
    private Mob alien(Location l, int kind, boolean boss) {
        Class<? extends Mob> c = kind == 1 ? Husk.class : kind == 2 ? Stray.class : Zombie.class;
        Mob m = spawn(l, c, e -> {
            e.setRemoveWhenFarAway(false);
            e.setPersistent(false);
            e.setLootTable(null);
            if (e instanceof Ageable a) a.setAdult();
            var eq = e.getEquipment();
            eq.setHelmet(new ItemStack(boss ? Material.LIME_STAINED_GLASS : Material.GLASS));
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta lm = (LeatherArmorMeta) chest.getItemMeta(); lm.setColor(boss ? Color.PURPLE : Color.fromRGB(0x55FF55)); chest.setItemMeta(lm);
            eq.setChestplate(chest);
            for (var s : org.bukkit.inventory.EquipmentSlot.values()) try { eq.setDropChance(s, 0f); } catch (IllegalArgumentException ignored) {}
            double hp = (boss ? 160 : 14) + wave * (boss ? 20 : 2);
            Objects.requireNonNull(e.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(hp);
            e.setHealth(hp);
            if (boss) {
                Objects.requireNonNull(e.getAttribute(Attribute.SCALE)).setBaseValue(2.6);
                Objects.requireNonNull(e.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(9);
                e.getPersistentDataContainer().set(U.key("zboss"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                e.customName(U.c("&5&l☠ Mimozemský obr ☠"));
                e.setCustomNameVisible(true);
            }
            var sp = e.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(sp.getBaseValue() * (kind == 1 ? 1.25 : 1.0) * (1 + wave * 0.02));
        });
        w.spawnParticle(Particle.PORTAL, l, 30, 0.4, 1, 0.4, 0.5);
        return m;
    }

    // ---------------- hráči ----------------
    @Override public boolean onPvp(Player victim, Player attacker, EntityDamageByEntityEvent e) { return false; } // tým se nezraňuje

    @Override public void onDeath(Player p, Player killer) {
        if (!alive.remove(p)) return;
        down.add(p);
        broadcast("&c☠ " + p.getName() + " &7padl! &8(oživí se po vlně)");
        U.reset(p, GameMode.SPECTATOR);
        p.teleport(loc(0.5, 12, 0.5));
        U.title(p, "&c&lPADL JSI", "&7Oživíš se po konci vlny");
        checkWin();
    }

    @Override protected void checkWin() {
        if (state != State.RUNNING) return;
        if (alive.isEmpty()) { broadcast("&c&lVšichni padli! &7Základna je ztracena."); end(List.of()); }
    }

    @Override protected void onLeave(Player p) { down.remove(p); if (bar != null) p.hideBossBar(bar); }

    @Override protected void onDestroy() {
        if (bar != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        for (Mob m : mobs) m.remove();
        mobs.clear();
    }

    @Override protected String status(Player p) {
        return "&eVlna &f" + wave + "/" + WAVES + " &8| &bReaktor &f" + Math.max(0, reactor) + " &8| &cZombíci &f" + mobs.size();
    }

    @Override protected void timeUp() { end(reactor > 0 ? new ArrayList<>(players) : List.of()); }
}
