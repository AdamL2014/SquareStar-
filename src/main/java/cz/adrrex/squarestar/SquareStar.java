package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.GameManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public final class SquareStar extends JavaPlugin {
    public static SquareStar I;
    public World mars, arenas;
    public U.BlockQueue queue;
    public GameManager games;
    public SpaceItems items;
    public Crates crates;
    public Intro intro;
    public Levels levels;
    public RedstoneWeek redstone;
    public Motd motd;
    public Pack pack;
    public Lobby lobby;
    public Ranks ranks;
    public LobbyFx fx;
    public Cosmetics cosmetics;
    public Stats stats;
    public Friends friends;
    public News news;
    public Fly fly;
    public Updater updater;
    public EventManager events;
    public Meteors meteors;
    public Coins coins;
    public Christmas xmas;

    @Override public void onEnable() {
        I = this;
        saveDefaultConfig();
        Gui.enabled = getConfig().getBoolean("gui.custom", true);
        queue = new U.BlockQueue(getConfig().getInt("blocks-per-tick", 6000));
        queue.runTaskTimer(this, 1, 1);
        mars = makeWorld("ss_mars", 18000);
        // herní svět se při každém startu smaže = vždy čisté mapy (žádné zbytky staveb)
        if (Bukkit.getWorld("ss_arenas") == null) U.deleteDir(new java.io.File(Bukkit.getWorldContainer(), "ss_arenas"));
        arenas = makeWorld("ss_arenas", 18000);
        items = new SpaceItems(this);
        crates = new Crates(this);
        games = new GameManager(this);
        lobby = new Lobby(this);
        ranks = new Ranks(this);
        redstone = new RedstoneWeek(this);
        fx = new LobbyFx(this);
        cosmetics = new Cosmetics(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(items, this);
        pm.registerEvents(crates, this);
        pm.registerEvents(games, this);
        pm.registerEvents(lobby, this);
        pm.registerEvents(ranks, this);
        pm.registerEvents(fx, this);
        pm.registerEvents(cosmetics, this);
        levels = new Levels(this);
        pm.registerEvents(redstone, this);
        intro = new Intro(this);
        pm.registerEvents(intro, this);
        pm.registerEvents(new Gui(), this);
        pm.registerEvents(new IpLimit(this), this);
        pack = new Pack(this);
        pm.registerEvents(pack, this);
        motd = new Motd(this);
        pm.registerEvents(motd, this);
        stats = new Stats(this);
        coins = new Coins(this); pm.registerEvents(coins, this);
        xmas = new Christmas(this); pm.registerEvents(xmas, this);
        bind("mince", coins, null);
        bind("advent", xmas, null);
        friends = new Friends(this); pm.registerEvents(friends, this);
        news = new News(this); pm.registerEvents(news, this);
        fly = new Fly(this);
        events = new EventManager(this); pm.registerEvents(events, this);
        updater = new Updater(this);
        bind("pritel", friends, friends);
        bind("event", events, events);
        bind("fly", fly, null);
        pm.registerEvents(new CmdGuard(this), this);
        org.bukkit.command.CommandExecutor toLobby = (s, c, l, a) -> { if (s instanceof Player p) { if (games.of(p) != null) games.leave(p); else lobby.send(p); } return true; };
        bind("spawn", toLobby, null);
        Basic basic = new Basic(this); pm.registerEvents(basic, this);
        for (String n : Basic.PLAYER_CMDS) bind(n, basic, basic);
        for (String n : Basic.STAFF_CMDS) bind(n, basic, basic);
        bind("hudba", new Music(this), null);
        meteors = new Meteors(this);
        Holo.start(this);
        bind("novinky", (s, c, l, a) -> { if (s instanceof Player p) news.open(p); return true; }, null);
        Emotes em = new Emotes(this);
        var mc = getCommand("udrzba");
        if (mc != null) mc.setExecutor((s, c, l, a) -> {
            if (!s.hasPermission("squarestar.admin")) { s.sendMessage(U.tc(s, "&cNa tohle nemáš práva.")); return true; }
            boolean on = a.length > 0 ? a[0].equalsIgnoreCase("on") || a[0].equalsIgnoreCase("zap") : !motd.maintenance();
            motd.setMaintenance(on);
            U.all(on ? "&c&l⚠ ÚDRŽBA ZAPNUTA &7- na serveru zůstává jen tým." : "&a&l✔ Údržba skončila &7- server je zase otevřený!");
            return true;
        });
        var ec = getCommand("emote"); if (ec != null) { ec.setExecutor(em); ec.setTabCompleter(em); }
        Commands cmd = new Commands(this);
        var pc = getCommand("ss");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        lobby.ensureBuilt();
        fx.start();
        Bukkit.getScheduler().runTaskLater(this, stats::spawnBoards, 60);
        for (Player p : Bukkit.getOnlinePlayers()) ranks.apply(p);
        // gravitace + efekty vesmírného oblečení každých 10 ticků
        Bukkit.getScheduler().runTaskTimer(this, () -> { for (Player p : Bukkit.getOnlinePlayers()) applyGravity(p); }, 20, 10);
        getLogger().info("SquareStar zapnut! Mars ceka. Gravitace: " + getConfig().getDouble("gravity.value"));
    }

    @Override public void onDisable() { if (games != null) games.stopAll(); if (stats != null) stats.save(); }

    private void bind(String name, org.bukkit.command.CommandExecutor ex, org.bukkit.command.TabCompleter tc) {
        var c = getCommand(name);
        if (c == null) { getLogger().warning("Prikaz chybi v plugin.yml: " + name); return; }
        c.setExecutor(ex); if (tc != null) c.setTabCompleter(tc);
    }

    public final java.util.Map<cz.adrrex.squarestar.game.GameType, World> gameWorlds = new java.util.EnumMap<>(cz.adrrex.squarestar.game.GameType.class);
    public World world(cz.adrrex.squarestar.game.GameType t) { return gameWorlds.getOrDefault(t, arenas); }
    public boolean isArena(World w) { return w == arenas || gameWorlds.containsValue(w); }

    private World makeWorld(String name, long time) {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).generator(new ChunkGenerator() {}).generateStructures(false).createWorld();
        }
        if (w == null) throw new IllegalStateException("Nelze vytvorit svet " + name);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.FALL_DAMAGE, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setTime(time);
        w.setStorm(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setAutoSave(!name.equals("ss_arenas"));
        return w;
    }

    public boolean isSpaceWorld(World w) {
        if (w == mars || isArena(w) || w.getName().equals("ss_event")) return true;
        List<String> extra = getConfig().getStringList("gravity.extra-worlds");
        return extra.contains(w.getName());
    }

    public void applyGravity(Player p) {
        boolean space = isSpaceWorld(p.getWorld()) && getConfig().getBoolean("gravity.enabled", true);
        double g = space ? getConfig().getDouble("gravity.value", 0.03) : 0.08;
        if (space && games != null) { var gm = games.of(p); if (gm != null && gm.eventGravity != null) g = gm.eventGravity; else if (gm != null && gm.gravity() != null) g = gm.gravity(); }
        if (space && events != null && events.inCustom(p)) g = events.gravity();
        double fall = space ? getConfig().getDouble("gravity.safe-fall-distance", 12) : 3;
        ItemStack boots = p.getInventory().getBoots();
        double jump = 0.41999998688697815;
        if (space && "gravity_boots".equals(U.tagOf(boots, "ss_item"))) jump = 0.75;
        set(p, Attribute.GRAVITY, g);
        set(p, Attribute.SAFE_FALL_DISTANCE, fall);
        set(p, Attribute.JUMP_STRENGTH, jump);
        if ("oxygen_helmet".equals(U.tagOf(p.getInventory().getHelmet(), "ss_item"))) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, false, false));
        }
    }

    private static void set(Player p, Attribute a, double v) {
        AttributeInstance i = p.getAttribute(a);
        if (i != null && i.getBaseValue() != v) i.setBaseValue(v);
    }
}
