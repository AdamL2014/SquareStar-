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
    private volatile byte[] hash, hudHash;
    private final java.util.Set<UUID> hudOn = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final UUID HUD_ID = UUID.fromString("5a1b2c3d-0000-4000-8000-5157a25a7a2b");
    private static final String HUD_DEFAULT = "https://raw.githubusercontent.com/AdamL2014/SquareStar-/main/download/SquareStar-LobbyHud.zip";
    public static final UUID ID = UUID.fromString("5a1b2c3d-0000-4000-8000-5157a25a7a2a");
    public Pack(SquareStar pl) { this.pl = pl; refresh(); }

    private static final String DEFAULT = "https://raw.githubusercontent.com/AdamL2014/SquareStar-/main/download/SquareStar-ResourcePack.zip";
    /** výchozí adresa dostane ?v=<verze pluginu>: GitHub CDN drží starý soubor pár minut, takže po updatu by hráči dostali starý pack (bez nových modelů) */
    public String url() {
        String u = pl.getConfig().getString("resource-pack.url", DEFAULT);
        return u.equals(DEFAULT) ? u + "?v=" + pl.getPluginMeta().getVersion() : u;
    }

    /** druhý, malý pack: schová vanilla srdíčka a hlad. Hráč ho má jen v lobby (v minihrách se odebere a srdíčka se vrátí). */
    public String hudUrl() {
        String u = pl.getConfig().getString("resource-pack.lobby-hud-url", HUD_DEFAULT);
        return u.equals(HUD_DEFAULT) ? u + "?v=" + pl.getPluginMeta().getVersion() : u;
    }

    private final java.util.Map<UUID, Integer> differ = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<UUID, Long> joined = new java.util.concurrent.ConcurrentHashMap<>();

    public void lobbyHud(Player p, boolean lobby) {
        if (!pl.getConfig().getBoolean("resource-pack.enabled", true) || Ranks.bedrock(p)) return;
        if (System.currentTimeMillis() - joined.getOrDefault(p.getUniqueId(), 0L) < 20_000) return;   // po připojení nic nepřepínat (pack se teprve načítá)
        boolean on = hudOn.contains(p.getUniqueId());
        // přepnout až když stav vydrží 3 s – krátké teleporty (lobby -> čekárna -> ...) nespustí zbytečné načítání
        if (lobby == on) { differ.remove(p.getUniqueId()); return; }
        if (differ.merge(p.getUniqueId(), 1, Integer::sum) < 3) return;
        differ.remove(p.getUniqueId());
        if (lobby && !on) {
            hudOn.add(p.getUniqueId());
            p.addResourcePack(HUD_ID, hudUrl(), hudHash, null, false);
        } else if (!lobby && on) {
            hudOn.remove(p.getUniqueId());
            p.removeResourcePack(HUD_ID);
        }
    }

    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e) { hudOn.remove(e.getPlayer().getUniqueId()); differ.remove(e.getPlayer().getUniqueId()); joined.remove(e.getPlayer().getUniqueId()); }

    /** stáhne pack a spočítá SHA-1 (na pozadí, neblokuje server) */
    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try { hash = sha1(url()); pl.getLogger().info("Resource pack nacten, sha1=" + hex(hash)); }
            catch (Exception e) { pl.getLogger().warning("Resource pack se nepodarilo stahnout (" + e.getMessage() + ") - posilam bez kontroly sha1."); }
            try { hudHash = sha1(hudUrl()); pl.getLogger().info("Lobby HUD pack nacten, sha1=" + hex(hudHash)); }
            catch (Exception e) { pl.getLogger().warning("Lobby HUD pack se nepodarilo stahnout (" + e.getMessage() + ")."); }
        });
    }

    private static byte[] sha1(String url) throws Exception {
        try (InputStream in = URI.create(url).toURL().openStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return md.digest();
        }
    }
    private static String hex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }

    public void send(Player p) {
        if (!pl.getConfig().getBoolean("resource-pack.enabled", true) || Ranks.bedrock(p)) return;
        joined.put(p.getUniqueId(), System.currentTimeMillis());
        // hlavní pack + lobby HUD pack NAJEDNOU = jen jedno načítací okno při připojení
        try {
            var main = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(ID, URI.create(url()), hash == null ? "" : hex(hash));
            var hud = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(HUD_ID, URI.create(hudUrl()), hudHash == null ? "" : hex(hudHash));
            p.sendResourcePacks(net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                    .packs(main, hud).replace(true)
                    .required(pl.getConfig().getBoolean("resource-pack.required", true))
                    .prompt(Component.text("SquareStar potřebuje resource pack (ranky, 3D modely, Země)")).build());
            hudOn.add(p.getUniqueId());
        } catch (Exception ex) {
            pl.getLogger().warning("Resource packy nejdou poslat spolu (" + ex.getMessage() + "), posilam jen hlavni.");
            p.setResourcePack(ID, url(), hash, Component.text("SquareStar potřebuje resource pack (ranky, 3D modely, Země)"), pl.getConfig().getBoolean("resource-pack.required", true));
        }
    }

    @EventHandler public void join(PlayerJoinEvent e) { send(e.getPlayer()); }
}
