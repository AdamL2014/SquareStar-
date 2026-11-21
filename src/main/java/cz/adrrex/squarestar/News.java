package cz.adrrex.squarestar;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** „Co je nové“ – kniha s novinkami. Po aktualizaci se každému ukáže jednou sama. */
public final class News implements Listener {
    private static final NamespacedKey SEEN = new NamespacedKey("squarestar", "news_seen");
    private final SquareStar pl;
    public News(SquareStar pl) { this.pl = pl; }

    /** stránky knihy – nejnovější nahoře; při každé verzi sem přidej novou stránku */
    private static final String[][] PAGES = {
            {"&4&lCO JE NOVÉ\n&8verze 4.0\n\n&0✦ &5Přátelé&0 – /pritel\n&0✦ &6Žebříčky&0 v lobby\n&0✦ &1Výběr týmu&0 v BedWars\n&0✦ &dEventy&0 – /event\n&0✦ &8Černá díra&0 v BedWars\n\n&7(další strana →)"},
            {"&4&lCO JE NOVÉ 2\n\n&0✦ Všechna menu v &cMars&0 stylu\n&0✦ &2Kooperace&0 v Redstone týdnu\n&0✦ Bariéry nejdou rozbít\n&0✦ &bFly&0 pro VIP, MVP, YouTubery…\n&0✦ Automatické &aaktualizace\n&0✦ Lepší 3D modely"},
            {"&4&lTIPY\n\n&0/pritel pridat <jméno>\n&0/ss top &8- statistiky\n&0/fly &8- létání v lobby\n&0/emote &8- emoty\n&0/ss jazyk &8- jazyk\n\n&5Hodně štěstí na Marsu!"}};

    public void open(Player p) {
        List<Component> pages = new ArrayList<>();
        for (String[] pg : PAGES) pages.add(Component.text().append(U.c(Lang.t(p, pg[0]))).build());
        p.openBook(Book.book(U.c("Novinky"), U.c("SquareStar"), pages));
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String v = pl.getPluginMeta().getVersion();
        if (v.equals(p.getPersistentDataContainer().get(SEEN, PersistentDataType.STRING))) return;
        p.getPersistentDataContainer().set(SEEN, PersistentDataType.STRING, v);
        // až po intru / titulku
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            U.msg(p, "&d&l✦ NOVÁ VERZE " + v + "! &7Co je nové: &e/novinky");
            p.sendMessage(U.c(Lang.t(p, "  &a&l[ ZOBRAZIT NOVINKY ]")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/novinky")));
        }, 120);
    }
}
