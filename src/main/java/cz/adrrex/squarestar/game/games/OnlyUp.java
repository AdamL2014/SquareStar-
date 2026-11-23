package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Only Up: vysoká věž plná překážek. Jen nahoru! Spadneš = vrátíš se na poslední checkpoint (zlatý blok).
 * Kdo první doleze na smaragdovou plošinu, vyhrává. Normální gravitace, ať jsou skoky přesné.
 */
public final class OnlyUp extends Game {
    private static final int CP_EVERY = 11, CHECKPOINTS = 9, LIM = 17;
    private final List<int[]> cps = new ArrayList<>();          // relativní {x,y,z} checkpointů (0 = start)
    private final Map<Long, Integer> cpIndex = new HashMap<>();
    private final Map<Player, Integer> progress = new HashMap<>();
    private final Map<Player, Integer> bestY = new HashMap<>();
    private int topY;

    public OnlyUp(GameManager gm, int slot) { super(gm, GameType.ONLYUP, slot); }

    @Override public Double gravity() { return 0.08; }
    @Override public List<String> maps() { return List.of("Neonová věž", "Zlatá věž"); }
    @Override protected int voidY() { return oy - 70; }
    /** čekárna pod věží (standardní čekárna nad mapou by byla uvnitř věže) */
    @Override protected int waitY() { return oy - 40; }
    @Override protected int timeLimit() { return 900; }
    @Override protected Dmg damage() { return Dmg.NONE; }

    private static final Material[][] PALETTE = {
            {Material.CYAN_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.LIGHT_BLUE_CONCRETE},
            {Material.GOLD_BLOCK, Material.YELLOW_TERRACOTTA, Material.SMOOTH_SANDSTONE, Material.ORANGE_TERRACOTTA, Material.RAW_GOLD_BLOCK, Material.HONEYCOMB_BLOCK}};

    // směr pohybu (cardinal)
    private int dx = 0, dz = 1;

    private final Map<Long, List<Integer>> col = new HashMap<>();   // obsazené výšky v každém sloupci (x,z) – aby se trasa nikdy neprotnula
    private static long ck(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        Material[] pal = PALETTE[Math.min(map, 1)];
        col.clear(); cps.clear(); cpIndex.clear();
        List<Object[]> tmp = new ArrayList<>();
        // startovní plošina
        for (int a = -2; a <= 2; a++) for (int e = -2; e <= 2; e++) put(tmp, a, 0, e, Material.QUARTZ_BLOCK.createBlockData());
        commit(b, tmp);
        b.fill(ox - 2, oy - 1, oz - 2, ox + 2, oy - 1, oz + 2, Material.SEA_LANTERN);
        cps.add(new int[]{0, 0, 0});
        int x = 0, y = 0, z = 2;   // "přední" okraj aktuálního prvku (poslední blok ve směru jízdy)
        dx = 0; dz = 1;
        int steps = 0;
        for (int c = 1; c <= CHECKPOINTS; c++) {
            for (int s = 0; s < CP_EVERY; s++) {
                steps++;
                if (steps % 4 == 0 && rnd.nextInt(3) > 0) turn(x, z);
                if (Math.abs(x) > LIM || Math.abs(z) > LIM) turn(x, z);
                int[] p = element(b, pal, x, y, z);
                x = p[0]; y = p[1]; z = p[2];
            }
            // checkpoint: zlatá plocha 3x3 (když by se křížila s trasou, zkusí se jiný směr)
            int cx = 0, cz = 0;
            for (int att = 0; att < 80; att++) {
                if (att > 0 && att % 2 == 0) turn(x, z);
                cx = x + dx * 3; cz = z + dz * 3;
                tmp.clear();
                for (int a = -1; a <= 1; a++) for (int e = -1; e <= 1; e++) put(tmp, cx + a, y, cz + e, Material.GOLD_BLOCK.createBlockData());
                if (free(tmp)) break;
            }
            commit(b, tmp);
            b.fill(ox + cx - 1, oy + y - 1, oz + cz - 1, ox + cx + 1, oy + y - 1, oz + cz + 1, Material.SEA_LANTERN);
            b.set(ox + cx, oy + y + 1, oz + cz, Material.LIGHT);
            int idx = cps.size();
            cps.add(new int[]{cx, y, cz});
            for (int a = -1; a <= 1; a++) for (int e = -1; e <= 1; e++) cpIndex.put(U.pack(ox + cx + a, oy + y, oz + cz + e), idx);
            x = cx + dx; z = cz + dz;
        }
        // cíl: smaragdová plošina 5x5
        int fx = 0, fz = 0;
        for (int att = 0; att < 80; att++) {
            if (att > 0 && att % 2 == 0) turn(x, z);
            fx = x + dx * 4; fz = z + dz * 4;
            tmp.clear();
            for (int a = -2; a <= 2; a++) for (int e = -2; e <= 2; e++) put(tmp, fx + a, y, fz + e, Material.EMERALD_BLOCK.createBlockData());
            if (free(tmp)) break;
        }
        commit(b, tmp);
        b.fill(ox + fx - 2, oy + y - 1, oz + fz - 2, ox + fx + 2, oy + y - 1, oz + fz + 2, Material.SEA_LANTERN);
        for (int a = -2; a <= 2; a++) for (int e = -2; e <= 2; e++) if (Math.abs(a) == 2 || Math.abs(e) == 2) b.set(ox + fx + a, oy + y + 1, oz + fz + e, Material.END_ROD);
        topY = y;
        cps.add(new int[]{fx, y, fz});   // poslední = cíl (jen pro informaci)
    }

