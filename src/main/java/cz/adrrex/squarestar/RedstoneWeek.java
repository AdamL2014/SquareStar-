package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * Redstone týden (nápad kámoše): každý týden nové redstone téma, každý hráč má svůj pozemek
 * (kreativ), v neděli se hlasuje a v pondělí se vyhlásí vítěz. Svět i hlasy se ukládají (přežijí restart).
 */
public final class RedstoneWeek implements Listener {
    private static final int PLOT = 32, GAP = 8, Y = 64;
    private final SquareStar pl;
    private final File file;
    private final YamlConfiguration data;
    public World world;

    public RedstoneWeek(SquareStar pl) {
        this.pl = pl;
        file = new File(Bukkit.getWorldContainer(), "squarestar-redstone.yml");
        data = YamlConfiguration.loadConfiguration(file);
        world = Bukkit.getWorld("ss_redstone");
        if (world == null) world = new WorldCreator("ss_redstone").generator(new ChunkGenerator() {}).generateStructures(false).createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setTime(6000);
            world.setAutoSave(true);
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::checkWeek, 100, 20 * 60);
    }

    // ---------------- týden a téma ----------------
    private static String weekId() { LocalDate d = LocalDate.now(); return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR); }
    public static boolean voting() { return LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY; }
    public String theme() {
        List<String> t = pl.getConfig().getStringList("redstone.themes");
        if (t.isEmpty()) t = List.of("Automatické dveře", "Tajný vchod", "Farma na cukrovou třtinu", "Výtah", "Kódový zámek",
                "Hudební automat", "Odpočet a raketa", "Automatická lampa", "Sorter itemů", "Past na kamaráda", "Kalkulačka", "Vesmírný hangár");
        return t.get(LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % t.size());
    }

    /** při změně týdne: vyhodnoť minulý, vyhlas vítěze, vyčisti pozemky */
    private void checkWeek() {
        String now = weekId(), last = data.getString("week");
        if (last == null) { data.set("week", now); save(); return; }
        if (last.equals(now)) return;
        Map<String, Integer> score = new HashMap<>();
        var votes = data.getConfigurationSection("votes");
        if (votes != null) for (String voter : votes.getKeys(false)) {
            var v = votes.getConfigurationSection(voter);
            if (v != null) for (String owner : v.getKeys(false)) score.merge(owner, v.getInt(owner), Integer::sum);
        }
        Optional<Map.Entry<String, Integer>> best = score.entrySet().stream().max(Map.Entry.comparingByValue());
        if (best.isPresent()) {
            String name = data.getString("names." + best.get().getKey(), "?");
            data.set("last-winner", name + " (" + best.get().getValue() + " bodů)");
            data.set("pending-reward", best.get().getKey());
            Bukkit.broadcast(U.c("&c&l⚡ REDSTONE TÝDEN &7- vítěz: &e&l" + name + " &7(" + best.get().getValue() + " bodů)! Nové téma: &6" + theme()));
        }
        data.set("votes", null);
        data.set("week", now);
        save();
        // nové téma = čisté pozemky
        var plots = data.getConfigurationSection("plots");
        if (plots != null) for (String id : plots.getKeys(false)) clearPlot(plots.getInt(id));
    }

    // ---------------- pozemky ----------------
    private int plotOf(Player p) {
        String k = "plots." + p.getUniqueId();
        if (!data.contains(k)) {
            int n = data.getInt("next-plot", 0);
            data.set(k, n); data.set("next-plot", n + 1);
            data.set("names." + p.getUniqueId(), p.getName());
            save();
            buildPlot(n);
        }
        return data.getInt(k);
    }
    private int[] origin(int n) {
        // spirála do mřížky: 0,1,2... -> (x,z)
        int side = 8, gx = n % side, gz = n / side;
        return new int[]{gx * (PLOT + GAP), gz * (PLOT + GAP)};
    }
    private void buildPlot(int n) {
        int[] o = origin(n);
        // malý pozemek (~1200 bloků) stavíme hned, ať hráč nespadne do prázdna
        for (int x = -1; x <= PLOT; x++) for (int z = -1; z <= PLOT; z++) {
            boolean edge = x == -1 || z == -1 || x == PLOT || z == PLOT;
            world.getBlockAt(o[0] + x, Y, o[1] + z).setType(edge ? Material.POLISHED_BLACKSTONE_BRICKS : ((x + z) % 2 == 0 ? Material.SMOOTH_STONE : Material.LIGHT_GRAY_CONCRETE), false);
            if (edge) world.getBlockAt(o[0] + x, Y + 1, o[1] + z).setType((x + z) % 4 == 0 ? Material.REDSTONE_LAMP : Material.POLISHED_BLACKSTONE_SLAB, false);
        }
        // cesta k pozemku
        for (int x = -1; x <= PLOT; x++) for (int z = -GAP + 1; z < -1; z++) world.getBlockAt(o[0] + x, Y, o[1] + z).setType(Material.POLISHED_ANDESITE, false);
    }
    private void clearPlot(int n) {
        int[] o = origin(n);
        U.BlockQueue.Builder b = pl.queue.builder(world);
        for (int x = 0; x < PLOT; x++) for (int z = 0; z < PLOT; z++) for (int y = Y + 1; y < Y + 40; y++) b.set(o[0] + x, y, o[1] + z, Material.AIR);
        b.submit(() -> buildPlot(n));
    }
    private Integer plotAt(Location l) {
        int side = PLOT + GAP;
        int gx = Math.floorDiv(l.getBlockX(), side), gz = Math.floorDiv(l.getBlockZ(), side);
        int lx = Math.floorMod(l.getBlockX(), side), lz = Math.floorMod(l.getBlockZ(), side);
        if (gx < 0 || gx >= 8 || gz < 0 || lx >= PLOT || lz >= PLOT) return null;
        return gz * 8 + gx;
    }
    private String ownerOf(int n) {
        var plots = data.getConfigurationSection("plots");
        if (plots != null) for (String id : plots.getKeys(false)) if (plots.getInt(id) == n) return id;
        return null;
    }

    // ---------------- vstup / odchod ----------------
    public void join(Player p) {
        if (pl.games.of(p) != null) { U.msg(p, "&cNejdřív opusť hru: &e/ss leave"); return; }
        int n = plotOf(p);
        int[] o = origin(n);
        // pozemek chybí (svět se neuložil / smazal) -> postavit znovu, ať hráč nepropadne
        world.getChunkAt(o[0] >> 4, o[1] >> 4).load();
        if (world.getBlockAt(o[0] + PLOT / 2, Y, o[1] + PLOT / 2).getType().isAir() || world.getBlockAt(o[0] + PLOT / 2, Y, o[1] - 3).getType().isAir()) buildPlot(n);
        if (p.getWorld() == pl.mars) pl.lobby.saveInv(p);
        p.getInventory().clear();
        p.teleport(new Location(world, o[0] + PLOT / 2.0, Y + 1, o[1] - 3, 0, 20));
        world.getChunkAt(o[0] >> 4, o[1] >> 4).load();
        p.setGameMode(voting() ? GameMode.ADVENTURE : GameMode.CREATIVE);
        p.setFallDistance(0);
        p.getInventory().setItem(8, U.tag(U.item(Material.RED_BED, 1, "&c&lZpět do lobby &7(pravý klik)"), "ss_rs", "leave"));
        if (voting()) giveVoteItems(p);
        else for (Material m : new Material[]{Material.REDSTONE, Material.REPEATER, Material.COMPARATOR, Material.PISTON, Material.STICKY_PISTON, Material.OBSERVER, Material.LEVER, Material.REDSTONE_LAMP})
            p.getInventory().addItem(new ItemStack(m, 64));
        U.title(p, "&c&l⚡ REDSTONE TÝDEN", "&7Téma: &e" + theme());
        U.msg(p, "&7Téma týdne: &e&l" + theme() + (voting() ? " &8| &dDnes se HLASUJE! &7Navštiv pozemky: &e/ss redstone hlasovat" : " &8| &7Stav na svém pozemku, v neděli se hlasuje."));
        String lw = data.getString("last-winner");
        if (lw != null) U.msg(p, "&7Minulý vítěz: &6" + lw);
        // odměna pro vítěze minulého týdne
        if (p.getUniqueId().toString().equals(data.getString("pending-reward"))) {
            ItemStack k = pl.crates.key("legendary", 2);
            if (k != null) p.getInventory().addItem(k);
            U.title(p, "&6&l✦ VYHRÁL JSI REDSTONE TÝDEN ✦", "&e2x legendární klíč");
            pl.levels.add(p, 500, "Redstone týden");
            data.set("pending-reward", null); save();
        }
    }

    /** neděle: projdi pozemky ostatních a dej hlas 1–5 */
    public void visit(Player p, int index) {
        var plots = data.getConfigurationSection("plots");
        if (plots == null || plots.getKeys(false).isEmpty()) { U.msg(p, "&7Zatím tu nikdo nestaví."); return; }
        List<String> ids = new ArrayList<>(plots.getKeys(false));
        String id = ids.get(Math.floorMod(index, ids.size()));
        int[] o = origin(plots.getInt(id));
        if (p.getWorld() != world) join(p);
        p.teleport(new Location(world, o[0] + PLOT / 2.0, Y + 8, o[1] - 6, 0, 35));
        U.title(p, "&e" + data.getString("names." + id, "?"), "&7Pozemek " + (Math.floorMod(index, ids.size()) + 1) + "/" + ids.size());
        p.getPersistentDataContainer().set(U.key("rs_visit"), org.bukkit.persistence.PersistentDataType.INTEGER, Math.floorMod(index, ids.size()));
    }

    private void giveVoteItems(Player p) {
        Material[] m = {Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.GREEN_TERRACOTTA};
        for (int i = 0; i < 5; i++) p.getInventory().setItem(i, U.tag(U.item(m[i], i + 1, "&e&l" + (i + 1) + " ★ &7(pravý klik = hlas)"), "ss_rs", "vote" + (i + 1)));
        p.getInventory().setItem(6, U.tag(U.item(Material.ARROW, 1, "&b&lDalší pozemek ➜"), "ss_rs", "next"));
    }

    @EventHandler public void use(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld() != world || e.getItem() == null) return;
        String t = U.tagOf(e.getItem(), "ss_rs");
        if (t == null) return;
        e.setCancelled(true);
        if (t.equals("leave")) { pl.lobby.send(p); return; }
        int cur = p.getPersistentDataContainer().getOrDefault(U.key("rs_visit"), org.bukkit.persistence.PersistentDataType.INTEGER, -1);
        if (t.equals("next")) { visit(p, cur + 1); return; }
        if (t.startsWith("vote") && voting()) {
            Integer n = plotAt(p.getLocation());
            if (n == null) { var plots = data.getConfigurationSection("plots"); if (plots != null && cur >= 0) n = plots.getInt(new ArrayList<>(plots.getKeys(false)).get(cur)); }
            String owner = n == null ? null : ownerOf(n);
            if (owner == null) { U.msg(p, "&cStůj na něčím pozemku."); return; }
            if (owner.equals(p.getUniqueId().toString())) { U.msg(p, "&cZa svou stavbu hlasovat nemůžeš."); return; }
            int v = Integer.parseInt(t.substring(4));
            data.set("votes." + p.getUniqueId() + "." + owner, v); save();
            U.msg(p, "&aHlas &e" + v + " ★ &apro &f" + data.getString("names." + owner, "?"));
            U.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        }
    }

    // ---------------- ochrana: stavět jen na svém pozemku ----------------
    private boolean mayEdit(Player p, Block b) {
        if (p.hasPermission("squarestar.admin") && p.isSneaking()) return true;
        if (voting()) return false;
        Integer n = plotAt(b.getLocation());
        return n != null && b.getY() > Y && b.getY() < Y + 40 && p.getUniqueId().toString().equals(ownerOf(n));
    }
    @EventHandler(priority = EventPriority.HIGH) public void brk(BlockBreakEvent e) { if (e.getBlock().getWorld() == world && !mayEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH) public void place(BlockPlaceEvent e) { if (e.getBlock().getWorld() == world && !mayEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true); }
    @EventHandler public void spawn(CreatureSpawnEvent e) { if (e.getLocation().getWorld() == world) e.setCancelled(true); }
    @EventHandler public void boom(EntityExplodeEvent e) { if (e.getLocation().getWorld() == world) e.blockList().clear(); }
    @EventHandler public void piston(BlockPistonExtendEvent e) { if (e.getBlock().getWorld() == world && plotAt(e.getBlock().getRelative(e.getDirection(), e.getBlocks().size() + 1).getLocation()) == null) e.setCancelled(true); }
    @EventHandler public void flow(BlockFromToEvent e) { if (e.getBlock().getWorld() == world && plotAt(e.getToBlock().getLocation()) == null) e.setCancelled(true); }
    /** pád pod pozemek -> zpátky nahoru (místo smrti v prázdnu) */
    @EventHandler public void fall(org.bukkit.event.player.PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld() != world || e.getTo().getY() > Y - 12) return;
        Integer n = data.contains("plots." + p.getUniqueId()) ? data.getInt("plots." + p.getUniqueId()) : null;
        if (n != null) { int[] o = origin(n); buildPlot(n); p.teleport(new Location(world, o[0] + PLOT / 2.0, Y + 1, o[1] - 3, 0, 20)); }
        else pl.lobby.send(p);
        p.setFallDistance(0);
    }
    @EventHandler public void voidDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity().getWorld() == world && (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID || e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL)) e.setCancelled(true);
    }

    @EventHandler public void leaveWorld(PlayerChangedWorldEvent e) {
        if (e.getFrom() == world && e.getPlayer().getGameMode() == GameMode.CREATIVE) e.getPlayer().setGameMode(GameMode.ADVENTURE);
    }

    private void save() { try { data.save(file); } catch (IOException ex) { pl.getLogger().warning("redstone: " + ex.getMessage()); } }
}
