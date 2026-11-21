package cz.adrrex.squarestar;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Ranky s pixel-art odznaky (resource pack font). Bez packu se ukáže barevný text. */
public final class Ranks implements Listener {
    public enum Rank {
        // POZOR: nové ranky přidávat jen NA KONEC (pořadí = obrázek v resource packu); řazení v tabu určuje „sort"
        OWNER("&4&l", true, 0), ADMIN("&c&l", true, 1), MODERATOR("&e&l", false, 3), HELPER("&a&l", false, 4), BUILDER("&6&l", false, 5),
        YOUTUBER("&c&l", false, 7), STREAMER("&5&l", false, 12), VIDRIFTER("&b&l", false, 13), LEGENDA("&d&l", false, 14), MVP("&b&l", false, 15), VIP("&a&l", false, 16), HRAC("&7", false, 17),
        TIKTOKER("&d&l", false, 8), DEVELOPER("&2&l", true, 2), PARTNER("&6&l", false, 6),
        KICK("&a&l", false, 10), TWITCH("&5&l", false, 9), INSTAGRAM("&d&l", false, 11);
        public final String color; public final boolean admin; public final int sort;
        Rank(String c, boolean a, int s) { color = c; admin = a; sort = s; }
        public char glyph() { return (char) (0xE000 + ordinal()); }
    }

    private static final Key FONT = Key.key("squarestar", "ranks");
    private final SquareStar pl;
    private final File file;
    private final YamlConfiguration data;
    private final Set<UUID> declined = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> packed = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, PermissionAttachment> perms = new HashMap<>();

