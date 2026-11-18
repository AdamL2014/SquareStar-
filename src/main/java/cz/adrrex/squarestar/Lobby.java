package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.Random;

/** Mars lobby: postaví se samo při prvním spuštění. */
public final class Lobby implements Listener {
    private final SquareStar pl;
    public static final int Y = 99; // podlaha náměstí
    public Lobby(SquareStar pl) { this.pl = pl; }

    public Location spawn() { return new Location(pl.mars, 0.5, Y + 1, 0.5, 0, 0); }

    public void ensureBuilt() {
        pl.mars.setSpawnLocation(0, Y + 1, 0);
        if (pl.mars.getBlockAt(0, Y, 0).getType() != Material.AIR) { spawnHolo(); pl.crates.placeBlocks(); return; }
        pl.getLogger().info("Stavim Mars lobby...");
        build(() -> { pl.getLogger().info("Mars lobby hotova!"); spawnHolo(); pl.crates.placeBlocks(); });
    }

    public void build(Runnable done) {
        World w = pl.mars; Random r = new Random(42);
        U.BlockQueue.Builder b = pl.queue.builder(w);
        Material[] top = {Material.RED_SAND, Material.RED_SAND, Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.BROWN_TERRACOTTA, Material.RED_SAND};
        int R = 55;
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R) continue;
            int h;
            if (d <= 17) h = 0;
            else {
                double n = 1.6 * Math.sin(x / 6.0) + 1.4 * Math.cos(z / 8.0) + Math.sin((x + z) / 11.0);
                h = (int) Math.round(Math.max(-1, n) * Math.min(1, (d - 17) / 8.0));
                if (d > R - 6) h -= (int) ((d - (R - 6)) / 1.5);
            }
            int topY = Y + h;
            int bottom = Y - 5 - (int) ((R - d) / 6);
            for (int y = bottom; y < topY; y++) b.set(x, y, z, y < topY - 2 ? Material.RED_SANDSTONE : Material.RED_TERRACOTTA);
            b.set(x, topY, z, d <= 17 ? (d >= 15 ? Material.RED_NETHER_BRICKS : Material.SMOOTH_RED_SANDSTONE) : top[r.nextInt(top.length)]);
        }
        // světla na náměstí
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            b.set((int) Math.round(Math.cos(ang) * 11), Y, (int) Math.round(Math.sin(ang) * 11), Material.SEA_LANTERN);
        }
        // skleněná kopule s 4 vchody
        int D = 18;
        for (int x = -D; x <= D; x++) for (int y = 0; y <= D; y++) for (int z = -D; z <= D; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d > D || d <= D - 1) continue;
            if (y <= 4 && (Math.abs(x) <= 1 || Math.abs(z) <= 1)) continue;
            b.set(x, Y + 1 + y, z, y > D - 3 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
        }
        // kameny
        for (int i = 0; i < 16; i++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 22 + r.nextInt(28);
            int cx = (int) (Math.cos(ang) * dist), cz = (int) (Math.sin(ang) * dist), rr = 1 + r.nextInt(2);
            b.sphere(cx, Y + 1, cz, rr, p -> b.set(p[0], p[1], p[2], r.nextBoolean() ? Material.BLACKSTONE : Material.BASALT));
        }
        // raketa
        int rx = 32, rz = 30;
        for (int y = 0; y < 16; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            if (x * x + z * z <= 5) b.set(rx + x, Y + 1 + y, rz + z, y % 5 == 4 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
        for (int y = 0; y < 5; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (y < 3 || (x == 0 && z == 0)) b.set(rx + x, Y + 17 + y, rz + z, Material.RED_CONCRETE);
        for (int[] f : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) b.fill(rx + f[0], Y + 1, rz + f[1], rx + f[0], Y + 4, rz + f[1], Material.GRAY_CONCRETE);
        b.set(rx, Y + 9, rz - 2, Material.LIGHT_BLUE_STAINED_GLASS);
        b.submit(done);
    }

    public void spawnHolo() {
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_holo_lobby")) e.remove();
        Location l = new Location(pl.mars, 0.5, Y + 4.2, 6.5);
        pl.mars.spawn(l, TextDisplay.class, t -> {
            t.text(U.c("&c&l✦ SQUARESTAR ✦\n&6Minihry na Marsu\n&7Nízká gravitace všude!\n\n&eKompas &7= výběr miniher\n&dBedny &7jsou přímo před tebou"));
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.addScoreboardTag("ss_holo_lobby");
            t.setPersistent(false);
        });
    }

    /** Uloží lobby inventář (klíče, odměny z beden) do dat hráče, než jde do hry. */
    public void saveInv(Player p) {
        byte[] data = org.bukkit.inventory.ItemStack.serializeItemsAsBytes(p.getInventory().getContents());
        p.getPersistentDataContainer().set(U.key("lobby_inv"), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, data);
    }

    public void send(Player p) {
        var pdc = p.getPersistentDataContainer();
        byte[] saved = pdc.get(U.key("lobby_inv"), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
        org.bukkit.inventory.ItemStack[] contents = saved != null
                ? org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(saved)
                : p.getInventory().getContents().clone();
        pdc.remove(U.key("lobby_inv"));
        U.reset(p, GameMode.ADVENTURE);
        try { p.getInventory().setContents(contents); } catch (IllegalArgumentException ex) { pl.getLogger().warning("Inventar " + p.getName() + " nelze obnovit: " + ex.getMessage()); }
        p.teleport(spawn());
        giveItems(p);
        pl.applyGravity(p);
        pl.games.deliverRewards(p);
    }

    public void giveItems(Player p) {
        p.getInventory().setItem(0, U.tag(U.item(Material.COMPASS, 1, "&6&l✦ Minihry &7(pravý klik)", "&7Vyber si minihru na Marsu"), "ss_lobby", "games"));
        p.getInventory().setItem(8, U.tag(U.item(Material.ENDER_EYE, 1, "&b&lZpět na spawn", "&7Teleport na náměstí"), "ss_lobby", "spawn"));
    }

    boolean inLobby(Player p) { return p.getWorld() == pl.mars; }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (pl.getConfig().getBoolean("lobby.force-on-join", true)) Bukkit.getScheduler().runTask(pl, () -> send(p));
    }

    @EventHandler public void respawn(PlayerRespawnEvent e) {
        if (e.getPlayer().getWorld() == pl.mars) e.setRespawnLocation(spawn());
    }

    @EventHandler(priority = EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        String t = U.tagOf(e.getItem(), "ss_lobby");
        if (t == null) return;
        e.setCancelled(true);
        if (t.equals("games")) pl.games.openMenu(e.getPlayer());
        else if (t.equals("spawn")) e.getPlayer().teleport(spawn());
    }

    private boolean admin(Player p) { return p.hasPermission("squarestar.admin") && p.getGameMode() == GameMode.CREATIVE; }

    @EventHandler public void brk(BlockBreakEvent e) { if (inLobby(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void place(BlockPlaceEvent e) { if (inLobby(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void drop(PlayerDropItemEvent e) { if (inLobby(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void dmg(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && inLobby(p)) e.setCancelled(true); }
    @EventHandler public void food(FoodLevelChangeEvent e) { if (e.getEntity() instanceof Player p && inLobby(p)) e.setCancelled(true); }
    @EventHandler public void move(PlayerMoveEvent e) {
        if (e.getTo().getY() < Y - 40 && inLobby(e.getPlayer())) e.getPlayer().teleport(spawn());
    }
    @EventHandler public void swap(PlayerSwapHandItemsEvent e) { if (inLobby(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true); }

    public static List<String> help() {
        return List.of("&6/ss hry &7- menu miniher", "&6/ss join <hra> &7- připojit se", "&6/ss leave &7- opustit hru", "&6/ss lobby &7- zpět na Mars");
    }
}
