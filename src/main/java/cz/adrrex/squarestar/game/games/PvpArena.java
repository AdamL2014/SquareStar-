package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PvP Aréna (3 životy, vybavení) a Sumo (jen odhazování z talíře). */
public final class PvpArena extends Game {
    private final boolean sumo;
    /** 0 = všichni proti všem, 1 = 1v1, 5 = 5v5, 10 = 10v10 (dva týmy) */
    private final int teamSize;
    private final Map<Player, Integer> team = new HashMap<>();
    private static final String[] TN = {"&c&lČervení", "&9&lModří"};
    private static final Color[] TC = {Color.RED, Color.BLUE};
    private final Map<Player, Integer> lives = new HashMap<>();
    private static final int R = 24, SR = 7, LIVES = 3;

    public PvpArena(GameManager gm, int slot, boolean sumo) { this(gm, slot, sumo ? GameType.SUMO : GameType.PVP, 0); }
    public PvpArena(GameManager gm, int slot, GameType type, int teamSize) {
        super(gm, type, slot);
        this.sumo = type == GameType.SUMO;
        this.teamSize = teamSize;
    }
    private boolean teams() { return teamSize >= 5; }

    @Override protected Object[][] themes() {
        if (sumo) return new Object[][]{
                new Object[]{"Neonový talíř", 0, Material.LIGHT_BLUE_CONCRETE, Material.MAGENTA_CONCRETE, Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Material.CALCITE, Material.CYAN_CONCRETE},
                new Object[]{"Zlatý talíř", 0, Material.LIGHT_BLUE_CONCRETE, Material.GOLD_BLOCK, Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE, Material.CALCITE, Material.SMOOTH_SANDSTONE}};
        return new Object[][]{
                new Object[]{"Ledová aréna", 0, Material.RED_SANDSTONE, Material.PACKED_ICE, Material.SMOOTH_SANDSTONE, Material.SNOW_BLOCK, Material.SMOOTH_RED_SANDSTONE, Material.BLUE_ICE, Material.SANDSTONE, Material.CALCITE, Material.RED_NETHER_BRICKS, Material.PRISMARINE_BRICKS, Material.RED_TERRACOTTA, Material.LIGHT_BLUE_CONCRETE, Material.RED_SAND, Material.SNOW_BLOCK, Material.TERRACOTTA, Material.CALCITE, Material.BLACKSTONE, Material.PACKED_ICE, Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.CALCITE, Material.SNOW_BLOCK, Material.SHROOMLIGHT, Material.SEA_LANTERN},
                new Object[]{"Lávová aréna", 1, Material.RED_SAND, Material.NETHERRACK, Material.RED_TERRACOTTA, Material.MAGMA_BLOCK, Material.TERRACOTTA, Material.BLACKSTONE, Material.BLACKSTONE, Material.BASALT, Material.WHITE_CONCRETE, Material.NETHER_BRICKS, Material.CALCITE, Material.BLACKSTONE, Material.LIGHT_BLUE_CONCRETE, Material.NETHER_BRICKS, Material.SEA_LANTERN, Material.SHROOMLIGHT}};
    }

