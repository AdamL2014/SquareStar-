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
    /** zvyš, když se změní stavba lobby – server ji pak sám přestaví */
    public static final int LOBBY_VER = 41;
    public static final int Y = 99; // podlaha náměstí
    public Lobby(SquareStar pl) { this.pl = pl; }

    public Location spawn() { return new Location(pl.mars, 0.5, Y + 1, 0.5, 0, 0); }

    public void ensureBuilt() {
        pl.mars.setSpawnLocation(0, Y + 1, 0);
        // lobby drž pořád načtenou, jinak by 3D nápis, raketa a NPC zmizely
        for (int cx = -4; cx <= 4; cx++) for (int cz = -2; cz <= 5; cz++) pl.mars.addPluginChunkTicket(cx, cz, pl);
        // nová verze lobby -> postavit znovu (přes starou), aby se objevily novinky na mapě
        var verKey = new org.bukkit.NamespacedKey("squarestar", "lobby_ver");
        boolean old = pl.mars.getPersistentDataContainer().getOrDefault(verKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0) < LOBBY_VER;
        if (old) pl.mars.getPersistentDataContainer().set(verKey, org.bukkit.persistence.PersistentDataType.INTEGER, LOBBY_VER);
        var domeKey = new org.bukkit.NamespacedKey("squarestar", "dome_ver");
        int dv = pl.mars.getPersistentDataContainer().getOrDefault(domeKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        if (!old && pl.mars.getBlockAt(0, Y, 0).getType() != Material.AIR && dv < 3) {
            pl.mars.getPersistentDataContainer().set(domeKey, org.bukkit.persistence.PersistentDataType.INTEGER, 3);
            pl.getLogger().info("Upravuji kopuli lobby...");
            if (dv < 2) upgradeDome(() -> pl.getLogger().info("Kopule hotova!")); else closeDoors(() -> pl.getLogger().info("Vchody zmenseny!"));
        }
        if (old) pl.mars.getPersistentDataContainer().set(domeKey, org.bukkit.persistence.PersistentDataType.INTEGER, 3);
        if (!old && pl.mars.getBlockAt(0, Y, 0).getType() != Material.AIR) { spawnHolo(); pl.crates.placeBlocks(); pl.fx.spawnAll(); return; }
        pl.getLogger().info("Stavim Mars lobby...");
        build(() -> { pl.getLogger().info("Mars lobby hotova!"); spawnHolo(); pl.crates.placeBlocks(); pl.fx.spawnAll(); });
    }

    static final int D = 18, DH = 28;
    /** vchod do kopule: 1 blok široký, 3 vysoký (dřív 3x5) */
    static boolean door(int x, int y, int z) { return y <= 2 && (x == 0 || z == 0); }
    /** jednorázově: zasklít staré velké vchody (3x5), nechat jen malé */
    private void closeDoors(Runnable done) {
        U.BlockQueue.Builder b = pl.queue.builder(pl.mars);
        for (int x = -D; x <= D; x++) for (int y = 0; y <= 4; y++) for (int z = -D; z <= D; z++) {
            if (!inDomeShell(x, y, z, D, DH) || door(x, y, z) || !(Math.abs(x) <= 1 || Math.abs(z) <= 1)) continue;
            b.set(x, Y + 1 + y, z, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        b.submit(done);
    }
    /** skořápka půlelipsoidu o vodorovném poloměru d a výšce h (tloušťka 1 blok) */
    static boolean inDomeShell(int x, int y, int z, int d, int h) {
        double r2 = x * x + z * z;
        double out = r2 / (d * (double) d) + y * (double) y / (h * (double) h);
        double in = r2 / ((d - 1.0) * (d - 1.0)) + y * (double) y / ((h - 1.0) * (h - 1.0));
        return out <= 1 && in > 1;
    }
    /** jednorázově: stará nízká kopule (koule poloměr 18) -> vyšší kopule; smaže i díru po starém stromě */
    private void upgradeDome(Runnable done) {
        U.BlockQueue.Builder b = pl.queue.builder(pl.mars);
        for (int x = -D; x <= D; x++) for (int y = 0; y <= D; y++) for (int z = -D; z <= D; z++) {
            double dd = Math.sqrt(x * x + y * y + z * z);
            if (dd > D || dd <= D - 1 || inDomeShell(x, y, z, D, DH)) continue;
            Material m = pl.mars.getBlockAt(x, Y + 1 + y, z).getType();
            if (m == Material.LIGHT_BLUE_STAINED_GLASS || m == Material.SEA_LANTERN) b.set(x, Y + 1 + y, z, Material.AIR);
        }
        for (int x = -D; x <= D; x++) for (int y = 0; y <= DH; y++) for (int z = -D; z <= D; z++) {
            if (!inDomeShell(x, y, z, D, DH) || (door(x, y, z))) continue;
            b.set(x, Y + 1 + y, z, y > DH - 3 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
        }
        b.submit(done);
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
        // skleněná kopule s 4 vchody (vyšší: poloměr 18, výška 28 – vejde se vánoční strom)
        for (int x = -D; x <= D; x++) for (int y = 0; y <= DH; y++) for (int z = -D; z <= D; z++) {
            if (!inDomeShell(x, y, z, D, DH)) continue;
            if (door(x, y, z)) continue;
            b.set(x, Y + 1 + y, z, y > DH - 3 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
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
        // ---- v4.1: krátery, skleník, vlajky, balvany, žhnoucí praskliny, podstavce žebříčků ----
        for (int[] c : new int[][]{{-26, 36, 6}, {40, 24, 5}, {-42, -12, 5}, {14, -46, 4}, {40, -30, 4}}) {
            int cx = c[0], cz = c[1], rc = c[2];
            for (int x = -rc - 2; x <= rc + 2; x++) for (int z = -rc - 2; z <= rc + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= rc) {
                    int depth = (int) Math.round((1 - (d / rc) * (d / rc)) * 3) + 1;
                    for (int y = Y - depth + 1; y <= Y + 4; y++) b.set(cx + x, y, cz + z, Material.AIR);
                    b.set(cx + x, Y - depth, cz + z, d < 1.5 ? Material.MAGMA_BLOCK : d < rc * 0.5 ? Material.BLACKSTONE : Material.BROWN_TERRACOTTA);
                    if (d < 1) b.set(cx + x, Y - depth + 1, cz + z, Material.AMETHYST_CLUSTER);
                } else if (d <= rc + 1.6) { b.set(cx + x, Y + 1, cz + z, Material.RED_TERRACOTTA); if (r.nextInt(4) == 0) b.set(cx + x, Y + 2, cz + z, Material.RED_SAND); }
            }
        }
        // skleník (zelená kopule – „první zahrada na Marsu")
        int gx = 26, gz = 36;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 6.5) continue;
            b.set(gx + x, Y, gz + z, d > 5.5 ? Material.POLISHED_BLACKSTONE : (x + z) % 4 == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
            for (int y = 1; y <= 6; y++) b.set(gx + x, Y + y, gz + z, Material.AIR);
        }
        for (int x = -6; x <= 6; x++) for (int y = 0; y <= 6; y++) for (int z = -6; z <= 6; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d <= 6.5 && d > 5.6) b.set(gx + x, Y + 1 + y, gz + z, y % 3 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_STAINED_GLASS);
        }
        b.fill(gx - 1, Y + 1, gz - 6, gx + 1, Y + 3, gz - 6, Material.AIR);   // vchod směrem k náměstí
        b.fill(gx, Y + 1, gz, gx, Y + 3, gz, Material.OAK_LOG);
        for (int x = -2; x <= 2; x++) for (int y = 3; y <= 5; y++) for (int z = -2; z <= 2; z++) if (Math.abs(x) + Math.abs(z) + (y - 3) <= 3 && !(x == 0 && z == 0 && y == 3)) b.set(gx + x, Y + y, gz + z, Material.AZALEA_LEAVES);
        Material[] fl = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.ALLIUM, Material.FERN, Material.SHORT_GRASS};
        for (int i = 0; i < 18; i++) { int x = r.nextInt(9) - 4, z = r.nextInt(9) - 4; if (Math.abs(x) + Math.abs(z) > 1) b.set(gx + x, Y + 1, gz + z, fl[r.nextInt(fl.length)]); }
        b.set(gx, Y + 6, gz, Material.SEA_LANTERN);
        // vlajky SquareStar u každého východu
        for (int[] f : new int[][]{{3, 18, 1, 0}, {-3, -18, -1, 0}, {18, -3, 0, -1}, {-18, 3, 0, 1}}) {
            b.fill(f[0], Y + 1, f[1], f[0], Y + 6, f[1], Material.IRON_BARS);
            b.set(f[0], Y + 7, f[1], Material.END_ROD);
            for (int k = 1; k <= 3; k++) for (int y = 4; y <= 6; y++)
                b.set(f[0] + f[2] * k, Y + y, f[1] + f[3] * k, y == 5 && k == 2 ? Material.GOLD_BLOCK : (y + k) % 2 == 0 ? Material.RED_CONCRETE : Material.ORANGE_CONCRETE);
        }
        // balvany v krajině
        Material[] rock = {Material.TUFF, Material.BLACKSTONE, Material.RED_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.BASALT};
        for (int i = 0; i < 16; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 22 + r.nextDouble() * 26;
            int cx = (int) (Math.cos(a) * d), cz = (int) (Math.sin(a) * d);
            if (Math.abs(cx) <= 4 || Math.abs(cz) <= 4) continue;   // ne na cestách
            int rr = 1 + r.nextInt(2);
            for (int x = -rr; x <= rr; x++) for (int y = 0; y <= rr; y++) for (int z = -rr; z <= rr; z++)
                if (x * x + y * y * 2 + z * z <= rr * rr + 1) b.set(cx + x, Y + 1 + y, cz + z, rock[r.nextInt(rock.length)]);
        }
        // žhnoucí praskliny v zemi
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90 + r.nextInt(20) - 10);
            double x = Math.cos(a) * 20, z = Math.sin(a) * 20;
            for (int k = 0; k < 22; k++) {
                a += (r.nextDouble() - 0.5) * 0.5;
                x += Math.cos(a); z += Math.sin(a);
                b.set((int) x, Y, (int) z, k % 5 == 0 ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK);
            }
        }
        // podstavce pod žebříčky (po stranách náměstí)
        for (int[] p : new int[][]{{-14, 5}, {-9, 12}, {8, 12}, {13, 5}}) {
            b.fill(p[0] - 1, Y, p[1] - 1, p[0] + 1, Y, p[1] + 1, Material.POLISHED_BLACKSTONE);
            b.set(p[0], Y, p[1], Material.SEA_LANTERN);
            b.set(p[0], Y + 1, p[1], Material.POLISHED_BLACKSTONE_WALL);
            b.set(p[0], Y + 2, p[1], Material.END_ROD);
        }
    }

    /** Úklid staré blokové Země (od verze 2.2 je Země 3D model, který se točí). */
    private void earth(U.BlockQueue.Builder b) {
        b.fill(-34 - 16, Y + 50 - 16, 66 - 16, -34 + 16, Y + 50 + 16, 66 + 16, Material.AIR);
    }

    public void spawnHolo() {
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_holo_lobby")) e.remove();
        Location l = new Location(pl.mars, 0.5, Y + 4.2, 6.5);
        TextDisplay holo = pl.mars.spawn(l, TextDisplay.class, t -> {
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.addScoreboardTag("ss_holo_lobby");
            t.setPersistent(false);
        });
        Holo.dual(holo, "&c&l✦ SQUARESTAR ✦\n&6Minihry na Marsu\n&7Nízká gravitace všude!\n\n&eNPC za tebou &7nebo &eKompas &7= minihry\n&dBedny &7jsou přímo před tebou\n&bDvojskok: &7skoč 2x");
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
        // úklid zdvojených lobby itemů (mimo jejich sloty)
        for (int i = 9; i < 36; i++) if (U.tagOf(p.getInventory().getItem(i), "ss_lobby") != null) p.getInventory().setItem(i, null);
        place(p, 0, Gui.skin(p, U.tag(U.item(Material.COMPASS, 1, "&6&l✦ Minihry &7(pravý klik)", "&7Vyber si minihru na Marsu"), "ss_lobby", "games"), "icon_menu_games"));
        place(p, 1, Gui.skin(p, pl.coins.menuItem(), "icon_menu_shop"));
        if (pl.xmas.active()) place(p, 7, Gui.skin(p, U.tag(U.item(Material.RED_SHULKER_BOX, 1, "&c&l❄ Adventní kalendář &7(pravý klik)", "&7Každý den dárek a mince!"), "ss_lobby", "advent"), "gift_red"));
        else if (U.tagOf(p.getInventory().getItem(7), "ss_lobby") != null) p.getInventory().setItem(7, null);
        place(p, 2, Gui.skin(p, U.tag(U.item(Material.PLAYER_HEAD, 1, "&d&l♥ Přátelé &7(pravý klik)", "&7Kdo je online a co hraje"), "ss_lobby", "friends"), "icon_menu_friends"));
        place(p, 4, Gui.skin(p, pl.cosmetics.menuItem(), "icon_menu_cosmetics"));
        place(p, 5, U.tag(U.item(Material.JUKEBOX, 1, "&d&l♫ Hudba &7(pravý klik)", "&7Vyber skladbu a sestav playlist"), "ss_lobby", "music"));
        place(p, 6, Gui.skin(p, U.tag(U.item(Material.WRITABLE_BOOK, 1, "&e&l✦ Co je nové &7(pravý klik)", "&7Novinky na serveru"), "ss_lobby", "news"), "icon_menu_news"));
        place(p, 8, Gui.skin(p, U.tag(U.item(Material.ENDER_EYE, 1, "&b&lZpět na spawn", "&7Teleport na náměstí"), "ss_lobby", "spawn"), "icon_menu_spawn"));
        pl.cosmetics.apply(p);
        pl.levels.showBar(p);
        pl.fly.restore(p);
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
        else if (t.equals("friends")) pl.friends.open(e.getPlayer());
        else if (t.equals("news")) pl.news.open(e.getPlayer());
        else if (t.equals("shop")) pl.coins.open(e.getPlayer());
        else if (t.equals("music")) e.getPlayer().performCommand("hudba");
        else if (t.equals("advent")) pl.xmas.openCalendar(e.getPlayer());
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
        return List.of("&6/ss hry &7- menu miniher", "&6/ss join <hra> &7- připojit se", "&6/ss leave &7- opustit hru", "&6/ss lobby &7- zpět na Mars", "&6/ss top &7- tvoje statistiky", "&6/ss novinky &7- co je nové",
                "&6/pritel &7- přátelé", "&6/fly &7- létání (VIP+, YouTuber...)", "&6/ss redstone pridat <hráč> &7- spoluhráč na pozemku", "&6/ss lag &7- výkon serveru");
    }
}
