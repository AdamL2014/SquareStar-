package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Automatická aktualizace: každých X minut se podívá na GitHub (download/version.txt).
 * Když je tam novější verze, stáhne SquareStar.jar do plugins/update/ (Paper ho při startu sám vymění),
 * odpočítá, vykopne hráče se zprávou a vypne server. MineKeep ho pak znovu zapne (nebo ho zapni ručně).
 */
public final class Updater {
    private static final String BASE = "https://raw.githubusercontent.com/AdamL2014/SquareStar-/main/download/";
    private final SquareStar pl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private boolean busy;

    public Updater(SquareStar pl) {
        this.pl = pl;
        verifyPrevious();
        if (!pl.getConfig().getBoolean("auto-update.enabled", true)) return;
        long every = Math.max(5, pl.getConfig().getLong("auto-update.check-minutes", 10)) * 1200L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(pl, () -> check(null), 600, every);
    }

    /** po restartu: sedí běžící verze s tou, na kterou jsme aktualizovali? když ne, zapamatuj si to, ať se server nezačne donekonečna restartovat */
    private void verifyPrevious() {
        File m = pl.dataFile("update-expected.txt");
        if (!m.exists()) return;
        try {
            String exp = Files.readString(m.toPath()).trim(), now = pl.getPluginMeta().getVersion();
            Files.deleteIfExists(m.toPath());
            if (exp.equals(now)) { pl.getLogger().info("Aktualizace na " + exp + " proběhla v pořádku."); return; }
            pl.getLogger().warning("Aktualizace na " + exp + " se NEPOVEDLA – běží pořád " + now + " (jar v plugins/update/ se nevyměnil). Další pokus o " + exp + " se přeskočí; nahraj SquareStar.jar do plugins/ ručně.");
            Files.writeString(pl.dataFile("update-failed.txt").toPath(), exp);
        } catch (Exception ex) { pl.getLogger().warning("Kontrola po aktualizaci selhala: " + ex.getMessage()); }
    }
    private String failedVersion() {
        try { File f = pl.dataFile("update-failed.txt"); return f.exists() ? Files.readString(f.toPath()).trim() : ""; } catch (Exception e) { return ""; }
    }
    /** verze zapsaná v plugin.yml uvnitř staženého jaru */
    static String jarVersion(File jar) throws Exception {
        try (var z = new java.util.zip.ZipFile(jar)) {
            var e = z.getEntry("paper-plugin.yml"); if (e == null) e = z.getEntry("plugin.yml");
            if (e == null) return "";
            try (var in = z.getInputStream(e)) {
                for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R"))
                    if (l.startsWith("version:")) return l.substring(8).trim().replace("'", "").replace("\"", "");
            }
        }
        return "";
    }

    static int[] parse(String v) {
        String[] s = v.trim().split("[.\\-]");
        int[] r = new int[3];
        for (int i = 0; i < 3 && i < s.length; i++) try { r[i] = Integer.parseInt(s[i].replaceAll("\\D", "")); } catch (NumberFormatException ignored) { }
        return r;
    }
    static boolean newer(String remote, String local) {
        int[] a = parse(remote), b = parse(local);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    /** who = kdo kontrolu spustil (může být null) – běží asynchronně */
    public void check(org.bukkit.command.CommandSender who) {
        if (busy) return;
        String local = pl.getPluginMeta().getVersion();
        try {
            String remote = http.send(HttpRequest.newBuilder(URI.create(BASE + "version.txt?t=" + System.currentTimeMillis())).timeout(Duration.ofSeconds(15)).build(),
                    HttpResponse.BodyHandlers.ofString()).body().trim();
            if (!remote.matches("\\d+(\\.\\d+){0,2}") || !newer(remote, local)) {
                if (who != null) who.sendMessage(U.c("&aServer má nejnovější verzi &e" + local + " &7(GitHub: " + remote + ")"));
                return;
            }
            if (remote.equals(failedVersion())) {
                if (who != null) who.sendMessage(U.c("&cAktualizace na " + remote + " se minule nepovedla (jar se nevyměnil). Nahraj SquareStar.jar do plugins/ ručně a smaž squarestar-data/update-failed.txt."));
                return;
            }
            busy = true;
            File dir = Bukkit.getUpdateFolderFile();
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("nelze vytvorit " + dir);
            File tmp = new File(dir, "SquareStar.jar.part");
            HttpResponse<java.nio.file.Path> r = http.send(HttpRequest.newBuilder(URI.create(BASE + "SquareStar.jar?t=" + System.currentTimeMillis())).timeout(Duration.ofMinutes(3)).build(),
                    HttpResponse.BodyHandlers.ofFile(tmp.toPath()));
            if (r.statusCode() != 200 || tmp.length() < 50_000) { Files.deleteIfExists(tmp.toPath()); busy = false; throw new IllegalStateException("stazeni selhalo (HTTP " + r.statusCode() + ")"); }
            String inJar = jarVersion(tmp);
            if (!remote.equals(inJar)) { Files.deleteIfExists(tmp.toPath()); busy = false; throw new IllegalStateException("stažený jar je verze '" + inJar + "', GitHub hlásí " + remote + " (zkusím později)"); }
            Files.writeString(pl.dataFile("update-expected.txt").toPath(), remote);
            // jméno musí sedět s jarem v plugins/
            String jar = new File(pl.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
            Files.move(tmp.toPath(), new File(dir, jar).toPath(), StandardCopyOption.REPLACE_EXISTING);
            pl.getLogger().info("Stazena nova verze " + remote + " -> restart.");
            Bukkit.getScheduler().runTask(pl, () -> countdown(remote));
        } catch (Exception ex) {
            busy = false;
            pl.getLogger().warning("Kontrola aktualizace selhala: " + ex.getMessage());
            if (who != null) who.sendMessage(U.c("&cKontrola aktualizace selhala: &7" + ex.getMessage()));
        }
    }

    private void countdown(String v) {
        int[] left = {Bukkit.getOnlinePlayers().isEmpty() ? 0 : pl.getConfig().getInt("auto-update.countdown-seconds", 60)};
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            int s = left[0]--;
            if (s == 60 || s == 30 || s == 10 || (s <= 5 && s > 0)) {
                U.all("&d&l⟳ AKTUALIZACE &7na verzi &e" + v + "&7 – restart za &c" + s + " s");
                for (Player p : Bukkit.getOnlinePlayers()) { U.title(p, "&d&l⟳ AKTUALIZACE", "&7Restart za &c" + s + " s"); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, s <= 5 ? 2f : 1f); }
            }
            if (s > 0) return;
            t.cancel();
            if (pl.stats != null) pl.stats.save();
            for (Player p : Bukkit.getOnlinePlayers())
                p.kick(U.c(Lang.t(p, "&6&lSQUARE&c&lSTAR\n\n&d⟳ Server se aktualizuje na verzi &e" + v + "\n&7Restartuje se – připoj se znovu za minutku!")));
            Bukkit.getScheduler().runTaskLater(pl, Bukkit::shutdown, 20);
        }, 0, 20);
    }
}