    public Ranks(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, () -> { for (Player p : Bukkit.getOnlinePlayers()) tab(p); }, 40, 40);
        // ranky leží MIMO složku pluginu (hlavní složka serveru), aby přežily update/smazání plugins/SquareStar
        file = new File(Bukkit.getWorldContainer(), "squarestar-ranks.yml");
        File old = new File(pl.getDataFolder(), "ranks.yml");
        if (!file.exists() && old.exists()) old.renameTo(file);
        data = YamlConfiguration.loadConfiguration(file);
    }

    private static final org.bukkit.NamespacedKey PDC = new org.bukkit.NamespacedKey("squarestar", "rank");

    public Rank of(Player p) {
        String r = data.getString("players." + p.getUniqueId());
        // záloha přímo v datech hráče (přežije i smazání souboru s ranky)
        if (r == null) r = p.getPersistentDataContainer().get(PDC, org.bukkit.persistence.PersistentDataType.STRING);
        if (r == null) return Rank.HRAC;
        try { return Rank.valueOf(r); } catch (IllegalArgumentException e) { return Rank.HRAC; }
    }

    public void set(Player p, Rank r) {
        Rank before = of(p);
        // OWNER = operátor (všechna práva); kdo o OWNER přijde, přijde i o OP
        if (r == Rank.OWNER) p.setOp(true);
        else if (before == Rank.OWNER) p.setOp(false);
        data.set("players." + p.getUniqueId(), r == Rank.HRAC ? null : r.name());
        data.set("names." + p.getUniqueId(), p.getName());
        if (r == Rank.HRAC) p.getPersistentDataContainer().remove(PDC);
        else p.getPersistentDataContainer().set(PDC, org.bukkit.persistence.PersistentDataType.STRING, r.name());
        try { data.save(file); } catch (IOException e) { pl.getLogger().warning("Nelze ulozit ranks.yml: " + e.getMessage()); }
        apply(p);
    }

    private Component textBadge(Rank r) {
        if (r == Rank.HRAC) return Component.empty();
        if (r == Rank.VIDRIFTER) return link(U.c("&8[&b&lVID&d&lRIFTER&8]")).append(Component.text(" "));
        if (r == Rank.TIKTOKER) return U.c("&8[&b&lTIK&d&lTOKER&8] ");
        if (r == Rank.INSTAGRAM) return U.c("&8[&5&lINSTA&d&lGRAM&8] ");
        return U.c("&8[" + r.color + r.name() + "&8] ");
    }
    private Component glyphBadge(Rank r) {
        Component g = Component.text(String.valueOf(r.glyph())).font(FONT).color(NamedTextColor.WHITE);
        // obal bez fontu: vše, co se připojí za odznak, musí mít normální písmo (jinak čtverečky)
        return Component.text().append(r == Rank.VIDRIFTER ? link(g) : g).append(Component.text(" ")).build();
    }
    /** VidRifter odznak je klikací odkaz na VidRift */
    private Component link(Component c) {
        String url = pl.getConfig().getString("ranks.vidrift-url", "https://vidriftvideo.netlify.app");
        return c.clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(U.c("&b&lVid&d&lRift\n&7Česká video platforma\n&eKlikni: &f" + url)));
    }
    private static String nameColor(Rank r) { return r == Rank.HRAC ? "&7" : r.color.replace("&l", ""); }

    private boolean glyphsEverywhere() { return pl.getConfig().getBoolean("ranks.badges-in-tab", true); }

    /** hezký tab: logo nahoře, info dole */
    public void tab(Player p) {
        int on = Bukkit.getOnlinePlayers().size();
        Component logo = Component.text(String.valueOf((char) 0xE100)).font(FONT).color(NamedTextColor.WHITE);
        Component header = Component.text("\n").append(!declined.contains(p.getUniqueId()) ? logo : U.c("&c&l✦ &6&lSQUARE&c&lSTAR &c&l✦"))
                .append(U.c("\n&7Minihry na Marsu &8• &bnízká gravitace\n"));
        Component footer = U.c("\n&7Online: &a" + on + " &8• &7Ping: &e" + p.getPing() + " ms &8• &7Hra: &6" +
                (pl.games.of(p) == null ? "Lobby" : pl.games.of(p).type.display) + "\n&8vidriftvideo.netlify.app\n");
        p.sendPlayerListHeaderAndFooter(header, footer);
    }

    // ---------------- práva podle ranku (bez LuckPerms) ----------------
    /** výchozí práva; jdou upravit v config.yml -> rank-permissions */
    private static final Map<Rank, List<String>> DEF = new EnumMap<>(Rank.class);
    static {
        List<String> helper = List.of("minecraft.command.kick", "minecraft.command.teleport", "minecraft.command.tp", "minecraft.command.list", "squarestar.helper");
        List<String> mod = new ArrayList<>(helper);
        mod.addAll(List.of("minecraft.command.ban", "minecraft.command.ban-ip", "minecraft.command.pardon", "minecraft.command.pardon-ip", "minecraft.command.banlist",
                "minecraft.command.spectate", "minecraft.command.gamemode", "minecraft.command.clear", "grim.alerts", "grim.alerts.enable-on-join", "grim.verbose", "grim.spectate", "squarestar.moderator"));
        List<String> builder = List.of("minecraft.command.gamemode", "minecraft.command.teleport", "minecraft.command.tp", "minecraft.command.give", "minecraft.command.time",
                "minecraft.command.weather", "minecraft.command.fill", "minecraft.command.setblock", "minecraft.command.clone", "squarestar.builder");
        List<String> admin = new ArrayList<>(mod);
        admin.addAll(builder);
        admin.addAll(List.of("squarestar.admin", "minecraft.command.whitelist", "minecraft.command.op", "minecraft.command.deop", "minecraft.command.effect",
                "minecraft.command.kill", "minecraft.command.say", "minecraft.command.difficulty", "minecraft.command.gamerule", "grim.*", "grim.exempt.false"));
        DEF.put(Rank.OWNER, admin); DEF.put(Rank.ADMIN, admin); DEF.put(Rank.DEVELOPER, admin); DEF.put(Rank.MODERATOR, mod); DEF.put(Rank.HELPER, helper); DEF.put(Rank.BUILDER, builder);
    }
    /** je hráč (podle UUID) v týmu? – funguje i před připojením */
    public boolean staffByUuid(UUID id) {
        String r = data.getString("players." + id);
        return r != null && (r.equals("OWNER") || r.equals("ADMIN") || r.equals("DEVELOPER"));
    }

    public void applyPerms(Player p) {
        var old = perms.remove(p.getUniqueId());
        if (old != null) try { p.removeAttachment(old); } catch (IllegalArgumentException ignored) {}
        Rank r = of(p);
        List<String> list = pl.getConfig().isList("rank-permissions." + r.name().toLowerCase()) ? pl.getConfig().getStringList("rank-permissions." + r.name().toLowerCase()) : DEF.getOrDefault(r, List.of());
        if (list.isEmpty()) { p.updateCommands(); return; }
        var at = p.addAttachment(pl);
        for (String perm : list) { if (perm.startsWith("-")) at.setPermission(perm.substring(1), false); else at.setPermission(perm, true); }
        perms.put(p.getUniqueId(), at);
        p.updateCommands();
    }

    /** hráč z Bedrocku (Geyser/Floodgate) – nemá Java resource pack, dostane textové ranky */
    public static boolean bedrock(Player p) {
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = c.getMethod("getInstance").invoke(null);
            return (boolean) c.getMethod("isFloodgatePlayer", UUID.class).invoke(api, p.getUniqueId());
        } catch (Throwable t) { return p.getUniqueId().getMostSignificantBits() == 0; }
    }

    public void apply(Player p) {
        applyPerms(p);
        if (bedrock(p)) declined.add(p.getUniqueId());
        Rank r = of(p);
        boolean g = glyphsEverywhere();
        p.playerListName((g ? glyphBadge(r) : textBadge(r)).append(U.c(nameColor(r) + p.getName() + " " + pl.levels.tag(p).trim())));
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String tn = String.format("ss_s%02d", r.sort);
        Team t = sb.getTeam(tn);
        if (t == null) t = sb.registerNewTeam(tn);
        // vždy přepsat (starý tým z dřívější verze by držel textový prefix)
        t.prefix(g ? glyphBadge(r) : textBadge(r));
        t.color(r == Rank.HRAC ? NamedTextColor.GRAY : NamedTextColor.WHITE);
        if (!t.hasEntry(p.getName())) t.addEntry(p.getName());
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        apply(e.getPlayer());
        Rank r = of(e.getPlayer());
        if (r != Rank.HRAC) e.joinMessage(U.c(r.color + r.name() + " " + nameColor(r) + e.getPlayer().getName() + " &7přiletěl na Mars!"));
    }

    @EventHandler public void quit(PlayerQuitEvent e) { packed.remove(e.getPlayer().getUniqueId()); declined.remove(e.getPlayer().getUniqueId()); perms.remove(e.getPlayer().getUniqueId()); }

    @EventHandler public void pack(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) { packed.add(e.getPlayer().getUniqueId()); declined.remove(e.getPlayer().getUniqueId()); }
        else if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD || e.getStatus() == PlayerResourcePackStatusEvent.Status.DISCARDED) { packed.remove(e.getPlayer().getUniqueId()); declined.add(e.getPlayer().getUniqueId()); }
    }

    @EventHandler(priority = EventPriority.HIGH) public void chat(AsyncChatEvent e) {
        Rank r = of(e.getPlayer());
        String name = e.getPlayer().getName();
        String lvl = pl.levels.tag(e.getPlayer());
        e.renderer((source, displayName, message, viewer) -> {
            // pack je povinný (require-resource-pack) -> obrázek ranku ukazuj vždy; jen kdo pack odmítl, vidí text
            boolean glyph = !(viewer instanceof Player v) || !declined.contains(v.getUniqueId());
            Component head = Component.object(net.kyori.adventure.text.object.ObjectContents.playerHead().id(source.getUniqueId()).name(name).hat(true).build());
            return (glyph ? glyphBadge(r) : textBadge(r)).append(head).append(Component.text(" ")).append(U.c(lvl + nameColor(r) + name + "&8: &f")).append(message);
        });
    }
}
