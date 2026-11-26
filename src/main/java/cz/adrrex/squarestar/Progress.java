package cz.adrrex.squarestar;

import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.*;

/**
 * Progres jako na velkých serverech: denní odměna se sérií, 3 denní úkoly, úspěchy a profil hráče.
 * Vše se ukládá do dat hráče (PersistentDataContainer), takže to přežije update i restart.
 */
public final class Progress implements CommandExecutor, Listener {
    private final SquareStar pl;
    public Progress(SquareStar pl) { this.pl = pl; }

    private static NamespacedKey k(String s) { return new NamespacedKey("squarestar", s); }
    private static final NamespacedKey DAILY_LAST = k("daily_last"), DAILY_STREAK = k("daily_streak"),
            Q_DATE = k("q_date"), Q_IDS = k("q_ids"), Q_PROG = k("q_prog"), Q_DONE = k("q_done"), Q_COUNT = k("q_count"), ACH = k("ach"), CATS = k("cats");

    private static String str(Player p, NamespacedKey key, String def) { return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, def); }
    private static int num(Player p, NamespacedKey key) { return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0); }
    private static void set(Player p, NamespacedKey key, String v) { p.getPersistentDataContainer().set(key, PersistentDataType.STRING, v); }
    private static void set(Player p, NamespacedKey key, int v) { p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, v); }
    private static String today() { return LocalDate.now().toString(); }

    // =================== DENNÍ ODMĚNA ===================
    public boolean dailyReady(Player p) { return !today().equals(str(p, DAILY_LAST, "")); }
    public int streak(Player p) { return num(p, DAILY_STREAK); }

    public void claimDaily(Player p) {
        if (!dailyReady(p)) { U.msg(p, "&7Dnešní odměnu už máš. &eDalší bude zítra!"); U.sound(p, Sound.ENTITY_VILLAGER_NO, 1); return; }
        String last = str(p, DAILY_LAST, "");
        int s = last.equals(LocalDate.now().minusDays(1).toString()) ? streak(p) + 1 : 1;
        set(p, DAILY_LAST, today());
        set(p, DAILY_STREAK, s);
        int day = (s - 1) % 7 + 1;                                  // 1..7, pak znovu dokola
        int coins = 40 + day * 15, xp = 50 + day * 10;
        pl.coins.add(p, coins, "denní odměna – den " + day);
        pl.levels.add(p, xp, "denní odměna");
        if (day == 7) {
            ItemStack key = pl.crates.key("rare", 1);
            if (key != null) for (ItemStack left : p.getInventory().addItem(key).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            U.msg(p, "&d&l★ 7. den v řadě! &fBonus: &9Vzácný klíč");
        }
        U.title(p, "&6&lDENNÍ ODMĚNA", "&eSérie: &f" + s + " " + (s == 1 ? "den" : s < 5 ? "dny" : "dní"));
        U.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
        checkAch(p);
    }

    // =================== DENNÍ ÚKOLY ===================
    /** {id, popis, cíl, druh, mince, xp}; druh: games | wins | kills | cat:<KATEGORIE> */
    private static final Object[][] QUESTS = {
            {"g3", "Odehraj 3 hry", 3, "games", 60, 80},
            {"g6", "Odehraj 6 her", 6, "games", 120, 150},
            {"w1", "Vyhraj 1 hru", 1, "wins", 80, 100},
            {"w3", "Vyhraj 3 hry", 3, "wins", 180, 220},
            {"k5", "Vyřaď 5 hráčů", 5, "kills", 80, 100},
            {"k15", "Vyřaď 15 hráčů", 15, "kills", 170, 200},
            {"cb", "Odehraj 2 bojové hry", 2, "cat:BATTLE", 70, 90},
            {"cr", "Odehraj 2 závodní hry", 2, "cat:RACE", 70, 90},
            {"cf", "Odehraj 2 zábavné hry", 2, "cat:FUN", 70, 90},
            {"ch", "Odehraj 2 schovávací hry", 2, "cat:HIDE", 70, 90},
            {"cp", "Odehraj 2 PvP duely/arény", 2, "cat:PVP", 70, 90},
    };

    private static Object[] quest(String id) { for (Object[] q : QUESTS) if (q[0].equals(id)) return q; return null; }

    /** dnešní úkoly; po půlnoci se vylosují nové */
    public List<String> quests(Player p) {
        if (!today().equals(str(p, Q_DATE, ""))) {
            List<Object[]> pool = new ArrayList<>(Arrays.asList(QUESTS));
            Collections.shuffle(pool, new Random(p.getUniqueId().getMostSignificantBits() ^ LocalDate.now().toEpochDay()));
            List<String> pick = new ArrayList<>();
            Set<String> kinds = new HashSet<>();
            for (Object[] q : pool) { if (pick.size() == 3) break; if (kinds.add((String) q[3])) pick.add((String) q[0]); }
            set(p, Q_DATE, today()); set(p, Q_IDS, String.join(",", pick)); set(p, Q_PROG, "0,0,0"); set(p, Q_DONE, 0);
        }
        return Arrays.asList(str(p, Q_IDS, "").split(","));
    }
    private int[] prog(Player p) {
        String[] s = str(p, Q_PROG, "0,0,0").split(",");
        int[] r = new int[3];
        for (int i = 0; i < 3 && i < s.length; i++) try { r[i] = Integer.parseInt(s[i]); } catch (NumberFormatException ignored) {}
        return r;
    }

    private static String catOf(GameType t) {
        if (GameType.PVP_MODES.contains(t)) return "PVP";
        for (GameManager.Cat c : GameManager.Cat.values()) if (c.games.contains(t)) return c.name();
        return "";
    }

    /** zavolá hra: kind = games | wins | kills */
    public void track(Player p, String kind, GameType type, int n) {
        if (!p.isOnline()) return;
        List<String> ids = quests(p);
        int[] pr = prog(p);
        int done = num(p, Q_DONE);
        String cat = type == null ? "" : "cat:" + catOf(type);
        boolean changed = false;
        for (int i = 0; i < ids.size() && i < 3; i++) {
            Object[] q = quest(ids.get(i));
            if (q == null || (done & (1 << i)) != 0) continue;
            String qk = (String) q[3];
            if (!qk.equals(kind) && !(kind.equals("games") && qk.equals(cat))) continue;
            pr[i] = Math.min((int) q[2], pr[i] + n);
            changed = true;
            if (pr[i] >= (int) q[2]) {
                done |= 1 << i;
                set(p, Q_COUNT, num(p, Q_COUNT) + 1);
                U.msg(p, "&a&l✔ ÚKOL SPLNĚN: &f" + q[1]);
                U.sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.3f);
                pl.coins.add(p, (int) q[4], "denní úkol");
                pl.levels.add(p, (int) q[5], "denní úkol");
                if (done == 7) { U.msg(p, "&6&l★ Všechny denní úkoly hotové! &fBonus &6+100 " + Coins.ICON); pl.coins.add(p, 100, "všechny úkoly"); }
            } else if (kind.equals("games") || pr[i] % Math.max(1, (int) q[2] / 3) == 0)
                U.msg(p, "&7Úkol &f" + q[1] + "&7: &e" + pr[i] + "/" + q[2]);
        }
        if (changed) { set(p, Q_PROG, pr[0] + "," + pr[1] + "," + pr[2]); set(p, Q_DONE, done); }
        if (kind.equals("games") && type != null) {
            Set<String> cats = new HashSet<>(Arrays.asList(str(p, CATS, "").split(",")));
            if (cats.add(catOf(type))) set(p, CATS, String.join(",", cats));
        }
        checkAch(p);
    }

    // =================== ÚSPĚCHY ===================
    /** {id, název, popis, ikona, odměna mincí} */
    private static final Object[][] ACHS = {
            {"first_game", "První krok", "Odehraj svou první hru", Material.LEATHER_BOOTS, 20},
            {"games25", "Pravidelný hráč", "Odehraj 25 her", Material.IRON_BOOTS, 100},
            {"games100", "Veterán Marsu", "Odehraj 100 her", Material.DIAMOND_BOOTS, 300},
            {"first_win", "První vítězství", "Vyhraj svou první hru", Material.GOLD_NUGGET, 50},
            {"wins10", "Šampion", "Vyhraj 10 her", Material.GOLD_INGOT, 150},
            {"wins50", "Legenda", "Vyhraj 50 her", Material.GOLD_BLOCK, 500},
            {"kills50", "Lovec", "Vyřaď 50 hráčů", Material.IRON_SWORD, 150},
            {"kills250", "Predátor", "Vyřaď 250 hráčů", Material.NETHERITE_SWORD, 500},
            {"level10", "Stoupáme", "Dosáhni levelu 10", Material.EXPERIENCE_BOTTLE, 100},
            {"level25", "Hvězda", "Dosáhni levelu 25", Material.NETHER_STAR, 400},
            {"streak7", "Věrný Marťan", "Vyzvedni denní odměnu 7 dní v řadě", Material.CLOCK, 200},
            {"quests10", "Pracant", "Splň 10 denních úkolů", Material.WRITABLE_BOOK, 150},
            {"allcats", "Všestranný", "Zahraj si hru z každé kategorie", Material.COMPASS, 200},
    };

    private boolean has(Player p, String id) { return Arrays.asList(str(p, ACH, "").split(",")).contains(id); }

    private boolean reached(Player p, String id) {
        int games = pl.stats.get(p, "games"), wins = pl.stats.get(p, "wins"), kills = pl.stats.get(p, "kills");
        return switch (id) {
            case "first_game" -> games >= 1; case "games25" -> games >= 25; case "games100" -> games >= 100;
            case "first_win" -> wins >= 1; case "wins10" -> wins >= 10; case "wins50" -> wins >= 50;
            case "kills50" -> kills >= 50; case "kills250" -> kills >= 250;
            case "level10" -> pl.levels.level(p) >= 10; case "level25" -> pl.levels.level(p) >= 25;
            case "streak7" -> streak(p) >= 7; case "quests10" -> num(p, Q_COUNT) >= 10;
            case "allcats" -> new HashSet<>(Arrays.asList(str(p, CATS, "").split(","))).containsAll(List.of("PVP", "BATTLE", "HIDE", "RACE", "FUN"));
            default -> false;
        };
    }

    public void checkAch(Player p) {
        if (pl.stats == null) return;
        for (Object[] a : ACHS) {
            String id = (String) a[0];
            if (has(p, id) || !reached(p, id)) continue;
            String cur = str(p, ACH, "");
            set(p, ACH, cur.isEmpty() ? id : cur + "," + id);
            U.title(p, "&e&l🏆 ÚSPĚCH", "&f" + a[1]);
            U.msg(p, "&e&l🏆 Nový úspěch: &f" + a[1] + " &8– &7" + a[2]);
            U.sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
            pl.coins.add(p, (int) a[4], "úspěch");
        }
    }
    public int questsDone(Player p) { quests(p); return Integer.bitCount(num(p, Q_DONE) & 7); }
    public int achCount(Player p) { int n = 0; for (Object[] a : ACHS) if (has(p, (String) a[0])) n++; return n; }

    // =================== MENU ===================
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    public ItemStack menuItem(Player p) {
        ItemStack it = U.tag(U.item(Material.PLAYER_HEAD, 1, "&a&l★ Můj profil &7(pravý klik)", "&7Denní odměna, úkoly, úspěchy", "&7a tvoje statistiky"), "ss_lobby", "profile");
        if (it.getItemMeta() instanceof SkullMeta sm) { sm.setOwningPlayer(p); it.setItemMeta(sm); }
        return it;
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 45, Gui.title(45, Lang.t(p, "&f&l★ Profil – " + p.getName())));
        int games = pl.stats.get(p, "games"), wins = pl.stats.get(p, "wins"), kills = pl.stats.get(p, "kills");
        ItemStack head = U.item(Material.PLAYER_HEAD, 1, "&e&l" + p.getName(),
                "&7Level: &b✦" + pl.levels.level(p), "&7Mince: &6" + pl.coins.get(p) + " " + Coins.ICON, "",
                "&7Odehrané hry: &f" + games, "&7Výhry: &a" + wins + " &8(" + (games == 0 ? 0 : wins * 100 / games) + " %)", "&7Vyřazení hráči: &c" + kills,
                "&7Úspěchy: &e" + achCount(p) + "/" + ACHS.length, "&7Série denních odměn: &d" + streak(p));
        if (head.getItemMeta() instanceof SkullMeta sm) { sm.setOwningPlayer(p); head.setItemMeta(sm); }
        h.inv.setItem(4, head);

        boolean ready = dailyReady(p);
        int day = ready ? (dailyNextStreak(p) - 1) % 7 + 1 : (streak(p) - 1) % 7 + 1;
        h.inv.setItem(20, U.tag(U.item(ready ? Material.CHEST_MINECART : Material.MINECART, 1, ready ? "&6&lDenní odměna &a(připravená!)" : "&7&lDenní odměna &8(vyzvednuto)",
                "&7Den série: &e" + day + "/7", "&7Odměna: &6" + (40 + day * 15) + " " + Coins.ICON + " &7+ &b" + (50 + day * 10) + " XP", day == 7 ? "&d+ Vzácný klíč!" : "&8Každý 7. den vzácný klíč", "",
                ready ? "&eKlikni pro vyzvednutí" : "&7Přijď zase zítra"), "ss_prof", "daily"));

        List<String> ids = quests(p);
        int[] pr = prog(p);
        int done = num(p, Q_DONE);
        for (int i = 0; i < 3 && i < ids.size(); i++) {
            Object[] q = quest(ids.get(i));
            if (q == null) continue;
            boolean ok = (done & (1 << i)) != 0;
            h.inv.setItem(22 + i, U.item(ok ? Material.LIME_DYE : Material.PAPER, 1, (ok ? "&a&l✔ " : "&e&l") + q[1],
                    "&7Postup: " + bar(pr[i], (int) q[2]) + " &f" + pr[i] + "/" + q[2], "&7Odměna: &6" + q[4] + " " + Coins.ICON + " &7+ &b" + q[5] + " XP", "", "&8Nové úkoly každý den o půlnoci"));
        }
        h.inv.setItem(31, U.tag(U.item(Material.GOLDEN_HELMET, 1, "&e&l🏆 Úspěchy", "&7Splněno: &e" + achCount(p) + "/" + ACHS.length, "", "&eKlikni pro zobrazení"), "ss_prof", "ach"));
        h.inv.setItem(30, U.tag(U.item(Material.EMERALD, 1, "&a&l" + Coins.ICON + " Obchod", "&eKlikni pro otevření"), "ss_prof", "shop"));
        h.inv.setItem(32, U.tag(U.item(Material.COMPASS, 1, "&6&l✦ Minihry", "&eKlikni pro otevření"), "ss_prof", "games"));
        // další funkce bez příkazů (pro hráče bez chatu)
        h.inv.setItem(37, U.tag(U.item(Material.CAKE, 1, "&9&lParta", "&7Hraj s kamarády", "&eKlikni pro otevření"), "ss_prof", "party"));
        h.inv.setItem(38, U.tag(U.item(Material.NAME_TAG, 1, "&d&lEmoty", "&7Zamávej, tancuj, oslavuj", "&eKlikni pro otevření"), "ss_prof", "emotes"));
        h.inv.setItem(39, U.tag(U.item(Material.AMETHYST_SHARD, 1, "&e&lHerna", "&7Automaty o mince", "&eKlikni pro vstup"), "ss_prof", "arcade"));
        h.inv.setItem(40, U.tag(U.item(Material.FIREWORK_ROCKET, 1, "&6&lEvent", "&7Připojit se k eventu", "&eKlikni pro připojení"), "ss_prof", "event"));
        h.inv.setItem(41, U.tag(U.item(Material.PLAYER_HEAD, 1, "&d&lPřijmout žádosti o přátelství", "&eKlikni pro přijetí"), "ss_prof", "friends"));
        h.inv.setItem(42, U.tag(U.item(Material.FEATHER, 1, "&b&lLétání", "&7Zapnout / vypnout (VIP+)", "&eKlikni"), "ss_prof", "fly"));
        h.inv.setItem(43, U.tag(U.item(Material.WRITABLE_BOOK, 1, "&f&lJazyk / Language", "&eKlikni pro změnu"), "ss_prof", "lang"));
        p.openInventory(h.inv);
    }

    private int dailyNextStreak(Player p) { return str(p, DAILY_LAST, "").equals(LocalDate.now().minusDays(1).toString()) ? streak(p) + 1 : 1; }

    private static String bar(int v, int max) {
        int f = max <= 0 ? 10 : Math.min(10, v * 10 / max);
        return "&a" + "■".repeat(f) + "&8" + "■".repeat(10 - f);
    }

    public void openAch(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 36, Gui.title(36, Lang.t(p, "&f&l🏆 Úspěchy " + achCount(p) + "/" + ACHS.length)));
        int[] pos = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < ACHS.length && i < pos.length; i++) {
            Object[] a = ACHS[i];
            boolean ok = has(p, (String) a[0]);
            ItemStack it = U.item(ok ? (Material) a[3] : Material.GRAY_DYE, 1, (ok ? "&a&l✔ " : "&7&l") + a[1], "&7" + a[2], "&7Odměna: &6" + a[4] + " " + Coins.ICON, "", ok ? "&aSplněno!" : "&8Zatím nesplněno");
            if (ok) { var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(pos[i], it);
        }
        h.inv.setItem(31, U.tag(U.item(Material.ARROW, 1, "&7◀ Zpět na profil"), "ss_prof", "back"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_prof");
        if (t == null) return;
        switch (t) {
            case "daily" -> { claimDaily(p); open(p); }
            case "ach" -> openAch(p);
            case "back" -> open(p);
            case "shop" -> pl.coins.open(p);
            case "games" -> pl.games.openMenu(p);
            case "party" -> pl.party.openMenu(p);
            case "emotes" -> pl.emotes.openMenu(p);
            case "arcade" -> { p.closeInventory(); p.performCommand("herna"); }
            case "event" -> { p.closeInventory(); p.performCommand("event join"); }
            case "friends" -> { pl.friends.acceptAll(p); }
            case "fly" -> { p.closeInventory(); p.performCommand("fly"); }
            case "lang" -> pl.intro.openLang(p);
        }
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            quests(p);
            if (dailyReady(p)) {
                ChatFree.send(p, U.c(U.PREFIX + Lang.t(p, "&6&l🎁 Denní odměna je připravená! "))
                        .append(U.c(Lang.t(p, "&a&l[VYZVEDNOUT]")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/odmena"))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(U.c(Lang.t(p, "&eKlikni pro odměnu"))))));
                U.sound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.4f);
            }
        }, 100);
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) { ChatFree.send(s, "Jen pro hrace."); return true; }
        switch (c.getName()) {
            case "odmena" -> claimDaily(p);
            case "uspechy" -> openAch(p);
            case "ukoly" -> {
                List<String> ids = quests(p); int[] pr = prog(p); int done = num(p, Q_DONE);
                U.msg(p, "&6&lDenní úkoly:");
                for (int i = 0; i < 3 && i < ids.size(); i++) { Object[] q = quest(ids.get(i)); if (q != null) U.msg(p, ((done & (1 << i)) != 0 ? "&a✔ " : "&e• ") + "&f" + q[1] + " &7" + pr[i] + "/" + q[2]); }
            }
            default -> open(p);
        }
        return true;
    }
}
