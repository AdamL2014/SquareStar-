package cz.adrrex.squarestar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Input;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Herna (arkáda): samostatná hala v lobby světě. Automaty (Snake, Dodge, Catch) hraješ klávesami WASD přímo před automatem,
 * za hru dostaneš lístky ★, které u pokladny vyměníš za mince.
 */
public final class Arcade implements Listener, CommandExecutor {
    static final int AX = 240, NZ = -10;               // střed haly (x) a severní zeď (z)
    static final int W = 16, H = 12;                   // mřížka obrazovky
    private static final int VER = 3;
    /** 8 automatů: severní a jižní řada (stejné hry, víc kusů) */
    static final int[] OFFS = {-9, -3, 3, 9};
    static final String[][] ROWS = {{"snake", "dodge", "catch", "snake"}, {"catch", "snake", "dodge", "catch"}};
    private static final String TAG = "ss_arc";
    private static final NamespacedKey TICKETS = new NamespacedKey("squarestar", "arc_tickets");
    private static final NamespacedKey BUILT = new NamespacedKey("squarestar", "arc_ver");
    private static final int BG = 0x15151f;

    private final SquareStar pl;
    private final List<Machine> machines = new ArrayList<>();
    private final Map<UUID, Float> oldSpeed = new HashMap<>();
    private final Random rnd = new Random();

