package cz.adrrex.squarestar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Resource pack posílá plugin sám (jen hráčům z Javy). Bedrock ho dostat nemůže – kdyby byl
 * v server.properties povinný, Bedrock hráče by to vyhodilo. SHA-1 si plugin spočítá sám při startu.
 */
public final class Pack implements Listener {
    private final SquareStar pl;
    private volatile byte[] hash;
    private static final UUID ID = UUID.fromString("5a1b2c3d-0000-4000-8000-5157a25a7a2a");
    public Pack(SquareStar pl) { this.pl = pl; refresh(); }

    private static final String DEFAULT = "https://raw.githubusercontent.com/AdamL2014/SquareStar-/main/download/SquareStar-ResourcePack.zip";
    /** výchozí adresa dostane ?v=<verze pluginu>: GitHub CDN drží starý soubor pár minut, takže po updatu by hráči dostali starý pack (bez nových modelů) */
    public String url() {
        String u = pl.getConfig().getString("resource-pack.url", DEFAULT);
        return u.equals(DEFAULT) ? u + "?v=" + pl.getPluginMeta().getVersion() : u;
    }

    /** stáhne pack a spočítá SHA-1 (na pozadí, neblokuje server) */
    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try (InputStream in = URI.create(url()).toURL().openStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                hash = md.digest();
                StringBuilder sb = new StringBuilder(); for (byte b : hash) sb.append(String.format("%02x", b));
                pl.getLogger().info("Resource pack nacten, sha1=" + sb);
            } catch (Exception e) {
                pl.getLogger().warning("Resource pack se nepodarilo stahnout (" + e.getMessage() + ") - posilam bez kontroly sha1.");
            }
        });
    }

    public void send(Player p) {
        if (!pl.getConfig().getBoolean("resource-pack.enabled", true) || Ranks.bedrock(p)) return;
        p.setResourcePack(ID, url(), hash, Component.text("SquareStar potřebuje resource pack (ranky, 3D modely, Země)"), pl.getConfig().getBoolean("resource-pack.required", true));
    }

    @EventHandler public void join(PlayerJoinEvent e) { send(e.getPlayer()); }
}
