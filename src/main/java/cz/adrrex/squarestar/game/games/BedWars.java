package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Bed Wars na Marsu: až 4 týmy, generátory surovin, obchod u vesničana. */
public final class BedWars extends Game implements Listener {
    private static final class Team {
        final String name, color; final Material wool, bed, concrete; final Color leather; final int dx, dz;
        final List<Player> members = new ArrayList<>(); boolean bedAlive = true, used;
        int[] foot, head;
        Team(String n, String c, Material w, Material b, Material co, Color l, int dx, int dz) { name = n; color = c; wool = w; bed = b; concrete = co; leather = l; this.dx = dx; this.dz = dz; }
    }
    private static final class Shop implements InventoryHolder {
        Inventory inv; Team team; @Override public @NotNull Inventory getInventory() { return inv; }
    }
    private record Offer(ItemStack item, Material currency, int price) {}

    private final List<Team> teams = List.of(
            new Team("Červení", "&c", Material.RED_WOOL, Material.RED_BED, Material.RED_CONCRETE, Color.RED, 0, -44),
            new Team("Modří", "&9", Material.BLUE_WOOL, Material.BLUE_BED, Material.BLUE_CONCRETE, Color.BLUE, 44, 0),
            new Team("Zelení", "&a", Material.LIME_WOOL, Material.LIME_BED, Material.LIME_CONCRETE, Color.LIME, 0, 44),
            new Team("Žlutí", "&e", Material.YELLOW_WOOL, Material.YELLOW_BED, Material.YELLOW_CONCRETE, Color.YELLOW, -44, 0));
    private final Map<Player, Team> teamOf = new HashMap<>();
    private final Set<Long> breakable = new HashSet<>();
    private final Set<Player> respawning = new HashSet<>();

    public BedWars(GameManager gm, int slot) { super(gm, GameType.BEDWARS, slot); }

    private static BlockFace toward(Team t) {
        if (t.dz < 0) return BlockFace.SOUTH; if (t.dz > 0) return BlockFace.NORTH;
        if (t.dx < 0) return BlockFace.EAST; return BlockFace.WEST;
    }

    @Override public List<String> maps() { return List.of("Rudá planeta", "Orbitální stanice"); }

    private Material top(boolean edge) {
        if (map == 1) return edge ? Material.GRAY_CONCRETE : (rnd.nextInt(6) == 0 ? Material.POLISHED_ANDESITE : Material.LIGHT_GRAY_CONCRETE);
        Material[] m = {Material.RED_SAND, Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.RED_SAND, Material.TERRACOTTA};
        return edge ? Material.RED_SANDSTONE : m[rnd.nextInt(m.length)];
    }
    private Material under() {
        if (map == 1) return rnd.nextBoolean() ? Material.IRON_BLOCK : Material.GRAY_CONCRETE;
        return rnd.nextInt(5) == 0 ? Material.BLACKSTONE : (rnd.nextBoolean() ? Material.RED_SANDSTONE : Material.TERRACOTTA);
    }

