package cz.adrrex.squarestar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Hezké GUI: pozadí z resource packu + titulek.  = posun -8,  = posun -169 */
public final class Gui implements org.bukkit.event.Listener {
    private static final Key FONT = Key.key("squarestar", "ranks");
    public static boolean enabled = true;
    public Gui() {}

    /** ikonka s vlastním 3D/pixel modelem z resource packu */
    public static org.bukkit.inventory.ItemStack icon(String model, String name, String... lore) {
        org.bukkit.inventory.ItemStack it = U.item(org.bukkit.Material.PAPER, 1, name, lore);
        var m = it.getItemMeta(); m.setItemModel(new org.bukkit.NamespacedKey("squarestar", model)); it.setItemMeta(m);
        return it;
    }
    /** obyčejnému itemu dá vlastní ikonku z resource packu (jen Java hráčům) */
    public static org.bukkit.inventory.ItemStack skin(Player p, org.bukkit.inventory.ItemStack it, String model) {
        it = Lang.item(p, it);
        if (p != null && Ranks.bedrock(p)) return it;
        var m = it.getItemMeta(); m.setItemModel(new org.bukkit.NamespacedKey("squarestar", model)); it.setItemMeta(m);
        return it;
    }
    public static org.bukkit.inventory.ItemStack closeButton() { return U.tag(icon("icon_close", "&c&l✖ Zavřít"), "ss_btn", "close"); }

    /** při otevření našeho menu: přeložit itemy + přidat tlačítko Zavřít */
    @org.bukkit.event.EventHandler public void open(org.bukkit.event.inventory.InventoryOpenEvent e) {
        var inv = e.getInventory();
        var h = inv.getHolder();
        if (h == null || !h.getClass().getName().startsWith("cz.adrrex.squarestar") || !(e.getPlayer() instanceof org.bukkit.entity.Player p)) return;
        if (h instanceof Crates.Holder ch && ch.anim) return; // animace otevírání
        int last = inv.getSize() - 1;
        if (inv.getItem(last) == null) inv.setItem(last, closeButton());
        for (int i = 0; i < inv.getSize(); i++) { var it = inv.getItem(i); if (it != null) inv.setItem(i, Lang.item(p, it)); }
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST) public void click(org.bukkit.event.inventory.InventoryClickEvent e) {
        if ("close".equals(U.tagOf(e.getCurrentItem(), "ss_btn"))) {
            e.setCancelled(true);
            e.getWhoClicked().closeInventory();
            if (e.getWhoClicked() instanceof org.bukkit.entity.Player p) p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
        }
    }

    /** vesmírný panel (minihry, kosmetika, hlasování, obchod) */
    public static Component title(int size, String legacy) { return make((char) (0xE200 + rows(size)), legacy); }
    /** zlato-modrý panel pro bedny */
    public static Component crate(int size, String legacy) { return make((char) (0xE200 + rows(size)), legacy); }
    /** růžový panel pro kosmetiku */
    public static Component cosmetics(int size, String legacy) { return make((char) (0xE200 + rows(size)), legacy); }
    /** zelený panel pro hlasování */
    public static Component vote(int size, String legacy) { return make((char) (0xE200 + rows(size)), legacy); }
    /** dřevěný panel pro obchod */
    public static Component shop(int size, String legacy) { return make((char) (0xE200 + rows(size)), legacy); }
    /** otevírání bedny se zvýrazněným výherním políčkem */
    public static Component crateOpen(String legacy) { return make('', legacy); }

    private static int rows(int size) { return Math.max(1, Math.min(6, size / 9)); }

    private static Component make(char glyph, String legacy) {
        Component text = U.c(legacy);
        Player viewer = Lang.last; Lang.last = null;
        if (!enabled || (viewer != null && Ranks.bedrock(viewer))) return text; // Bedrock nemá Java resource pack
        Component bg = Component.text("" + glyph + "").font(FONT).color(NamedTextColor.WHITE);
        // text NESMÍ být dítě pozadí, jinak zdědí font s obrázky (čtverečky)
        return Component.text().append(bg).append(text.colorIfAbsent(NamedTextColor.WHITE)).build();
    }
}
