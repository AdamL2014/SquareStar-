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
    private final Map<String, String[]> cache = new ConcurrentHashMap<>();   // jméno(lowercase) -> {value, signature}
    private final Map<String, Long> tried = new ConcurrentHashMap<>();       // kdy jsme naposledy zkusili stahovat
    private boolean dirty;

    public Skins(SquareStar pl) {
        this.pl = pl;
        file = new File(pl.getDataFolder(), "skins.yml");
        y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) cache.put(k, new String[]{y.getString(k + ".v", ""), y.getString(k + ".s")});
        Bukkit.getScheduler().runTaskTimerAsynchronously(pl, this::save, 1200, 1200);
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private void put(String name, String value, String sig) {
        if (value == null || value.isEmpty()) return;
        String[] old = cache.put(key(name), new String[]{value, sig});
        if (old != null && old[0].equals(value)) return;
        synchronized (y) { y.set(key(name) + ".v", value); y.set(key(name) + ".s", sig); dirty = true; }
        Bukkit.getScheduler().runTask(pl, () -> { if (pl.stats != null) pl.stats.refresh(); });
    }

    private void save() {
        synchronized (y) { if (!dirty) return; dirty = false; try { y.save(file); } catch (Exception ignored) {} }
    }
    public void shutdown() { save(); }

    /** vezme skin z profilu přihlášeného hráče; když tam není, stáhne ho podle jména */
    private void learn(Player p) {
        for (ProfileProperty pp : p.getPlayerProfile().getProperties())
            if (pp.getName().equals("textures")) { put(p.getName(), pp.getValue(), pp.getSignature()); return; }
        fetch(p.getName());
    }

    private void fetch(String name) {
        if (name.startsWith(".") || name.startsWith("*")) return;                // Bedrock hráči nemají Mojang skin
        long now = System.currentTimeMillis();
        Long last = tried.get(key(name));
        if (last != null && now - last < 600_000) return;                        // nejvýš jednou za 10 minut
        tried.put(key(name), now);
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try {
                PlayerProfile pr = Bukkit.createProfile(name);
                pr.complete(true);
                for (ProfileProperty pp : pr.getProperties())
                    if (pp.getName().equals("textures")) { put(name, pp.getValue(), pp.getSignature()); return; }
            } catch (Exception ignored) {}
        });
    }

    /** hlava hráče (podle jména) jako kus textu – i s vrstvou „hat“ */
    public Component head(String name, UUID id) {
        var b = ObjectContents.playerHead().name(name).hat(true);
        if (id != null) b.id(id);
        String[] t = cache.get(key(name));
        if (t != null) b.profileProperty(PlayerHeadObjectContents.property("textures", t[0], t[1]));
        else fetch(name);
        return Component.object(b.build());
    }
    public Component head(Player p) { return head(p.getName(), p.getUniqueId()); }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) learn(p); }, 10);
    }
}
