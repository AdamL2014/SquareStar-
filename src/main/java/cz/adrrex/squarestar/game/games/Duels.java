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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/** Duels: souboj 1v1 na kola. Kdo první vyhraje 3 kola, vyhrává. Každý si vybere kit. */
public final class Duels extends Game {
    private static final int WIN = 3, R = 12;
    private final Map<Player, Integer> wins = new HashMap<>();
    private boolean locked = true;      // mezi koly se nebojuje
    private int round;
    private BukkitTask task;

    public Duels(GameManager gm, int slot) { super(gm, GameType.DUELS, slot); }

    @Override public List<String> maps() { return List.of("Křišťálová aréna", "Lávová aréna", "Ledová aréna"); }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected int voidY() { return oy - 12; }
    @Override protected int timeLimit() { return 600; }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        Material floor = map == 1 ? Material.BLACKSTONE : map == 2 ? Material.PACKED_ICE : Material.SMOOTH_QUARTZ;
        Material alt = map == 1 ? Material.RED_NETHER_BRICKS : map == 2 ? Material.BLUE_ICE : Material.LIGHT_BLUE_CONCRETE;
        Material wall = map == 1 ? Material.RED_STAINED_GLASS : map == 2 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.CYAN_STAINED_GLASS;
        Material light = map == 1 ? Material.SHROOMLIGHT : Material.SEA_LANTERN;
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            b.set(ox + x, oy, oz + z, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? alt : floor);
            b.set(ox + x, oy - 1, oz + z, Material.POLISHED_BLACKSTONE);
            if (Math.abs(x) == R || Math.abs(z) == R) {
                for (int y = 1; y <= 7; y++) b.set(ox + x, oy + y, oz + z, y == 7 ? light : wall);
            }
        }
        // sloupy kryty
        int[][] pil = {{-4, -4}, {4, 4}, {-4, 4}, {4, -4}, {0, 0}};
        for (int[] p : pil) for (int y = 1; y <= 3; y++) b.set(ox + p[0], oy + y, oz + p[1], y == 3 ? light : alt);
    }

    @Override public List<Object[]> kits() {
        return List.of(
                new Object[]{"classic", "Klasik", Material.DIAMOND_SWORD, "Diamantový meč, luk a 8 šípů", "Železné brnění", "2x zlaté jablko"},
                new Object[]{"archer", "Lučištník", Material.BOW, "Luk Síla II, 24 šípů", "Kamenný meč, kroužkové brnění", "Rychlost I"},
                new Object[]{"knight", "Rytíř", Material.SHIELD, "Železná sekera a štít", "Diamantový hrudník", "3x zlaté jablko"},
                new Object[]{"fisher", "Rybář", Material.FISHING_ROD, "Rybářský prut, železný meč", "Kožené brnění (Ochrana II)", "Přitáhni si soupeře!"});
    }

    private Location spot(Player p) {
        int idx = new ArrayList<>(players).indexOf(p);
        int x = idx == 0 ? -R + 4 : R - 4;
        Location l = U.center(w, ox + x, oy + 1, oz);
        l.setDirection(new Vector(-x, 0, 0));
        return l;
    }

    @Override protected void start() {
        for (Player p : players) wins.put(p, 0);
        round = 0;
        broadcast("&eKdo první vyhraje &6" + WIN + " kola&e, vyhrává duel! Vybraný kit dostaneš v každém kole.");
        newRound();
    }

    private void newRound() {
        if (state != State.RUNNING) return;
        round++;
        locked = true;
        for (Player p : players) {
            if (!alive.contains(p)) continue;
            U.reset(p, GameMode.ADVENTURE);
            pl.applyGravity(p);
            p.teleport(spot(p));
            kit(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 9, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 70, 128, false, false));
            p.setInvulnerable(true);
            U.title(p, "&eKolo " + round, "&7" + scoreLine());
        }
        for (int i = 3; i >= 1; i--) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (state == State.RUNNING && locked) for (Player p : players) { U.title(p, "&6" + n, "&7Připrav se"); U.sound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.4f); } }, (3 - i) * 20L);
        }
        task = Bukkit.getScheduler().runTaskLater(pl, () -> {
            locked = false;
            for (Player p : players) { p.setInvulnerable(false); U.title(p, "&a&lBOJUJ!", "&7" + scoreLine()); U.sound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f); }
        }, 70);
    }

    private String scoreLine() {
        StringBuilder sb = new StringBuilder();
        for (Player p : players) { if (sb.length() > 0) sb.append("  ✦  "); sb.append(p.getName()).append(' ').append(wins.getOrDefault(p, 0)); }
        return sb.toString();
    }

    private void kit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        Material h = Material.IRON_HELMET, c = Material.IRON_CHESTPLATE, l = Material.IRON_LEGGINGS, bo = Material.IRON_BOOTS;
        switch (kitId(p)) {
            case "archer" -> {
                h = Material.CHAINMAIL_HELMET; c = Material.CHAINMAIL_CHESTPLATE; l = Material.CHAINMAIL_LEGGINGS; bo = Material.CHAINMAIL_BOOTS;
                ItemStack bow = unb(Material.BOW); bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 2);
                inv.setItem(0, bow); inv.setItem(1, unb(Material.STONE_SWORD)); inv.setItem(9, new ItemStack(Material.ARROW, 24));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
            }
            case "knight" -> {
                c = Material.DIAMOND_CHESTPLATE;
                inv.setItem(0, unb(Material.IRON_AXE)); inv.setItemInOffHand(unb(Material.SHIELD)); inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 3));
            }
            case "fisher" -> {
                h = Material.LEATHER_HELMET; c = Material.LEATHER_CHESTPLATE; l = Material.LEATHER_LEGGINGS; bo = Material.LEATHER_BOOTS;
                inv.setItem(0, unb(Material.IRON_SWORD)); inv.setItem(1, unb(Material.FISHING_ROD)); inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2));
            }
            default -> {
                inv.setItem(0, unb(Material.DIAMOND_SWORD)); inv.setItem(1, unb(Material.BOW)); inv.setItem(9, new ItemStack(Material.ARROW, 8));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2));
            }
        }
        inv.setHelmet(armor(h, kitId(p))); inv.setChestplate(armor(c, kitId(p))); inv.setLeggings(armor(l, kitId(p))); inv.setBoots(armor(bo, kitId(p)));
    }

    private static ItemStack armor(Material m, String kit) {
        ItemStack it = unb(m);
        if (kit.equals("fisher")) it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 2);
        return it;
    }

    private static ItemStack unb(Material m) { ItemStack it = new ItemStack(m); ItemMeta im = it.getItemMeta(); im.setUnbreakable(true); it.setItemMeta(im); return it; }

    @Override public void onDeath(Player p, Player killer) {
        if (locked || !alive.contains(p)) return;
        locked = true;
        Player win = killer != null && killer != p && alive.contains(killer) ? killer : players.stream().filter(x -> x != p && alive.contains(x)).findFirst().orElse(null);
        if (win == null) { end(List.of()); return; }
        int s = wins.merge(win, 1, Integer::sum);
        creditKill(win, p);
        broadcast("&6" + win.getName() + " &evyhrál kolo &7(" + scoreLine() + "&7)");
        for (Player x : players) { x.setInvulnerable(true); U.sound(x, Sound.ENTITY_PLAYER_LEVELUP, 1.2f); }
        p.setHealth(1);
        if (s >= WIN) {
            Bukkit.getScheduler().runTaskLater(pl, () -> { for (Player x : players) x.setInvulnerable(false); if (state == State.RUNNING) end(List.of(win)); }, 20);
            return;
        }
        Bukkit.getScheduler().runTaskLater(pl, this::newRound, 50);
    }

    @Override public boolean onPvp(Player victim, Player attacker, org.bukkit.event.entity.EntityDamageByEntityEvent e) { return !locked; }

    @Override protected void onDestroy() {
        if (task != null) task.cancel();
        for (Player p : players) p.setInvulnerable(false);
    }
    @Override protected void onLeave(Player p) { p.setInvulnerable(false); }

    @Override protected String status(Player p) { return "&6Kolo " + round + " &8| &f" + scoreLine() + " &8| &7Do " + WIN + " výher"; }

    @Override protected void timeUp() {
        int best = wins.values().stream().max(Integer::compare).orElse(0);
        List<Player> top = players.stream().filter(p -> wins.getOrDefault(p, 0) == best).toList();
        end(top.size() == 1 ? top : List.of());
    }
}
