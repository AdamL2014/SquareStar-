package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** 3D efekty v lobby: obří létající nápis, obíhající raketa, NPC pro minihry, točící se bedny a dvojskok. */
public final class LobbyFx implements Listener {
    private final SquareStar pl;
    private record Pixel(Display d, int letter, Vector3f base, Vector3f scale) {}
    private final List<Pixel> title = new ArrayList<>();
    private final List<Display> rocket = new ArrayList<>();
    private final List<ItemDisplay> spinners = new ArrayList<>();
    private final Map<GameType, Mannequin> npcs = new EnumMap<>(GameType.class);
    private double phase, orbit;
    private float spin;
    private int tick;
    private Location titleCenter;
    private static final String TAG = "ss_fx";

    // 5x7 pixelové písmo
    private static final Map<Character, String[]> FONT = new HashMap<>();
    static {
        String[] d = {
                "A 01110 10001 10001 11111 10001 10001 10001", "B 11110 10001 10001 11110 10001 10001 11110", "C 01111 10000 10000 10000 10000 10000 01111",
                "D 11110 10001 10001 10001 10001 10001 11110", "E 11111 10000 10000 11110 10000 10000 11111", "F 11111 10000 10000 11110 10000 10000 10000",
                "G 01111 10000 10000 10011 10001 10001 01111", "H 10001 10001 10001 11111 10001 10001 10001", "I 11111 00100 00100 00100 00100 00100 11111",
                "J 00111 00010 00010 00010 00010 10010 01100", "K 10001 10010 10100 11000 10100 10010 10001", "L 10000 10000 10000 10000 10000 10000 11111",
                "M 10001 11011 10101 10101 10001 10001 10001", "N 10001 11001 10101 10011 10001 10001 10001", "O 01110 10001 10001 10001 10001 10001 01110",
                "P 11110 10001 10001 11110 10000 10000 10000", "Q 01110 10001 10001 10001 10101 10010 01101", "R 11110 10001 10001 11110 10100 10010 10001",
                "S 01111 10000 10000 01110 00001 00001 11110", "T 11111 00100 00100 00100 00100 00100 00100", "U 10001 10001 10001 10001 10001 10001 01110",
                "V 10001 10001 10001 10001 10001 01010 00100", "W 10001 10001 10001 10101 10101 10101 01010", "X 10001 10001 01010 00100 01010 10001 10001",
                "Y 10001 10001 01010 00100 00100 00100 00100", "Z 11111 00001 00010 00100 01000 10000 11111", "0 01110 10011 10101 10101 11001 10001 01110",
                "1 00100 01100 00100 00100 00100 00100 01110", "2 01110 10001 00001 00110 01000 10000 11111", "3 11110 00001 00001 01110 00001 00001 11110",
                "4 00010 00110 01010 10010 11111 00010 00010", "5 11111 10000 11110 00001 00001 10001 01110", "6 01110 10000 10000 11110 10001 10001 01110",
                "7 11111 00001 00010 00100 01000 01000 01000", "8 01110 10001 10001 01110 10001 10001 01110", "9 01110 10001 10001 01111 00001 00001 01110",
                "! 00100 00100 00100 00100 00100 00000 00100", "- 00000 00000 00000 11111 00000 00000 00000", "  00000 00000 00000 00000 00000 00000 00000"};
        for (String s : d) FONT.put(s.charAt(0), s.substring(2).split(" "));
    }

    public LobbyFx(SquareStar pl) { this.pl = pl; }