    private static void put(List<Object[]> tmp, int x, int y, int z, org.bukkit.block.data.BlockData d) { tmp.add(new Object[]{x, y, z, d}); }
    private void put(List<Object[]> tmp, int x, int y, int z, Material m) { put(tmp, x, y, z, m.createBlockData()); }

    /** žádný nový blok nesmí být v tom samém sloupci blíž než 3 výšky od existujícího (jinak by se trasa o sebe zasekla hlavou) */
    private boolean free(List<Object[]> tmp) {
        for (Object[] o : tmp) {
            List<Integer> ys = col.get(ck((int) o[0], (int) o[2]));
            if (ys != null) for (int yy : ys) if (Math.abs(yy - (int) o[1]) <= 2) return false;
        }
        return true;
    }
    private void commit(U.BlockQueue.Builder b, List<Object[]> tmp) {
        for (Object[] o : tmp) {
            b.set(ox + (int) o[0], oy + (int) o[1], oz + (int) o[2], (org.bukkit.block.data.BlockData) o[3]);
            col.computeIfAbsent(ck((int) o[0], (int) o[2]), k -> new ArrayList<>()).add((int) o[1]);
        }
    }

    /** otočí směr o 90° (a směrem ke středu, když je moc u okraje) */
    private void turn(int x, int z) {
        int ndx, ndz;
        if (dx == 0) { ndx = rnd.nextBoolean() ? 1 : -1; ndz = 0; } else { ndz = rnd.nextBoolean() ? 1 : -1; ndx = 0; }
        if (ndx != 0 && Math.abs(x + ndx * 6) > LIM) ndx = -ndx;
        if (ndz != 0 && Math.abs(z + ndz * 6) > LIM) ndz = -ndz;
        dx = ndx; dz = ndz;
        if (Math.abs(x) > LIM && Math.signum(dx) == Math.signum(x) && dx != 0) dx = -dx;
        if (Math.abs(z) > LIM && Math.signum(dz) == Math.signum(z) && dz != 0) dz = -dz;
    }

    /** postaví jeden prvek na volné místo (až 60 pokusů s otáčením), vrátí nový "přední" okraj {x, y, z} */
    private int[] element(U.BlockQueue.Builder b, Material[] pal, int x, int y, int z) {
        List<Object[]> tmp = new ArrayList<>();
        for (int att = 0; att < 60; att++) {
            if (att > 0 && att % 3 == 0) turn(x, z);
            tmp.clear();
            int[] nxt = build(tmp, pal, x, y, z);
            if (free(tmp)) { commit(b, tmp); return nxt; }
        }
        tmp.clear();
        int[] nxt = build(tmp, pal, x, y, z);
        commit(b, tmp);
        return nxt;
    }

