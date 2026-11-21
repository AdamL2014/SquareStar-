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
            {"&4&lCO JE NOVÉ\n&8verze 4.3\n\n&0✦ Builder event:\n&0   cedule &6Pozemek\n&6   hráče&0 + nápis\n&0   v liště\n&0✦ Pořadatel létá\n&0   vysoko nad eventem\n&0✦ &dOvládání eventu&0:\n&0   start, konec, čas,\n&0   pauza, teleporty"},
            {"&4&lCO JE NOVÉ\n&8verze 4.2\n\n&0✦ Nové eventy:\n&8 ■ &0Bedrocková\n&0   místnost\n&6 ■ &0Builder event –\n&0   každý svůj pozemek\n&0✦ Event NPC před\n&0   bednami\n&0✦ 3D meteority"},
            {"&4&lCO JE NOVÉ\n&8verze 4.1\n\n&0✦ &5Mars&0 inventář a truhly\n&0✦ &6Hudba&0 v lobby (/hudba)\n&0✦ &d8 nových čepic\n&0✦ &bBalónky\n&0✦ 9 nových stop\n&0✦ Lepší &cmapa spawnu\n\n&7(další strana →)"},
            {"&4&lCO JE NOVÉ 2\n\n&0✦ &1/spawn&0 a &1/lobby\n&0✦ /msg, /r, /ping, /afk,\n&0   /report, /pravidla...\n&0✦ Jetpack ve hrách:\n&0   3× a pak pauza 14 s\n&0✦ Meteory v menu ☄\n&0✦ Žebříčky po stranách"},
            {"&4&lVERZE 4.0\n\n&0✦ &5Přátelé&0 – /pritel\n&0✦ &6Žebříčky&0 v lobby\n&0✦ &1Výběr týmu&0 v BedWars\n&0✦ &dEventy&0 – /event\n&0✦ &8Černá díra&0 v BedWars\n&0✦ Automatické aktualizace"},
            {"&4&lTIPY\n\n&0/help &8- všechny příkazy\n&0/pritel pridat <jméno>\n&0/stats &8- statistiky\n&0/fly &8- VIP+\n&0/kosmetika\n&0/jazyk &8- language\n\n&5Hodně štěstí na Marsu!"}};

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
