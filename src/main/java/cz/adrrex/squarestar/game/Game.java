package cz.adrrex.squarestar.game;

import cz.adrrex.squarestar.SquareStar;
import cz.adrrex.squarestar.U;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Základ každé minihry: čekání, odpočet, průběh, konec a úklid mapy. */
public abstract class Game {
    public enum State { BUILDING, WAITING, COUNTDOWN, STARTING, RUNNING, ENDING }
    public enum Dmg { NONE, KNOCKBACK, NORMAL }

    protected final SquareStar pl;
    protected final GameManager gm;
    public final GameType type;
    public final int slot;
    public final World w;
    public final int ox, oy = 100, oz;
    public State state = State.BUILDING;
    public final List<Player> players = new ArrayList<>();
    public final Set<Player> alive = new LinkedHashSet<>();
    protected final Random rnd = new Random();
    protected final Set<Long> placed = new HashSet<>();
    protected final List<int[]> placedList = new ArrayList<>();
    protected final List<Entity> spawned = new ArrayList<>();
    protected final Map<Player, Player> lastHit = new HashMap<>();
    protected int countdown, time;
    public int map;
    final Map<Player, Integer> votes = new HashMap<>();
    private BukkitTask ticker;
    private boolean destroyed;

    protected Game(GameManager gm, GameType type, int slot) {
        this.pl = gm.pl; this.gm = gm; this.type = type; this.slot = slot; this.w = pl.world(type); this.ox = slot * 1000; this.oz = type.ordinal() * 1000;
        // každá minihra má vlastní oblast (řádek Z), takže se mapy různých her nikdy nepřekryjí
    }

    // ---------- co implementuje každá hra ----------
    protected abstract void buildMap(U.BlockQueue.Builder b);
    /** názvy variant mapy (hlasování) */
    public List<String> maps() { return List.of("Standard"); }
    protected abstract void start();
    protected void afterBuild() {}
    protected void second() {}
    protected int timeLimit() { return 600; }
    protected void timeUp() { end(new ArrayList<>(alive)); }
    protected GameMode mode() { return GameMode.ADVENTURE; }
    protected int voidY() { return oy - 40; }
    protected Dmg damage() { return Dmg.NONE; }
    protected boolean hunger() { return false; }
    protected boolean canDrop() { return false; }
    protected boolean hideNames() { return false; }
    public boolean canBreak(Player p, Block b) { return false; }
    public boolean canPlace(Player p, Block b) { return false; }
    public boolean allowFlow(Block to) { return true; }
    public void onDeath(Player p, Player killer) { eliminate(p, killer); }
    public void onVoid(Player p) { onDeath(p, lastHit.get(p)); }
    /** vrátí false = útok zrušit */
    public boolean onPvp(Player victim, Player attacker, EntityDamageByEntityEvent e) { return true; }
    public void onMove(Player p, Location to) {}
    public void onInteract(PlayerInteractEvent e) {}
    public void onInteractEntity(PlayerInteractEntityEvent e) {}
    public void onBlockDamage(BlockDamageEvent e) {}
    public void onProjectileHit(ProjectileHitEvent e) {}
    protected String status(Player p) { return status(); }
    protected String status() { return "&7Naživu: &a" + alive.size() + " &8| &7Čas: &e" + fmt(time); }

    // ---------- společná logika ----------
    public Location waitSpot() { return U.center(w, ox, oy + 46, oz); }
    protected Location specSpot() { return U.center(w, ox, oy + 15, oz); }
    public boolean joinable() { return (state == State.BUILDING || state == State.WAITING || state == State.COUNTDOWN) && players.size() < type.max; }
    public boolean isPlaced(Block b) { return placed.contains(U.pack(b.getX(), b.getY(), b.getZ())); }
    public void markPlaced(Block b) { if (placed.add(U.pack(b.getX(), b.getY(), b.getZ()))) placedList.add(new int[]{b.getX(), b.getY(), b.getZ()}); }
    public boolean owns(Location l) { return l.getWorld() == w && Math.abs(l.getBlockX() - ox) < 450 && Math.abs(l.getBlockZ() - oz) < 450; }

