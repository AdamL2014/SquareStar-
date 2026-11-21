package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Levelování: XP za hraní a výhry, uloženo přímo v datech hráče (přežije update pluginu). */
public final class Levels {
    private static final NamespacedKey XP = new NamespacedKey("squarestar", "xp");
    private final SquareStar pl;
    public Levels(SquareStar pl) { this.pl = pl; }

    public int xp(Player p) { return p.getPersistentDataContainer().getOrDefault(XP, PersistentDataType.INTEGER, 0); }
    /** XP potřebné na další level: 100, 150, 200, ... */
    public static int need(int level) { return 100 + (level - 1) * 50; }
    public static int levelOf(int x) { int l = 1; while (x >= need(l)) { x -= need(l); l++; } return l; }
    public int level(Player p) { int x = xp(p), l = 1; while (x >= need(l)) { x -= need(l); l++; } return l; }
    private int rest(Player p) { int x = xp(p), l = 1; while (x >= need(l)) { x -= need(l); l++; } return x; }

    public void add(Player p, int amount, String why) {
        int before = level(p);
        p.getPersistentDataContainer().set(XP, PersistentDataType.INTEGER, xp(p) + amount);
        int after = level(p);
        if (pl.stats != null) pl.stats.setXp(p, xp(p));
        U.msg(p, "&b+" + amount + " XP &7(" + why + ") &8| &7Level &e" + after + " &8(" + rest(p) + "/" + need(after) + ")");
        if (after > before) {
            U.title(p, "&6&l✦ LEVEL " + after + " ✦", "&eNový level! Jen tak dál!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1.4f);
            U.all("&6✦ &e" + p.getName() + " &7dosáhl &6levelu " + after + "&7!");
            if (pl.coins != null) pl.coins.add(p, 20, "nový level");
            // každých 5 levelů bonusový klíč
            if (after % 5 == 0) {
                var k = pl.crates.key(after % 10 == 0 ? "legendary" : "rare", 1);
                if (k != null) pl.games.pendingRewards.computeIfAbsent(p.getUniqueId(), x -> new java.util.ArrayList<>()).add(k);
                U.msg(p, "&dBonus za level " + after + ": &fklíč k bedně &7(na spawnu)");
            }
            p.getWorld().spawn(p.getLocation().add(0, 1, 0), Firework.class, f -> {
                var m = f.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.AQUA, Color.YELLOW).trail(true).build());
                m.setPower(1); f.setFireworkMeta(m);
            });
        }
        showBar(p);
        pl.ranks.apply(p);
    }

    /** level a postup v XP liště dole (jen v lobby) */
    public void showBar(Player p) {
        if (pl.games.of(p) != null) return;
        int l = level(p);
        p.setLevel(l);
        p.setExp(Math.min(0.999f, rest(p) / (float) need(l)));
    }

    /** krátký štítek do tabu/chatu */
    public String tag(Player p) {
        int l = level(p);
        String c = l >= 50 ? "&c" : l >= 30 ? "&6" : l >= 20 ? "&d" : l >= 10 ? "&b" : "&7";
        return "&8[" + c + "✦" + l + "&8] ";
    }
}
