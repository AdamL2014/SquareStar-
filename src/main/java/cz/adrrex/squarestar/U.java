package cz.adrrex.squarestar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Pomocné funkce: texty, itemy, fronta stavění bloků. */
public final class U {
    private U() {}
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();
    public static final String PREFIX = "&8[&c✦ &6SquareStar&8] &7";

    public static Component c(String s) { return LEG.deserialize(s).decoration(TextDecoration.ITALIC, false); }
    /** text pro příjemce příkazu – hráči se přeloží podle jeho jazyka */
    public static Component tc(org.bukkit.command.CommandSender to, String s) { return c(to instanceof Player p ? Lang.t(p, s) : s); }
    public static void msg(Player p, String s) { p.sendMessage(c(PREFIX + Lang.t(p, s))); }
    public static void bar(Player p, String s) { p.sendActionBar(c(Lang.t(p, s))); }
    public static void title(Player p, String t, String sub) {
        p.showTitle(Title.title(c(Lang.t(p, t)), c(Lang.t(p, sub)), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))));
    }
    public static NamespacedKey key(String k) { return new NamespacedKey(SquareStar.I, k); }

    public static ItemStack item(Material m, int amount, String name, String... lore) {
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (name != null) meta.displayName(c(name));
        if (lore.length > 0) { List<Component> l = new ArrayList<>(); for (String s : lore) l.add(c(s)); meta.lore(l); }
        it.setItemMeta(meta);
        return it;
    }
    public static ItemStack tag(ItemStack it, String key, String val) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, val);
        it.setItemMeta(m);
        return it;
    }
    public static String tagOf(ItemStack it, String key) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key(key), PersistentDataType.STRING);
    }

    public static void reset(Player p, GameMode gm) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(gm);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        var kbr = p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE); if (kbr != null) kbr.setBaseValue(0);
        p.setHealth(Objects.requireNonNull(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).getValue());
        p.setFoodLevel(20); p.setSaturation(10); p.setFireTicks(0); p.setFallDistance(0);
        p.setLevel(0); p.setExp(0); p.setWalkSpeed(0.2f);
        boolean fly = gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
        p.setAllowFlight(fly); if (!fly) p.setFlying(false);
        p.eject();
    }

    public static long pack(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }

    /** Fronta bloků: staví mapy postupně, aby server nelagoval. */
    public static final class BlockQueue extends BukkitRunnable {
        private record Job(World w, int[] xyz, List<BlockData> data, Runnable done) {}
        private final Deque<Job> jobs = new ArrayDeque<>();
        private Job cur; private int idx;
        private final int perTick;
        public BlockQueue(int perTick) { this.perTick = perTick; }

        public Builder builder(World w) { return new Builder(w); }

        @Override public void run() {
            int n = 0;
            while (n < perTick) {
                if (cur == null) { cur = jobs.poll(); idx = 0; if (cur == null) return; }
                int total = cur.data.size();
                while (idx < total && n < perTick) {
                    int i = idx * 3;
                    cur.w.getBlockAt(cur.xyz[i], cur.xyz[i + 1], cur.xyz[i + 2]).setBlockData(cur.data.get(idx), false);
                    idx++; n++;
                }
                if (idx >= total) { Runnable d = cur.done; cur = null; if (d != null) d.run(); }
            }
        }

        public final class Builder {
            private final World w; private final LinkedHashMap<Long, Object[]> map = new LinkedHashMap<>();
            private final Map<Material, BlockData> cache = new HashMap<>();
            Builder(World w) { this.w = w; }
            /** záměna bloků pro barevné varianty map (null = beze změny) */
            public Map<Material, Material> remap;
            public Builder set(int x, int y, int z, Material m) { if (remap != null) m = remap.getOrDefault(m, m); return set(x, y, z, cache.computeIfAbsent(m, Material::createBlockData)); }
            public Builder set(int x, int y, int z, BlockData d) { map.put(pack(x, y, z), new Object[]{x, y, z, d}); return this; }
            public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
                return this;
            }
            public Builder sphere(int cx, int cy, int cz, int r, Consumer<int[]> fn) {
                for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++)
                    if (x * x + y * y + z * z <= r * r + r) fn.accept(new int[]{cx + x, cy + y, cz + z});
                return this;
            }
            public Collection<Object[]> entries() { return map.values(); }
            public void submit(Runnable done) {
                int[] xyz = new int[map.size() * 3]; List<BlockData> data = new ArrayList<>(map.size()); int i = 0;
                for (Object[] e : map.values()) { xyz[i++] = (int) e[0]; xyz[i++] = (int) e[1]; xyz[i++] = (int) e[2]; data.add((BlockData) e[3]); }
                jobs.add(new Job(w, xyz, data, done));
            }
        }
    }

    public static <T> T pick(Random r, List<T> l) { return l.get(r.nextInt(l.size())); }
    public static Location center(World w, int x, int y, int z) { return new Location(w, x + 0.5, y, z + 0.5); }
    public static void sound(Player p, Sound s, float pitch) { p.playSound(p.getLocation(), s, 1f, pitch); }

    /** smaže složku i s obsahem (jen nenačtené světy!) */
    public static void deleteDir(java.io.File f) {
        if (!f.exists()) return;
        java.io.File[] k = f.listFiles();
        if (k != null) for (java.io.File c : k) deleteDir(c);
        f.delete();
    }
    /** zpráva všem online hráčům, každému v jeho jazyce */
    public static void all(String s) {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) p.sendMessage(c(Lang.t(p, s)));
        org.bukkit.Bukkit.getConsoleSender().sendMessage(c(s));
    }
}