    public void spawnAll() {
        for (Entity e : pl.mars.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        title.clear(); rocket.clear(); spinners.clear(); npcs.clear();
        if (pl.getConfig().getBoolean("lobby.fx.title", true)) spawnTitle();
        if (pl.getConfig().getBoolean("lobby.fx.rocket", true)) spawnRocket();
        spawnNpcs();
        spawnSpinners();
    }

    // ---------------- obří 3D nápis ----------------
    private void spawnTitle() {
        String text = pl.getConfig().getString("lobby.title", "SQUARESTAR").toUpperCase(Locale.ROOT);
        float s = (float) pl.getConfig().getDouble("lobby.fx.title-scale", 1.4);
        int cols = text.length() * 6 - 1;
        titleCenter = new Location(pl.mars, 0.5, Lobby.Y + 26, 44.5);
        Material[] grad = {Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.ORANGE_CONCRETE, Material.RED_CONCRETE, Material.RED_CONCRETE, Material.RED_TERRACOTTA, Material.RED_TERRACOTTA};
        for (int li = 0; li < text.length(); li++) {
            String[] g = FONT.getOrDefault(text.charAt(li), FONT.get(' '));
            for (int r = 0; r < 7; r++) for (int c = 0; c < 5; c++) {
                if (g[r].charAt(c) != '1') continue;
                int col = li * 6 + c;
                // čteno od spawnu (hráč kouká na +Z, takže sloupce jdou do -X)
                float x = (cols / 2f - col - 1) * s, y = (6 - r) * s;
                add(li, new Vector3f(x, y, 0), new Vector3f(s, s, s * 1.2f), grad[r].createBlockData());
                add(li, new Vector3f(x - 0.12f * s, y - 0.18f * s, s * 0.6f), new Vector3f(s * 1.08f, s * 1.08f, s * 0.9f), Material.BLACK_CONCRETE.createBlockData());
            }
        }
    }

    private void add(int letter, Vector3f base, Vector3f scale, BlockData data) {
        BlockDisplay d = pl.mars.spawn(titleCenter, BlockDisplay.class, e -> {
            e.setBlock(data);
            e.setTransformation(new Transformation(base, new AxisAngle4f(), scale, new AxisAngle4f()));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(4f);
            e.addScoreboardTag(TAG);
            e.setPersistent(false);
        });
        title.add(new Pixel(d, letter, base, scale));
    }

    // ---------------- raketa ----------------
    private void spawnRocket() {
        Location c = new Location(pl.mars, 0.5, Lobby.Y + 30, 44.5);
        part(c, Material.WHITE_CONCRETE, -0.45f, -0.45f, -1.5f, 0.9f, 0.9f, 3f);
        part(c, Material.RED_CONCRETE, -0.35f, -0.35f, 1.5f, 0.7f, 0.7f, 0.6f);
        part(c, Material.RED_CONCRETE, -0.2f, -0.2f, 2.1f, 0.4f, 0.4f, 0.5f);
        part(c, Material.LIGHT_BLUE_STAINED_GLASS, -0.25f, 0.42f, 0.4f, 0.5f, 0.08f, 0.5f);
        part(c, Material.RED_CONCRETE, -0.05f, 0.45f, -1.5f, 0.1f, 0.6f, 0.8f);
        part(c, Material.RED_CONCRETE, -0.05f, -1.05f, -1.5f, 0.1f, 0.6f, 0.8f);
        part(c, Material.RED_CONCRETE, 0.45f, -0.05f, -1.5f, 0.6f, 0.1f, 0.8f);
        part(c, Material.RED_CONCRETE, -1.05f, -0.05f, -1.5f, 0.6f, 0.1f, 0.8f);
        part(c, Material.SHROOMLIGHT, -0.3f, -0.3f, -2.1f, 0.6f, 0.6f, 0.6f);
        part(c, Material.RED_CONCRETE, -0.47f, -0.47f, 0.9f, 0.94f, 0.94f, 0.15f);
        part(c, Material.RED_CONCRETE, -0.47f, -0.47f, -0.6f, 0.94f, 0.94f, 0.12f);
        part(c, Material.LIGHT_BLUE_STAINED_GLASS, -0.2f, 0.42f, -0.3f, 0.4f, 0.08f, 0.4f);
        part(c, Material.GRAY_CONCRETE, -0.38f, -0.38f, -1.8f, 0.76f, 0.76f, 0.3f);
        part(c, Material.LIGHT_GRAY_CONCRETE, 0.5f, -0.22f, -1.7f, 0.32f, 0.44f, 1.5f);
        part(c, Material.LIGHT_GRAY_CONCRETE, -0.82f, -0.22f, -1.7f, 0.32f, 0.44f, 1.5f);
        part(c, Material.RED_CONCRETE, 0.5f, -0.22f, -0.2f, 0.32f, 0.44f, 0.3f);
        part(c, Material.RED_CONCRETE, -0.82f, -0.22f, -0.2f, 0.32f, 0.44f, 0.3f);
        part(c, Material.ORANGE_CONCRETE, 0.55f, -0.17f, -2.0f, 0.22f, 0.34f, 0.3f);
        part(c, Material.ORANGE_CONCRETE, -0.77f, -0.17f, -2.0f, 0.22f, 0.34f, 0.3f);
    }

    private void part(Location c, Material m, float x, float y, float z, float sx, float sy, float sz) {
        BlockDisplay d = pl.mars.spawn(c, BlockDisplay.class, e -> {
            e.setBlock(m.createBlockData());
            e.setTransformation(new Transformation(new Vector3f(x * 2, y * 2, z * 2), new AxisAngle4f(), new Vector3f(sx * 2, sy * 2, sz * 2), new AxisAngle4f()));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(3);
            e.setViewRange(4f);
            e.addScoreboardTag(TAG);
            e.setPersistent(false);
        });
        rocket.add(d);
    }

    private Location orbitPos(double t) {
        double rx = 52, rz = 18;
        return new Location(pl.mars, 0.5 + Math.cos(t) * rx, Lobby.Y + 30 + Math.sin(t * 2) * 5, 44.5 + Math.sin(t) * rz);
    }

    // ---------------- NPC ----------------
    public static void npc(Mannequin m, String name, String desc, String look) {
        m.customName(U.c(name));
        m.setCustomNameVisible(true);
        m.setDescription(U.c(desc));
        m.setImmovable(true);
        m.setAI(false);
        m.setInvulnerable(true);
        m.setSilent(true);
        m.setCollidable(false);
        m.setPersistent(false);
        Color col = switch (look) { case "npc_shop" -> Color.ORANGE; default -> Color.WHITE; };
        suit(m, col, null);
    }

    private static void suit(Mannequin m, Color col, Material hand) {
        var eq = m.getEquipment();
        eq.setHelmet(dye(Material.LEATHER_HELMET, Color.WHITE));
        eq.setChestplate(dye(Material.LEATHER_CHESTPLATE, col));
        eq.setLeggings(dye(Material.LEATHER_LEGGINGS, col));
        eq.setBoots(dye(Material.LEATHER_BOOTS, Color.fromRGB(0x333333)));
        if (hand != null) eq.setItemInMainHand(new ItemStack(hand));
    }

    private static ItemStack dye(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(c);
        it.setItemMeta(lm);
        return it;
    }

    private static final Color[] COLORS = {Color.fromRGB(0xFF9F1C), Color.fromRGB(0x9ADCFF), Color.fromRGB(0xE63946), Color.fromRGB(0x8D99AE), Color.fromRGB(0xD62828),
            Color.fromRGB(0x6A040F), Color.fromRGB(0x2A9D8F), Color.fromRGB(0xFFD60A), Color.fromRGB(0x3A86FF), Color.fromRGB(0x8AC926), Color.fromRGB(0xFF006E)};

    private void spawnNpcs() {
        GameType[] t = GameType.values();
        int n = t.length;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(200 + 140.0 * i / Math.max(1, n - 1));
            double x = Math.cos(a) * 13, z = Math.sin(a) * 13;
            Location l = new Location(pl.mars, x + 0.5, Lobby.Y + 1, z + 0.5);
            l.setDirection(new Vector(-x, 0, -z));
            GameType g = t[i];
            Mannequin m = pl.mars.spawn(l, Mannequin.class, e -> {
                npc(e, "&6&l" + g.display, "&eKlikni pro hru", "game");
                suit(e, COLORS[g.ordinal() % COLORS.length], g.icon);
                e.addScoreboardTag(TAG);
                e.addScoreboardTag("ss_npc_game:" + g.name());
            });
            npcs.put(g, m);
            pl.mars.getBlockAt(l.getBlockX(), Lobby.Y, l.getBlockZ()).setType(Material.GOLD_BLOCK, false);
        }
    }

