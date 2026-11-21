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
        // lobby drž pořád načtenou, jinak by 3D nápis, raketa a NPC zmizely
        for (int cx = -4; cx <= 4; cx++) for (int cz = -2; cz <= 5; cz++) pl.mars.addPluginChunkTicket(cx, cz, pl);
        if (pl.mars.getBlockAt(0, Y, 0).getType() != Material.AIR) { spawnHolo(); pl.crates.placeBlocks(); pl.fx.spawnAll(); return; }
        pl.getLogger().info("Stavim Mars lobby...");
        build(() -> { pl.getLogger().info("Mars lobby hotova!"); spawnHolo(); pl.crates.placeBlocks(); pl.fx.spawnAll(); });
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
        decorate(b, r);
        earth(b);
        b.submit(done);
    }

    /** Vylepšení lobby: mozaika náměstí, cesty s lampami, krystalové věže, moduly základny, radar, solární pole. */
    private void decorate(U.BlockQueue.Builder b, java.util.Random r) {
        // mozaika náměstí: soustředné kruhy, 8 paprsků a hvězda uprostřed
        for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 14.5) continue;
            double ang = Math.toDegrees(Math.atan2(z, x)); if (ang < 0) ang += 360;
            boolean spoke = d > 3 && d < 14 && (Math.abs(ang % 45) < 3 || Math.abs(ang % 45) > 42);
            Material m = null;
            if (Math.abs(d - 6) < 0.5 || Math.abs(d - 13) < 0.5) m = Material.POLISHED_BLACKSTONE_BRICKS;
            else if (spoke) m = ((int) d % 3 == 0) ? Material.OCHRE_FROGLIGHT : Material.CUT_RED_SANDSTONE;
            else if (d < 3) { double a5 = Math.abs(Math.cos(Math.atan2(z, x) * 2.5)); m = d < 1.2 + a5 * 1.8 ? Material.GOLD_BLOCK : Material.ORANGE_GLAZED_TERRACOTTA; }
            if (m != null) b.set(x, Y, z, m);
        }
        // 4 cesty od vchodů ven, lampy po stranách
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int t = 18; t <= 44; t++) for (int w2 = -1; w2 <= 1; w2++) {
                int x = dir[0] * t + dir[1] * w2, z = dir[1] * t + dir[0] * w2;
                b.set(x, Y, z, w2 == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                for (int y = 1; y <= 3; y++) b.set(x, Y + y, z, Material.AIR);
            }
            for (int t = 20; t <= 44; t += 6) for (int side : new int[]{-2, 2}) {
                int x = dir[0] * t + dir[1] * side, z = dir[1] * t + dir[0] * side;
                b.fill(x, Y + 1, z, x, Y + 3, z, Material.POLISHED_BLACKSTONE_WALL);
                b.set(x, Y + 4, z, Material.SEA_LANTERN);
            }
        }
        // krystalové věže (6 kolem kopule)
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.toRadians(15);
            int cx = (int) Math.round(Math.cos(a) * 30), cz = (int) Math.round(Math.sin(a) * 30);
            int hgt = 7 + r.nextInt(6);
            for (int y = 0; y < hgt; y++) {
                int rad = Math.max(0, 2 - y * 3 / hgt);
                for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++)
                    if (dx * dx + dz * dz <= rad * rad + 1) b.set(cx + dx, Y + 1 + y, cz + dz, y % 3 == 0 ? Material.AMETHYST_BLOCK : Material.PURPLE_STAINED_GLASS);
            }
            b.set(cx, Y + 1 + hgt, cz, Material.END_ROD);
            for (int k = 0; k < 4; k++) b.set(cx + r.nextInt(5) - 2, Y + 1, cz + r.nextInt(5) - 2, Material.AMETHYST_CLUSTER);
        }
        // moduly základny (malé kopule s okny)
        for (int[] m : new int[][]{{-14, -40}, {-30, -26}, {36, -14}}) {
            for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + y * y + z * z);
                if (d > 4.3 || d <= 3.3) continue;
                b.set(m[0] + x, Y + 1 + y, m[1] + z, y == 2 && (x == 0 || z == 0) ? Material.LIGHT_BLUE_STAINED_GLASS : y == 4 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE);
            }
            b.fill(m[0] - 1, Y + 1, m[1] - 4, m[0] + 1, Y + 2, m[1] - 4, Material.AIR);
        }
        // radar (talíř na stožáru)
        int dx0 = 26, dz0 = -34;
        b.fill(dx0, Y + 1, dz0, dx0, Y + 8, dz0, Material.IRON_BARS);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x * x + z * z <= 10) b.set(dx0 + x, Y + 9 + (x * x + z * z) / 5, dz0 + z, Material.WHITE_CONCRETE);
        b.set(dx0, Y + 10, dz0, Material.REDSTONE_LAMP); b.set(dx0, Y + 11, dz0, Material.REDSTONE_BLOCK);
        // solární pole
        for (int row = 0; row < 3; row++) for (int col = 0; col < 5; col++) {
            int x = -44 + col * 3, z = 8 + row * 4;
            b.set(x, Y + 1, z, Material.IRON_BARS);
            b.fill(x - 1, Y + 2, z - 1, x + 1, Y + 2, z + 1, Material.DAYLIGHT_DETECTOR);
        }
    }

    /** Úklid staré blokové Země (od verze 2.2 je Země 3D model, který se točí). */
    private void earth(U.BlockQueue.Builder b) {
        b.fill(-34 - 16, Y + 50 - 16, 66 - 16, -34 + 16, Y + 50 + 16, 66 + 16, Material.AIR);
    }

    public void spawnHolo() {
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_holo_lobby")) e.remove();
        Location l = new Location(pl.mars, 0.5, Y + 4.2, 6.5);
        pl.mars.spawn(l, TextDisplay.class, t -> {
            t.text(U.c("&c&l✦ SQUARESTAR ✦\n&6Minihry na Marsu\n&7Nízká gravitace všude!\n\n&eNPC za tebou &7nebo &eKompas &7= minihry\n&dBedny &7jsou přímo před tebou\n&bDvojskok: &7skoč 2x"));
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
        // bez uloženého inventáře: z minihry nic nepřenášet (žádná zlatá jablka do lobby), jinak nechat co má
        boolean fromGame = pl.isArena(p.getWorld()) || (pl.redstone != null && p.getWorld() == pl.redstone.world);
        org.bukkit.inventory.ItemStack[] contents = saved != null
                ? org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(saved)
                : fromGame ? new org.bukkit.inventory.ItemStack[0] : p.getInventory().getContents().clone();
        pdc.remove(U.key("lobby_inv"));
        U.reset(p, GameMode.ADVENTURE);
        try { p.getInventory().setContents(contents); } catch (IllegalArgumentException ex) { pl.getLogger().warning("Inventar " + p.getName() + " nelze obnovit: " + ex.getMessage()); }
        p.teleport(spawn());
        giveItems(p);
        pl.applyGravity(p);
        pl.games.deliverRewards(p);
    }

    public void giveItems(Player p) {
        place(p, 0, Gui.skin(p, U.tag(U.item(Material.COMPASS, 1, "&6&l✦ Minihry &7(pravý klik)", "&7Vyber si minihru na Marsu"), "ss_lobby", "games"), "icon_menu_games"));
        place(p, 4, Gui.skin(p, pl.cosmetics.menuItem(), "icon_menu_cosmetics"));
        place(p, 8, Gui.skin(p, U.tag(U.item(Material.ENDER_EYE, 1, "&b&lZpět na spawn", "&7Teleport na náměstí"), "ss_lobby", "spawn"), "icon_menu_spawn"));
        pl.cosmetics.apply(p);
        pl.levels.showBar(p);
    }

    /** dá lobby item do slotu; co tam bylo (klíče, odměny), se jen přesune jinam */
    private void place(Player p, int slot, org.bukkit.inventory.ItemStack it) {
        var cur = p.getInventory().getItem(slot);
        p.getInventory().setItem(slot, Lang.item(p, it));
        if (cur != null && !cur.getType().isAir() && U.tagOf(cur, "ss_lobby") == null)
            for (var left : p.getInventory().addItem(cur).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    boolean inLobby(Player p) { return p.getWorld() == pl.mars; }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (pl.getConfig().getBoolean("lobby.force-on-join", true)) Bukkit.getScheduler().runTask(pl, () -> send(p));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            p.showTitle(net.kyori.adventure.title.Title.title(U.c("&c&l✦ &6&lSQUARE&c&lSTAR &c&l✦"), U.c("&7Vítej na Marsu, &e" + p.getName() + "&7!"),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(600), java.time.Duration.ofMillis(2500), java.time.Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.6, 1, 0.6, 0.08);
        }, 20);
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
        else if (t.equals("cosmetics")) pl.cosmetics.open(e.getPlayer());
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
        return List.of("&6/ss hry &7- menu miniher", "&6/ss join <hra> &7- připojit se", "&6/ss leave &7- opustit hru", "&6/ss lobby &7- zpět na Mars", "&6/ss lag &7- výkon serveru");
    }
}
