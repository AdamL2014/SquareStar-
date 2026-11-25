package cz.adrrex.squarestar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Web serveru přímo v pluginu: na http://<ip>:<web.port>/ je stránka SquareStar a na /api/status živá data
 * (kdo je online, level, odehraný čas, rank, skin, co hraje, žebříčky). Data se skládají v hlavním vlákně
 * každých 5 s, web server je jen vrací – hru to nezpomalí.
 */
public final class Web {
    private final SquareStar pl;
    private HttpServer http;
    private volatile byte[] json = "{}".getBytes(StandardCharsets.UTF_8);
    private byte[] page;

    public Web(SquareStar pl) {
        this.pl = pl;
        if (!pl.getConfig().getBoolean("web.enabled", true)) return;
        int port = pl.getConfig().getInt("web.port", 8123);
        try (InputStream in = pl.getResource("web/index.html")) { page = in == null ? "<h1>SquareStar</h1>".getBytes(StandardCharsets.UTF_8) : in.readAllBytes(); }
        catch (Exception e) { page = "<h1>SquareStar</h1>".getBytes(StandardCharsets.UTF_8); }
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/api/status", ex -> send(ex, 200, "application/json; charset=utf-8", json));
            http.createContext("/", ex -> {
                String path = ex.getRequestURI().getPath();
                if (path.equals("/") || path.equals("/index.html")) send(ex, 200, "text/html; charset=utf-8", page);
                else send(ex, 404, "text/plain; charset=utf-8", "404".getBytes(StandardCharsets.UTF_8));
            });
            http.setExecutor(Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "SquareStar-Web"); t.setDaemon(true); return t; }));
            http.start();
            pl.getLogger().info("Web bezi na portu " + port + " (http://<ip-serveru>:" + port + "/)");
        } catch (Exception e) {
            pl.getLogger().warning("Web se nepodarilo spustit na portu " + port + ": " + e.getMessage() + " - zmen web.port v config.yml");
            http = null;
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::snapshot, 40, 100);
        // odehraný čas do statistik (aby byl vidět i u hráčů, kteří zrovna nejsou online)
        Bukkit.getScheduler().runTaskTimer(pl, () -> { for (Player p : Bukkit.getOnlinePlayers()) saveProfile(p); }, 200, 1200);
    }

    public void stop() { if (http != null) http.stop(0); }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");      // web může běžet i na jiné adrese
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if (ex.getRequestMethod().equalsIgnoreCase("HEAD")) { ex.sendResponseHeaders(code, -1); ex.close(); return; }
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(body); }
    }

    private void saveProfile(Player p) {
        if (pl.stats == null) return;
        pl.stats.setInt(p, "minutes", p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200);
        pl.stats.setInt(p, "xp", pl.levels.xp(p));
        Ranks.Rank r = pl.ranks.of(p);
        pl.stats.setStr(p, "rank", r.name());
        pl.stats.setStr(p, "uuid", p.getUniqueId().toString());
    }

    private static String color(String legacy) {
        // první barva z kódu ranku (&4&l -> #AA0000) pro web
        String[] hex = {"#AAAAAA", "#5555FF", "#55FF55", "#55FFFF", "#FF5555", "#FF55FF", "#FFAA00", "#AAAAAA", "#555555", "#5555FF", "#55FF55", "#55FFFF", "#FF5555", "#FF55FF", "#FFFF55", "#FFFFFF"};
        for (int i = 0; i + 1 < legacy.length(); i++) if (legacy.charAt(i) == '&') { int d = Character.digit(legacy.charAt(i + 1), 16); if (d >= 0) return hex[d]; }
        return "#AAAAAA";
    }

    private JsonObject player(String name, String rank, int level, int xpRest, int xpNeed, long minutes) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        Ranks.Rank r;
        try { r = Ranks.Rank.valueOf(rank); } catch (Exception e) { r = Ranks.Rank.HRAC; }
        o.addProperty("rank", r == Ranks.Rank.HRAC ? "Hráč" : r.name().charAt(0) + r.name().substring(1).toLowerCase(Locale.ROOT));
        o.addProperty("rankColor", color(r.color));
        o.addProperty("level", level);
        o.addProperty("xp", xpRest);
        o.addProperty("xpNeed", xpNeed);
        o.addProperty("minutes", minutes);
        String skin = pl.skins == null ? null : pl.skins.skinUrl(name);
        if (skin != null) o.addProperty("skin", skin);
        return o;
    }

    private static int[] lvl(int xp) { int l = 1; while (xp >= Levels.need(l)) { xp -= Levels.need(l); l++; } return new int[]{l, xp, Levels.need(l)}; }

    /** složí JSON s živými daty (hlavní vlákno) */
    private void snapshot() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("name", "SquareStar");
            root.addProperty("version", pl.getPluginMeta().getVersion());
            root.addProperty("mc", "1.21.11");
            root.addProperty("max", Bukkit.getMaxPlayers());
            root.addProperty("tps", Math.round(Math.min(20.0, Bukkit.getTPS()[0]) * 10) / 10.0);
            root.addProperty("games", pl.games.activeGames());
            root.addProperty("time", System.currentTimeMillis());
            JsonArray on = new JsonArray();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (Basic.vanished.contains(p.getUniqueId())) continue;   // neviditelný tým na webu není
                int[] l = lvl(pl.levels.xp(p));
                JsonObject o = player(p.getName(), pl.ranks.of(p).name(), l[0], l[1], l[2], p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200);
                var g = pl.games.of(p);
                o.addProperty("playing", g != null ? g.type.display : p.getWorld() == pl.mars ? "Lobby" : "Jinde na serveru");
                o.addProperty("coins", pl.coins.get(p));
                if (pl.stats != null) { o.addProperty("wins", pl.stats.get(p, "wins")); o.addProperty("kills", pl.stats.get(p, "kills")); o.addProperty("gamesPlayed", pl.stats.get(p, "games")); }
                on.add(o);
            }
            root.addProperty("online", on.size());
            root.add("players", on);
            // žebříčky ze statistik (i offline hráči)
            if (pl.stats != null) {
                YamlConfiguration y = pl.stats.raw();
                List<JsonObject> all = new ArrayList<>();
                for (String id : y.getKeys(false)) {
                    String name = y.getString(id + ".name");
                    if (name == null) continue;
                    int[] l = lvl(y.getInt(id + ".xp"));
                    JsonObject o = player(name, y.getString(id + ".rank", "HRAC"), l[0], l[1], l[2], y.getLong(id + ".minutes"));
                    o.addProperty("wins", y.getInt(id + ".wins"));
                    o.addProperty("kills", y.getInt(id + ".kills"));
                    o.addProperty("gamesPlayed", y.getInt(id + ".games"));
                    o.addProperty("xpTotal", y.getInt(id + ".xp"));
                    all.add(o);
                }
                JsonObject top = new JsonObject();
                for (String f : new String[]{"xpTotal", "wins", "kills", "minutes", "gamesPlayed"}) {
                    List<JsonObject> s = new ArrayList<>(all);
                    s.removeIf(o -> o.get(f).getAsLong() <= 0);
                    s.sort((a, b) -> Long.compare(b.get(f).getAsLong(), a.get(f).getAsLong()));
                    JsonArray arr = new JsonArray();
                    for (int i = 0; i < Math.min(10, s.size()); i++) arr.add(s.get(i));
                    top.add(f, arr);
                }
                root.add("top", top);
                root.addProperty("registered", all.size());
            }
            json = root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) { pl.getLogger().warning("Web: data se nepodarilo slozit: " + e.getMessage()); }
    }
}
