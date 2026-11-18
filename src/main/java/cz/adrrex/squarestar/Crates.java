package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Bedny s klíči. Zatím BEZ placení – klíče dává admin příkazem nebo se získávají za výhry. */
public final class Crates implements Listener {
    public record Reward(String kind, String id, int amount, int weight) {}
    public record Type(String id, String name, Location loc, List<Reward> rewards) {
        int total() { return rewards.stream().mapToInt(Reward::weight).sum(); }
    }
    public static final class Holder implements InventoryHolder {
        Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final SquareStar pl;
    public final Map<String, Type> types = new LinkedHashMap<>();
    private final Random rnd = new Random();

    public Crates(SquareStar pl) {
        this.pl = pl;
        ConfigurationSection s = pl.getConfig().getConfigurationSection("crates");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            List<Integer> xyz = c.getIntegerList("location");
            Location loc = xyz.size() == 3 ? new Location(pl.mars, xyz.get(0), xyz.get(1), xyz.get(2)) : null;
            List<Reward> rw = new ArrayList<>();
            for (String line : c.getStringList("rewards")) {
                String[] a = line.split(":");
                if (a.length != 4) { pl.getLogger().warning("Spatna odmena v bedne " + id + ": " + line); continue; }
                try { rw.add(new Reward(a[0], a[1], Integer.parseInt(a[2]), Integer.parseInt(a[3]))); }
                catch (NumberFormatException ex) { pl.getLogger().warning("Spatne cislo v odmene: " + line); }
            }
            if (!rw.isEmpty()) types.put(id, new Type(id, c.getString("name", id), loc, rw));
        }
    }

    public ItemStack rewardItem(Reward r) {
        if (r.kind().equals("space")) { ItemStack it = pl.items.get(r.id(), r.amount()); if (it != null) return it; }
        if (r.kind().equals("key")) return key(r.id(), r.amount());
        Material m = Material.matchMaterial(r.id());
        return new ItemStack(m == null ? Material.STONE : m, r.amount());
    }

    public ItemStack key(String type, int amount) {
        Type t = types.get(type);
        if (t == null) return null;
        ItemStack it = U.item(Material.TRIPWIRE_HOOK, amount, t.name() + " &7- klíč", "&7Pravým klikem otevři bednu", "&7na Marsu (spawn).");
        ItemMeta m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m);
        return U.tag(it, "ss_key", type);
    }

    public void placeBlocks() {
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_holo_crate")) e.remove();
        for (Type t : types.values()) {
            if (t.loc() == null) continue;
            t.loc().getBlock().setType(Material.ENDER_CHEST);
            pl.mars.spawn(t.loc().clone().add(0.5, 1.4, 0.5), TextDisplay.class, d -> {
                d.text(U.c(t.name() + "\n&7Pravý klik s klíčem\n&8Levý klik = náhled"));
                d.setBillboard(Display.Billboard.CENTER);
                d.addScoreboardTag("ss_holo_crate");
                d.setPersistent(false);
            });
        }
    }

    private Type at(Block b) {
        for (Type t : types.values()) if (t.loc() != null && t.loc().getWorld() == b.getWorld()
                && t.loc().getBlockX() == b.getX() && t.loc().getBlockY() == b.getY() && t.loc().getBlockZ() == b.getZ()) return t;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void click(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getHand() != EquipmentSlot.HAND) return;
        Type t = at(e.getClickedBlock());
        if (t == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) { preview(p, t); return; }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!t.id().equals(U.tagOf(hand, "ss_key"))) {
            U.msg(p, "&cPotřebuješ " + t.name() + " &c- klíč! &7Klíče získáš za výhry v minihrách.");
            preview(p, t);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        open(p, t);
    }

    private void preview(Player p, Type t) {
        Holder h = new Holder();
        int size = Math.min(54, ((t.rewards().size() + 8) / 9) * 9);
        h.inv = Bukkit.createInventory(h, Math.max(9, size), U.c(t.name() + " &8- náhled"));
        for (Reward r : t.rewards()) {
            ItemStack it = rewardItem(r).clone();
            ItemMeta m = it.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = m.hasLore() ? new ArrayList<>(Objects.requireNonNull(m.lore())) : new ArrayList<>();
            lore.add(U.c(String.format("&8Šance: &e%.1f %%", 100.0 * r.weight() / t.total())));
            m.lore(lore); it.setItemMeta(m);
            h.inv.addItem(it);
        }
        p.openInventory(h.inv);
    }

    private Reward roll(Type t) {
        int x = rnd.nextInt(t.total());
        for (Reward r : t.rewards()) { x -= r.weight(); if (x < 0) return r; }
        return t.rewards().get(0);
    }

    private void open(Player p, Type t) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 27, U.c(t.name() + " &8- otevírání..."));
        ItemStack pane = U.item(Material.PURPLE_STAINED_GLASS_PANE, 1, " ");
        for (int i = 0; i < 27; i++) h.inv.setItem(i, pane);
        h.inv.setItem(4, U.item(Material.HOPPER, 1, "&e▼ Výhra ▼"));
        p.openInventory(h.inv);
        Reward win = roll(t);
        List<Reward> strip = new ArrayList<>();
        int steps = 32;
        for (int i = 0; i < steps + 9; i++) strip.add(roll(t));
        strip.set(steps + 4, win);
        new BukkitRunnable() {
            int step = 0, wait = 0, delay = 1;
            @Override public void run() {
                if (wait++ < delay) return;
                wait = 0;
                for (int s = 0; s < 9; s++) h.inv.setItem(9 + s, rewardItem(strip.get(step + s)));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                step++;
                if (step > steps - 12) delay++;
                if (step >= steps) {
                    cancel();
                    ItemStack prize = rewardItem(win);
                    for (ItemStack left : p.getInventory().addItem(prize).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                    U.title(p, "&a&lVÝHRA!", "&f" + win.amount() + "x " + plain(prize));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1.2f);
                    Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.getOpenInventory().getTopInventory() == h.inv) p.closeInventory(); }, 40);
                }
            }
        }.runTaskTimer(pl, 2, 1);
    }

    private static String plain(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName())
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(it.getItemMeta().displayName()));
        return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @EventHandler public void inv(InventoryClickEvent e) { if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true); }
    @EventHandler public void drag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true); }
}
