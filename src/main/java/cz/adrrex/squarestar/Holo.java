package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hologramy ve dvou jazycích: ke každému českému TextDisplay vznikne anglické dvojče.
 * Každý hráč vidí jen to svoje (podle /jazyk).
 */
public final class Holo {
    private Holo() {}
    private record Pair(TextDisplay cs, TextDisplay en, Entity owner) {}
    private static final List<Pair> PAIRS = new ArrayList<>();

    public static void start(SquareStar pl) { Bukkit.getScheduler().runTaskTimer(pl, Holo::tick, 20, 10); }

    /** udělá z hologramu dvojjazyčný; owner = entita, se kterou má zmizet (může být null) */
    public static void dual(TextDisplay cs, String legacy, Entity owner) {
        cs.text(U.c(legacy));
        TextDisplay en = cs.getWorld().spawn(cs.getLocation(), TextDisplay.class, d -> {
            d.text(U.c(Lang.toEn(legacy)));
            d.setBillboard(cs.getBillboard());
            d.setBackgroundColor(cs.getBackgroundColor());
            d.setDefaultBackground(cs.isDefaultBackground());
            d.setShadowed(cs.isShadowed());
            d.setSeeThrough(cs.isSeeThrough());
            d.setLineWidth(cs.getLineWidth());
            d.setAlignment(cs.getAlignment());
            d.setTransformation(cs.getTransformation());
            d.setBrightness(cs.getBrightness());
            d.setViewRange(cs.getViewRange());
            d.setPersistent(cs.isPersistent());
            for (String t : cs.getScoreboardTags()) d.addScoreboardTag(t);
            d.setVisibleByDefault(false);
        });
        if (cs.getVehicle() != null) cs.getVehicle().addPassenger(en);
        cs.setVisibleByDefault(false);
        PAIRS.add(new Pair(cs, en, owner));
        for (Player p : cs.getWorld().getPlayers()) show(p, PAIRS.get(PAIRS.size() - 1));
    }
    public static void dual(TextDisplay cs, String legacy) { dual(cs, legacy, null); }

    /** změna textu (např. žebříčky) */
    public static void setText(TextDisplay cs, String legacy) {
        for (Pair p : PAIRS) if (p.cs == cs) { cs.text(U.c(legacy)); if (p.en.isValid()) p.en.text(U.c(Lang.toEn(legacy))); return; }
        cs.text(U.c(legacy));
    }

    private static void show(Player p, Pair x) {
        boolean en = Lang.en(p);
        if (en) { p.showEntity(SquareStar.I, x.en); p.hideEntity(SquareStar.I, x.cs); }
        else { p.showEntity(SquareStar.I, x.cs); p.hideEntity(SquareStar.I, x.en); }
    }

    private static int n;
    /** anglickým hráčům přeloží jména itemů v inventáři (kity, obchod, vesmírné itemy) */
    private static void items() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!Lang.en(p)) continue;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                var it = inv.getItem(i);
                if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) continue;
                var tr = Lang.item(p, it.clone());
                if (!tr.isSimilar(it)) inv.setItem(i, tr);
            }
        }
    }

    private static void tick() {
        if (++n % 4 == 0) items();
        for (Iterator<Pair> it = PAIRS.iterator(); it.hasNext(); ) {
            Pair x = it.next();
            if (!x.cs.isValid() || !x.en.isValid() || (x.owner != null && !x.owner.isValid())) { x.cs.remove(); x.en.remove(); it.remove(); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) for (Pair x : PAIRS) if (x.cs.getWorld() == p.getWorld()) show(p, x);
    }
}