    private int[] build(List<Object[]> tmp, Material[] pal, int x, int y, int z) {
        int kind = rnd.nextInt(10);
        Material m = pal[rnd.nextInt(pal.length)];
        int gap = 2 + rnd.nextInt(2);            // 2–3 bloky vzduchu
        int up = rnd.nextInt(3) == 0 ? 1 : 0;    // občas o 1 výš
        if (up == 1) gap = 2;
        if (kind == 6) { up = 0; gap = 2; }      // žebřík začíná ve výšce očí hráče
        int nx = x + dx * (gap + 1), nz = z + dz * (gap + 1), ny = y + up;
        int px = -dz, pz = dx;                   // kolmý směr
        int off = rnd.nextInt(3) - 1;            // boční posun
        nx += px * off; nz += pz * off;
        switch (kind) {
            case 0, 1, 2 -> {   // plošina 2x2
                put(tmp, nx, ny, nz, m); put(tmp, nx + dx, ny, nz + dz, m); put(tmp, nx + px, ny, nz + pz, m); put(tmp, nx + dx + px, ny, nz + dz + pz, m);
                return new int[]{nx + dx, ny, nz + dz};
            }
            case 3, 4 -> {      // jeden blok
                put(tmp, nx, ny, nz, m);
                return new int[]{nx, ny, nz};
            }
            case 5 -> {         // ledový pruh 1x4 (klouže)
                for (int i = 0; i < 4; i++) put(tmp, nx + dx * i, ny, nz + dz * i, i == 0 ? m : Material.PACKED_ICE);
                return new int[]{nx + dx * 3, ny, nz + dz * 3};
            }
            case 6 -> {         // žebřík: sloupec vysoký 6 s žebříkem a plošina nahoře
                for (int i = 0; i < 6; i++) put(tmp, nx, ny + i, nz, Material.STONE_BRICKS);
                put(tmp, nx, ny + 6, nz, m);
                BlockFace f = face(-dx, -dz);        // žebřík na straně příchodu, směřuje k hráči
                for (int i = 1; i <= 6; i++) {
                    var d = (Directional) Material.LADDER.createBlockData(); d.setFacing(f);
                    put(tmp, nx - dx, ny + i, nz - dz, d);
                }
                put(tmp, nx + dx, ny + 6, nz + dz, m);
                return new int[]{nx + dx, ny + 6, nz + dz};
            }
            case 7 -> {         // schody nahoru (3 schody)
                BlockFace f = face(dx, dz);
                for (int i = 0; i < 3; i++) {
                    Stairs st = (Stairs) Material.STONE_BRICK_STAIRS.createBlockData(); st.setFacing(f);
                    put(tmp, nx + dx * i, ny + i, nz + dz * i, st);
                    put(tmp, nx + dx * i, ny + i - 1, nz + dz * i, Material.STONE_BRICKS);
                }
                return new int[]{nx + dx * 2, ny + 2, nz + dz * 2};
            }
            case 8 -> {         // půlbloky v řadě (3 dlaždice s mezerou 1)
                for (int i = 0; i < 3; i++) {
                    Slab s = (Slab) Material.SMOOTH_STONE_SLAB.createBlockData(); s.setType(Slab.Type.BOTTOM);
                    put(tmp, nx + dx * i * 2, ny, nz + dz * i * 2, s);
                }
                return new int[]{nx + dx * 4, ny, nz + dz * 4};
            }
            default -> {        // široká plošina 3x3 (oddech)
                for (int a = -1; a <= 1; a++) for (int e = -1; e <= 1; e++) put(tmp, nx + dx + px * a + dx * e, ny, nz + dz + pz * a + dz * e, m);
                return new int[]{nx + dx * 2, ny, nz + dz * 2};
            }
        }
    }

    private static BlockFace face(int dx, int dz) { return dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH; }

    // ---------------- hra ----------------
    @Override protected void start() {
        for (Player p : players) { progress.put(p, 0); bestY.put(p, 0); p.teleport(cp(0)); }
        broadcast("&eJen &bnahoru&e! Zlaté bloky jsou &6checkpointy&e, když spadneš, vrátíš se na poslední. &aSmaragd&e na vrcholu = výhra!");
    }

    private Location cp(int i) { int[] c = cps.get(Math.min(i, cps.size() - 2)); return U.center(w, ox + c[0], oy + c[1] + 1, oz + c[2]).setDirection(new Vector(0, 0, 1)); }

    @Override public void onMove(Player p, Location to) {
        int rel = (int) Math.floor(to.getY()) - oy;
        if (rel > bestY.getOrDefault(p, 0)) bestY.put(p, rel);
        Block below = to.clone().subtract(0, 0.2, 0).getBlock();
        Material m = below.getType();
        if (m == Material.GOLD_BLOCK) {
            Integer idx = cpIndex.get(U.pack(below.getX(), below.getY(), below.getZ()));
            if (idx != null && idx > progress.getOrDefault(p, 0)) {
                progress.put(p, idx);
                U.title(p, "&6Checkpoint " + idx + "/" + CHECKPOINTS, "&7Jen tak dál!");
                U.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.4f);
            }
        } else if (m == Material.EMERALD_BLOCK) {
            broadcast("&a&l" + p.getName() + " &adoskákal na vrchol za &e" + fmt(time) + "&a!");
            end(List.of(p));
            return;
        }
        // pád hluboko pod poslední checkpoint = zpátky
        int[] c = cps.get(progress.getOrDefault(p, 0));
        if (to.getY() < oy + c[1] - 9) back(p);
    }

    private void back(Player p) {
        p.teleport(cp(progress.getOrDefault(p, 0)));
        p.setFallDistance(0);
        p.setVelocity(new Vector(0, 0, 0));
        U.title(p, "&c↓ Spadl jsi", "&7Zpět na checkpoint");
        U.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
    }

    @Override public void onVoid(Player p) { back(p); }

    @Override protected String status(Player p) {
        int y = (int) p.getLocation().getY() - oy;
        return "&bVýška: &f" + Math.max(0, y) + "&7/&f" + topY + " &8| &6Checkpoint: &f" + progress.getOrDefault(p, 0) + "&7/" + CHECKPOINTS + " &8| &7Čas: &e" + fmt(time);
    }

    @Override protected void timeUp() {
        int best = bestY.values().stream().max(Integer::compare).orElse(0);
        end(players.stream().filter(p -> bestY.getOrDefault(p, 0) == best).toList());
    }
}
