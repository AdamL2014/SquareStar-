package cz.adrrex.squarestar.game;

import cz.adrrex.squarestar.SquareStar;
import cz.adrrex.squarestar.U;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GameManager implements Listener {
    public final SquareStar pl;
    final Map<Player, Game> map = new HashMap<>();
    private final List<Game> games = new ArrayList<>();
    private final BitSet slots = new BitSet();
    public final Map<UUID, List<ItemStack>> pendingRewards = new HashMap<>();

    public static final class MenuHolder implements InventoryHolder {
        Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public static final class VoteHolder implements InventoryHolder {
        final Game game; Inventory inv; VoteHolder(Game g) { game = g; } @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public GameManager(SquareStar pl) { this.pl = pl; }

    public int playing(GameType t) { return games.stream().filter(x -> x.type == t).mapToInt(x -> x.players.size()).sum(); }
    public int activeGames() { return games.size(); }

    public Game of(Player p) { return map.get(p); }

    public void openMenu(Player p) {
        MenuHolder h = new MenuHolder();
        h.inv = Bukkit.createInventory(h, 45, cz.adrrex.squarestar.Gui.title(45, cz.adrrex.squarestar.Lang.t(p, "&f&l✦ Minihry na Marsu")));
        int[] pos = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        GameType[] t = GameType.values();
        for (int i = 0; i < t.length; i++) {
            GameType g = t[i];
            int waiting = games.stream().filter(x -> x.type == g && x.joinable()).mapToInt(x -> x.players.size()).sum();
            int playing = games.stream().filter(x -> x.type == g).mapToInt(x -> x.players.size()).sum();
            ItemStack it = U.item(g.icon, 1, "&6&l" + g.display, "&7" + g.desc, "",
                    "&7Hráči: &f" + g.min + "–" + g.max, "&7Čeká: &a" + waiting + " &8| &7Celkem hraje: &e" + playing, "", "&e▶ Klikni pro připojení");
            h.inv.setItem(pos[i], U.tag(it, "ss_menu", g.name()));
        }
        // Redstone týden (trvalý režim, ne kolo minihry)
        boolean vote = cz.adrrex.squarestar.RedstoneWeek.voting();
        h.inv.setItem(40, U.tag(U.item(Material.REDSTONE_BLOCK, 1, "&c&l⚡ Redstone týden", "&7Každý týden nové redstone téma.", "&7Postav nápad na svém pozemku,", "&7v neděli se hlasuje o nejlepší!", "",
                "&7Téma: &e" + pl.redstone.theme(), vote ? "&d&lDnes se hlasuje!" : "&7Hlasování: &fneděle", "", "&e▶ Klikni pro vstup"), "ss_menu", "REDSTONE"));
        p.openInventory(h.inv);
    }

    public void join(Player p, GameType type) {
        if (map.containsKey(p)) { U.msg(p, "&cUž jsi ve hře. &7Napiš &e/ss leave"); return; }
        Game g = games.stream().filter(x -> x.type == type && x.joinable()).findFirst().orElse(null);
        if (g == null) {
            int max = pl.getConfig().getInt("max-arenas", 6);
            if (games.size() >= max) { U.msg(p, "&cVšechny arény jsou plné, zkus to za chvíli."); return; }
            int slot = slots.nextClearBit(1);
            slots.set(slot);
            g = type.create(this, slot);
            games.add(g);
            g.build();
        }
        pl.lobby.saveInv(p);
        map.put(p, g);
        g.addPlayer(p);
    }

    public void leave(Player p) {
        Game g = map.get(p);
        if (g == null) { U.msg(p, "&7Nejsi v žádné hře."); return; }
        g.removePlayer(p, true);
        U.msg(p, "&7Opustil jsi hru.");
    }

    void free(Game g) { games.remove(g); slots.clear(g.slot); }

    public void stopAll() { for (Game g : new ArrayList<>(games)) g.destroyNow(); games.clear(); }

    public void deliverRewards(Player p) {
        List<ItemStack> l = pendingRewards.remove(p.getUniqueId());
        if (l == null) return;
        for (ItemStack it : l) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    void hideName(Player p, boolean hide) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("ss_hidden");
        if (t == null) { t = sb.registerNewTeam("ss_hidden"); t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER); }
        if (hide) t.addEntry(p.getName()); else { t.removeEntry(p.getName()); pl.ranks.apply(p); }
    }

    private Game gameAt(Location l) {
        if (!pl.isArena(l.getWorld())) return null;
        for (Game g : games) if (g.owns(l)) return g;
        return null;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    // ---------------- události ----------------
    @EventHandler public void voteClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof VoteHolder h)) return;
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == e.getInventory() && map.get(p) == h.game) h.game.vote(p, e.getSlot());
    }

    @EventHandler public void menu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_menu");
        if (t != null && e.getWhoClicked() instanceof Player p) {
            p.closeInventory();
            if (t.equals("REDSTONE")) { if (cz.adrrex.squarestar.RedstoneWeek.voting()) pl.redstone.visit(p, 0); else pl.redstone.join(p); }
            else join(p, GameType.valueOf(t));
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) { Game g = map.get(e.getPlayer()); if (g != null) g.removePlayer(e.getPlayer(), false); }

    @EventHandler(priority = EventPriority.HIGH) public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Game g = map.get(v);
        if (g == null) return;
        if (g.state != Game.State.RUNNING || !g.alive.contains(v)) { e.setCancelled(true); return; }
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.FALL || c == EntityDamageEvent.DamageCause.VOID) { e.setCancelled(true); return; }
        Player killer = null;
        if (e instanceof EntityDamageByEntityEvent ed) {
            Player a = attacker(ed.getDamager());
            if (a != null) {
                if (a == v && ed.getDamager() instanceof TNTPrimed) { /* vlastní TNT je OK */ }
                else if (map.get(a) != g || !g.alive.contains(a)) { e.setCancelled(true); return; }
                if (!g.onPvp(v, a, ed)) { e.setCancelled(true); return; }
                if (e.isCancelled()) return;
                if (a != v) g.lastHit.put(v, a);
                killer = a;
            }
        }
        switch (g.damage()) {
            case NONE -> { e.setCancelled(true); return; }
            case KNOCKBACK -> { e.setDamage(0); return; }
            default -> {}
        }
        if (e.getFinalDamage() >= v.getHealth()) {
            e.setCancelled(true);
            g.onDeath(v, killer != null ? killer : g.lastHit.get(v));
        }
    }

    @EventHandler public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = map.get(p);
        if (g == null) return;
        if (e.getTo().getY() < g.voidY()) {
            if (g.state == Game.State.RUNNING && g.alive.contains(p)) g.onVoid(p);
            else p.teleport(g.state == Game.State.RUNNING ? g.specSpot() : g.waitSpot());
            return;
        }
        if (g.state == Game.State.RUNNING && g.alive.contains(p)) g.onMove(p, e.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH) public void brk(BlockBreakEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) { if (pl.isArena(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("squarestar.admin")) e.setCancelled(true); return; }
        if (g.state != Game.State.RUNNING || !g.alive.contains(e.getPlayer()) || !g.canBreak(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH) public void place(BlockPlaceEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) { if (pl.isArena(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("squarestar.admin")) e.setCancelled(true); return; }
        Block b = e.getBlock();
        if (g.state != Game.State.RUNNING || !g.alive.contains(e.getPlayer()) || !g.owns(b.getLocation()) || !g.canPlace(e.getPlayer(), b)) { e.setCancelled(true); return; }
        g.markPlaced(b);
    }

    @EventHandler(priority = EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) return;
        String tag = U.tagOf(e.getItem(), "ss_game");
        if ("leave".equals(tag)) { e.setCancelled(true); leave(e.getPlayer()); return; }
        if ("vote".equals(tag)) { e.setCancelled(true); g.openVote(e.getPlayer()); return; }
        if (g.state == Game.State.RUNNING) g.onInteract(e);
    }

    @EventHandler public void interactEntity(PlayerInteractEntityEvent e) {
        Game g = map.get(e.getPlayer());
        if (g != null && g.state == Game.State.RUNNING) g.onInteractEntity(e);
    }

    @EventHandler public void blockDamage(BlockDamageEvent e) {
        Game g = map.get(e.getPlayer());
        if (g != null && g.state == Game.State.RUNNING && g.alive.contains(e.getPlayer())) g.onBlockDamage(e);
    }

    @EventHandler public void projectile(ProjectileHitEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) { Game g = map.get(p); if (g != null && g.state == Game.State.RUNNING) g.onProjectileHit(e); }
    }

    @EventHandler public void food(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) { Game g = map.get(p); if (g != null && (!g.hunger() || g.state != Game.State.RUNNING)) e.setCancelled(true); }
    }

    @EventHandler public void drop(PlayerDropItemEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) return;
        if (g.state != Game.State.RUNNING || !g.canDrop() || "leave".equals(U.tagOf(e.getItemDrop().getItemStack(), "ss_game"))) e.setCancelled(true);
    }

    @EventHandler public void explode(EntityExplodeEvent e) {
        if (!pl.isArena(e.getLocation().getWorld())) return;
        Game g = gameAt(e.getLocation());
        e.blockList().removeIf(b -> g == null || !g.isPlaced(b) || b.getType().name().endsWith("_BED") || b.getType() == Material.BARRIER || b.getType().name().contains("GLASS"));
    }

    @EventHandler public void blockExplode(BlockExplodeEvent e) { if (pl.isArena(e.getBlock().getWorld())) e.blockList().clear(); }

    @EventHandler public void creature(CreatureSpawnEvent e) {
        if (pl.isArena(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler public void teleport(PlayerTeleportEvent e) {
        // spektátor nesmí utéct z arény
        Game g = map.get(e.getPlayer());
        if (g != null && e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && !g.owns(e.getTo())) e.setCancelled(true);
    }

    @EventHandler public void bucket(PlayerBucketEmptyEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) return;
        if (g.state != Game.State.RUNNING || !g.canPlace(e.getPlayer(), e.getBlock())) { e.setCancelled(true); return; }
        g.markPlaced(e.getBlock());
    }

    @EventHandler public void flow(BlockFromToEvent e) {
        if (!pl.isArena(e.getBlock().getWorld())) return;
        Game g = gameAt(e.getToBlock().getLocation());
        if (g == null || !g.allowFlow(e.getToBlock())) { e.setCancelled(true); return; }
        g.markPlaced(e.getToBlock());
    }

    // ---- růst stromů/hub/trávy, tvorba bloků: zaznamenat, aby se po hře uklidily ----
    @EventHandler(ignoreCancelled = true) public void grow(org.bukkit.event.world.StructureGrowEvent e) {
        Game g = gameAt(e.getLocation());
        if (g == null) return;
        e.getBlocks().removeIf(st -> gameAt(st.getLocation()) != g);
        for (org.bukkit.block.BlockState st : e.getBlocks()) g.markPlaced(st.getBlock());
    }
    @EventHandler(ignoreCancelled = true) public void spread(org.bukkit.event.block.BlockSpreadEvent e) { track(e.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void form(org.bukkit.event.block.BlockFormEvent e) { track(e.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void growB(org.bukkit.event.block.BlockGrowEvent e) { track(e.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void fert(org.bukkit.event.block.BlockFertilizeEvent e) {
        for (org.bukkit.block.BlockState st : e.getBlocks()) track(st.getBlock());
    }
    private void track(org.bukkit.block.Block b) {
        Game g = gameAt(b.getLocation());
        if (g != null) g.markPlaced(b);
    }
    /** levné moby (slizy pod hlavami) se po smrti nedělí */
    @EventHandler public void slimeSplit(org.bukkit.event.entity.SlimeSplitEvent e) { if (pl.isArena(e.getEntity().getWorld())) e.setCancelled(true); }
    @EventHandler public void exitVehicle(org.bukkit.event.vehicle.VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) { Game g = of(p); if (g != null && !g.canExitVehicle(p)) e.setCancelled(true); }
    }
    @EventHandler public void vehicleDamage(org.bukkit.event.vehicle.VehicleDamageEvent e) { if (pl.isArena(e.getVehicle().getWorld())) e.setCancelled(true); }
    @EventHandler public void vehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent e) { if (pl.isArena(e.getVehicle().getWorld())) e.setCancelled(true); }
}
