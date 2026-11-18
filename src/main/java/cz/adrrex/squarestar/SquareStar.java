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
    public Lobby lobby;
    public Ranks ranks;
    public LobbyFx fx;
    public Cosmetics cosmetics;

    @Override public void onEnable() {
        I = this;
        saveDefaultConfig();
        Gui.enabled = getConfig().getBoolean("gui.custom", true);
        queue = new U.BlockQueue(getConfig().getInt("blocks-per-tick", 6000));
        queue.runTaskTimer(this, 1, 1);
        mars = makeWorld("ss_mars", 18000);
        arenas = makeWorld("ss_arenas", 18000);
        items = new SpaceItems(this);
        crates = new Crates(this);
        games = new GameManager(this);
        lobby = new Lobby(this);
        ranks = new Ranks(this);
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
        Commands cmd = new Commands(this);
        var pc = getCommand("ss");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        lobby.ensureBuilt();
        fx.start();
        for (Player p : Bukkit.getOnlinePlayers()) ranks.apply(p);
        // gravitace + efekty vesmírného oblečení každých 10 ticků
        Bukkit.getScheduler().runTaskTimer(this, () -> { for (Player p : Bukkit.getOnlinePlayers()) applyGravity(p); }, 20, 10);
        getLogger().info("SquareStar zapnut! Mars ceka. Gravitace: " + getConfig().getDouble("gravity.value"));
    }

    @Override public void onDisable() { if (games != null) games.stopAll(); }

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
        if (w == mars || w == arenas) return true;
        List<String> extra = getConfig().getStringList("gravity.extra-worlds");
        return extra.contains(w.getName());
    }

    public void applyGravity(Player p) {
        boolean space = isSpaceWorld(p.getWorld()) && getConfig().getBoolean("gravity.enabled", true);
        double g = space ? getConfig().getDouble("gravity.value", 0.03) : 0.08;
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
