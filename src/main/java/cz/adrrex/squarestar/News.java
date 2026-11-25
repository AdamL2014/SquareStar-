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
            {"&4&lCO JE NOVÉ\n&8verze 4.6.66\n\n&0✦ &a/profil&0 – profil\n&0✦ &6/odmena&0 denní\n&0   odměna se sérií\n&0✦ 3 &edenní úkoly\n&0✦ 13 &eúspěchů 🏆\n&0✦ &9/parta&0 – hraj\n&0   s kamarády\n&0✦ Hrát znovu,\n&0   Rychlá hra"},
            {"&4&lCO JE NOVÉ\n&8verze 4.6.66\n\n&0✦ Sidebar v lobby\n&0✦ Shrnutí po hře\n&0✦ Série zabití,\n&0   první krev\n&0✦ UHC Run v pravém\n&0   overworldu\n&0✦ Srdíčka schovaná\n&0   jen v lobby"},
            {"&4&lCO JE NOVÉ\n&8verze 4.6\n\n&0✦ Jedna pevná\n&0   tabule žebříčku\n&0✦ Filmovější intro\n&0✦ Nové disky: Tears,\n&0   Lava Chicken, Bounce,\n&0   Flurry, Calm 4 (Notch)\n&0✦ Hlasitost hudby\n&0✦ Herna: 8 automatů\n&0   a hra na celou\n&0   obrazovku"},
            {"&4&lCO JE NOVÉ\n&8verze 4.5.1\n\n&0✦ Skutečné &6hlavy\n&0   hráčů v chatu\n&0✦ Hlavy v žebříčcích\n&0✦ Skin se stáhne\n&0   automaticky"},
            {"&4&lCO JE NOVÉ\n&8verze 4.5\n\n&0✦ &d/herna&0 – arkáda:\n&0   Snake, Dodge, Catch\n&0✦ Lístky ★ → mince\n&0✦ Jedno &cPvP&0 NPC\n&0✦ Parkour: jezdící\n&0   plošiny, nová mapa\n&0✦ Závody: každá\n&0   trať jiná\n&0✦ Filmové intro"},
            {"&4&lCO JE NOVÉ\n&8verze 4.4.5\n\n&0✦ &a/ufo&0 – UFO nad\n&0   kopulí\n&0✦ Vyšší kopule,\n&0   malé vchody\n&0✦ &d/hudba&0 playlist\n&0✦ Disky vánočních\n&0   písniček\n&0✦ Dárky skáčou"},
            {"&4&lCO JE NOVÉ\n&8verze 4.4\n\n&0✦ &6Mince ⛃&0 za hry,\n&0   výhry a levely\n&0✦ &a/obchod&0 – klíče\n&0✦ Kosmetika se kupuje\n&0   (zdarma jen helma)\n&0✦ &c❄ Vánoce&0: strom,\n&0   sníh, hudba\n&0✦ &c/advent&0 kalendář\n&0✦ ☕ Java / ☗ Bedrock"},
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
