package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kosmetika v lobby: stopy částic za hráčem a 3D čepice (modely z resource packu). */
public final class Cosmetics implements Listener {
    public enum Trail {
        FLAME("Ohnivá stopa", Material.BLAZE_POWDER, Particle.FLAME), HEARTS("Srdíčka", Material.RED_DYE, Particle.HEART),
        NOTES("Noty", Material.NOTE_BLOCK, Particle.NOTE), STARS("Hvězdný prach", Material.END_ROD, Particle.END_ROD),
        SOUL("Duše", Material.SOUL_LANTERN, Particle.SOUL_FIRE_FLAME), CHERRY("Sakura", Material.CHERRY_LEAVES, Particle.CHERRY_LEAVES),
        COSMIC("Vesmírná magie", Material.AMETHYST_CLUSTER, Particle.WITCH), RAINBOW("Duha", Material.PRISMARINE_CRYSTALS, Particle.DUST),
        SNOW("Sněžení", Material.SNOWBALL, Particle.SNOWFLAKE), ELECTRIC("Elektřina", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK),
        CLOUD("Obláček", Material.WHITE_WOOL, Particle.CLOUD), SCULK("Sculk duše", Material.SCULK, Particle.SCULK_SOUL),
        ENCHANT("Kouzla", Material.ENCHANTING_TABLE, Particle.ENCHANT), GLOW("Záře", Material.GLOW_INK_SAC, Particle.GLOW),
        PORTAL("Portál", Material.OBSIDIAN, Particle.PORTAL), TOTEM("Totem", Material.TOTEM_OF_UNDYING, Particle.TOTEM_OF_UNDYING),
        HAPPY("Jiskřičky", Material.EMERALD, Particle.HAPPY_VILLAGER);
        final String name; final Material icon; final Particle particle;
        Trail(String n, Material i, Particle p) { name = n; icon = i; particle = p; }
    }
    public enum Hat {
        ROCKET("Mini raketa", "hat_rocket"), UFO("UFO", "hat_ufo"), EARTH("Planeta Země", "earth"), HELMET("Kyslíková helma", "oxygen_helmet"), STAR("Hvězda", "star_shard"),
        CROWN("Koruna", "hat_crown"), HALO("Svatozář", "hat_halo"), ALIEN("Mimozemská tykadla", "hat_alien"), SATURN("Saturn", "hat_saturn"),
        PROPELLER("Čepice s vrtulkou", "hat_propeller"), SATELLITE("Satelit", "hat_satellite"), MARS("Planeta Mars", "hat_mars"), WITCH("Čarodějný klobouk", "hat_witch");
        final String name, model;
        Hat(String n, String m) { name = n; model = m; }
    }
    public enum Balloon {
        RED("Červený balónek", "balloon_red"), BLUE("Modrý balónek", "balloon_blue"), PURPLE("Fialový balónek", "balloon_purple"),
        GOLD("Zlatý balónek", "balloon_gold"), GREEN("Zelený balónek", "balloon_green"), WHITE("Bílý balónek", "balloon_white");
        final String name, model;
        Balloon(String n, String m) { name = n; model = m; }
    }
    private final Map<UUID, org.bukkit.entity.ItemDisplay> balloons = new HashMap<>();
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    private final SquareStar pl;
    private final Map<UUID, Location> last = new HashMap<>();
    private int hue;

