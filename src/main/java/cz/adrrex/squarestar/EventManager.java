package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * /event – majitel (OWNER) nebo admin uspořádá event: vybere minihru, max. počet hráčů a gravitaci,
 * pak ho oznámí všem (kliknutelná zpráva), hráči se připojí přes /event join a majitel ho spustí.
 */
public final class EventManager implements CommandExecutor, TabCompleter, Listener {
    private final SquareStar pl;
    private GameType type = GameType.PVP;
    private int max = 10;
    private boolean lowGravity = true;
    private Game game;
    private String host;
    /** GAME = minihra, ROOM = bedrocková místnost, BUILD = builder event (každý svůj pozemek) */
    enum Mode { GAME, ROOM, BUILD }
    private Mode mode = Mode.GAME;
    /** vlastní event (ROOM/BUILD) – běží */
    private boolean custom, started;
    private World world;
    private final List<UUID> joined = new ArrayList<>();
    private final Map<UUID, int[]> plotOf = new HashMap<>();
    private static final int BY = 100, PLOT = 21, PITCH = 26, H = 40;

    public EventManager(SquareStar pl) { this.pl = pl; }

    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    private boolean allowed(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        var r = pl.ranks.of(p);
        return p.isOp() || r == Ranks.Rank.OWNER || r == Ranks.Rank.ADMIN || r == Ranks.Rank.DEVELOPER;
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("leave") || sub.equals("odejit")) {
            if (s instanceof Player p && joined.contains(p.getUniqueId())) pl.lobby.send(p);
            return true;
        }
        if (sub.equals("join") || sub.equals("pripojit")) {
            if (!(s instanceof Player p)) return true;
            if (custom) { joinCustom(p); return true; }
            if (game == null || !game.joinable()) { U.msg(p, "&cTeď neběží žádný event, ke kterému se dá připojit."); return true; }
            if (!pl.games.joinEvent(p, game)) U.msg(p, "&cNejdřív opusť hru: &e/ss leave");
            else U.msg(p, "&d&l★ &aJsi v eventu! &7Počkej, až ho " + host + " spustí.");
            return true;
        }
        if (!allowed(s)) { s.sendMessage(U.tc(s, "&cEventy může pořádat jen majitel nebo admin. &7Připojit se: &e/event join")); return true; }
        switch (sub) {
            case "", "menu" -> { if (s instanceof Player p) open(p); else s.sendMessage(U.tc(s, "&7/event start|stop|join")); }
            case "start" -> start(s);
            case "stop" -> stop(s);
            default -> s.sendMessage(U.tc(s, "&7/event &8- menu, &7/event start&8, &7/event stop&8, &7/event join"));
        }
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (a.length != 1) return List.of();
        List<String> o = allowed(s) ? List.of("join", "leave", "start", "stop", "menu") : List.of("join", "leave");
        return o.stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
    }

    // ---------------- menu ----------------
    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Gui.title(54, Lang.t(p, "&f&l★ Pořádání eventu")));
        GameType[] t = GameType.values();
        for (int i = 0; i < t.length && i < 27; i++) {
            ItemStack it = Gui.skin(p, U.item(t[i].icon, 1, (t[i] == type ? "&a&l✔ " : "&e") + t[i].display, "&7" + t[i].desc, "", t[i] == type ? "&aVybráno" : "&eKlikni pro výběr"), "icon_" + t[i].name().toLowerCase(Locale.ROOT));
            if (t[i] == type && mode == Mode.GAME) { var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(i, U.tag(it, "ss_ev", "type:" + t[i].name()));
        }
        ItemStack room = U.item(Material.BEDROCK, 1, (mode == Mode.ROOM ? "&a&l✔ " : "&f&l") + "Bedrocková místnost", "&7Všichni jsou spolu zavření", "&7v bedrockové místnosti.", "&7Ty jim tam něco uspořádáš.", "", mode == Mode.ROOM ? "&aVybráno" : "&eKlikni pro výběr");
        ItemStack build = U.item(Material.BRICKS, 1, (mode == Mode.BUILD ? "&a&l✔ " : "&6&l") + "Builder event", "&7Každý hráč dostane svůj", "&7pozemek a staví v kreativu.", "&7Okraje nejdou rozbít.", "", mode == Mode.BUILD ? "&aVybráno" : "&eKlikni pro výběr");
        for (ItemStack it : List.of(room, build)) if (it.getItemMeta().getDisplayName().contains("✔")) { var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
        h.inv.setItem(30, U.tag(room, "ss_ev", "mode:ROOM"));
        h.inv.setItem(32, U.tag(build, "ss_ev", "mode:BUILD"));
        h.inv.setItem(37, U.tag(U.item(Material.RED_CONCRETE, 1, "&c&l- 1 hráč", "&7Shift = -5"), "ss_ev", "minus"));
        h.inv.setItem(38, U.item(Material.PLAYER_HEAD, Math.max(1, Math.min(64, max)), "&f&lMax. hráčů: &e" + max, "&7Kolik lidí se může připojit"));
        h.inv.setItem(39, U.tag(U.item(Material.LIME_CONCRETE, 1, "&a&l+ 1 hráč", "&7Shift = +5"), "ss_ev", "plus"));
        h.inv.setItem(41, U.tag(U.item(lowGravity ? Material.FEATHER : Material.ANVIL, 1, "&b&lGravitace: " + (lowGravity ? "&aNízká (Mars)" : "&cNormální (Země)"), "&7Klikni pro přepnutí"), "ss_ev", "gravity"));
        boolean running = game != null || custom;
        h.inv.setItem(43, U.tag(U.item(running ? Material.ORANGE_CONCRETE : Material.BELL, 1, running ? "&6&l▶ SPUSTIT EVENT" : "&d&l✦ OZNÁMIT EVENT",
                running ? "&7Přihlášeno: &e" + (custom ? joined.size() : game.players.size()) + "&7/" + max : "&7Pošle všem pozvánku", running ? (custom ? "&7Klikni = start (" + (mode == Mode.BUILD ? "stavění" : "místnost") + ")" : "&7Klikni = start hry") : "&7Hráči se připojí přes &e/event join"), "ss_ev", running ? "start" : "announce"));
        if (running) h.inv.setItem(49, U.tag(U.item(Material.BARRIER, 1, "&c&lZrušit event"), "ss_ev", "stop"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_ev");
        if (t == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        boolean busy = game != null || custom;
        if (t.startsWith("type:")) { if (!busy) { type = GameType.valueOf(t.substring(5)); mode = Mode.GAME; max = Math.min(max, 50); } else U.msg(p, "&cEvent už běží – nejdřív ho zruš."); }
        else if (t.startsWith("mode:")) { if (!busy) mode = Mode.valueOf(t.substring(5)); else U.msg(p, "&cEvent už běží – nejdřív ho zruš."); }
        else switch (t) {
            case "minus" -> max = Math.max(1, max - (e.isShiftClick() ? 5 : 1));
            case "plus" -> max = Math.min(100, max + (e.isShiftClick() ? 5 : 1));
            case "gravity" -> lowGravity = !lowGravity;
            case "announce" -> announce(p);
            case "start" -> { p.closeInventory(); start(p); return; }
            case "stop" -> { p.closeInventory(); stop(p); return; }
        }
        open(p);
    }

    // ---------------- NPC eventu před bednami ----------------
    private org.bukkit.entity.Mannequin npc;
    private void spawnNpc() {
        removeNpc();
        var l = new org.bukkit.Location(pl.mars, 0.5, Lobby.Y + 1, 6.0, 180, 0);
        npc = pl.mars.spawn(l, org.bukkit.entity.Mannequin.class, m -> {
            LobbyFx.npc(m, "&d&l★ EVENT ★ &6" + eventName(), "&eKlikni = připojit se", "game");
            m.addScoreboardTag("ss_event_npc");
            m.setGlowing(true);
        });
    }
    private void removeNpc() {
        if (npc != null) npc.remove();
        npc = null;
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_event_npc")) e.remove();
    }
    @EventHandler public void npcClick(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains("ss_event_npc")) return;
        e.setCancelled(true);
        e.getPlayer().performCommand("event join");
    }
    @EventHandler public void npcHit(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains("ss_event_npc")) e.setCancelled(true);
    }

    private String eventName() { return mode == Mode.ROOM ? "Bedrocková místnost" : mode == Mode.BUILD ? "Builder event" : type.display; }

    private void announce(Player p) {
        if (game != null || custom) return;
        if (mode != Mode.GAME) {
            U.msg(p, "&7Připravuji svět eventu...");
            prepareWorld(() -> { host = p.getName(); custom = true; started = false; invite(); joinCustom(p); });
            return;
        }
        game = pl.games.createEvent(type, max, lowGravity ? null : 0.08);
        if (game == null) { U.msg(p, "&cVšechny arény jsou plné, zkus to za chvíli."); return; }
        host = p.getName();
        invite();
        if (!pl.games.joinEvent(p, game)) U.msg(p, "&7(Ty jsi zůstal mimo – hraješ jinou hru.)");
    }

    private void invite() {
        spawnNpc();
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.sendMessage(U.c(""));
            o.sendMessage(U.c(Lang.t(o, "&d&l★ EVENT ★ &f" + host + " &7pořádá event: &6&l" + eventName())));
            o.sendMessage(U.c(Lang.t(o, "&7Max. hráčů: &e" + max + " &8| &7Gravitace: " + (lowGravity ? "&anízká" : "&cnormální"))));
            o.sendMessage(U.c(Lang.t(o, "&a&l[ KLIKNI A PŘIPOJ SE ]")).clickEvent(ClickEvent.runCommand("/event join"))
                    .hoverEvent(HoverEvent.showText(U.c("&e/event join"))));
            o.sendMessage(U.c(Lang.t(o, "&7…nebo klikni na &d&lEVENT NPC &7před bednami na spawnu!")));
            o.sendMessage(U.c(""));
            U.title(o, "&d&l★ EVENT ★", "&6" + eventName() + " &7– napiš &e/event join");
            o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
        }
        spawnNpc();
    }

    private void start(CommandSender s) {
        if (custom) {
            started = true;
            removeNpc();
            for (UUID u : joined) { Player o = Bukkit.getPlayer(u); if (o == null) continue;
                U.title(o, mode == Mode.BUILD ? "&6&l✦ STAVĚJTE! ✦" : "&d&l★ EVENT ZAČÍNÁ ★", mode == Mode.BUILD ? "&7Tvůj pozemek je tvůj – ukaž, co umíš!" : "&7Poslouchej " + host);
                o.playSound(o.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
                if (mode == Mode.BUILD) o.setGameMode(GameMode.CREATIVE); }
            U.all("&d&l★ EVENT &6" + eventName() + " &dzačal! &7(" + joined.size() + " hráčů)");
            return;
        }
        if (game == null) { s.sendMessage(U.tc(s, "&cŽádný event. Otevři menu: &e/event")); return; }
        if (game.players.size() < game.type.min) { s.sendMessage(U.tc(s, "&cMálo hráčů: &e" + game.players.size() + "&c (min. " + game.type.min + ")")); return; }
        pl.games.startEvent(game);
        U.all("&d&l★ EVENT &6" + game.type.display + " &dzačíná! &7(" + game.players.size() + " hráčů)");
        game = null;
        removeNpc();
    }

    private void stop(CommandSender s) {
        if (custom) {
            custom = false; started = false;
            for (UUID u : new ArrayList<>(joined)) { Player o = Bukkit.getPlayer(u); if (o != null) pl.lobby.send(o); }
            joined.clear(); plotOf.clear();
            removeNpc();
            U.all("&c&l★ Event skončil. &7Díky všem!");
            Bukkit.getScheduler().runTaskLater(pl, this::dropWorld, 40);
            return;
        }
        if (game == null) { s.sendMessage(U.tc(s, "&7Žádný event neběží.")); return; }
        for (Player p : new ArrayList<>(game.players)) pl.games.leave(p);
        game = null;
        removeNpc();
        U.all("&c&l★ Event byl zrušen.");
    }

    // ================= vlastní eventy: bedrocková místnost / builder event =================
    public boolean inCustom(Player p) { return custom && world != null && p.getWorld() == world; }
    public double gravity() { return lowGravity ? pl.getConfig().getDouble("gravity.value", 0.03) : 0.08; }

    private void dropWorld() {
        if (world == null) return;
        for (Player p : world.getPlayers()) pl.lobby.send(p);
        String n = world.getName();
        Bukkit.unloadWorld(world, false);
        world = null;
        U.deleteDir(new java.io.File(Bukkit.getWorldContainer(), n));
    }

    private void prepareWorld(Runnable done) {
        dropWorld();
        world = new WorldCreator("ss_event").generator(new org.bukkit.generator.ChunkGenerator() {}).generateStructures(false).createWorld();
        if (world == null) return;
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false); world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false); world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setTime(18000);
        U.BlockQueue.Builder b = pl.queue.builder(world);
        if (mode == Mode.ROOM) {
            int r = 15, h = 12;
            b.fill(-r - 1, BY, -r - 1, r + 1, BY + h + 1, r + 1, Material.BEDROCK);
            b.fill(-r, BY + 1, -r, r, BY + h, r, Material.AIR);
            for (int x = -r; x <= r; x += 5) for (int z = -r; z <= r; z += 5) b.set(x, BY + h + 1, z, Material.SEA_LANTERN);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) b.set(x, BY, z, Math.abs(x) + Math.abs(z) <= 2 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE);
            b.set(0, BY, 0, Material.SEA_LANTERN);
        } else {
            int n = (int) Math.ceil(Math.sqrt(Math.max(1, max)));
            for (int i = 0; i < n * n; i++) {
                int ox = (i % n) * PITCH, oz = (i / n) * PITCH, half = PLOT / 2;
                b.fill(ox - half - 1, BY, oz - half - 1, ox + half + 1, BY, oz + half + 1, Material.BEDROCK);          // okraj
                b.fill(ox - half, BY, oz - half, ox + half, BY, oz + half, Material.SMOOTH_RED_SANDSTONE);
                for (int[] w : new int[][]{{-half - 1, -half - 1, half + 1, -half - 1}, {-half - 1, half + 1, half + 1, half + 1}, {-half - 1, -half - 1, -half - 1, half + 1}, {half + 1, -half - 1, half + 1, half + 1}})
                    b.fill(ox + w[0], BY + 1, oz + w[1], ox + w[2], BY + H, oz + w[3], Material.BARRIER);              // neviditelné stěny
                b.fill(ox - half - 1, BY + H + 1, oz - half - 1, ox + half + 1, BY + H + 1, oz + half + 1, Material.BARRIER);
                b.set(ox, BY, oz - half - 1, Material.SEA_LANTERN);
            }
        }
        b.submit(done);
    }

    private void joinCustom(Player p) {
        if (!custom || world == null) { U.msg(p, "&cTeď neběží žádný event."); return; }
        if (joined.contains(p.getUniqueId())) { U.msg(p, "&7Už jsi v eventu."); return; }
        if (pl.games.of(p) != null) { U.msg(p, "&cNejdřív opusť hru: &e/ss leave"); return; }
        if (joined.size() >= max) { U.msg(p, "&cEvent je plný (" + max + ")."); return; }
        if (p.getWorld() == pl.mars) pl.lobby.saveInv(p);
        joined.add(p.getUniqueId());
        Location to;
        if (mode == Mode.BUILD) {
            int i = 0;
            Set<Integer> used = new HashSet<>(); for (int[] v : plotOf.values()) used.add(v[2]);
            while (used.contains(i)) i++;
            int n = (int) Math.ceil(Math.sqrt(Math.max(1, max)));
            int[] pos = {(i % n) * PITCH, (i / n) * PITCH, i};
            plotOf.put(p.getUniqueId(), pos);
            to = new Location(world, pos[0] + 0.5, BY + 1, pos[1] + 0.5);
            U.reset(p, started ? GameMode.CREATIVE : GameMode.ADVENTURE);
            U.msg(p, "&6&lBuilder event! &7Tohle je tvůj pozemek. " + (started ? "&aStavěj!" : "&eStavět můžeš, až " + host + " event spustí."));
        } else {
            to = new Location(world, 0.5, BY + 1, 0.5);
            U.reset(p, GameMode.ADVENTURE);
            U.msg(p, "&d&lJsi v bedrockové místnosti! &7Počkej na pokyny od " + host + ".");
        }
        p.getInventory().clear();
        p.getInventory().setItem(8, U.tag(U.item(Material.RED_BED, 1, "&c&lOpustit event &7(pravý klik)"), "ss_evleave", "1"));
        p.teleport(to);
        pl.applyGravity(p);
        for (UUID u : joined) { Player o = Bukkit.getPlayer(u); if (o != null) U.msg(o, "&a" + p.getName() + " &7přišel na event &8(" + joined.size() + "/" + max + ")"); }
        if (Bukkit.getPlayerExact(host) == p && mode == Mode.BUILD) p.setGameMode(GameMode.CREATIVE);
    }

    private boolean mayBuild(Player p, org.bukkit.block.Block b) {
        if (p.isOp() && p.isSneaking()) return true;
        if (mode != Mode.BUILD || !started || b.getType() == Material.BARRIER || b.getType() == Material.BEDROCK) return false;
        int[] pos = plotOf.get(p.getUniqueId());
        if (pos == null) return false;
        int half = PLOT / 2;
        return Math.abs(b.getX() - pos[0]) <= half && Math.abs(b.getZ() - pos[1]) <= half && b.getY() >= BY && b.getY() <= BY + H;
    }
    @EventHandler public void evBreak(org.bukkit.event.block.BlockBreakEvent e) { if (world != null && e.getBlock().getWorld() == world && !mayBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true); }
    @EventHandler public void evPlace(org.bukkit.event.block.BlockPlaceEvent e) { if (world != null && e.getBlock().getWorld() == world && (!mayBuild(e.getPlayer(), e.getBlock()) || e.getBlock().getY() == BY && e.getBlockReplacedState().getType() == Material.BEDROCK)) e.setCancelled(true); }
    @EventHandler public void evDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (world != null && e.getEntity().getWorld() == world && e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) e.setCancelled(true);
    }
    @EventHandler public void evUse(org.bukkit.event.player.PlayerInteractEvent e) {
        if ("1".equals(U.tagOf(e.getItem(), "ss_evleave"))) { e.setCancelled(true); pl.lobby.send(e.getPlayer()); }
    }
    @EventHandler public void evMove(org.bukkit.event.player.PlayerMoveEvent e) {
        if (world == null || e.getTo().getWorld() != world || e.getTo().getY() > BY - 20) return;
        Player p = e.getPlayer(); int[] pos = plotOf.get(p.getUniqueId());
        p.teleport(pos == null ? new Location(world, 0.5, BY + 1, 0.5) : new Location(world, pos[0] + 0.5, BY + 1, pos[1] + 0.5));
    }
    @EventHandler public void evWorld(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (world != null && e.getFrom() == world) { joined.remove(e.getPlayer().getUniqueId()); plotOf.remove(e.getPlayer().getUniqueId()); }
    }
    @EventHandler public void evQuit(org.bukkit.event.player.PlayerQuitEvent e) { joined.remove(e.getPlayer().getUniqueId()); plotOf.remove(e.getPlayer().getUniqueId()); }
}
