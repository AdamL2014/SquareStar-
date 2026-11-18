package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
        Inventory inv; boolean anim; @Override public @NotNull Inventory getInventory() { return inv; }
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
            t.loc().getBlock().setType(Material.BARRIER);
            ItemDisplay body = model(t, "crate_", 0), lid = model(t, "crate_lid_", 0);
            bodies.put(t.id(), body); lids.put(t.id(), lid);
            pl.mars.spawn(t.loc().clone().add(0.5, 3.3, 0.5), TextDisplay.class, d -> {
                d.text(U.c(t.name() + "\n&7Pravý klik s klíčem\n&8Levý klik = náhled"));
                d.setBillboard(Display.Billboard.CENTER);
                d.addScoreboardTag("ss_holo_crate");
                d.setPersistent(false);
            });
        }
    }

    // ---------- 3D bedna (model z resource packu) ----------
    private static final float S = 1.5f;
    private final Map<String, ItemDisplay> bodies = new HashMap<>(), lids = new HashMap<>();
    private final Set<String> opening = new HashSet<>();
    private float yaw = 0;
    private String tier(Type t) { return switch (t.id()) { case "rare", "legendary" -> t.id(); default -> "common"; }; }

    private ItemDisplay model(Type t, String prefix, float lift) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta(); m.setItemModel(new NamespacedKey("squarestar", prefix + tier(t))); it.setItemMeta(m);
        return pl.mars.spawn(t.loc().clone().add(0.5, S * 0.5, 0.5), ItemDisplay.class, d -> {
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0, lift, 0), new Quaternionf(), new Vector3f(S, S, S), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.addScoreboardTag("ss_holo_crate"); d.setPersistent(false);
        });
    }

    /** pomalé otáčení + jiskry kolem beden (voláno každých 20 ticků) */
    public void idle() {
        yaw += 0.35f;
        for (Type t : types.values()) {
            if (t.loc() == null || opening.contains(t.id())) continue;
            pose(bodies.get(t.id()), 0, 0, 40); pose(lids.get(t.id()), 0, 0, 40);
            Particle p = switch (tier(t)) { case "legendary" -> Particle.WAX_OFF; case "rare" -> Particle.WITCH; default -> Particle.GLOW; };
            pl.mars.spawnParticle(p, t.loc().clone().add(0.5, 1, 0.5), 6, 0.6, 0.6, 0.6, 0);
        }
    }
    private void pose(ItemDisplay d, float lift, float tilt, int dur) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0); d.setInterpolationDuration(dur);
        d.setTransformation(new Transformation(new Vector3f(0, lift, 0), new Quaternionf().rotateY(yaw).rotateX(tilt), new Vector3f(S, S, S), new Quaternionf()));
    }

    /** animace bedny ve světě: víko se zvedne, paprsek, výhra vyletí nahoru */
    private void worldAnim(Type t, ItemStack prize) {
        if (t.loc() == null) return;
        opening.add(t.id());
        Location c = t.loc().clone().add(0.5, 1.2, 0.5);
        pose(lids.get(t.id()), 0.25f, 0, 6);
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                i++;
                pl.mars.spawnParticle(Particle.END_ROD, c, 4, 0.15, 0.3, 0.15, 0.06);
                if (i % 5 == 0) pl.mars.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.5f + i / 100f);
                if (i == 50) {
                    pose(lids.get(t.id()), 1.1f, -0.9f, 8);
                    pl.mars.playSound(c, Sound.BLOCK_ENDER_CHEST_OPEN, 1, 0.8f);
                    pl.mars.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
                    pl.mars.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 60, 0.3, 0.5, 0.3, 0.5);
                    ItemDisplay show = pl.mars.spawn(c, ItemDisplay.class, d -> {
                        d.setItemStack(prize); d.setBrightness(new Display.Brightness(15, 15));
                        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf()));
                        d.addScoreboardTag("ss_holo_crate"); d.setPersistent(false);
                    });
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        show.setInterpolationDelay(0); show.setInterpolationDuration(25);
                        show.setTransformation(new Transformation(new Vector3f(0, 1.6f, 0), new Quaternionf().rotateY(6.28f), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
                    }, 2);
                    if (tier(t).equals("legendary")) pl.mars.spawn(c.clone().add(0, 2, 0), org.bukkit.entity.Firework.class, f -> {
                        var fm = f.getFireworkMeta();
                        fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR).withColor(Color.YELLOW, Color.ORANGE).withFade(Color.WHITE).trail(true).flicker(true).build());
                        fm.setPower(0); f.setFireworkMeta(fm); f.detonate();
                    });
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        show.remove(); opening.remove(t.id()); pose(lids.get(t.id()), 0, 0, 10);
                        pl.mars.playSound(c, Sound.BLOCK_ENDER_CHEST_CLOSE, 1, 0.8f);
                    }, 70);
                    cancel();
                }
            }
        }.runTaskTimer(pl, 0, 1);
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
        h.inv = Bukkit.createInventory(h, Math.max(9, size), Gui.crate(Math.max(9, size), cz.adrrex.squarestar.Lang.t(p, t.name() + " &f- náhled")));
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

    private static final Material[] SHINE = {Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE};

    private void open(Player p, Type t) {
        Holder h = new Holder();
        h.anim = true;
        h.inv = Bukkit.createInventory(h, 27, Gui.crateOpen(Lang.t(p, t.name())));
        p.openInventory(h.inv);
        Reward win = roll(t);
        ItemStack prizeShow = rewardItem(win);
        worldAnim(t, prizeShow.clone());
        List<Reward> strip = new ArrayList<>();
        int steps = 38;
        for (int i = 0; i < steps + 9; i++) strip.add(roll(t));
        strip.set(steps - 1 + 4, win);
        int[] corners = {0, 8, 18, 26};
        new BukkitRunnable() {
            int step = 0, wait = 0, delay = 1;
            @Override public void run() {
                if (wait++ < delay) return;
                wait = 0;
                for (int s = 0; s < 9; s++) h.inv.setItem(9 + s, rewardItem(strip.get(step + s)));
                for (int k = 0; k < corners.length; k++) h.inv.setItem(corners[k], U.item(SHINE[(step + k) % SHINE.length], 1, " "));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.6f + step * 0.035f);
                step++;
                if (step > steps - 14) delay++;
                if (step >= steps) {
                    cancel();
                    ItemStack black = U.item(Material.BLACK_STAINED_GLASS_PANE, 1, " ");
                    for (int i = 0; i < 27; i++) if (i != 13) h.inv.setItem(i, black);
                    ItemStack shown = rewardItem(win); ItemMeta sm = shown.getItemMeta(); sm.setEnchantmentGlintOverride(true); shown.setItemMeta(sm);
                    h.inv.setItem(13, shown);
                    ItemStack prize = rewardItem(win);
                    for (ItemStack left : p.getInventory().addItem(prize).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                    U.title(p, "&6&l✦ VÝHRA! ✦", "&f" + win.amount() + "x " + plain(prize));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1.2f);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.4f);
                    if (tier(t).equals("legendary")) Bukkit.broadcast(U.c("&6&l✦ &e" + p.getName() + " &7vyhrál z " + t.name() + "&7: &f" + plain(prize) + " &6&l✦"));
                    Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.getOpenInventory().getTopInventory() == h.inv) p.closeInventory(); }, 50);
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