    public Cosmetics(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tickTrails, 40, 2);
        Bukkit.getScheduler().runTaskTimer(pl, this::tickBalloons, 40, 2);
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_balloon")) e.remove();
    }

    private <T extends Enum<T>> T get(Player p, String key, Class<T> c) {
        String v = p.getPersistentDataContainer().get(U.key(key), PersistentDataType.STRING);
        if (v == null) return null;
        try { return Enum.valueOf(c, v); } catch (IllegalArgumentException e) { return null; }
    }
    private void set(Player p, String key, Enum<?> v) {
        if (v == null) p.getPersistentDataContainer().remove(U.key(key));
        else p.getPersistentDataContainer().set(U.key(key), PersistentDataType.STRING, v.name());
    }

    public static ItemStack model(Material base, String model, String name, String... lore) {
        ItemStack it = U.item(base, 1, name, lore);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("squarestar", model));
        it.setItemMeta(m);
        return it;
    }

    public ItemStack menuItem() { return U.tag(model(Material.NETHER_STAR, "star_shard", "&d&l✦ Kosmetika &7(pravý klik)", "&7Stopy za tebou a 3D čepice"), "ss_lobby", "cosmetics"); }

    /** nasadí čepici v lobby */
    public void apply(Player p) {
        Hat h = get(p, "cos_hat", Hat.class);
        p.getInventory().setHelmet(h == null ? null : U.tag(model(Material.PAPER, h.model, "&d" + h.name, "&7Kosmetika"), "ss_cos", "hat"));
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, cz.adrrex.squarestar.Gui.title(54, cz.adrrex.squarestar.Lang.t(p, "&f&l✦ Kosmetika")));
        Trail ct = get(p, "cos_trail", Trail.class);
        Hat chat = get(p, "cos_hat", Hat.class);
        Balloon cb = get(p, "cos_balloon", Balloon.class);
        // řádek 1–2: stopy, 3–4: čepice, 5: balónky
        h.inv.setItem(0, U.item(Material.BLAZE_POWDER, 1, "&6&lStopy za tebou", "&7Částice, když chodíš po lobby", "&8→"));
        int[] ts = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
        int i = 0;
        for (Trail t : Trail.values()) {
            if (i >= ts.length) break;
            ItemStack it = U.item(t.icon, 1, (t == ct ? "&a✔ " : "&e") + t.name, t == ct ? "&aNasazeno" : "&7Klikni pro nasazení");
            if (t == ct) glint(it);
            h.inv.setItem(ts[i++], U.tag(it, "ss_cos", "trail:" + t.name()));
        }
        h.inv.setItem(18, U.item(Material.LEATHER_HELMET, 1, "&b&l3D čepice", "&7Potřebuje resource pack", "&8→"));
        int[] hs = {19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35};
        i = 0;
        for (Hat t : Hat.values()) {
            if (i >= hs.length) break;
            ItemStack it = cz.adrrex.squarestar.Gui.skin(p, U.item(Material.LEATHER_HELMET, 1, (t == chat ? "&a✔ " : "&e") + t.name, t == chat ? "&aNasazeno" : "&7Klikni pro nasazení"), t.model);
            if (t == chat) glint(it);
            h.inv.setItem(hs[i++], U.tag(it, "ss_cos", "hat:" + t.name()));
        }
        h.inv.setItem(36, U.item(Material.LEAD, 1, "&d&lBalónky", "&7Letí nad tebou v lobby", "&8→"));
        i = 37;
        for (Balloon t : Balloon.values()) {
            ItemStack it = cz.adrrex.squarestar.Gui.skin(p, U.item(Material.LEAD, 1, (t == cb ? "&a✔ " : "&e") + t.name, t == cb ? "&aNasazeno" : "&7Klikni pro nasazení"), t.model);
            if (t == cb) glint(it);
            h.inv.setItem(i++, U.tag(it, "ss_cos", "balloon:" + t.name()));
        }
        h.inv.setItem(49, U.tag(U.item(Material.BARRIER, 1, "&c&lSundat vše"), "ss_cos", "off"));
        h.inv.setItem(53, cz.adrrex.squarestar.Gui.closeButton());
        p.openInventory(h.inv);
    }
    private static void glint(ItemStack it) { ItemMeta m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }

    // ---------------- balónky ----------------
    private void tickBalloons() {
        for (var it = balloons.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !show(p) || get(p, "cos_balloon", Balloon.class) == null || !e.getValue().isValid()) { e.getValue().remove(); it.remove(); }
        }
        for (Player p : pl.mars.getPlayers()) {
            Balloon b = get(p, "cos_balloon", Balloon.class);
            if (b == null || !show(p)) continue;
            Location l = p.getLocation();
            double yaw = Math.toRadians(l.getYaw());
            // vpravo za ramenem, lehce se houpe
            double bob = Math.sin(Bukkit.getCurrentTick() / 12.0 + p.getEntityId()) * 0.12;
            Location at = l.clone().add(-Math.cos(yaw) * 0.9 + Math.sin(yaw) * 0.5, 2.2 + bob, -Math.sin(yaw) * 0.9 - Math.cos(yaw) * 0.5);
            at.setYaw(l.getYaw()); at.setPitch(0);
            var d = balloons.get(p.getUniqueId());
            String model = "squarestar:" + b.model;
            if (d == null || !d.isValid() || d.getWorld() != at.getWorld()) {
                if (d != null) d.remove();
                d = pl.mars.spawn(at, org.bukkit.entity.ItemDisplay.class, x -> {
                    x.setPersistent(false); x.addScoreboardTag("ss_balloon");
                    x.setTeleportDuration(3);
                    x.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                });
                balloons.put(p.getUniqueId(), d);
            }
            ItemStack cur = d.getItemStack();
            if (cur == null || !cur.hasItemMeta() || cur.getItemMeta().getItemModel() == null || !model.equals(cur.getItemMeta().getItemModel().toString()))
                d.setItemStack(model(Material.LEAD, b.model, b.name));
            d.teleport(at);
        }
    }
    private boolean show(Player p) { return p.getWorld() == pl.mars && pl.games.of(p) == null && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible(); }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // čepici v lobby nejde sundat
        if (p.getWorld() == pl.mars && e.getSlotType() == InventoryType.SlotType.ARMOR && "hat".equals(U.tagOf(e.getCurrentItem(), "ss_cos"))) { e.setCancelled(true); return; }
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_cos");
        if (t == null) return;
        if (t.equals("close")) { p.closeInventory(); return; }
        if (t.equals("off")) { set(p, "cos_trail", null); set(p, "cos_hat", null); set(p, "cos_balloon", null); }
        else if (t.startsWith("balloon:")) { Balloon v = Balloon.valueOf(t.substring(8)); set(p, "cos_balloon", get(p, "cos_balloon", Balloon.class) == v ? null : v); }
        else if (t.startsWith("trail:")) { Trail v = Trail.valueOf(t.substring(6)); set(p, "cos_trail", get(p, "cos_trail", Trail.class) == v ? null : v); }
        else if (t.startsWith("hat:")) { Hat v = Hat.valueOf(t.substring(4)); set(p, "cos_hat", get(p, "cos_hat", Hat.class) == v ? null : v); }
        U.sound(p, Sound.ENTITY_ITEM_PICKUP, 1.4f);
        if (pl.games.of(p) == null) apply(p);
        open(p);
    }

    private void tickTrails() {
        hue = (hue + 7) % 360;
        for (Player p : pl.mars.getPlayers()) {
            if (pl.games.of(p) != null || p.getGameMode() == GameMode.SPECTATOR) continue;
            Trail t = get(p, "cos_trail", Trail.class);
            if (t == null) continue;
            Location now = p.getLocation(), prev = last.put(p.getUniqueId(), now);
            boolean moving = prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) > 0.004;
            Location at = now.clone().add(0, moving ? 0.15 : 2.1, 0);
            if (!moving && (Bukkit.getCurrentTick() % 8) != 0) continue;
            switch (t) {
                case RAINBOW -> {
                    java.awt.Color c = java.awt.Color.getHSBColor(hue / 360f, 1f, 1f);
                    p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.2, 0.1, 0.2, 0, new Particle.DustOptions(Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue()), 1.3f));
                }
                case NOTES -> p.getWorld().spawnParticle(Particle.NOTE, at.add(0, 0.3, 0), 1, 0.3, 0.1, 0.3, hue / 360.0);
                default -> p.getWorld().spawnParticle(t.particle, at, moving ? 2 : 1, 0.2, 0.05, 0.2, 0.01);
            }
        }
    }
}