    /** létající ostrov: plochý vršek, pod ním kužel */
    private void island(U.BlockQueue.Builder b, int cx, int cz, int r) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > r + 0.3) continue;
            b.set(cx + x, oy, cz + z, top(d > r - 1));
            int depth = (int) Math.round((r - d) * 0.8) + 1 + rnd.nextInt(2);
            for (int y = 1; y <= depth; y++) b.set(cx + x, oy - y, cz + z, under());
        }
    }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        // střed: 3 patra se smaragdem
        island(b, ox, oz, 11);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (x * x + z * z <= 36) b.set(ox + x, oy + 1, oz + z, map == 1 ? Material.SMOOTH_STONE : Material.RED_NETHER_BRICKS);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x * x + z * z <= 9) b.set(ox + x, oy + 2, oz + z, map == 1 ? Material.QUARTZ_BLOCK : Material.SMOOTH_RED_SANDSTONE);
        b.set(ox, oy + 2, oz, Material.EMERALD_BLOCK);
        for (int[] c : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) { b.fill(ox + c[0], oy + 1, oz + c[1], ox + c[0], oy + 4, oz + c[1], map == 1 ? Material.IRON_BARS : Material.RED_NETHER_BRICK_WALL); b.set(ox + c[0], oy + 5, oz + c[1], map == 1 ? Material.SEA_LANTERN : Material.SHROOMLIGHT); }
        // diamantové ostrovy
        for (int[] c : new int[][]{{26, 26}, {-26, 26}, {26, -26}, {-26, -26}}) {
            island(b, ox + c[0], oz + c[1], 4);
            b.set(ox + c[0], oy, oz + c[1], Material.DIAMOND_BLOCK);
        }
        // malé kameny na odraz (nízká gravitace!)
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 15 + rnd.nextInt(25);
            int cx = ox + (int) (Math.cos(a) * d), cz = oz + (int) (Math.sin(a) * d), cy = oy - 2 + rnd.nextInt(6);
            b.sphere(cx, cy, cz, 1, p -> b.set(p[0], p[1], p[2], map == 1 ? Material.GRAY_CONCRETE : Material.BLACKSTONE));
        }
        for (Team t : teams) {
            int cx = ox + t.dx, cz = oz + t.dz;
            island(b, cx, cz, 9);
            BlockFace f = toward(t);
            int fx0 = f.getModX(), fz0 = f.getModZ(), px = -fz0, pz = fx0; // kolmý směr
            // týmová plošina + kopule
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x * x + z * z <= 10) b.set(cx + x, oy, cz + z, t.concrete);
            Material glass = Material.valueOf(t.wool.name().replace("_WOOL", "_STAINED_GLASS"));
            for (int x = -5; x <= 5; x++) for (int y = 0; y <= 5; y++) for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + y * y + z * z);
                if (d > 5.3 || d < 4.4) continue;
                boolean door = y <= 3 && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
                if (!door) b.set(cx + x, oy + 1 + y, cz + z, y == 5 ? Material.SEA_LANTERN : glass);
            }
            // postel směrem ke středu
            int fx = cx + fx0 * 7, fz = cz + fz0 * 7;
            Bed foot = (Bed) t.bed.createBlockData(); foot.setPart(Bed.Part.FOOT); foot.setFacing(f);
            Bed head = (Bed) t.bed.createBlockData(); head.setPart(Bed.Part.HEAD); head.setFacing(f);
            b.set(fx, oy + 1, fz, foot);
            b.set(fx + fx0, oy + 1, fz + fz0, head);
            t.foot = new int[]{fx, oy + 1, fz};
            t.head = new int[]{fx + fx0, oy + 1, fz + fz0};
            for (int[] c : new int[][]{t.foot, t.head}) for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if ((x == t.foot[0] && z == t.foot[2] && y == t.foot[1]) || (x == t.head[0] && z == t.head[2] && y == t.head[1])) continue;
                b.set(x, y, z, t.wool);
                breakable.add(U.pack(x, y, z));
            }
            // generátor za základnou
            int gx = cx - fx0 * 7, gz = cz - fz0 * 7;
            b.set(gx, oy, gz, Material.IRON_BLOCK);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(gx + d[0], oy, gz + d[1], Material.GOLD_BLOCK);
            b.set(cx + px * 7, oy, cz + pz * 7, Material.EMERALD_BLOCK);
        }
    }

    @Override protected void afterBuild() { Bukkit.getPluginManager().registerEvents(this, pl); }

    private Location spawnOf(Team t) {
        Location l = U.center(w, ox + t.dx, oy + 1, oz + t.dz);
        l.setDirection(new Vector(-t.dx, 0, -t.dz));
        return l;
    }
    private Location genOf(Team t) { BlockFace f = toward(t); return U.center(w, ox + t.dx - f.getModX() * 7, oy + 1, oz + t.dz - f.getModZ() * 7); }

    @Override protected GameMode mode() { return GameMode.SURVIVAL; }
    @Override protected Dmg damage() { return Dmg.NORMAL; }
    @Override protected int voidY() { return oy - 30; }
    @Override protected int timeLimit() { return 1200; }

    @Override protected void start() {
        int n = Math.max(2, Math.min(4, players.size()));
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, rnd);
        for (int i = 0; i < shuffled.size(); i++) { Team t = teams.get(i % n); t.members.add(shuffled.get(i)); teamOf.put(shuffled.get(i), t); }
        for (int i = 0; i < teams.size(); i++) {
            Team t = teams.get(i);
            t.used = i < n;
            if (!t.used) { t.bedAlive = false; air(t.foot); air(t.head); continue; }
            Location s = spawnOf(t);
            BlockFace f = toward(t);
            Location shop = U.center(w, ox + t.dx - f.getModZ() * 7, oy + 1, oz + t.dz + f.getModX() * 7);
            shop.setDirection(new Vector(-t.dx, 0, -t.dz));
            spawn(shop, Mannequin.class, m -> {
                cz.adrrex.squarestar.LobbyFx.npc(m, "&6&lOBCHOD", "&7Pravý klik", "npc_shop");
                m.addScoreboardTag("ss_shop");
            });
        }
        for (Player p : players) { Team t = teamOf.get(p); kit(p, t); p.teleport(spawnOf(t)); U.title(p, t.color + "&lTým " + t.name, "&7Braň svou postel!"); }
        broadcast("&eSuroviny: &fželezo&e/&6zlato &eza základnou, &bdiamanty &ena rohových ostrovech, &asmaragdy &euprostřed. Obchod = &6astronaut&e.");
    }

    private void air(int[] c) { w.getBlockAt(c[0], c[1], c[2]).setType(Material.AIR, false); }

    private void kit(Player p, Team t) {
        PlayerInventory inv = p.getInventory();
        inv.setHelmet(leather(Material.LEATHER_HELMET, t)); inv.setChestplate(leather(Material.LEATHER_CHESTPLATE, t));
        inv.setLeggings(leather(Material.LEATHER_LEGGINGS, t)); inv.setBoots(leather(Material.LEATHER_BOOTS, t));
        inv.addItem(unbreak(new ItemStack(Material.WOODEN_SWORD)));
    }
    private static ItemStack leather(Material m, Team t) {
        ItemStack it = new ItemStack(m); LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta(); lm.setColor(t.leather); lm.setUnbreakable(true); it.setItemMeta(lm); return it;
    }
    private static ItemStack unbreak(ItemStack it) { ItemMeta m = it.getItemMeta(); m.setUnbreakable(true); it.setItemMeta(m); return it; }

    @Override protected void second() {
        for (Team t : teams) {
            if (!t.used || t.members.stream().noneMatch(alive::contains)) continue;
            Location g = genOf(t);
            if (g.getNearbyEntitiesByType(Item.class, 2).size() > 40) continue;
            drop(g, Material.IRON_INGOT);
            if (time % 6 == 0) drop(g, Material.GOLD_INGOT);
        }
        if (time % 30 == 0) drop(U.center(w, ox, oy + 3, oz), Material.EMERALD);
        if (time % 25 == 0) for (int[] c : new int[][]{{26, 26}, {-26, 26}, {26, -26}, {-26, -26}}) drop(U.center(w, ox + c[0], oy + 1, oz + c[1]), Material.DIAMOND);
        if (time == 600) broadcast("&c&lZa 5 minut končí hra! Rychle zničte postele!");
    }
    private void drop(Location l, Material m) { w.dropItem(l, new ItemStack(m)).setVelocity(new Vector()); }

    @Override public boolean onPvp(Player victim, Player attacker, EntityDamageByEntityEvent e) {
        return victim == attacker || teamOf.get(victim) != teamOf.get(attacker);
    }

    @Override public boolean canPlace(Player p, Block b) {
        if (b.getY() > oy + 30 || Math.abs(b.getX() - ox) > 75 || Math.abs(b.getZ() - oz) > 75) return false;
        breakable.add(U.pack(b.getX(), b.getY(), b.getZ()));
        return true;
    }

    @Override public boolean canBreak(Player p, Block b) {
        if (b.getType().name().endsWith("_BED")) {
            for (Team t : teams) {
                if (!(same(b, t.foot) || same(b, t.head))) continue;
                if (teamOf.get(p) == t) { U.msg(p, "&cSvou postel ničit nemůžeš!"); return false; }
                t.bedAlive = false;
                air(t.foot); air(t.head);
                broadcast("&f&lPOSTEL ZNIČENA! " + t.color + "Tým " + t.name + " &7už se neoživí. &8(" + p.getName() + ")");
                for (Player m : t.members) U.title(m, "&c&lPOSTEL ZNIČENA!", "&7Už se neoživíš!");
                for (Player all : players) U.sound(all, Sound.ENTITY_WITHER_DEATH, 1.2f);
                return false;
            }
            return false;
        }
        return breakable.contains(U.pack(b.getX(), b.getY(), b.getZ()));
    }
    private static boolean same(Block b, int[] c) { return c != null && b.getX() == c[0] && b.getY() == c[1] && b.getZ() == c[2]; }

    @Override public void onDeath(Player p, Player killer) {
        Team t = teamOf.get(p);
        if (killer != null && killer != p) {
            for (Material m : new Material[]{Material.IRON_INGOT, Material.GOLD_INGOT, Material.EMERALD, Material.DIAMOND}) {
                int n = 0; for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == m) n += it.getAmount();
                if (n > 0) killer.getInventory().addItem(new ItemStack(m, n));
            }
        }
        if (t == null || !t.bedAlive) { eliminate(p, killer); return; }
        broadcast(t.color + p.getName() + " &7zemřel" + (killer != null && killer != p ? " &8(" + killer.getName() + ")" : "") + " &7- oživení za 5 s");
        respawning.add(p);
        U.reset(p, GameMode.SPECTATOR);
        p.teleport(specSpot());
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            respawning.remove(p);
            if (state != State.RUNNING || !alive.contains(p) || !p.isOnline()) return;
            U.reset(p, GameMode.SURVIVAL);
            kit(p, t);
            p.teleport(spawnOf(t));
            pl.applyGravity(p);
        }, 100);
    }

    @Override protected void checkWin() {
        if (state != State.RUNNING) return;
        List<Team> left = teams.stream().filter(t -> t.used && t.members.stream().anyMatch(alive::contains)).toList();
        if (left.size() <= 1) end(left.isEmpty() ? List.of() : new ArrayList<>(left.get(0).members.stream().filter(players::contains).toList()));
    }

    @Override protected void timeUp() {
        Team best = null; int bc = -1;
        for (Team t : teams) { int c = (int) t.members.stream().filter(alive::contains).count() + (t.bedAlive ? 10 : 0); if (t.used && c > bc) { bc = c; best = t; } }
        end(best == null ? List.of() : new ArrayList<>(best.members.stream().filter(players::contains).toList()));
    }

    @Override protected void onLeave(Player p) { Team t = teamOf.get(p); if (t != null) t.members.remove(p); }

    @Override protected String status() {
        StringBuilder s = new StringBuilder();
        for (Team t : teams) if (t.used) s.append(t.color).append(t.name.charAt(0)).append(t.bedAlive ? " ✔ " : " ✘ ").append("&7").append(t.members.stream().filter(alive::contains).count()).append("  ");
        return s + "&8| &7" + fmt(time);
    }

    // ---------------- obchod ----------------
    private List<Offer> offers(Team t) {
        var it = pl.items;
        return List.of(
                new Offer(new ItemStack(t.wool, 16), Material.IRON_INGOT, 4),
                new Offer(new ItemStack(Material.END_STONE, 12), Material.IRON_INGOT, 24),
                new Offer(unbreak(new ItemStack(Material.STONE_SWORD)), Material.IRON_INGOT, 10),
                new Offer(unbreak(new ItemStack(Material.IRON_SWORD)), Material.GOLD_INGOT, 7),
                new Offer(unbreak(new ItemStack(Material.STONE_PICKAXE)), Material.IRON_INGOT, 10),
                new Offer(unbreak(new ItemStack(Material.SHEARS)), Material.IRON_INGOT, 20),
                new Offer(unbreak(new ItemStack(Material.IRON_CHESTPLATE)), Material.GOLD_INGOT, 12),
                new Offer(new ItemStack(Material.GOLDEN_APPLE), Material.GOLD_INGOT, 3),
                new Offer(new ItemStack(Material.TNT), Material.GOLD_INGOT, 4),
                new Offer(new ItemStack(Material.BOW), Material.GOLD_INGOT, 12),
                new Offer(new ItemStack(Material.ARROW, 8), Material.GOLD_INGOT, 2),
                new Offer(new ItemStack(Material.ENDER_PEARL), Material.EMERALD, 4),
                new Offer(it.get("meteor_grenade", 2), Material.GOLD_INGOT, 4),
                new Offer(it.get("jetpack", 1), Material.EMERALD, 2),
                new Offer(it.get("laser_pistol", 1), Material.EMERALD, 5),
                new Offer(it.get("plasma_sword", 1), Material.EMERALD, 4),
                new Offer(sharp(), Material.DIAMOND, 4),
                new Offer(unbreak(new ItemStack(Material.DIAMOND_CHESTPLATE)), Material.DIAMOND, 6),
                new Offer(unbreak(new ItemStack(Material.DIAMOND_PICKAXE)), Material.DIAMOND, 3),
                new Offer(it.get("gravity_boots", 1), Material.DIAMOND, 2));
    }
    private static ItemStack sharp() { ItemStack s = unbreak(new ItemStack(Material.DIAMOND_SWORD)); s.addUnsafeEnchantment(Enchantment.SHARPNESS, 1); return s; }

    @Override public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains("ss_shop")) return;
        e.setCancelled(true);
        Team t = teamOf.get(e.getPlayer());
        if (t == null) return;
        Shop h = new Shop(); h.team = t;
        h.inv = Bukkit.createInventory(h, 27, cz.adrrex.squarestar.Gui.shop(27, "&f&lObchod &e- " + t.name));
        List<Offer> o = offers(t);
        for (int i = 0; i < o.size(); i++) {
            ItemStack show = o.get(i).item().clone();
            ItemMeta m = show.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = m.hasLore() ? new ArrayList<>(Objects.requireNonNull(m.lore())) : new ArrayList<>();
            lore.add(U.c("&7Cena: " + price(o.get(i))));
            m.lore(lore); show.setItemMeta(m);
            h.inv.setItem(i, show);
        }
        e.getPlayer().openInventory(h.inv);
    }
    private static String price(Offer o) {
        return switch (o.currency()) { case IRON_INGOT -> "&f" + o.price() + " železa"; case GOLD_INGOT -> "&6" + o.price() + " zlata"; case DIAMOND -> "&b" + o.price() + " diamantů"; default -> "&a" + o.price() + " smaragdů"; };
    }

    @EventHandler public void shopClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Shop h) || h.team == null) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory() || !teamOf.containsKey(p)) return;
        List<Offer> o = offers(h.team);
        int s = e.getSlot();
        if (s < 0 || s >= o.size()) return;
        Offer of = o.get(s);
        if (!p.getInventory().containsAtLeast(new ItemStack(of.currency()), of.price())) { U.msg(p, "&cNemáš dost surovin! &7(" + price(of) + "&7)"); U.sound(p, Sound.ENTITY_VILLAGER_NO, 1); return; }
        p.getInventory().removeItem(new ItemStack(of.currency(), of.price()));
        for (ItemStack left : p.getInventory().addItem(of.item().clone()).values()) w.dropItemNaturally(p.getLocation(), left);
        U.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f);
    }

    @Override protected void onDestroy() { HandlerList.unregisterAll(this); }
}