    // ---------------- točící se ikony nad bednami ----------------
    private void spawnSpinners() {
        Material[] icons = {Material.EMERALD, Material.DIAMOND, Material.NETHER_STAR};
        int i = 0;
        for (Crates.Type t : pl.crates.types.values()) {
            if (t.loc() == null) continue;
            Material icon = icons[Math.min(i++, icons.length - 1)];
            ItemDisplay d = pl.mars.spawn(t.loc().clone().add(0.5, 1.7, 0.5), ItemDisplay.class, e -> {
                e.setItemStack(new ItemStack(icon));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
                e.setBrightness(new Display.Brightness(15, 15));
                e.addScoreboardTag(TAG);
                e.setPersistent(false);
            });
            spinners.add(d);
        }
    }

    // ---------------- animace ----------------
    public void start() {
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (pl.mars.getPlayers().isEmpty()) return;
            tick++;
            if (!rocket.isEmpty()) {
                orbit += 0.012;
                Location p = orbitPos(orbit), next = orbitPos(orbit + 0.01);
                p.setDirection(next.toVector().subtract(p.toVector()));
                for (Display d : rocket) if (d.isValid()) d.teleport(p);
                Vector back = p.getDirection().multiply(-4.6);
                pl.mars.spawnParticle(Particle.FLAME, p.clone().add(back), 4, 0.15, 0.15, 0.15, 0.02);
                if (tick % 2 == 0) pl.mars.spawnParticle(Particle.CLOUD, p.clone().add(back.multiply(1.3)), 2, 0.2, 0.2, 0.2, 0.01);
            }
            if (tick % 10 == 0 && !title.isEmpty()) {
                phase += 0.55;
                for (Pixel px : title) {
                    if (!px.d().isValid()) continue;
                    float off = (float) Math.sin(phase + px.letter() * 0.55) * 1.1f;
                    px.d().setInterpolationDelay(0);
                    px.d().setInterpolationDuration(20);
                    px.d().setTransformation(new Transformation(new Vector3f(px.base().x, px.base().y + off, px.base().z), new AxisAngle4f(), px.scale(), new AxisAngle4f()));
                }
            }
            if (tick % 20 == 0) {
                spin += (float) Math.toRadians(120);
                for (ItemDisplay d : spinners) {
                    if (!d.isValid()) continue;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(40);
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(spin), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                }
                for (var e : npcs.entrySet()) {
                    if (!e.getValue().isValid()) continue;
                    int n = pl.games.playing(e.getKey());
                    e.getValue().setDescription(U.c((n > 0 ? "&a" + n + " hraje" : "&7nikdo nehraje") + " &8| &eKlikni"));
                }
            }
        }, 40, 2);
    }

    // ---------------- interakce ----------------
    private GameType npcGame(Entity e) {
        for (String t : e.getScoreboardTags()) if (t.startsWith("ss_npc_game:")) return GameType.valueOf(t.substring(12));
        return null;
    }

    @EventHandler public void clickNpc(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        GameType g = npcGame(e.getRightClicked());
        if (g == null) return;
        e.setCancelled(true);
        pl.games.join(e.getPlayer(), g);
    }

    @EventHandler public void hitNpc(EntityDamageByEntityEvent e) {
        GameType g = npcGame(e.getEntity());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) pl.games.join(p, g);
    }

    // ---------------- dvojskok ----------------
    private boolean canDouble(Player p) {
        return p.getWorld() == pl.mars && pl.games.of(p) == null && (p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SURVIVAL)
                && pl.getConfig().getBoolean("lobby.double-jump", true);
    }

    @SuppressWarnings("deprecation")
    @EventHandler public void ground(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!p.getAllowFlight() && p.isOnGround() && canDouble(p)) p.setAllowFlight(true);
    }

    @EventHandler public void doubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!canDouble(p) || !e.isFlying()) return;
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setVelocity(p.getLocation().getDirection().multiply(1.1).setY(0.85));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.05, 0.3, 0.05);
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 8, 0.2, 0.05, 0.2, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.4f);
    }
}
