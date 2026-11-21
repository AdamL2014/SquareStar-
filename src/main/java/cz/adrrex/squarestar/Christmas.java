package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Vánoce na Marsu: od 1. adventní neděle do Tří králů (6. 1.).
 * Sníh v lobby, vánoční strom s dárky, vánoční písnička, adventní kalendář (1.–24. 12.) s mincemi a dárky.
 * config: christmas.mode = auto | on | off
 */
public final class Christmas implements Listener, CommandExecutor {
    private static final ZoneId ZONE = ZoneId.of("Europe/Prague");
    private static final String TAG = "ss_xmas";
    private final SquareStar pl;
    private boolean decorated;

    public Christmas(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 100, 5);
    }

    public static LocalDate today() { return LocalDate.now(ZONE); }
    /** 1. adventní neděle = 4. neděle před Štědrým dnem */
    public static LocalDate firstAdvent(int year) {
        return LocalDate.of(year, 12, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusWeeks(3);
    }
    private static final NamespacedKey MODE = new NamespacedKey("squarestar", "xmas_mode"), BUILT = new NamespacedKey("squarestar", "xmas_built");
    private String mode() { return pl.mars.getPersistentDataContainer().getOrDefault(MODE, PersistentDataType.STRING, pl.getConfig().getString("christmas.mode", "auto")); }
    public boolean active() {
        String m = mode();
        if (m.equalsIgnoreCase("on")) return true;
        if (m.equalsIgnoreCase("off")) return false;
        LocalDate d = today();
        // vánoční výzdoba už od 20. listopadu (okénka kalendáře se otevírají až od 1. 12.)
        return (d.getMonthValue() == 11 && d.getDayOfMonth() >= 20) || d.getMonthValue() == 12 || (d.getMonthValue() == 1 && d.getDayOfMonth() <= 6);
    }

    // ---------------- výzdoba + sníh ----------------
    private int ticks;
    private void tick() {
        boolean on = active();
        if (on != decorated) { if (on) decorate(); else if (decorated || pl.mars.getPersistentDataContainer().has(BUILT)) undecorate(); }
        if (!on) return;
        ticks++;
        for (Player p : pl.mars.getPlayers()) {
            if (pl.games.of(p) != null) continue;
            Location l = p.getLocation();
            p.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 7, 0), 14, 9, 2, 9, 0.01);
            if (ticks % 4 == 0) p.spawnParticle(Particle.END_ROD, TREE.clone().add(0, 3 + Math.random() * 6, 0), 2, 1.8, 1.5, 1.8, 0.005);
        }
    }

    private static final Location TREE = new Location(null, 8.5, Lobby.Y + 1, 5.5);   // uvnitř kopule (poloměr 18), mezi bednami a vchodem +X
    private void decorate() {
        decorated = true;
        pl.mars.getPersistentDataContainer().set(BUILT, PersistentDataType.BYTE, (byte) 1);
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        TREE.setWorld(pl.mars);
        U.BlockQueue.Builder b = pl.queue.builder(pl.mars);
        int x0 = 8, z0 = 5, y0 = Lobby.Y + 1;
        clearTree(b);   // starý strom (z verze 4.4.1) i nový
        org.bukkit.block.data.type.Leaves leaves = (org.bukkit.block.data.type.Leaves) Material.SPRUCE_LEAVES.createBlockData();
        leaves.setPersistent(true);   // listí neopadává
        b.fill(x0, y0, z0, x0, y0 + 2, z0, Material.SPRUCE_LOG);
        Material[] orn = {Material.RED_CONCRETE, Material.GOLD_BLOCK, Material.SHROOMLIGHT, Material.LIGHT_BLUE_CONCRETE, Material.SEA_LANTERN};
        Random r = new Random(24);
        int[] radius = {3, 3, 2, 2, 2, 1, 1, 1, 0};
        for (int i = 0; i < radius.length; i++) {
            int rad = radius[i], y = y0 + 2 + i;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad + 1) continue;
                boolean edge = dx * dx + dz * dz >= (rad - 1) * (rad - 1) + 1 || rad == 0;
                if (edge && r.nextInt(5) == 0) b.set(x0 + dx, y, z0 + dz, orn[r.nextInt(orn.length)]); else b.set(x0 + dx, y, z0 + dz, leaves);
            }
        }
        b.set(x0, y0 + 11, z0, Material.GOLD_BLOCK);
        b.submit(() -> {
            // hvězda na špičce + dárky pod stromem (3D modely)
            item(TREE.clone().add(0, 11.6, 0), "star_shard", 1.6f, true);
            String[] gifts = {"gift_red", "gift_green", "gift_blue", "gift_gold", "gift_red", "gift_gold"};
            for (int i = 0; i < gifts.length; i++) {
                double a = i * Math.PI * 2 / gifts.length + 0.3;
                Location gl = TREE.clone().add(Math.cos(a) * 3.6, 0.35, Math.sin(a) * 3.6);
                item(gl, gifts[i], 0.7f + (i % 3) * 0.15f, false);
                pl.mars.spawn(gl.clone().add(0, -0.3, 0), org.bukkit.entity.Interaction.class, in -> {
                    in.setInteractionWidth(1.1f); in.setInteractionHeight(1.1f); in.setResponsive(true);
                    in.addScoreboardTag(TAG); in.addScoreboardTag("ss_gift"); in.setPersistent(false);
                });
            }
            TextDisplay t = pl.mars.spawn(TREE.clone().add(0, 13.3, 0), TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(160, 120, 10, 10));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f), new AxisAngle4f()));
                d.addScoreboardTag(TAG); d.setPersistent(false);
            });
            Holo.dual(t, "&c&l❄ VESELÉ VÁNOCE ❄\n&fna Marsu!\n&e/advent &7– adventní kalendář");
        });
    }
    private void item(Location l, String model, float size, boolean spin) {
        ItemStack it = new ItemStack(Material.PAPER);
        var m = it.getItemMeta(); m.setItemModel(new NamespacedKey("squarestar", model)); it.setItemMeta(m);
        pl.mars.spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(it);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) (Math.random() * 6), 0, 1, 0), new Vector3f(size), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.addScoreboardTag(TAG); d.setPersistent(false);
        });
    }
    /** smaže starý (x0=0,z0=14) i nový strom */
    private void clearTree(U.BlockQueue.Builder b) {
        b.fill(-3, Lobby.Y + 1, 11, 3, Lobby.Y + 12, 17, Material.AIR);
        b.fill(5, Lobby.Y + 1, 2, 11, Lobby.Y + 13, 8, Material.AIR);
    }
    private final Map<UUID, Long> giftCd = new HashMap<>();
    /** dárky pod stromem: zvuk + třpyt po kliknutí (pravé i levé tlačítko) */
    private void giftFx(Player p, org.bukkit.entity.Entity en) {
        long now = System.currentTimeMillis();
        if (now - giftCd.getOrDefault(p.getUniqueId(), 0L) < 400) return;
        giftCd.put(p.getUniqueId(), now);
        Location l = en.getLocation().add(0, 0.8, 0);
        float pitch = 0.8f + (float) Math.random() * 0.9f;
        p.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, pitch);
        p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, pitch);
        p.playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, pitch);
        p.spawnParticle(Particle.FIREWORK, l, 14, 0.3, 0.3, 0.3, 0.04);
        p.spawnParticle(Particle.END_ROD, l, 8, 0.3, 0.3, 0.3, 0.05);
        // dárek poskočí (animace posunu 3D modelu nahoru a zpět)
        for (var d : en.getNearbyEntities(0.8, 1.2, 0.8)) if (d instanceof ItemDisplay id && id.getScoreboardTags().contains(TAG)) hop(id);
        U.bar(p, ("&c🎁 &fDárek je zabalený! &7Otevři si &e/advent &7– adventní kalendář."));
    }
    private void hop(ItemDisplay id) {
        Transformation t = id.getTransformation();
        id.setInterpolationDelay(0); id.setInterpolationDuration(4);
        id.setTransformation(new Transformation(new Vector3f(0, 0.7f, 0), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!id.isValid()) return;
            id.setInterpolationDelay(0); id.setInterpolationDuration(5);
            id.setTransformation(new Transformation(new Vector3f(), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        }, 5);
    }
    @EventHandler public void giftClick(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("ss_gift")) { e.setCancelled(true); giftFx(e.getPlayer(), e.getRightClicked()); }
    }
    @EventHandler public void giftHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getEntity().getScoreboardTags().contains("ss_gift")) { e.setCancelled(true); if (e.getDamager() instanceof Player p) giftFx(p, e.getEntity()); }
    }
    private void undecorate() {
        decorated = false;
        pl.mars.getPersistentDataContainer().remove(BUILT);
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        U.BlockQueue.Builder b = pl.queue.builder(pl.mars);
        clearTree(b);
        b.submit(null);
    }

    // ---------------- adventní kalendář ----------------
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }
    private NamespacedKey key() { return new NamespacedKey("squarestar", "advent_" + (today().getMonthValue() == 1 ? today().getYear() - 1 : today().getYear())); }
    private int claimed(Player p) { return p.getPersistentDataContainer().getOrDefault(key(), PersistentDataType.INTEGER, 0); }
    /** den, do kterého jde otevírat (0 = ještě nic) */
    private int openUntil() {
        if (mode().equalsIgnoreCase("on") && today().getMonthValue() != 12) return 24;
        LocalDate d = today();
        if (d.getMonthValue() == 12) return Math.min(24, d.getDayOfMonth());
        if (d.getMonthValue() == 1) return 24;
        return 0;
    }
    public static int coinsFor(int day) { return day == 24 ? 300 : 15 + day * 5; }
    private static String extra(int day) {
        return switch (day) { case 1 -> "Čepice Santy"; case 6 -> "Obyčejný klíč (Mikuláš)"; case 12 -> "Vzácný klíč"; case 18 -> "Vzácný klíč"; case 24 -> "Legendární klíč"; default -> null; };
    }

    public void openCalendar(Player p) {
        if (!active()) { U.msg(p, "&7Adventní kalendář je jen o Vánocích (od 1. adventní neděle)."); return; }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Gui.title(54, Lang.t(p, "&f&l❄ Adventní kalendář")));
        int mask = claimed(p), until = openUntil();
        // 24 okének v mřížce 6×4 uprostřed
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 38, 40, 42};
        String[] gifts = {"gift_red", "gift_green", "gift_blue", "gift_gold"};
        for (int d = 1; d <= 24; d++) {
            boolean done = (mask & (1 << d)) != 0, open = d <= until;
            List<String> lore = new ArrayList<>();
            lore.add("&7Odměna: &6" + coinsFor(d) + " " + Coins.ICON + " mincí");
            if (extra(d) != null) lore.add("&7+ &d" + extra(d));
            lore.add("");
            lore.add(done ? "&a✔ Už otevřeno" : open ? "&e&lKlikni a otevři!" : "&7Otevře se &f" + d + ". prosince");
            ItemStack it = U.item(done ? Material.LIME_STAINED_GLASS_PANE : open ? Material.RED_SHULKER_BOX : Material.GRAY_SHULKER_BOX, d,
                    (done ? "&a" : open ? "&c&l" : "&7") + d + ". prosince", lore.toArray(new String[0]));
            if (open && !done) { it = Gui.skin(p, it, gifts[d % 4]); var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(slots[d - 1], U.tag(it, "ss_adv", String.valueOf(d)));
        }
        h.inv.setItem(4, U.item(Material.GOLD_NUGGET, 1, "&6&lTvoje mince: &e" + pl.coins.get(p) + " " + Coins.ICON, "&7Utrať je v &a/obchod"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_adv");
        if (t == null) return;
        int d = Integer.parseInt(t), mask = claimed(p);
        if ((mask & (1 << d)) != 0) { U.msg(p, "&7Tohle okénko už máš otevřené."); return; }
        if (d > openUntil()) { U.msg(p, "&cTohle okénko se otevře až &e" + d + ". prosince&c. Žádné podvádění! ☺"); p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1, 1); return; }
        p.getPersistentDataContainer().set(key(), PersistentDataType.INTEGER, mask | (1 << d));
        pl.coins.add(p, coinsFor(d), "adventní kalendář – " + d + ". prosince");
        ItemStack k = switch (d) { case 6 -> pl.crates.key("common", 1); case 12, 18 -> pl.crates.key("rare", 1); case 24 -> pl.crates.key("legendary", 1); default -> null; };
        if (k != null) for (ItemStack left : p.getInventory().addItem(k).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        if (d == 1) pl.cosmetics.grant(p, "hat:SANTA");
        if (extra(d) != null) U.msg(p, "&d&l✦ &dBonus: &f" + extra(d));
        U.title(p, "&c&l❄ " + d + ". PROSINCE ❄", "&6+" + coinsFor(d) + " " + Coins.ICON + (extra(d) != null ? " &7+ &d" + extra(d) : ""));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.6f);
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1.5, 0), 40, 0.5, 0.5, 0.5, 0.1);
        openCalendar(p);
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (a.length > 0 && s.hasPermission("squarestar.admin")) {
            String m = a[0].toLowerCase(Locale.ROOT);
            if (m.equals("on") || m.equals("off") || m.equals("auto")) {
                pl.mars.getPersistentDataContainer().set(MODE, PersistentDataType.STRING, m);
                s.sendMessage(U.tc(s, "&aVánoční režim: &e" + m + " &7(aktivní: " + (active() ? "&aano" : "&cne") + "&7)"));
                return true;
            }
        }
        if (s instanceof Player p) openCalendar(p);
        return true;
    }
}