    void build() {
        U.BlockQueue.Builder b = pl.queue.builder(w);
        // čekárna nad mapou
        int y = oy + 45;
        b.fill(ox - 5, y, oz - 5, ox + 5, y, oz + 5, Material.LIGHT_BLUE_STAINED_GLASS);
        b.fill(ox - 5, y + 1, oz - 5, ox + 5, y + 4, oz - 5, Material.BARRIER);
        b.fill(ox - 5, y + 1, oz + 5, ox + 5, y + 4, oz + 5, Material.BARRIER);
        b.fill(ox - 5, y + 1, oz - 5, ox - 5, y + 4, oz + 5, Material.BARRIER);
        b.fill(ox + 5, y + 1, oz - 5, ox + 5, y + 4, oz + 5, Material.BARRIER);
        b.fill(ox - 5, y + 5, oz - 5, ox + 5, y + 5, oz + 5, Material.BARRIER);
        b.set(ox, y, oz, Material.SEA_LANTERN);
        record(b);
        b.submit(() -> {
            if (destroyed) return;
            state = State.WAITING;
            for (Player p : players) p.teleport(waitSpot());
            if (players.isEmpty()) destroy(); else checkStart();
        });
        ticker = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20, 20);
    }

    private void record(U.BlockQueue.Builder b) {
        for (Object[] e : b.entries()) { int bx = (int) e[0], by = (int) e[1], bz = (int) e[2]; if (placed.add(U.pack(bx, by, bz))) placedList.add(new int[]{bx, by, bz}); }
    }

    public void openVote(Player p) {
        GameManager.VoteHolder h = new GameManager.VoteHolder(this);
        h.inv = Bukkit.createInventory(h, 9, cz.adrrex.squarestar.Gui.vote(9, cz.adrrex.squarestar.Lang.t(p, "&f&lHlasování o mapě")));
        List<String> m = maps();
        Material[] icons = {Material.RED_TERRACOTTA, Material.LIGHT_BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.LIME_CONCRETE};
        for (int i = 0; i < m.size(); i++) {
            final int idx = i;
            long n = votes.values().stream().filter(v -> v == idx).count();
            h.inv.setItem(2 + i * 2, U.item(icons[i % icons.length], (int) Math.max(1, n), "&e&l" + m.get(i), "&7Hlasů: &a" + n, votes.get(p) != null && votes.get(p) == i ? "&a✔ Tvůj hlas" : "&eKlikni pro hlas"));
        }
        p.openInventory(h.inv);
    }

    void vote(Player p, int slot) {
        int i = (slot - 2) / 2;
        if (slot < 2 || (slot - 2) % 2 != 0 || i >= maps().size()) return;
        if (state != State.WAITING && state != State.COUNTDOWN) { U.msg(p, "&cHlasování už skončilo."); return; }
        votes.put(p, i);
        U.msg(p, "&aHlasuješ pro mapu &e" + maps().get(i));
        U.sound(p, Sound.UI_BUTTON_CLICK, 1.5f);
        openVote(p);
    }

    void addPlayer(Player p) {
        players.add(p);
        U.reset(p, GameMode.ADVENTURE);
        p.getInventory().setItem(8, U.tag(U.item(Material.RED_BED, 1, "&c&lOpustit hru &7(pravý klik)"), "ss_game", "leave"));
        if (maps().size() > 1) p.getInventory().setItem(4, U.tag(U.item(Material.MAP, 1, "&e&lHlasování o mapě &7(pravý klik)", "&7Mapy: &f" + String.join("&7, &f", maps())), "ss_game", "vote"));
        if (state == State.BUILDING) U.msg(p, "&ePřipravuji čekárnu... &7(" + type.display + ")");
        else p.teleport(waitSpot());
        if (hideNames()) gm.hideName(p, true);
        pl.applyGravity(p);
        broadcast("&a" + p.getName() + " &7se připojil &8(" + players.size() + "/" + type.max + ")");
        checkStart();
    }

    void removePlayer(Player p, boolean toLobby) {
        players.remove(p);
        votes.remove(p);
        boolean wasAlive = alive.remove(p);
        gm.map.remove(p);
        gm.hideName(p, false);
        onLeave(p);
        if (toLobby && p.isOnline()) pl.lobby.send(p);
        if (state == State.ENDING) return;
        if (players.isEmpty() && state != State.BUILDING) { destroy(); return; }
        if (state == State.RUNNING && wasAlive) { broadcast("&c" + p.getName() + " &7odešel ze hry."); checkWin(); }
    }

    protected void onLeave(Player p) {}

    private void checkStart() {
        if (state == State.WAITING && players.size() >= type.min) { state = State.COUNTDOWN; countdown = pl.getConfig().getInt("countdown", 15); }
    }

    private void tick() {
        switch (state) {
            case WAITING -> { for (Player p : players) U.bar(p, "&eČekáme na hráče &f" + players.size() + "&7/&f" + type.min + " &8| &6" + type.display); }
            case COUNTDOWN -> {
                if (players.size() < type.min) { state = State.WAITING; broadcast("&cMálo hráčů, odpočet zrušen."); return; }
                if (players.size() >= type.max && countdown > 5) countdown = 5;
                for (Player p : players) {
                    U.bar(p, "&aStart za &f" + countdown + " s &8| &6" + type.display);
                    if (countdown <= 5) { U.title(p, "&6" + countdown, "&7" + type.display); U.sound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.5f); }
                }
                if (--countdown < 0) begin();
            }
            case STARTING -> { for (Player p : players) U.bar(p, "&eStavím mapu &6" + maps().get(map) + "&e..."); }
            case RUNNING -> {
                time++;
                second();
                if (state != State.RUNNING) return;
                if (time >= timeLimit()) { broadcast("&eČas vypršel!"); timeUp(); return; }
                for (Player p : players) U.bar(p, status(p));
            }
            default -> {}
        }
    }

    private void begin() {
        List<String> m = maps();
        int[] count = new int[m.size()];
        for (int v : votes.values()) if (v < count.length) count[v]++;
        int best = 0;
        for (int i = 1; i < count.length; i++) if (count[i] > count[best] || (count[i] == count[best] && rnd.nextBoolean())) best = i;
        map = best;
        state = State.STARTING;
        if (m.size() > 1) broadcast("&eVyhrála mapa: &6&l" + m.get(map) + " &7(" + count[map] + " hlasů)");
        U.BlockQueue.Builder b = pl.queue.builder(w);
        buildMap(b);
        record(b);
        b.submit(() -> {
            if (destroyed || state != State.STARTING) return;
            if (players.size() < Math.min(type.min, 1)) { destroy(); return; }
            afterBuild();
            launch();
        });
    }

    private void launch() {
        state = State.RUNNING; time = 0;
        alive.addAll(players);
        for (Player p : players) {
            U.reset(p, mode());
            pl.applyGravity(p);
            U.title(p, "&a&lSTART!", "&7" + type.display);
            U.sound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f);
        }
        start();
    }

    public void eliminate(Player p, Player killer) {
        if (!alive.remove(p)) return;
        if (killer != null && killer != p) broadcast("&c☠ " + p.getName() + " &7byl vyřazen hráčem &c" + killer.getName());
        else broadcast("&c☠ " + p.getName() + " &7je vyřazen!");
        if (canDrop()) for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() != Material.AIR) w.dropItemNaturally(p.getLocation(), it);
        U.reset(p, GameMode.SPECTATOR);
        p.teleport(specSpot());
        U.title(p, "&c&lVYŘAZEN", "&7Sleduješ hru. &e/ss leave &7= odejít");
        checkWin();
    }

    protected void checkWin() {
        if (state != State.RUNNING) return;
        if (alive.size() <= 1) end(new ArrayList<>(alive));
    }

    protected void end(List<Player> winners) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        String names = winners.isEmpty() ? "&7nikdo" : String.join("&7, &a", winners.stream().map(Player::getName).toList());
        for (Player p : players) {
            boolean win = winners.contains(p);
            U.title(p, win ? "&6&l✦ VÍTĚZSTVÍ ✦" : "&c&lKONEC HRY", "&7Vyhrál: &a" + names);
            U.sound(p, win ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 1f);
        }
        for (Player p : players) pl.levels.add(p, winners.contains(p) ? 100 : 25, winners.contains(p) ? "výhra" : "odehraná hra");
        String keyType = pl.getConfig().getString("win-reward.key", "common");
        int amount = pl.getConfig().getInt("win-reward.amount", 1);
        for (Player p : winners) {
            ItemStack k = pl.crates.key(keyType, amount);
            if (k != null) gm.pendingRewards.computeIfAbsent(p.getUniqueId(), x -> new ArrayList<>()).add(k);
            U.msg(p, "&aVyhrál jsi! Dostaneš &e" + amount + "x klíč &ana spawnu.");
            for (int i = 0; i < 3; i++) {
                final int d = i;
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (!p.isOnline() || p.getWorld() != w) return;
                    Firework fw = w.spawn(p.getLocation().add(rnd.nextInt(5) - 2, 1, rnd.nextInt(5) - 2), Firework.class, f -> {
                        var meta = f.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.values()[(d + rnd.nextInt(3)) % FireworkEffect.Type.values().length])
                                .withColor(Color.RED, Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).trail(true).flicker(true).build());
                        meta.setPower(1);
                        f.setFireworkMeta(meta);
                    });
                    spawned.add(fw);
                }, 10L + i * 15L);
            }
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (Player p : new ArrayList<>(players)) removePlayer(p, true);
            destroy();
        }, 120);
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        state = State.ENDING;
        if (ticker != null) ticker.cancel();
        for (Player p : new ArrayList<>(players)) removePlayer(p, true);
        cleanupEntities();
        U.BlockQueue.Builder b = pl.queue.builder(w);
        for (int[] c : placedList) b.set(c[0], c[1], c[2], Material.AIR);
        b.submit(() -> gm.free(this));
    }

    void destroyNow() {
        destroyed = true;
        if (ticker != null) ticker.cancel();
        for (Player p : new ArrayList<>(players)) removePlayer(p, true);
        cleanupEntities();
        for (int[] c : placedList) w.getBlockAt(c[0], c[1], c[2]).setType(Material.AIR, false);
    }

    protected void onDestroy() {}

    private void cleanupEntities() {
        onDestroy();
        for (Entity e : spawned) if (e.isValid()) e.remove();
        for (Entity e : w.getEntities()) if (!(e instanceof Player) && owns(e.getLocation())) e.remove();
    }

    protected <T extends Entity> T spawn(Location l, Class<T> c, java.util.function.Consumer<T> fn) {
        T e = w.spawn(l, c, en -> { en.setPersistent(false); fn.accept(en); });
        spawned.add(e);
        return e;
    }

    public void broadcast(String s) { for (Player p : players) U.msg(p, s); }
    protected static String fmt(int s) { return String.format("%d:%02d", s / 60, s % 60); }
    protected Location loc(double x, double y, double z) { return new Location(w, ox + x, oy + y, oz + z); }
}
