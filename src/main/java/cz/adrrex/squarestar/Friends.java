package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.Game;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Přátelé: /pritel pridat|prijmout|odebrat|seznam – vidíš, jestli kamarád hraje a co, a můžeš se k němu připojit. */
public final class Friends implements CommandExecutor, TabCompleter, Listener {
    private static final int MAX = 45;
    private final SquareStar pl;
    private final File file;
    private final YamlConfiguration y;
    /** komu -> od koho (žádosti platí do restartu) */
    private final Map<UUID, Set<UUID>> requests = new HashMap<>();

    public Friends(SquareStar pl) {
        this.pl = pl;
        file = pl.dataFile("squarestar-friends.yml");
        y = YamlConfiguration.loadConfiguration(file);
    }

    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    private Set<String> list(UUID u) { return new LinkedHashSet<>(y.getStringList(u + ".friends")); }
    private void setList(UUID u, Set<String> l) { y.set(u + ".friends", new ArrayList<>(l)); }
    private String name(String uuid) { return y.getString(uuid + ".name", Optional.ofNullable(Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName()).orElse("?")); }
    private void save() { try { y.save(file); } catch (IOException e) { pl.getLogger().warning("Pratele nelze ulozit: " + e.getMessage()); } }
    public boolean are(Player a, Player b) { return list(a.getUniqueId()).contains(b.getUniqueId().toString()); }

