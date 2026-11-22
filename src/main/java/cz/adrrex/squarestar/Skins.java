package cz.adrrex.squarestar;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hlavy hráčů v chatu a v žebříčcích. Server běží přes proxy / v offline módu, takže klient podle UUID najde jen Steva.
 * Proto si skin (textures) bereme z profilu hráče, nebo ho stáhneme od Mojangu podle jména, uložíme a pošleme ho klientům přímo.
 */
public final class Skins implements Listener {
    private final SquareStar pl;
    private final File file;
    private final YamlConfiguration y;
    private final Map<String, String[]> cache = new ConcurrentHashMap<>();   // jméno(lowercase) -> {value, signature, skutečné UUID z Mojangu}
    private final Map<String, Long> tried = new ConcurrentHashMap<>();       // kdy jsme naposledy zkusili stahovat
    private boolean dirty;

    public Skins(SquareStar pl) {
        this.pl = pl;
        file = pl.dataFile("skins.yml");
        y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) cache.put(k, new String[]{y.getString(k + ".v", ""), y.getString(k + ".s"), y.getString(k + ".u")});
        Bukkit.getScheduler().runTaskTimerAsynchronously(pl, this::save, 1200, 1200);
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private void put(String name, String value, String sig, String uuid) {
        if (value == null || value.isEmpty()) return;
        String[] old = cache.put(key(name), new String[]{value, sig, uuid});
        if (old != null && old[0].equals(value) && java.util.Objects.equals(old[2], uuid)) return;
        synchronized (y) { y.set(key(name) + ".v", value); y.set(key(name) + ".s", sig); y.set(key(name) + ".u", uuid); dirty = true; }
        pl.getLogger().info("Skins: skin hrace " + name + " ulozen");
        Bukkit.getScheduler().runTask(pl, () -> { Player on = Bukkit.getPlayerExact(name); if (on != null) apply(on); });
        Bukkit.getScheduler().runTask(pl, () -> { if (pl.stats != null) pl.stats.refresh(); });
    }

    private void save() {
        synchronized (y) { if (!dirty) return; dirty = false; try { y.save(file); } catch (Exception ignored) {} }
    }
    public void shutdown() { save(); }

    /** Offline mód: profil hráče nemá textures, takže BetterModel (emoty) vykreslí průhlednou postavu. Doplníme je z uloženého skinu. */
    private void apply(Player p) {
        if (Ranks.bedrock(p)) return;
        String[] t = cache.get(key(p.getName()));
        if (t == null || t[0].isEmpty()) return;
        PlayerProfile pr = p.getPlayerProfile();
        for (ProfileProperty pp : pr.getProperties()) if (pp.getName().equals("textures")) return;
        pr.setProperty(new ProfileProperty("textures", t[0], t[1]));
        p.setPlayerProfile(pr);
    }

    /** vezme skin z profilu přihlášeného hráče; když tam není, stáhne ho podle jména */
    private void learn(Player p) {
        for (ProfileProperty pp : p.getPlayerProfile().getProperties())
            if (pp.getName().equals("textures")) { put(p.getName(), pp.getValue(), pp.getSignature(), null); return; }
        if (cache.containsKey(key(p.getName()))) apply(p);
        fetch(p.getName());
    }

    private void fetch(String name) {
        if (name.startsWith(".") || name.startsWith("*")) return;                // Bedrock hráči nemají Mojang skin
        long now = System.currentTimeMillis();
        Long last = tried.get(key(name));
        if (last != null && now - last < 600_000) return;                        // nejvýš jednou za 10 minut
        tried.put(key(name), now);
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try {   // 1) přímo od Mojangu (jméno -> UUID -> podepsané textures); funguje i v offline módu
                java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build();
                String r1 = get(http, "https://api.mojang.com/users/profiles/minecraft/" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8));
                if (r1 == null) { pl.getLogger().warning("Skins: Mojang nezná jméno " + name + " (nebo je nedostupný)"); }
                else {
                    String id = com.google.gson.JsonParser.parseString(r1).getAsJsonObject().get("id").getAsString();
                    String r2 = get(http, "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
                    if (r2 != null) for (var el : com.google.gson.JsonParser.parseString(r2).getAsJsonObject().getAsJsonArray("properties")) {
                        var o = el.getAsJsonObject();
                        if (o.get("name").getAsString().equals("textures")) { put(name, o.get("value").getAsString(), o.has("signature") ? o.get("signature").getAsString() : null, java.util.UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")).toString()); return; }
                    }
                    pl.getLogger().warning("Skins: sessionserver nevratil textures pro " + name);
                }
            } catch (Exception e) { pl.getLogger().warning("Skins: stahovani selhalo pro " + name + ": " + e); }
            try {   // 2) záloha přes Paper
                PlayerProfile pr = Bukkit.createProfile(name);
                pr.complete(true);
                for (ProfileProperty pp : pr.getProperties())
                    if (pp.getName().equals("textures")) { put(name, pp.getValue(), pp.getSignature(), pr.getId() == null ? null : pr.getId().toString()); return; }
            } catch (Exception ignored) {}
        });
    }

    private static String get(java.net.http.HttpClient http, String url) throws Exception {
        var res = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? res.body() : null;
    }

    /** hlava hráče (podle jména) jako kus textu – i s vrstvou „hat“ */
    public Component head(String name, UUID id) {
        var b = ObjectContents.playerHead().name(name).hat(true);
        String[] t = cache.get(key(name));
        if (t != null) {
            // skutečné UUID z Mojangu (offline UUID by klient nenašel a ukázal výchozí skin)
            if (t.length > 2 && t[2] != null) { try { b.id(UUID.fromString(t[2])); } catch (Exception ignored) {} }
            b.profileProperty(PlayerHeadObjectContents.property("textures", t[0], t[1]));
        } else fetch(name);
        // bílá = hlava se nezabarví podle barvy okolního textu (jinak je u #1 zlatá, u #3 červená)
        return Component.object(b.build()).color(net.kyori.adventure.text.format.NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false);
    }
    public Component head(Player p) { return head(p.getName(), p.getUniqueId()); }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) learn(p); }, 10);
    }
}