    @Override public List<String> maps() { return sumo ? List.of("Měsíční talíř", "Rudý talíř", "Ledový talíř") : List.of("Koloseum", "Kráter", "Neonová aréna"); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        if (sumo) { buildSumo(b); return; }
        if (map == 2) { buildNeon(b); return; }
        boolean crater = map == 1;
        for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R + 1.5) continue;
            int h = crater ? (int) Math.round(Math.pow(d / R, 2) * 6) : 0;
            Material top = crater ? (rnd.nextInt(4) == 0 ? Material.RED_TERRACOTTA : Material.RED_SAND)
                    : ((int) d % 6 == 0 ? Material.RED_SANDSTONE : (x + z) % 2 == 0 ? Material.SMOOTH_SANDSTONE : Material.SMOOTH_RED_SANDSTONE);
            for (int y = -3; y < h; y++) b.set(ox + x, oy + y, oz + z, crater ? Material.TERRACOTTA : Material.SANDSTONE);
            b.set(ox + x, oy + h, oz + z, top);
            if (d > R - 0.5) {
                int wall = crater ? 3 : 8;
                for (int y = 1; y <= wall; y++) b.set(ox + x, oy + h + y, oz + z, crater ? Material.BLACKSTONE : (y == wall ? Material.SEA_LANTERN : Material.RED_NETHER_BRICKS));
            }
        }
        if (crater) {
            for (int i = 0; i < 12; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 5 + rnd.nextInt(14);
                int cx = ox + (int) (Math.cos(a) * d), cz = oz + (int) (Math.sin(a) * d);
                b.sphere(cx, oy + 1, cz, 1 + rnd.nextInt(2), p -> b.set(p[0], p[1], p[2], rnd.nextBoolean() ? Material.BASALT : Material.BLACKSTONE));
            }
        } else {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                int cx = ox + (int) Math.round(Math.cos(a) * 13), cz = oz + (int) Math.round(Math.sin(a) * 13);
                b.fill(cx, oy + 1, cz, cx + 1, oy + 6, cz + 1, Material.QUARTZ_PILLAR);
                b.fill(cx, oy + 7, cz, cx + 1, oy + 7, cz + 1, Material.CHISELED_QUARTZ_BLOCK);
            }
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (x * x + z * z <= 16) b.fill(ox + x, oy + 1, oz + z, ox + x, oy + 2, oz + z, Material.RED_SANDSTONE);
            b.set(ox, oy + 2, oz, Material.GOLD_BLOCK);
        }
    }

    /** Neonová aréna: černá podlaha s neonovou mřížkou, vyvýšené plošiny a kryty */
    private void buildNeon(U.BlockQueue.Builder b) {
        for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R + 1.5) continue;
            Material top = (x % 5 == 0 || z % 5 == 0) ? ((x + z) % 2 == 0 ? Material.CYAN_CONCRETE : Material.MAGENTA_CONCRETE) : Material.BLACK_CONCRETE;
            if (x % 5 == 0 && z % 5 == 0) top = Material.SEA_LANTERN;
            b.set(ox + x, oy, oz + z, top);
            for (int y = -3; y < 0; y++) b.set(ox + x, oy + y, oz + z, Material.POLISHED_BLACKSTONE);
            if (d > R - 0.5) for (int y = 1; y <= 7; y++) b.set(ox + x, oy + y, oz + z, y == 7 ? Material.SEA_LANTERN : (y % 3 == 0 ? Material.MAGENTA_STAINED_GLASS : Material.BLACK_STAINED_GLASS));
        }
        // 4 vyvýšené plošiny se schody (výhoda výšky)
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int cx = ox + (int) Math.round(Math.cos(a) * 11), cz = oz + (int) Math.round(Math.sin(a) * 11);
            b.fill(cx - 2, oy + 1, cz - 2, cx + 2, oy + 2, cz + 2, Material.POLISHED_BLACKSTONE_BRICKS);
            b.fill(cx - 2, oy + 3, cz - 2, cx + 2, oy + 3, cz + 2, i % 2 == 0 ? Material.CYAN_STAINED_GLASS : Material.MAGENTA_STAINED_GLASS);
            int sx = (int) Math.signum(ox - cx), sz = (int) Math.signum(oz - cz);
            b.set(cx + sx * 3, oy + 1, cz, Material.POLISHED_BLACKSTONE_SLAB); b.set(cx, oy + 1, cz + sz * 3, Material.POLISHED_BLACKSTONE_SLAB);
        }
        // kryty
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            int cx = ox + (int) Math.round(Math.cos(a) * 6), cz = oz + (int) Math.round(Math.sin(a) * 6);
            b.fill(cx, oy + 1, cz, cx, oy + 2, cz, Material.LIGHT_BLUE_STAINED_GLASS);
            b.set(cx, oy + 3, cz, Material.END_ROD);
        }
        b.set(ox, oy + 1, oz, Material.BEACON);
    }

    private void buildSumo(U.BlockQueue.Builder b) {
        if (map == 2) { // Ledový talíř – klouže!
            for (int x = -SR; x <= SR; x++) for (int z = -SR; z <= SR; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > SR + 0.3) continue;
                b.set(ox + x, oy, oz + z, d > SR - 1 ? Material.BLUE_ICE : d < 1.5 ? Material.SEA_LANTERN : Material.PACKED_ICE);
                b.set(ox + x, oy - 1, oz + z, Material.SNOW_BLOCK);
            }
            return;
        }
        boolean red = map == 1;
        for (int x = -SR; x <= SR; x++) for (int z = -SR; z <= SR; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > SR + 0.3) continue;
            Material m = d > SR - 1 ? (red ? Material.RED_NETHER_BRICKS : Material.LIGHT_BLUE_CONCRETE)
                    : (d < 1.5 ? (red ? Material.SHROOMLIGHT : Material.SEA_LANTERN) : (red ? Material.RED_TERRACOTTA : Material.WHITE_CONCRETE));
            b.set(ox + x, oy, oz + z, m);
            b.set(ox + x, oy - 1, oz + z, red ? Material.BLACKSTONE : Material.CALCITE);
        }
    }

    @Override protected GameMode mode() { return GameMode.ADVENTURE; }
    @Override protected Dmg damage() { return sumo ? Dmg.KNOCKBACK : Dmg.NORMAL; }
    @Override protected int voidY() { return sumo ? oy - 8 : oy - 15; }
    @Override protected int timeLimit() { return sumo ? 180 : 420; }

    private Location spot(int i, int n) {
        double a = i * Math.PI * 2 / Math.max(1, n) + rnd.nextDouble() * 0.2;
        double r = sumo ? 4 : 17;
        int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
        int h = (!sumo && map == 1) ? (int) Math.round(Math.pow(Math.sqrt(x * x + z * z) / R, 2) * 6) : 0;
        Location l = U.center(w, ox + x, oy + h + 1, oz + z);
        l.setDirection(new Vector(-x, 0, -z));
        return l;
    }

    @Override protected void start() {
        int i = 0;
        List<Player> order = new java.util.ArrayList<>(players);
        java.util.Collections.shuffle(order, rnd);
        for (Player p : order) {
            lives.put(p, sumo ? 1 : LIVES);
            if (teams()) {
                int t = i % 2; team.put(p, t);
                // tým 0 na jedné straně, tým 1 na druhé
                double a = (t == 0 ? 0 : Math.PI) + (i / 2 - order.size() / 4.0) * 0.18;
                Location l = U.center(w, ox + (int) Math.round(Math.cos(a) * 17), oy + (map == 1 ? 3 : 0) + 1, oz + (int) Math.round(Math.sin(a) * 17));
                l.setDirection(new Vector(-(l.getX() - ox), 0, -(l.getZ() - oz)));
                p.teleport(l);
                U.title(p, TN[t], "&7Tvůj tým");
                i++;
            } else p.teleport(spot(i++, order.size()));
            if (!sumo) kit(p);
        }
        broadcast(sumo ? "&eVystrč ostatní z talíře! Poslední nahoře vyhrává." : "&eKaždý má &c3 životy&e. Jetpack a granáty jsou v inventáři!");
    }

    @Override public List<Object[]> kits() {
        if (sumo) return List.of();
        return List.of(
                new Object[]{"warrior", "Válečník", Material.DIAMOND_SWORD, "Diamantový meč a štít", "Železné brnění", "2x zlaté jablko"},
                new Object[]{"archer", "Lučištník", Material.BOW, "Luk Síla II + Odhoz", "32 šípů, kamenný meč", "Kroužkové brnění, rychlost I"},
                new Object[]{"tank", "Tank", Material.SHIELD, "Diamantová vesta a kalhoty", "Sekera + štít", "Skoro žádný odhoz, 4x jablko"},
                new Object[]{"astro", "Astronaut", Material.FIREWORK_ROCKET, "Plazmový meč + laserová pistole", "Jetpack a kyslíková helma", "Železné brnění"},
                new Object[]{"ninja", "Ninja", Material.ENDER_PEARL, "Železný meč, 4x perla", "Rychlost II + vyšší skok", "Lehké černé brnění"},
                new Object[]{"bomber", "Bombardér", Material.FIRE_CHARGE, "6x meteoritový granát", "Kamenný meč", "Železné brnění"});
    }

    private void kit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        String k = kitId(p);
        var kbr = p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE); if (kbr != null) kbr.setBaseValue(0);
        Material helm = Material.IRON_HELMET, chest = Material.IRON_CHESTPLATE, legs = Material.IRON_LEGGINGS, boots = Material.IRON_BOOTS;
        switch (k) {
            case "archer" -> { helm = Material.CHAINMAIL_HELMET; chest = Material.CHAINMAIL_CHESTPLATE; legs = Material.CHAINMAIL_LEGGINGS; boots = Material.CHAINMAIL_BOOTS;
                ItemStack bow = unb(Material.BOW); bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 2); bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PUNCH, 1);
                inv.setItem(0, unb(Material.STONE_SWORD)); inv.setItem(1, bow); inv.setItem(9, new ItemStack(Material.ARROW, 32)); inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2));
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, PotionEffectInf(), 0, false, false)); }
            case "tank" -> { chest = Material.DIAMOND_CHESTPLATE; legs = Material.DIAMOND_LEGGINGS;
                inv.setItem(0, unb(Material.IRON_AXE)); inv.setItemInOffHand(unb(Material.SHIELD)); inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 4));
                var kb = p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE); if (kb != null) kb.setBaseValue(0.7); }
            case "astro" -> { inv.setItem(0, pl.items.get("plasma_sword", 1)); inv.setItem(1, pl.items.get("laser_pistol", 1)); inv.setItem(2, pl.items.get("jetpack", 1)); inv.setItem(3, new ItemStack(Material.GOLDEN_APPLE, 2)); }
            case "ninja" -> { helm = Material.LEATHER_HELMET; chest = Material.LEATHER_CHESTPLATE; legs = Material.LEATHER_LEGGINGS; boots = Material.LEATHER_BOOTS;
                inv.setItem(0, unb(Material.IRON_SWORD)); inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 4)); inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2));
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, PotionEffectInf(), 1, false, false)); }
            case "bomber" -> { inv.setItem(0, unb(Material.STONE_SWORD)); inv.setItem(1, pl.items.get("meteor_grenade", 6)); inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2)); }
            default -> { inv.setItem(0, unb(Material.DIAMOND_SWORD)); inv.setItemInOffHand(unb(Material.SHIELD)); inv.setItem(1, unb(Material.BOW)); inv.setItem(9, new ItemStack(Material.ARROW, 12)); inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2)); }
        }
        if (teams()) {
            ItemStack h = new ItemStack(Material.LEATHER_HELMET);
            var lm = (org.bukkit.inventory.meta.LeatherArmorMeta) h.getItemMeta(); lm.setColor(TC[team.getOrDefault(p, 0)]); lm.setUnbreakable(true); h.setItemMeta(lm);
            inv.setHelmet(h);
        } else if (k.equals("astro")) inv.setHelmet(pl.items.get("oxygen_helmet", 1));
        else inv.setHelmet(dye(unb(helm), k));
        inv.setChestplate(dye(unb(chest), k)); inv.setLeggings(dye(unb(legs), k)); inv.setBoots(dye(unb(boots), k));
        inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
        // ochrana po spawnu
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 50, 4, false, false));
        p.setGlowing(false);
    }
    private static int PotionEffectInf() { return org.bukkit.potion.PotionEffect.INFINITE_DURATION; }
    private static ItemStack dye(ItemStack it, String kit) {
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm && kit.equals("ninja")) { lm.setColor(Color.fromRGB(0x1a1a1a)); it.setItemMeta(lm); }
        return it;
    }

    private static ItemStack unb(Material m) { ItemStack it = new ItemStack(m); ItemMeta im = it.getItemMeta(); im.setUnbreakable(true); it.setItemMeta(im); return it; }

    @Override public void onDeath(Player p, Player killer) {
        int left = lives.getOrDefault(p, 1) - 1;
        lives.put(p, left);
        if (left <= 0) { reward(killer, p); eliminate(p, killer); return; }
        creditKill(killer, p);
        broadcast("&c⚔ " + p.getName() + " &7padl" + (killer != null && killer != p ? " &8(" + killer.getName() + ")" : "") + " &7- zbývá &c" + left + " ❤");
        U.reset(p, GameMode.ADVENTURE);
        p.teleport(spot(rnd.nextInt(8), 8));
        kit(p);
        pl.applyGravity(p);
        U.title(p, "&c" + left + " ❤", "&7Zbývající životy");
        reward(killer, p);
    }

    private final Map<Player, Integer> streak = new HashMap<>();
    private void reward(Player killer, Player victim) {
        streak.put(victim, 0);
        if (killer == null || killer == victim || !alive.contains(killer)) return;
        int s = streak.merge(killer, 1, Integer::sum);
        if (sumo || state != State.RUNNING || alive.size() <= 2) { U.bar(killer, "&a+1 zabití"); return; } // v Sumu a na konci hry žádná jablka
        killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
        killer.setHealth(Math.min(killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), killer.getHealth() + 6));
        U.sound(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        U.bar(killer, "&a+1 zabití &8| &6Série: &f" + s);
        if (s == 3) broadcast("&6✹ " + killer.getName() + " &emá sérii &63 zabití&e!");
        if (s == 5) broadcast("&c&l⚡ " + killer.getName() + " &cje NEZASTAVITELNÝ! &7(5 zabití)");
    }

    /** v týmech se spoluhráči nezraňují */
    @Override public boolean onPvp(Player victim, Player attacker, org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (teams() && team.get(victim) != null && team.get(victim).equals(team.get(attacker))) return false;
        return true;
    }

    @Override protected void checkWin() {
        if (!teams()) { super.checkWin(); return; }
        if (state != State.RUNNING) return;
        long t0 = alive.stream().filter(p -> team.getOrDefault(p, 0) == 0).count(), t1 = alive.size() - t0;
        if (t0 == 0 || t1 == 0) {
            int win = t0 > 0 ? 0 : 1;
            broadcast("&6&l" + TN[win] + " &6&lvyhráli!");
            end(players.stream().filter(p -> team.getOrDefault(p, -1) == win).toList());
        }
    }

    @Override protected String status(Player p) {
        if (teams()) {
            long t0 = alive.stream().filter(x -> team.getOrDefault(x, 0) == 0).count();
            return TN[team.getOrDefault(p, 0)] + " &8| &cČervení: &f" + t0 + " &9Modří: &f" + (alive.size() - t0) + " &8| &c" + "❤".repeat(Math.max(0, lives.getOrDefault(p, 0)));
        }
        String t = "&7Naživu: &a" + alive.size() + " &8| &7Čas: &e" + fmt(timeLimit() - time);
        return sumo ? t : "&c" + "❤".repeat(Math.max(0, lives.getOrDefault(p, 0))) + " &8| " + t;
    }

    @Override protected void timeUp() {
        if (sumo) { end(List.copyOf(alive)); return; }
        if (teams()) {
            int[] sc = new int[2]; for (Player p : alive) sc[team.getOrDefault(p, 0)] += lives.getOrDefault(p, 0);
            int win = sc[0] >= sc[1] ? 0 : 1;
            end(players.stream().filter(p -> team.getOrDefault(p, -1) == win).toList()); return;
        }
        int best = alive.stream().mapToInt(p -> lives.getOrDefault(p, 0)).max().orElse(0);
        end(alive.stream().filter(p -> lives.getOrDefault(p, 0) == best).toList());
    }
}