    /** kde hráč je – pro seznam přátel */
    public String status(Player o) {
        Game g = pl.games.of(o);
        if (g != null) return "&e✪ Hraje " + g.type.display + (g.joinable() ? " &a(můžeš se přidat)" : "");
        if (o.getWorld().getName().equals("ss_redstone")) return "&c⚙ Redstone týden";
        return "&a● V lobby";
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        String sub = a.length == 0 ? "seznam" : a[0].toLowerCase(Locale.ROOT);
        Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
        switch (sub) {
            case "pridat", "add" -> {
                if (t == null) { U.msg(p, "&cHráč není online. &7Použití: &e/pritel pridat <jméno>"); return true; }
                if (t == p) { U.msg(p, "&7Sám se sebou kamarád už jsi ☺"); return true; }
                if (are(p, t)) { U.msg(p, "&e" + t.getName() + " &7už je tvůj kamarád."); return true; }
                if (list(p.getUniqueId()).size() >= MAX) { U.msg(p, "&cMáš už max. " + MAX + " přátel."); return true; }
                // když už mi poslal žádost on, rovnou přijmout
                if (requests.getOrDefault(p.getUniqueId(), Set.of()).contains(t.getUniqueId())) { accept(p, t); return true; }
                requests.computeIfAbsent(t.getUniqueId(), x -> new HashSet<>()).add(p.getUniqueId());
                U.msg(p, "&aŽádost o přátelství poslána hráči &e" + t.getName());
                U.msg(t, "&e" + p.getName() + " &7ti posílá žádost o přátelství!");
                t.sendMessage(U.c(Lang.t(t, "  &a&l[ PŘIJMOUT ]")).clickEvent(ClickEvent.runCommand("/pritel prijmout " + p.getName()))
                        .append(U.c("   ")).append(U.c(Lang.t(t, "&c&l[ ODMÍTNOUT ]")).clickEvent(ClickEvent.runCommand("/pritel odmitnout " + p.getName()))));
                t.playSound(t.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
            }
            case "prijmout", "accept" -> {
                if (t == null || !requests.getOrDefault(p.getUniqueId(), Set.of()).contains(t.getUniqueId())) { U.msg(p, "&cOd tohoto hráče nemáš žádost."); return true; }
                accept(p, t);
            }
            case "odmitnout", "deny" -> {
                if (t != null && requests.getOrDefault(p.getUniqueId(), new HashSet<>()).remove(t.getUniqueId())) U.msg(p, "&7Žádost odmítnuta.");
            }
            case "odebrat", "remove" -> {
                if (a.length < 2) { U.msg(p, "&e/pritel odebrat <jméno>"); return true; }
                String id = null;
                for (String f : list(p.getUniqueId())) if (name(f).equalsIgnoreCase(a[1])) id = f;
                if (id == null) { U.msg(p, "&cTohoto hráče v přátelích nemáš."); return true; }
                Set<String> mine = list(p.getUniqueId()); mine.remove(id); setList(p.getUniqueId(), mine);
                Set<String> his = list(UUID.fromString(id)); his.remove(p.getUniqueId().toString()); setList(UUID.fromString(id), his);
                save();
                U.msg(p, "&7Odebral jsi &e" + a[1] + " &7z přátel.");
            }
            default -> open(p);
        }
        return true;
    }

    private void accept(Player p, Player t) {
        requests.getOrDefault(p.getUniqueId(), new HashSet<>()).remove(t.getUniqueId());
        requests.getOrDefault(t.getUniqueId(), new HashSet<>()).remove(p.getUniqueId());
        Set<String> a = list(p.getUniqueId()); a.add(t.getUniqueId().toString()); setList(p.getUniqueId(), a);
        Set<String> b = list(t.getUniqueId()); b.add(p.getUniqueId().toString()); setList(t.getUniqueId(), b);
        y.set(p.getUniqueId() + ".name", p.getName()); y.set(t.getUniqueId() + ".name", t.getName());
        save();
        for (Player x : List.of(p, t)) { U.msg(x, "&a&l♥ &aTy a &e" + (x == p ? t : p).getName() + " &ajste teď přátelé!"); x.playSound(x.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.6f); }
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (a.length == 1) return List.of("pridat", "prijmout", "odmitnout", "odebrat", "seznam").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    // ---------------- GUI ----------------
    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Gui.title(54, Lang.t(p, "&f&l♥ Přátelé")));
        List<String> fr = new ArrayList<>(list(p.getUniqueId()));
        // online nahoru
        fr.sort(Comparator.comparing((String f) -> Bukkit.getPlayer(UUID.fromString(f)) == null).thenComparing(this::name, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < fr.size() && i < MAX; i++) {
            UUID u = UUID.fromString(fr.get(i));
            Player o = Bukkit.getPlayer(u);
            ItemStack head = U.item(Material.PLAYER_HEAD, 1, (o != null ? "&a" : "&7") + name(fr.get(i)),
                    o != null ? status(o) : "&8● Offline", "",
                    o != null && pl.games.of(o) != null && pl.games.of(o).joinable() ? "&eKlikni = připojit se k němu" : o != null ? "&eKlikni = teleport k němu" : "&8Není online");
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setPlayerProfile(o != null ? o.getPlayerProfile() : Bukkit.createProfile(u, name(fr.get(i))));
            head.setItemMeta(m);
            h.inv.setItem(i, U.tag(head, "ss_friend", u.toString()));
        }
        if (fr.isEmpty()) h.inv.setItem(22, U.item(Material.BOOK, 1, "&e&lZatím nemáš přátele", "&7Přidej si někoho:", "&f/pritel pridat <jméno>"));
        h.inv.setItem(49, U.item(Material.WRITABLE_BOOK, 1, "&a&lPřidat kamaráda", "&7Napiš &f/pritel pridat <jméno>", "&7Odebrat: &f/pritel odebrat <jméno>"));
        h.inv.setItem(53, Gui.closeButton());
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String id = U.tagOf(e.getCurrentItem(), "ss_friend");
        if (id == null) return;
        Player o = Bukkit.getPlayer(UUID.fromString(id));
        if (o == null) return;
        p.closeInventory();
        if (pl.games.of(p) != null) { U.msg(p, "&cNejdřív opusť svou hru."); return; }
        Game g = pl.games.of(o);
        if (g != null) { if (!g.event && pl.games.joinEvent(p, g)) U.msg(p, "&aPřipojil ses ke hře kamaráda &e" + o.getName()); else U.msg(p, "&cDo jeho hry se teď připojit nedá."); }
        else if (o.getWorld() == pl.mars) { p.teleport(o); U.msg(p, "&aTeleport ke kamarádovi &e" + o.getName()); }
        else U.msg(p, "&7" + o.getName() + " je teď mimo lobby.");
    }

    @EventHandler public void join(PlayerJoinEvent e) { note(e.getPlayer(), "&a● &e%s &7se připojil na server."); }
    @EventHandler public void quit(PlayerQuitEvent e) { note(e.getPlayer(), "&8● &7%s se odpojil."); requests.remove(e.getPlayer().getUniqueId()); }

    private void note(Player p, String msg) {
        if (y.contains(p.getUniqueId() + ".friends")) y.set(p.getUniqueId() + ".name", p.getName());
        for (String f : list(p.getUniqueId())) { Player o = Bukkit.getPlayer(UUID.fromString(f)); if (o != null && o != p) U.msg(o, String.format(msg, p.getName())); }
    }
}
