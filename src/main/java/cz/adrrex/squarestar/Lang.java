package cz.adrrex.squarestar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Jazyk hráče (cs / en). Čeština je výchozí, angličtina se dělá překladem frází. */
public final class Lang {
    private Lang() {}
    public static final NamespacedKey KEY = new NamespacedKey("squarestar", "lang");
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String[]> D = new ArrayList<>();

    public static boolean chosen(Player p) { return p.getPersistentDataContainer().has(KEY, PersistentDataType.STRING); }
    public static boolean en(Player p) { return p != null && "en".equals(p.getPersistentDataContainer().get(KEY, PersistentDataType.STRING)); }
    public static void set(Player p, String lang) { p.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, lang); }

    /** přeloží text (s &-barvami) podle jazyka hráče */
    public static String t(Player p, String s) {
        if (s == null || !en(p)) return s;
        for (String[] e : D) if (s.contains(e[0])) s = s.replace(e[0], e[1]);
        return s;
    }

    /** přeloží jméno a popis itemu (pro menu a lobby itemy) */
    public static ItemStack item(Player p, ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !en(p) || !it.hasItemMeta()) return it;
        ItemMeta m = it.getItemMeta();
        if (m.hasDisplayName()) m.displayName(comp(p, m.displayName()));
        if (m.hasLore()) { List<Component> l = new ArrayList<>(); for (Component c : Objects.requireNonNull(m.lore())) l.add(comp(p, c)); m.lore(l); }
        it.setItemMeta(m);
        return it;
    }
    private static Component comp(Player p, Component c) {
        return LEG.deserialize(t(p, LEG.serialize(c))).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private static void a(String cs, String en) { D.add(new String[]{cs, en}); }
    static {
        // --- menu, lobby, itemy ---
        a("Minihry na Marsu", "Minigames on Mars"); a("Minihry", "Minigames"); a("pravý klik", "right click");
        a("Kosmetika", "Cosmetics"); a("Hlasování o mapě", "Map voting"); a("Obchod", "Shop");
        a("Zpět na spawn", "Back to spawn"); a("Teleport na náměstí", "Teleport to the plaza");
        a("Opustit hru", "Leave game"); a("Vyber si minihru na Marsu", "Pick a minigame on Mars");
        a("Klikni pro připojení", "Click to join"); a("Klikni pro hru", "Click to play"); a("Klikni pro hlas", "Click to vote");
        a("Klikni pro nasazení", "Click to equip"); a("Klikni", "Click");
        a("Celkem hraje", "Playing"); a("Hráči:", "Players:"); a("Čeká:", "Waiting:"); a("nikdo nehraje", "nobody playing"); a(" hraje", " playing");
        a("Stopy za tebou a 3D čepice", "Trails behind you and 3D hats"); a("Stopy za tebou", "Trails"); a("3D čepice", "3D hats");
        a("Částice, když chodíš po lobby", "Particles while walking in the lobby"); a("Sundat vše", "Remove all");
        a("Potřebuje resource pack", "Needs the resource pack"); a("Zavřít", "Close"); a("Zpět", "Back");
        a("Pravý klik s klíčem", "Right click with a key"); a("Levý klik = náhled", "Left click = preview");
        a("Pravým klikem otevři bednu", "Right click a crate to open it"); a("na Marsu (spawn).", "on Mars (spawn).");
        a("Obyčejná bedna", "Common crate"); a("Vzácná bedna", "Rare crate"); a("Legendární bedna", "Legendary crate");
        a("- klíč", "- key"); a("- náhled", "- preview"); a("Šance:", "Chance:"); a("Výhra", "Win"); a("VÝHRA!", "YOU WON!");
        a("Potřebuješ ", "You need "); a("Klíče získáš za výhry v minihrách.", "Get keys by winning minigames.");
        a("vyhrál z ", "won from "); a("Tvůj hlas", "Your vote"); a("Hlasů:", "Votes:");
        // --- vesmírné itemy ---
        a("Laserová pistole", "Laser pistol"); a("Plazmový meč", "Plasma sword"); a("Meteoritový granát", "Meteor grenade");
        a("Gravitační boty", "Gravity boots"); a("Kyslíková helma", "Oxygen helmet"); a("Měsíční kámen", "Moon rock");
        a("Vesmírná porce", "Space ration"); a("Hvězdný úlomek", "Star shard");
        a("Pravý klik: laserový paprsek", "Right click: laser beam"); a("Pravý klik: krátce se vzneseš", "Right click: hover for a moment");
        a("Pravý klik: vystřelí tě nahoru", "Right click: launches you up"); a("Pravý klik", "Right click");
        a("Poškození", "Damage"); a("Nabíjení", "Cooldown"); a("Hoď ho! Výbuch neničí mapu", "Throw it! The blast won't break the map");
        a("Obuj si je: skáčeš mnohem výš", "Wear them: you jump much higher"); a("Dýchání pod vodou + noční vidění", "Water breathing + night vision");
        a("Zapálí nepřítele plazmou", "Sets enemies on fire with plasma"); a("Vzácný sběratelský kousek", "Rare collectible");
        a("Jídlo astronautů", "Astronaut food");
        // --- kosmetika ---
        a("Ohnivá stopa", "Fire trail"); a("Srdíčka", "Hearts"); a("Hvězdný prach", "Stardust"); a("Duše", "Souls");
        a("Vesmírná magie", "Space magic"); a("Mini raketa", "Mini rocket"); a("Planeta Země", "Planet Earth"); a("Hvězda", "Star");
        // --- hry: názvy a popisy ---
        a("Schovávaná", "Hide and Seek"); a("PvP Aréna", "PvP Arena");
        a("Skáčeš po plošinách nad propastí. Kdo dřív v cíli, vyhrává.", "Jump across platforms over the void. First to the finish wins.");
        a("Kopej bloky pod soupeři. Poslední nahoře vyhrává.", "Dig blocks under your rivals. Last one standing wins.");
        a("Bloky pod tebou mizí. Nezastavuj se!", "Blocks vanish under you. Don't stop!");
        a("Každý má svůj asteroid s truhlou. Poslední přeživší vyhrává.", "Everyone has an asteroid with a chest. Last survivor wins.");
        a("Braň svou postel, znič cizí. Týmová hra.", "Defend your bed, destroy the others. Team game.");
        a("Mezi vámi je vrah. Detektiv ho musí najít.", "There's a murderer among you. The detective must find them.");
        a("Proměň se v blok a schovej se před hledačem.", "Turn into a block and hide from the seeker.");
        a("Stojíš na věži a dostáváš náhodné itemy. Shoď ostatní!", "Stand on a tower and get random items. Knock the others off!");
        a("Boj všichni proti všem. Máš 3 životy a vybavení.", "Free for all. You have 3 lives and gear.");
        a("Vystrč soupeře z talíře. Jen odhazování, žádné poškození.", "Push rivals off the platform. Knockback only, no damage.");
        a("Postav téma za 5 minut. Pak všichni hlasují.", "Build the theme in 5 minutes. Then everyone votes.");
        // --- průběh hry ---
        a("Čekáme na hráče", "Waiting for players"); a("Start za", "Starting in"); a("Málo hráčů, odpočet zrušen.", "Not enough players, countdown cancelled.");
        a("se připojil", "joined"); a("odešel ze hry.", "left the game."); a("Opustil jsi hru.", "You left the game.");
        a("Nejsi v žádné hře.", "You are not in a game."); a("Už jsi ve hře.", "You are already in a game."); a("Napiš", "Type");
        a("Všechny arény jsou plné, zkus to za chvíli.", "All arenas are full, try again soon.");
        a("Neznámá hra. Možnosti:", "Unknown game. Options:"); a("Stavím mapu", "Building map"); a("Připravuji čekárnu...", "Preparing the lobby...");
        a("Vyhrála mapa:", "Winning map:"); a("Hlasuješ pro mapu", "You voted for map"); a("Hlasování už skončilo.", "Voting has ended.");
        a("Vyhrál jsi! Dostaneš", "You won! You get"); a("Vyhrál:", "Winner:"); a("x klíč", "x key"); a("na spawnu.", "at spawn.");
        a("je vyřazen!", "is eliminated!"); a("VYŘAZEN", "ELIMINATED"); a("zemřel", "died"); a("byl vyřazen hráčem", "was eliminated by");
        a("Už se neoživíš!", "You won't respawn!"); a("už se neoživí.", "won't respawn."); a("oživení za 5 s", "respawn in 5 s");
        a("Sleduješ hru.", "You are spectating."); a("= odejít", "= leave"); a("Čas vypršel!", "Time's up!");
        a("Zbývá:", "Left:"); a("Zbývá", "Left"); a("zbývá", "left"); a("Čas:", "Time:"); a("Naživu:", "Alive:"); a("Zbývající životy", "Lives left");
        a("Super, jen tak dál!", "Great, keep going!"); a("doskákal do cíle za", "reached the finish in");
        a("Bloky pod tebou mizí! Běhej a skákej, nepadej dolů!", "Blocks vanish under you! Run and jump, don't fall!");
        a("Kopej sníh pod soupeři! Za každý blok dostaneš sněhovou kouli.", "Dig snow under your rivals! Each block gives you a snowball.");
        a("Doskákej na", "Jump to the"); a("smaragdovou plošinu", "emerald platform"); a("Zlaté bloky jsou", "Gold blocks are"); a("checkpointy", "checkpoints");
        a("Otevři truhlu na svém asteroidu!", "Open the chest on your asteroid!"); a("Uprostřed jsou lepší věci.", "Better loot in the middle.");
        a("Za 3 minuty se truhly doplní.", "Chests refill in 3 minutes."); a("Truhly byly doplněny!", "Chests refilled!");
        a("Braň svou postel!", "Defend your bed!"); a("POSTEL ZNIČENA!", "BED DESTROYED!"); a("Svou postel ničit nemůžeš!", "You can't break your own bed!");
        a("Za 5 minut končí hra! Rychle zničte postele!", "The game ends in 5 minutes! Destroy the beds!");
        a("Nemáš dost surovin!", "Not enough resources!"); a("Suroviny:", "Resources:"); a("železo", "iron"); a("zlato", "gold"); a("diamanty", "diamonds");
        a("smaragdy", "emeralds"); a("za základnou", "behind your base"); a("na rohových ostrovech", "on the corner islands"); a("uprostřed", "in the middle");
        a("astronaut", "astronaut"); a("železa", "iron"); a("diamantů", "diamonds"); a("smaragdů", "emeralds"); a("Cíl:", "Goal:");
        a("Tým ", "Team "); a("Červení", "Red"); a("Modří", "Blue"); a("Zelení", "Green"); a("Žlutí", "Yellow");
        a("Najdi vraha a zastřel ho lukem!", "Find the murderer and shoot them with the bow!"); a("Detektivův luk", "Detective's bow");
        a("Vražedný meč", "Murder sword"); a("Máš meč! Jeden zásah = smrt.", "You have a sword! One hit = death.");
        a("Za 10 s dostaneš meč. Zabij všechny!", "You get a sword in 10 s. Kill everyone!");
        a("Přežij! Za 10 zlatých dostaneš luk.", "Survive! 10 gold = a bow."); a("Za 10 zlatých máš luk!", "10 gold - you got a bow!");
        a("Zastřelil jsi nevinného! Umíráš taky.", "You shot an innocent! You die too."); a("Luk spadl na zem! Nevinní ho můžou sebrat.", "The bow dropped! Innocents can pick it up.");
        a("Hledači vyrazili!", "Seekers are out!"); a("Hledači:", "Seekers:"); a("Schovaní:", "Hidden:"); a("HLEDÁŠ", "SEEKING");
        a("Hledačův meč", "Seeker's sword"); a("Zásah = našel jsi ho", "Hit = found"); a("Jsi blok! Máš", "You are a block! You have");
        a("s na úkryt.", "s to hide."); a("s tě pustíme. Najdi všechny bloky!", "s you're released. Find all blocks!");
        a("byl nalezen hráčem", "was found by"); a("a teď hledá taky!", "and is now seeking too!"); a("Teď hledáš ostatní.", "Now you seek the others.");
        a("Postav se mezi stejné bloky a nehýbej se!", "Stand among matching blocks and don't move!"); a("Schovávání:", "Hiding:");
        a("Každý má", "Everyone has"); a("3 životy", "3 lives"); a("Jetpack a granáty jsou v inventáři!", "Jetpack and grenades are in your inventory!");
        a("Vystrč ostatní z talíře! Poslední nahoře vyhrává.", "Push the others off! Last one standing wins.");
        a("dostaneš náhodný item. Postav most a shoď ostatní!", "you get a random item. Build a bridge and knock the others off!"); a("Každé", "Every");
        a("Téma:", "Theme:"); a("minut na stavbu!", "minutes to build!"); a("na stavbu!", "to build!"); a("Stav jen na svém pozemku. Máš kreativ!", "Build only on your plot. You have creative!");
        a("Za svou stavbu hlasovat nemůžeš.", "You can't vote for your own build."); a("Hlasuj itemy v ruce", "Vote with the items in your hand");
        a("Hlasování:", "Voting:"); a("Úžasné", "Amazing"); a("Dobré", "Good"); a("Slabé", "Weak"); a("Hrozné", "Terrible"); a(" bodů", " points"); a(" hlasů)", " votes)");
        a("Dům na Marsu", "House on Mars"); a("Herní konzole", "Game console"); a("Kočka", "Cat"); a("Loď", "Ship"); a("Maják", "Lighthouse");
        a("Mimozemšťan", "Alien"); a("Pirátská loď", "Pirate ship"); a("Orbitální stanice", "Orbital station"); a("Kráter", "Crater");
        // --- obecné ---
        a("Vítej na Marsu", "Welcome to Mars"); a("přiletěl na Mars!", "landed on Mars!"); a("Nízká gravitace všude!", "Low gravity everywhere!");
        a("nízká gravitace", "low gravity"); a("Bedny", "Crates"); a("jsou přímo před tebou", "are right in front of you");
        a("NPC za tebou", "NPCs behind you"); a("nebo", "or"); a("Kompas", "Compass"); a("= minihry", "= minigames");
        a("Dvojskok:", "Double jump:"); a("skoč 2x", "jump twice"); a("Hra:", "Game:"); a("Lobby", "Lobby"); a("Online:", "Online:");
        a("Na tohle nemáš práva.", "You don't have permission."); a("Hráč není online.", "Player is not online.");
        a("Česká video platforma", "Czech video platform");
        D.sort((x, y) -> y[0].length() - x[0].length()); // nejdelší fráze první
    }
}