    public Arcade(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskLater(pl, this::setup, 60);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 80, 1);
        Bukkit.getScheduler().runTaskTimer(pl, this::sparks, 100, 3);
    }

    // ------------------------------------------------------------------ lístky
    public int tickets(Player p) { return p.getPersistentDataContainer().getOrDefault(TICKETS, PersistentDataType.INTEGER, 0); }
    private void setTickets(Player p, int n) { p.getPersistentDataContainer().set(TICKETS, PersistentDataType.INTEGER, Math.max(0, n)); }

    // ------------------------------------------------------------------ stavba
    private int yy() { return Lobby.Y; }

    private void setup() {
        World w = pl.mars;
        for (int cx = (AX - 15) >> 4; cx <= (AX + 15) >> 4; cx++) for (int cz = (NZ - 1) >> 4; cz <= 11 >> 4; cz++) w.setChunkForceLoaded(cx, cz, true);
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) { for (Entity p : new ArrayList<>(e.getPassengers())) p.remove(); e.remove(); }
        Integer built = w.getPersistentDataContainer().get(BUILT, PersistentDataType.INTEGER);
        if (built == null || built < VER) {
            build(w, () -> { w.getPersistentDataContainer().set(BUILT, PersistentDataType.INTEGER, VER); spawnAll(); });
        } else spawnAll();
    }

    private void build(World w, Runnable done) {
        int y = yy();
        var b = pl.queue.builder(w);
        int x0 = AX - 14, x1 = AX + 14, z0 = NZ, z1 = 10;
        // podlaha: šachovnice + neonové pruhy
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            Material m = ((x + z) & 1) == 0 ? Material.BLACK_CONCRETE : Material.GRAY_CONCRETE;
            if (Math.abs(x - AX) == 5 || Math.abs(x - AX) == 11) m = (z & 1) == 0 ? Material.MAGENTA_CONCRETE : Material.PURPLE_CONCRETE;
            b.set(x, y, z, m);
            b.set(x, y + 9, z, ((x - AX) % 4 == 0 && z % 4 == 0) ? Material.SEA_LANTERN : Material.BLACK_CONCRETE);
        }
        // vzduch uvnitř (kdyby tam něco bylo)
        b.fill(x0 + 1, y + 1, z0 + 1, x1 - 1, y + 8, z1 - 1, Material.AIR);
        // zdi + neonové linky
        b.fill(x0, y + 1, z0, x1, y + 8, z0, Material.BLACK_CONCRETE);
        b.fill(x0, y + 1, z1, x1, y + 8, z1, Material.BLACK_CONCRETE);
        b.fill(x0, y + 1, z0, x0, y + 8, z1, Material.BLACK_CONCRETE);
        b.fill(x1, y + 1, z0, x1, y + 8, z1, Material.BLACK_CONCRETE);
        for (int[] r : new int[][]{{x0, y + 2, z0, x1, y + 2, z0}, {x0, y + 2, z1, x1, y + 2, z1}, {x0, y + 2, z0, x0, y + 2, z1}, {x1, y + 2, z0, x1, y + 2, z1}})
            b.fill(r[0], r[1], r[2], r[3], r[4], r[5], Material.CYAN_CONCRETE);
        for (int[] r : new int[][]{{x0, y + 8, z0, x1, y + 8, z0}, {x0, y + 8, z1, x1, y + 8, z1}, {x0, y + 8, z0, x0, y + 8, z1}, {x1, y + 8, z0, x1, y + 8, z1}})
            b.fill(r[0], r[1], r[2], r[3], r[4], r[5], Material.MAGENTA_CONCRETE);
        // světelné sloupky u zdí
        for (int z = z0 + 3; z < z1; z += 5) { b.fill(x0 + 1, y + 1, z, x0 + 1, y + 3, z, Material.SEA_LANTERN); b.fill(x1 - 1, y + 1, z, x1 - 1, y + 3, z, Material.SEA_LANTERN); }
        // automaty u severní (s=+1) a jižní (s=-1) zdi
        for (int row = 0; row < 2; row++) {
            int s = row == 0 ? 1 : -1, zw = row == 0 ? NZ + 1 : z1 - 1;
            for (int off : OFFS) {
                int mx = AX + off;
                b.fill(mx - 2, y + 1, zw, mx + 2, y + 5, zw, Material.BLACK_CONCRETE);                  // tělo (nižší)
                b.fill(mx - 2, y + 1, zw + s, mx + 2, y + 1, zw + s, Material.GRAY_CONCRETE);           // ovládací pult
                b.fill(mx - 2, y + 2, zw, mx - 2, y + 5, zw, Material.PURPLE_CONCRETE);                  // boky
                b.fill(mx + 2, y + 2, zw, mx + 2, y + 5, zw, Material.PURPLE_CONCRETE);
                b.fill(mx - 1, y + 5, zw, mx + 1, y + 5, zw, Material.SEA_LANTERN);                      // svítící horní lišta
            }
        }
        // pokladna (východ) a výchozí plošina (západ)
        b.fill(AX + 11, y + 1, -2, AX + 13, y + 1, 3, Material.GOLD_BLOCK);
        b.fill(AX - 13, y + 1, -2, AX - 11, y + 1, 3, Material.LIGHT_BLUE_CONCRETE);
        b.fill(AX + 13, y + 2, -2, AX + 13, y + 3, 3, Material.YELLOW_STAINED_GLASS);
        b.submit(done);
    }

    private void spawnAll() {
        World w = pl.mars; int y = yy();
        machines.clear();
        java.util.Map<String, String> names = java.util.Map.of("snake", "&a&l🐍 Snake", "dodge", "&b&l🚀 Dodge", "catch", "&e&l★ Catch");
        java.util.Map<String, String> desc = java.util.Map.of("snake", "&7Sbírej jablka a nenarazy!", "dodge", "&7Uhýbej padajícím skalám!", "catch", "&7Chytej hvězdy, vyhýbej se bombám!");
        for (int i = 0; i < 8; i++) {
            int row = i / 4, col = i % 4, sg = row == 0 ? 1 : -1, zw = row == 0 ? NZ + 1 : 9;
            String mid = ROWS[row][col];
            Machine m = new Machine(mid, AX + OFFS[col], zw, sg, i);
            machines.add(m);
            m.screen = w.spawn(new Location(w, m.x + 0.5, y + 3.5, m.face() + m.s * 0.03, m.s > 0 ? 0f : 180f, 0f), TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.FIXED);   // pevně otočená do haly (neotáčí se za hráčem)
                d.setPersistent(false);
                d.setLineWidth(2000);   // široký řádek: hra se nesmí zalamovat ani useknout
                d.setBackgroundColor(Color.fromARGB(235, 6, 6, 12));
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(1.2f);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.62f, 0.62f, 0.62f), new AxisAngle4f()));
                d.addScoreboardTag(TAG);
            });
            final String ii = mid; final int idx = i;
            TextDisplay title = w.spawn(new Location(w, m.x + 0.5, y + 6.0, m.face() + m.s * 0.05, m.s > 0 ? 0f : 180f, 0f), TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.FIXED);
                d.setPersistent(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.addScoreboardTag(TAG);
                Holo.dual(d, names.get(ii) + "\n" + desc.get(ii), null);
            });
            double bz = m.face() + m.s * 0.5;
            m.button = w.spawn(new Location(w, m.x - 0.4, y + 2.0, bz), Interaction.class, d -> {
                d.setInteractionWidth(1.2f); d.setInteractionHeight(0.6f); d.setResponsive(true); d.setPersistent(false); d.addScoreboardTag(TAG);
                d.addScoreboardTag("ss_arc_btn:" + idx);
            });
            w.spawn(new Location(w, m.x + 1.4, y + 2.0, bz), Interaction.class, d -> {
                d.setInteractionWidth(1.2f); d.setInteractionHeight(0.6f); d.setResponsive(true); d.setPersistent(false); d.addScoreboardTag(TAG);
                d.addScoreboardTag("ss_arc_quit:" + idx);
            });
            for (int k = 0; k < 2; k++) {
                final double bx = k == 0 ? m.x - 0.4 : m.x + 1.4; final Material bm = k == 0 ? Material.LIME_CONCRETE : Material.RED_CONCRETE; final String label = k == 0 ? "&a&l▶ START" : "&c&l✖ ODEJÍT";
                w.spawn(new Location(w, bx, y + 2.0, bz), BlockDisplay.class, d -> {
                    d.setBlock(bm.createBlockData());
                    d.setPersistent(false); d.addScoreboardTag(TAG);
                    d.setTransformation(new Transformation(new Vector3f(-0.3f, 0f, -0.3f), new AxisAngle4f(), new Vector3f(0.6f, 0.12f, 0.6f), new AxisAngle4f()));
                });
                w.spawn(new Location(w, bx, y + 2.45, bz, m.s > 0 ? 0f : 180f, 0f), TextDisplay.class, d -> {
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setPersistent(false);
                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.addScoreboardTag(TAG);
                    d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.45f, 0.45f, 0.45f), new AxisAngle4f()));
                    Holo.dual(d, label, null);
                });
            }
            m.idle();
        }
        // NPC pokladna + východ v hale
        npc(new Location(w, AX + 12.5, y + 2, 0.5), new Vector(-1, 0, 0), "&6&l★ Pokladna", "&7Vyměň lístky za mince", "ss_arc_shop", Material.GOLD_INGOT, Color.fromRGB(0xFFD60A));
        npc(new Location(w, AX - 12.5, y + 2, 0.5), new Vector(1, 0, 0), "&b&l⏏ Východ", "&7Zpět do lobby", "ss_arc_exit", Material.COMPASS, Color.fromRGB(0x3A86FF));
        // NPC v lobby
        enterNpc = npc(new Location(w, 11.5, y + 1, 1.5), new Vector(-11.5, 0, -1.5), "&d&l🕹 Herna", "&7Arkádové automaty &8| &eKlikni", "ss_arc_enter", Material.NETHER_STAR, Color.fromRGB(0xFF006E));
        look(enterNpc);
        if (w.getBlockAt(12, y, 10).getType() == Material.GOLD_BLOCK) w.getBlockAt(12, y, 10).setType(w.getBlockAt(13, y, 10).getType(), false);   // starý podstavec NPC
        w.getBlockAt(11, y, 1).setType(Material.GOLD_BLOCK, false);
    }

    private Mannequin npc(Location l, Vector dir, String name, String desc, String tag, Material hand, Color col) {
        l.setDirection(dir);
        return pl.mars.spawn(l, Mannequin.class, e -> {
            LobbyFx.npc(e, name, desc, "npc");
            LobbyFx.suit(e, col, hand);
            e.addScoreboardTag(TAG);
            e.addScoreboardTag(tag);
        });
    }

    // ------------------------------------------------------------------ vzhled NPC Herna: výraznější (neonová záře + jiskry)
    private Mannequin enterNpc;
    private int fx;
    private static org.bukkit.inventory.ItemStack dyed(Material m, Color c) {
        var it = new org.bukkit.inventory.ItemStack(m);
        var lm = (org.bukkit.inventory.meta.LeatherArmorMeta) it.getItemMeta();
        lm.setColor(c); it.setItemMeta(lm);
        return it;
    }
    private void look(Mannequin e) {
        var eq = e.getEquipment();
        eq.setHelmet(dyed(Material.LEATHER_HELMET, Color.fromRGB(0xFF006E)));          // neonová helma
        eq.setBoots(dyed(Material.LEATHER_BOOTS, Color.fromRGB(0xFFD60A)));            // žluté boty
        eq.setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD));
        e.setGlowing(true);                                                            // růžový obrys
        var sb = Bukkit.getScoreboardManager().getMainScoreboard();
        var team = sb.getTeam("ss_arc_glow");
        if (team == null) team = sb.registerNewTeam("ss_arc_glow");
        team.color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE);
        team.addEntry(e.getUniqueId().toString());
    }
    private void sparks() {
        if (enterNpc == null || !enterNpc.isValid()) return;
        fx++;
        Location c = enterNpc.getLocation();
        for (int i = 0; i < 3; i++) {
            double a = fx * 0.5 + i * Math.PI * 2 / 3, h = 0.2 + (fx * 0.07 + i * 0.6) % 2.0;
            Location p = c.clone().add(Math.cos(a) * 0.9, h, Math.sin(a) * 0.9);
            c.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(i == 1 ? Color.fromRGB(0xFFD60A) : Color.fromRGB(0xFF006E), 1.1f));
        }
        if (fx % 4 == 0) c.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 1.2, 0), 4, 0.5, 0.7, 0.5, 0.1);
    }

    // ------------------------------------------------------------------ příkaz /herna
    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (s instanceof Player p) enter(p);
        return true;
    }
    public void enter(Player p) {
        if (pl.games.of(p) != null) { U.msg(p, "&cUž jsi ve hře. &7Napiš &e/ss leave"); return; }
        Location t = new Location(pl.mars, AX + 0.5, yy() + 1, 1.5, 180, 0);
        p.teleport(t);
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.8f);
        U.title(p, "&d&l🕹 HERNA", "&7Sbírej lístky ★ a vyměň je za mince");
    }
    public void leave(Player p) {
        stop(p);
        p.teleport(pl.lobby.spawn());
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
    }
    private boolean inHall(Player p) { return p.getWorld() == pl.mars && Math.abs(p.getLocation().getX() - AX) < 16 && p.getLocation().getZ() < 12 && p.getLocation().getZ() > NZ - 2; }

    // ------------------------------------------------------------------ události
    @EventHandler public void click(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (act(e.getPlayer(), e.getRightClicked())) e.setCancelled(true);
    }
    @EventHandler public void hit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && act(p, e.getEntity())) e.setCancelled(true);
    }
    private boolean act(Player p, Entity en) {
        Set<String> t = en.getScoreboardTags();
        if (t.contains("ss_arc_enter")) { enter(p); return true; }
        if (t.contains("ss_arc_exit")) { leave(p); return true; }
        if (t.contains("ss_arc_shop")) { openShop(p); return true; }
        for (String s : t) if (s.startsWith("ss_arc_quit:")) {
            try { Machine m = machines.get(Integer.parseInt(s.substring(12))); if (m.player == p) { m.end(true); U.msg(p, "&7Opustil jsi automat."); } } catch (Exception ignored) {}
            return true;
        }
        for (String s : t) if (s.startsWith("ss_arc_btn:")) {
            int idx; try { idx = Integer.parseInt(s.substring(11)); } catch (NumberFormatException ex) { continue; }
            if (idx >= 0 && idx < machines.size()) { startFor(p, machines.get(idx)); return true; }
        }
        return false;
    }
    @EventHandler public void quit(PlayerQuitEvent e) { stop(e.getPlayer()); }

    // ------------------------------------------------------------------ obchod
    private static final class ShopHolder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }
    private void openShop(Player p) {
        ShopHolder h = new ShopHolder();
        h.inv = Bukkit.createInventory(h, 27, Gui.title(27, Lang.t(p, "&f&l★ Pokladna herny")));
        int n = tickets(p);
        h.inv.setItem(4, U.item(Material.PAPER, 1, "&e&l★ Tvoje lístky: &f" + n, "&7Lístky dostaneš za hry na automatech."));
        h.inv.setItem(11, U.tag(U.item(Material.COPPER_INGOT, 1, "&6&l10 ★ → 5 " + Coins.ICON, "&7Klikni pro výměnu"), "ss_arc", "10"));
        h.inv.setItem(13, U.tag(U.item(Material.GOLD_INGOT, 1, "&6&l50 ★ → 30 " + Coins.ICON, "&7Výhodnější kurz!", "&7Klikni pro výměnu"), "ss_arc", "50"));
        h.inv.setItem(15, U.tag(U.item(Material.DIAMOND, 1, "&b&lVšechny lístky", "&7Po desítkách: 10 ★ = 5 " + Coins.ICON), "ss_arc", "ALL"));
        p.openInventory(h.inv);
    }
    @EventHandler public void shopClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ShopHolder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_arc");
        if (t == null || !(e.getWhoClicked() instanceof Player p)) return;
        int have = tickets(p), cost, coins;
        switch (t) {
            case "10" -> { cost = 10; coins = 5; }
            case "50" -> { cost = 50; coins = 30; }
            default -> { cost = have / 10 * 10; coins = cost / 2; }
        }
        if (cost <= 0 || have < cost) { U.msg(p, "&cNemáš dost lístků! &7Máš &e" + have + " ★"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1); return; }
        setTickets(p, have - cost);
        pl.coins.add(p, coins, "výměna lístků");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        p.closeInventory();
        Bukkit.getScheduler().runTask(pl, () -> openShop(p));
    }

    // ------------------------------------------------------------------ hlavní smyčka
    private void startFor(Player p, Machine m) {
        if (m.player != null && m.player != p) { U.msg(p, "&cAutomat je obsazený."); return; }
        if (m.player == p && m.state == 1) return;
        for (Machine o : machines) if (o.player == p && o != m) o.end(false);
        m.player = p;
        { Location c = m.stand(); c.setY(p.getLocation().getY()); c.setYaw(m.s > 0 ? 180f : 0f); c.setPitch(-12f); p.teleport(c); }
        oldSpeed.putIfAbsent(p.getUniqueId(), p.getWalkSpeed());
        p.setWalkSpeed(0.02f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        U.bar(p, "&7Ovládání: &eW A S D &8| &7Konec: &cčervené tlačítko &7nebo &eShift");
        pl.getLogger().info("ARC start " + m.id + " by " + p.getName()); m.begin();
    }
    private void stop(Player p) {
        for (Machine m : machines) if (m.player == p) m.end(true);
        release(p);
    }
    private void release(Player p) {
        Float s = oldSpeed.remove(p.getUniqueId());
        if (s != null) p.setWalkSpeed(s);
    }

    private void tick() {
        for (Machine m : machines) {
            Player p = m.player;
            if (p == null) continue;
            if (!p.isOnline() || p.getWorld() != pl.mars || !inHall(p) || p.isSneaking() || p.getLocation().distanceSquared(m.stand()) > 12) { m.end(true); continue; }
            m.t++;
            if (m.state == 1) { m.step(keys(p, m)); }
            else if (m.state == 2 && m.t > 70) { m.player = null; release(p); m.idle(); continue; }
            if (m.t % 2 == 0 && m.dirty) m.render();
        }
    }

    /** stisknuté směry: z Input API (klient 1.21.2+) NEBO z drobného posunu hráče (funguje i přes ViaVersion starších klientů a na Bedrocku) */
    record Keys(boolean f, boolean b, boolean l, boolean r) {}
    private Keys keys(Player p, Machine m) {
        Input in = p.getCurrentInput();
        boolean f = in.isForward(), b = in.isBackward(), l = in.isLeft(), r = in.isRight();
        Location c = m.stand(), pos = p.getLocation();
        double dx = pos.getX() - c.getX(), dz = pos.getZ() - c.getZ();
        if (Math.abs(dx) > 0.006 || Math.abs(dz) > 0.006) {
            // s=+1: automat u severní zdi, hráč kouká na sever (dopředu = -Z, doprava = +X); s=-1: obráceně
            if (Math.abs(dz) >= Math.abs(dx)) { if (dz * m.s < 0) f = true; else b = true; } else { if (dx * m.s > 0) r = true; else l = true; }
            Location back = c.clone(); back.setY(pos.getY()); back.setYaw(pos.getYaw()); back.setPitch(pos.getPitch());
            p.teleport(back);
        }
        return new Keys(f, b, l, r);
    }

    // ------------------------------------------------------------------ automat
    final class Machine {
        final String id; final int x, zw, s, idx;
        TextDisplay screen; Interaction button;
        Player player;
        int state;               // 0 volný, 1 hraje, 2 konec hry
        int t, score, lives, won;
        boolean dirty;
        final int[][] g = new int[H][W];
        // společné
        int sx, dirX, dirY, nx, ny, period, cd;           // snake: dir; dodge/catch: sx = pozice hráče
        final Deque<int[]> snake = new ArrayDeque<>();
        final List<int[]> objs = new ArrayList<>();       // dodge: skály, catch: {x,y,typ}
        int[] food = new int[]{5, 5};

        Machine(String id, int x, int zw, int s, int idx) { this.id = id; this.x = x; this.zw = zw; this.s = s; this.idx = idx; }
        double face() { return s > 0 ? zw + 1 : zw; }   // přední stěna těla automatu (směrem do haly)
        Location stand() { return new Location(pl.mars, x + 0.5, yy() + 1, face() + s * 2.5); }

        void put(Component c) { screen.text(c); }

        void clear() { for (int[] r : g) Arrays.fill(r, BG); }

        void begin() {
            state = 1; t = 0; score = 0; lives = 3; won = 0; cd = 0; objs.clear(); snake.clear(); clear();
            switch (id) {
                case "snake" -> {
                    for (int i = 0; i < 3; i++) snake.addLast(new int[]{4 - i, 5});
                    dirX = 1; dirY = 0; nx = 1; ny = 0; period = 5; placeFood();
                }
                default -> { sx = W / 2; period = 4; }
            }
            dirty = true; render();
        }

        void placeFood() {
            for (int k = 0; k < 200; k++) {
                int fx = rnd.nextInt(W), fy = rnd.nextInt(H); boolean ok = true;
                for (int[] c : snake) if (c[0] == fx && c[1] == fy) { ok = false; break; }
                if (ok) { food = new int[]{fx, fy}; return; }
            }
        }

        void step(Keys in) {
            switch (id) {
                case "snake" -> stepSnake(in);
                case "dodge" -> stepDodge(in, false);
                default -> stepDodge(in, true);
            }
        }

        void stepSnake(Keys in) {
            if (in.f() && dirY == 0) { nx = 0; ny = -1; }
            else if (in.b() && dirY == 0) { nx = 0; ny = 1; }
            else if (in.l() && dirX == 0) { nx = -1; ny = 0; }
            else if (in.r() && dirX == 0) { nx = 1; ny = 0; }
            if (t % Math.max(2, period - snake.size() / 8) != 0) return;
            dirX = nx; dirY = ny;
            int[] h = snake.peekFirst();
            int hx = h[0] + dirX, hy = h[1] + dirY;
            boolean self = false;
            for (int[] c : snake) if (c[0] == hx && c[1] == hy) { self = true; break; }
            if (hx < 0 || hy < 0 || hx >= W || hy >= H || self) { end(false); return; }
            snake.addFirst(new int[]{hx, hy});
            if (hx == food[0] && hy == food[1]) {
                score++; player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f + score * 0.02f);
                placeFood();
            } else snake.removeLast();
            clear();
            g[food[1]][food[0]] = 0xFF3B3B;
            int i = 0;
            for (int[] c : snake) g[c[1]][c[0]] = i++ == 0 ? 0xB6FF3C : 0x2FBF71;
            dirty = true;
        }

        /** Dodge (skály padají, uhýbáš) a Catch (hvězdy chytáš do košíku, bomby ne) */
        void stepDodge(Keys in, boolean catchMode) {
            if (t % 2 == 0) {
                if (in.l()) sx = Math.max(catchMode ? 1 : 0, sx - 1);
                else if (in.r()) sx = Math.min(catchMode ? W - 2 : W - 1, sx + 1);
            }
            int per = Math.max(2, period - t / 500);
            if (t % per == 0) {
                for (Iterator<int[]> it = objs.iterator(); it.hasNext(); ) {
                    int[] o = it.next(); o[1]++;
                    if (o[1] >= H - 1) {
                        it.remove();
                        boolean hit = catchMode ? Math.abs(o[0] - sx) <= 1 : o[0] == sx;
                        if (!catchMode) { if (hit) { end(false); return; } }
                        else if (o[2] == 0) {
                            if (hit) { score++; player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f); }
                            else { lives--; player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f); if (lives <= 0) { end(false); return; } }
                        } else if (hit) { player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f); end(false); return; }
                    }
                }
                if (rnd.nextInt(10) < (catchMode ? 5 : 6 + Math.min(3, t / 600))) objs.add(new int[]{rnd.nextInt(W), -1, catchMode && rnd.nextInt(4) == 0 ? 1 : 0});
            }
            if (!catchMode) score = t / 20;
            clear();
            for (int[] o : objs) if (o[1] >= 0 && o[1] < H) g[o[1]][o[0]] = catchMode ? (o[2] == 0 ? 0xFFD60A : 0xFF3B3B) : 0x9E9E9E;
            if (catchMode) for (int d = -1; d <= 1; d++) g[H - 1][sx + d] = 0xC08A5A;
            else g[H - 1][sx] = 0x4DD0E1;
            dirty = true;
        }

        int reward() {
            return switch (id) {
                case "snake" -> Math.min(30, score);
                case "dodge" -> Math.min(25, score / 4);
                default -> Math.min(30, score);
            };
        }

        /** konec hry; quit = hráč odešel (bez obrazovky konce) */
        void end(boolean quit) {
            Player p = player;
            if (state == 1 && p != null) {
                int r = reward();
                won = r;
                if (r > 0) {
                    setTickets(p, tickets(p) + r);
                    U.msg(p, "&e+" + r + " ★ lístků &7(celkem &e" + tickets(p) + " ★&7) &8| &7Vyměň je u pokladny.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                } else p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            }
            if (quit || p == null) { player = null; if (p != null) release(p); state = 0; idle(); return; }
            pl.getLogger().info("ARC over " + id + " score " + score); state = 2; t = 0;
            if (p != null) release(p);   // aby se mohl hned hýbat
            renderOver();
        }

        // ---- vykreslení
        Component line(String text, int rgb) { return Component.text(text, TextColor.color(rgb)); }

        void render() {
            dirty = false;
            Component c = Component.empty();
            String head = id.equals("catch") ? "★ " + score + "   ♥ " + "♥".repeat(Math.max(0, lives)) : id.equals("dodge") ? "⏱ " + score + "s" : "🍎 " + score;
            c = c.append(line(head, 0xFFE066)).append(Component.newline());
            for (int y = 0; y < H; y++) {
                int run = g[y][0], from = 0;
                for (int x = 1; x <= W; x++) {
                    if (x == W || g[y][x] != run) { c = c.append(line("█".repeat((x - from) * 2), run)); if (x < W) { run = g[y][x]; from = x; } }
                }
                if (y < H - 1) c = c.append(Component.newline());
            }
            put(c);
        }

        void renderOver() {
            Component c = Component.empty()
                    .append(line("\n\nGAME OVER\n\n", 0xFF4D4D))
                    .append(line((id.equals("dodge") ? "⏱ " + score + "s" : "★ " + score) + "\n", 0xFFE066))
                    .append(line("+" + won + " ★ lístků\n\n", 0x7CFC00))
                    .append(line("▶ znovu: klikni na START\n", 0xBBBBBB))
                    .append(line("again: click START\n\n", 0x888888));
            put(c);
        }

        void idle() {
            state = 0;
            String[] nm = switch (id) { case "snake" -> new String[]{"SNAKE", "sbírej jablka", "collect apples", "0x7CFC00"}; case "dodge" -> new String[]{"DODGE", "uhýbej skalám", "dodge the rocks", "0x4DD0E1"}; default -> new String[]{"CATCH", "chytej hvězdy ★", "catch the stars ★", "0xFFD60A"}; };
            int col = Integer.decode(nm[3]);
            Component c = Component.empty()
                    .append(line("\n\n" + nm[0] + "\n\n", col))
                    .append(line(nm[1] + "\n", 0xDDDDDD)).append(line(nm[2] + "\n\n", 0x888888))
                    .append(line("▶ klikni na zelené tlačítko\n", 0xFF6B6B))
                    .append(line("A S D W = ovládání\n\n", 0x999999));
            put(c);
        }
    }
}
