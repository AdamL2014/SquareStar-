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
        COSMIC("Vesmírná magie", Material.AMETHYST_CLUSTER, Particle.WITCH), RAINBOW("Duha", Material.PRISMARINE_CRYSTALS, Particle.DUST);
        final String name; final Material icon; final Particle particle;
        Trail(String n, Material i, Particle p) { name = n; icon = i; particle = p; }
    }
    public enum Hat {
        ROCKET("Mini raketa", "hat_rocket"), UFO("UFO", "hat_ufo"), EARTH("Planeta Země", "earth"), HELMET("Kyslíková helma", "oxygen_helmet"), STAR("Hvězda", "star_shard");
        final String name, model;
        Hat(String n, String m) { name = n; model = m; }
    }
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    private final SquareStar pl;
    private final Map<UUID, Location> last = new HashMap<>();
    private int hue;

    public Cosmetics(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tickTrails, 40, 2);
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
        h.inv = Bukkit.createInventory(h, 36, cz.adrrex.squarestar.Gui.title(36, "&f&l✦ Kosmetika"));
        Trail ct = get(p, "cos_trail", Trail.class);
        Hat chat = get(p, "cos_hat", Hat.class);
        h.inv.setItem(4, U.item(Material.BLAZE_POWDER, 1, "&6&lStopy za tebou", "&7Částice, když chodíš po lobby"));
        int i = 9;
        for (Trail t : Trail.values()) {
            ItemStack it = U.item(t.icon, 1, (t == ct ? "&a✔ " : "&e") + t.name, t == ct ? "&aNasazeno" : "&7Klikni pro nasazení");
            if (t == ct) { ItemMeta m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(i++, U.tag(it, "ss_cos", "trail:" + t.name()));
        }
        h.inv.setItem(22, U.item(Material.LEATHER_HELMET, 1, "&b&l3D čepice", "&7Potřebuje resource pack"));
        i = 29;
        for (Hat t : Hat.values()) {
            ItemStack it = model(Material.PAPER, t.model, (t == chat ? "&a✔ " : "&e") + t.name, t == chat ? "&aNasazeno" : "&7Klikni pro nasazení");
            h.inv.setItem(i++, U.tag(it, "ss_cos", "hat:" + t.name()));
        }
        h.inv.setItem(35, U.tag(U.item(Material.BARRIER, 1, "&c&lSundat vše"), "ss_cos", "off"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // čepici v lobby nejde sundat
        if (p.getWorld() == pl.mars && e.getSlotType() == InventoryType.SlotType.ARMOR && "hat".equals(U.tagOf(e.getCurrentItem(), "ss_cos"))) { e.setCancelled(true); return; }
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_cos");
        if (t == null) return;
        if (t.equals("off")) { set(p, "cos_trail", null); set(p, "cos_hat", null); }
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
