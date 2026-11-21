package cz.adrrex.squarestar;

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/** Mince (coiny): dostaneš je za hry, výhry, levely a adventní kalendář. Utratíš je v obchodě (klíče, kosmetika). */
public final class Coins implements CommandExecutor, Listener {
    private static final NamespacedKey KEY = new NamespacedKey("squarestar", "coins");
    public static final String ICON = "⛃";
    private final SquareStar pl;
    public Coins(SquareStar pl) { this.pl = pl; }

    public int get(Player p) { return p.getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.INTEGER, 0); }
    public void set(Player p, int n) { p.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER, Math.max(0, n)); if (pl.stats != null) pl.stats.setCoins(p, Math.max(0, n)); }
    public void add(Player p, int n, String why) {
        set(p, get(p) + n);
        U.msg(p, "&6+" + n + " " + ICON + " mincí &7(" + why + ") &8| &7Máš: &6" + get(p));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.7f);
    }
    /** zaplatí, když má dost; jinak napíše kolik chybí */
    public boolean pay(Player p, int price) {
        if (price <= 0) return true;
        int has = get(p);
        if (has < price) {
            U.msg(p, "&cNemáš dost mincí! &7Potřebuješ &6" + price + " " + ICON + "&7, máš &6" + has + "&7. Mince dostaneš za hry a výhry.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return false;
        }
        set(p, has - price);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1, 1.2f);
        return true;
    }

    // ---------------- obchod ----------------
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }
    private static final Object[][] KEYS = {{"common", "&a&lObyčejný klíč", 60, Material.TRIPWIRE_HOOK}, {"rare", "&9&lVzácný klíč", 150, Material.TRIPWIRE_HOOK}, {"legendary", "&6&lLegendární klíč", 400, Material.TRIPWIRE_HOOK}};

    public ItemStack menuItem() { return U.tag(U.item(Material.EMERALD, 1, "&a&l" + ICON + " Obchod &7(pravý klik)", "&7Klíče a kosmetika za mince"), "ss_lobby", "shop"); }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 36, Gui.title(36, Lang.t(p, "&f&l" + ICON + " Obchod")));
        h.inv.setItem(4, U.item(Material.GOLD_NUGGET, 1, "&6&lTvoje mince: &e" + get(p) + " " + ICON, "&7Mince dostaneš za:", "&8• &7odehranou hru &a+5", "&8• &7výhru &a+25", "&8• &7nový level &a+20",
                "&8• &7adventní kalendář &a(v prosinci)"));
        int slot = 11;
        for (Object[] k : KEYS) {
            ItemStack it = U.item((Material) k[3], 1, (String) k[1], "&7Otevři s ním bednu na spawnu", "", "&7Cena: &6" + k[2] + " " + ICON, get(p) >= (int) k[2] ? "&eKlikni pro koupi" : "&cNemáš dost mincí");
            h.inv.setItem(slot++, Gui.skin(p, U.tag(it, "ss_shop", "key:" + k[0]), "crate_" + k[0]));
        }
        h.inv.setItem(15, U.tag(Gui.skin(p, U.item(Material.LEATHER_HELMET, 1, "&d&lKosmetika", "&7Stopy, čepice a balónky", "&7Kupuješ je přímo v menu kosmetiky", "", "&eKlikni pro otevření"), "hat_crown"), "ss_shop", "cos"));
        if (pl.xmas != null && pl.xmas.active())
            h.inv.setItem(22, U.tag(Gui.skin(p, U.item(Material.RED_SHULKER_BOX, 1, "&c&l❄ Adventní kalendář", "&7Každý den v prosinci dárek!", "", "&eKlikni pro otevření"), "gift_red"), "ss_shop", "advent"));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_shop");
        if (t == null) return;
        if (t.equals("cos")) { pl.cosmetics.open(p); return; }
        if (t.equals("advent")) { pl.xmas.openCalendar(p); return; }
        if (t.startsWith("key:")) {
            for (Object[] k : KEYS) if (k[0].equals(t.substring(4))) {
                if (!pay(p, (int) k[2])) return;
                ItemStack key = pl.crates.key((String) k[0], 1);
                if (key != null) for (ItemStack left : p.getInventory().addItem(key).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                U.msg(p, "&aKoupil sis " + k[1] + "&a! &7Otevři ho u beden na spawnu.");
            }
            open(p);
        }
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        // /mince – stav, /mince <hráč> <+-počet> – admin
        if (a.length >= 2 && s.hasPermission("squarestar.admin")) {
            Player t = Bukkit.getPlayerExact(a[0]);
            if (t == null) { s.sendMessage(U.tc(s, "&cHráč není online.")); return true; }
            try { int n = Integer.parseInt(a[1]); if (a[1].startsWith("+") || a[1].startsWith("-")) set(t, get(t) + n); else set(t, n); }
            catch (NumberFormatException ex) { s.sendMessage(U.tc(s, "&e/mince <hráč> <počet|+počet|-počet>")); return true; }
            s.sendMessage(U.tc(s, "&a" + t.getName() + " má teď &6" + get(t) + " " + ICON));
            return true;
        }
        if (s instanceof Player p) { if (l.equalsIgnoreCase("obchod") || l.equalsIgnoreCase("shop")) open(p); else U.msg(p, "&7Máš &6" + get(p) + " " + ICON + " mincí&7. Obchod: &e/obchod"); }
        return true;
    }
}
