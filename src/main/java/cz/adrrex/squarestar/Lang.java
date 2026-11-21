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
    /** fráze se nahrazuje jen jako celé slovo (aby se z „zlatou" nestalo „goldu") */
    private static final List<java.util.regex.Pattern> P = new ArrayList<>();

    public static boolean chosen(Player p) { return p.getPersistentDataContainer().has(KEY, PersistentDataType.STRING); }
    public static boolean en(Player p) { return p != null && "en".equals(p.getPersistentDataContainer().get(KEY, PersistentDataType.STRING)); }
    public static void set(Player p, String lang) { p.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, lang); }

    /** přeloží text (s &-barvami) podle jazyka hráče */
    /** poslední hráč, pro kterého se překládalo (Gui podle něj pozná hráče z Bedrocku) */
    public static Player last;
    public static String t(Player p, String s) {
        last = p;
        if (s == null || !en(p)) return s;
        for (int i = 0; i < D.size(); i++) if (s.contains(D.get(i)[0])) s = P.get(i).matcher(s).replaceAll(java.util.regex.Matcher.quoteReplacement(D.get(i)[1]));
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
        a("Hraje:", "Playing:"); a("= vypnout", "= turn off"); a("Hudba v lobby", "Lobby music"); a("vypnuta", "off"); a("zapnuta!", "on!"); a("hudba zap/vyp", "music on/off");
        a("Jetpack se přehřál!", "Jetpack overheated!"); a("Pauza", "Cooldown"); a("do přehřátí", "until overheat"); a("Moc vysoko – jetpack už víc nevyletí!", "Too high – the jetpack won't go higher!");
        a("…nebo klikni na", "…or click the"); a("před bednami na spawnu!", "in front of the crates at spawn!"); a("Klikni = připojit se", "Click = join");
        // --- v4.1: příkazy, kosmetika, nápověda ---
        a("SQUARESTAR – PŘÍKAZY", "SQUARESTAR – COMMANDS"); a("menu miniher", "minigame menu"); a("zpět na Mars", "back to Mars"); a("opustit hru", "leave the game");
        a("soukromé zprávy", "private messages"); a("stopy, čepice, balónky", "trails, hats, balloons"); a("emoty", "emotes"); a("statistiky", "stats");
        a("co je nové", "what's new"); a("připojit se k eventu", "join an event"); a("létání", "flying"); a("nahlásit", "report"); a("zeptat se týmu", "ask the staff");
        a("Tenhle příkaz neexistuje.", "This command does not exist."); a("Nápověda:", "Help:"); a("Tento server běží na", "This server runs on");
        a("vlastních pluginech", "custom plugins"); a("na míru od", "made by"); a("Tenhle příkaz je jen pro tým serveru.", "This command is for staff only.");
        a("Nemáš komu odpovědět.", "Nobody to reply to."); a("je AFK", "is AFK"); a("je teď", "is now"); a("už není AFK", "is no longer AFK");
        a("Další report můžeš poslat za minutu.", "You can send another report in a minute."); a("Díky! Report jsme poslali týmu serveru.", "Thanks! The report was sent to the staff.");
        a("Zpráva odeslána týmu. Brzy ti někdo odpoví.", "Message sent to the staff. Someone will answer soon."); a("Ty", "You");
        a("PRAVIDLA SQUARESTAR", "SQUARESTAR RULES"); a("Buď slušný – žádné nadávky, urážky ani spam.", "Be nice – no swearing, insults or spam.");
        a("Žádné cheaty, hacky, makra ani X-ray.", "No cheats, hacks, macros or X-ray."); a("Nezneužívej chyby (bugy) – nahlas je přes", "Don't abuse bugs – report them with");
        a("Žádná reklama na jiné servery.", "No advertising other servers."); a("Nesdílej osobní údaje (adresa, telefon...).", "Don't share personal info (address, phone...).");
        a("Poslouchej tým serveru.", "Listen to the server staff."); a("Porušení = varování, kick nebo ban.", "Breaking rules = warning, kick or ban.");
        a("Discord zatím nemáme – sleduj", "No Discord yet – follow"); a("Online", "Online"); a("Jsi vyléčený", "You are healed");
        a("Létání má jen", "Flying is only for"); a("a influenceři", "and influencers"); a("Balónky", "Balloons"); a("Letí nad tebou v lobby", "Floats above you in the lobby");
        a("Sundat vše", "Remove all"); a("Nasazeno", "Equipped"); a("Částice, když chodíš po lobby", "Particles while walking in the lobby"); a("Potřebuje resource pack", "Needs the resource pack");
        a("Červený balónek", "Red balloon"); a("Modrý balónek", "Blue balloon"); a("Fialový balónek", "Purple balloon"); a("Zlatý balónek", "Gold balloon"); a("Zelený balónek", "Green balloon"); a("Bílý balónek", "White balloon");
        a("Kyslíková helma", "Oxygen helmet"); a("Koruna", "Crown"); a("Svatozář", "Halo"); a("Mimozemská tykadla", "Alien antennae"); a("Čepice s vrtulkou", "Propeller cap");
        a("Satelit", "Satellite"); a("Planeta Mars", "Planet Mars"); a("Čarodějný klobouk", "Witch hat"); a("Mini raketa", "Mini rocket"); a("Planeta Země", "Planet Earth"); a("Hvězda", "Star");
        a("Ohnivá stopa", "Fire trail"); a("Srdíčka", "Hearts"); a("Noty", "Notes"); a("Hvězdný prach", "Stardust"); a("Duše", "Souls"); a("Vesmírná magie", "Space magic"); a("Duha", "Rainbow");
        a("Sněžení", "Snowfall"); a("Elektřina", "Electricity"); a("Obláček", "Little cloud"); a("Sculk duše", "Sculk souls"); a("Kouzla", "Enchant"); a("Záře", "Glow"); a("Portál", "Portal"); a("Jiskřičky", "Sparkles");
        a("NEJVÍC VÝHER", "MOST WINS"); a("NEJVYŠŠÍ LEVEL", "HIGHEST LEVEL"); a("NEJVÍC ZABITÍ", "MOST KILLS"); a("NEJVÍC HER", "MOST GAMES");
        // --- v4.0: přátelé, eventy, žebříčky, novinky, týmy, aktualizace ---
        a("Klikni = připojit se k němu", "Click = join them"); a("Klikni = teleport k němu", "Click = teleport to them"); a("Není online", "Not online");
        a("můžeš se přidat", "you can join"); a("Redstone týden", "Redstone week"); a("V lobby", "In lobby"); a("Hraje ", "Playing ");
        a("Zatím nemáš přátele", "No friends yet"); a("Přidej si někoho:", "Add someone:"); a("Přidat kamaráda", "Add a friend"); a("Přátelé", "Friends");
        a("Kdo je online a co hraje", "Who is online and what they play"); a("ti posílá žádost o přátelství!", "sent you a friend request!");
        a("Žádost o přátelství poslána hráči", "Friend request sent to"); a("jste teď přátelé!", "are now friends!"); a("PŘIJMOUT", "ACCEPT"); a("ODMÍTNOUT", "DECLINE");
        a("se připojil na server.", "joined the server."); a("se odpojil.", "left."); a("Hráč není online.", "Player is not online.");
        a("OZNÁMIT EVENT", "ANNOUNCE EVENT"); a("SPUSTIT EVENT", "START EVENT"); a("Zrušit event", "Cancel event"); a("Pošle všem pozvánku", "Sends everyone an invite");
        a("Přihlášeno:", "Joined:"); a("Klikni = start hry", "Click = start the game"); a("Hráči se připojí přes", "Players join with"); a("Kolik lidí se může připojit", "How many people can join");
        a("Nízká (Mars)", "Low (Mars)"); a("Normální (Země)", "Normal (Earth)"); a("Klikni pro přepnutí", "Click to toggle"); a("1 hráč", "1 player"); a("Vybráno", "Selected");
        a("Pořádání eventu", "Event setup"); a("pořádá event:", "is hosting an event:"); a("KLIKNI A PŘIPOJ SE", "CLICK TO JOIN"); a("Max. hráčů:", "Max players:");
        a("Gravitace:", "Gravity:"); a("nízká", "low"); a("normální", "normal"); a("napiš", "type"); a("Jsi v eventu!", "You are in the event!");
        a("Event byl zrušen.", "The event was cancelled."); a("začíná!", "is starting!"); a("hráčů", "players");
        a("Co je nové", "What's new"); a("Novinky na serveru", "Server news"); a("NOVÁ VERZE", "NEW VERSION"); a("ZOBRAZIT NOVINKY", "SHOW NEWS"); a("CO JE NOVÉ", "WHAT'S NEW");
        a("Tvoje statistiky:", "Your stats:"); a("Výhry:", "Wins:"); a("Hry:", "Games:"); a("Zabití:", "Kills:");
        a("Vyber tým", "Choose team"); a("S kým chceš hrát?", "Who do you want to play with?"); a("Náhodný tým", "Random team"); a("Rozdělí tě hra", "The game picks for you");
        a("Tvůj tým", "Your team"); a("Klikni pro výběr", "Click to select"); a("Vybral sis tým", "You picked team"); a("Tento tým je plný – vyber jiný, ať je to fér.", "This team is full – pick another so it's fair.");
        a("Červení", "Red"); a("Modří", "Blue"); a("Zelení", "Green"); a("Žlutí", "Yellow");
        a("AKTUALIZACE", "UPDATE"); a("Restart za", "Restart in"); a("Server se aktualizuje na verzi", "Server is updating to version");
        a("Restartuje se – připoj se znovu za minutku!", "Restarting – join again in a minute!");
        a("Létání zapnuto!", "Flight enabled!"); a("Létání vypnuto.", "Flight disabled."); a("Létat můžeš jen v lobby.", "You can only fly in the lobby.");
        a("Mini černá díra", "Mini black hole"); a("Pravý klik: hodíš ji před sebe", "Right click: throw it ahead"); a("Přitáhne a podrží hráče!", "Pulls and holds players!");
        a("Zničíš ji úderem, jinak zmizí za", "Hit it to destroy it, otherwise it vanishes in"); a("Drží tě černá díra!", "A black hole holds you!"); a("Udeř do ní!", "Hit it!"); a("Černá díra", "Black hole"); a("Jsi na pozemku hráče", "You are on the plot of"); a("teď může stavět na tvém pozemku!", "can now build on your plot!");
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
        // --- nové hry a systémy (v3) ---
        a("PvP: Všichni proti všem", "PvP: Free for all"); a("Souboj jeden na jednoho. 3 životy.", "One on one duel. 3 lives.");
        a("Dva týmy po 5. Spoluhráče nezraníš.", "Two teams of 5. No friendly fire."); a("Velká bitva dvou týmů po 10.", "Big battle of two teams of 10.");
        a("Vesmírní zombíci", "Space Zombies"); a("Tým brání reaktor před vlnami mimozemských zombíků.", "Your team defends the reactor from waves of alien zombies.");
        a("Braňte reaktor!", "Defend the reactor!"); a("Mimozemští zombíci přijdou branami.", "Alien zombies come through the gates."); a("Přežijte", "Survive"); a(" vln", " waves");
        a("Další vlna za", "Next wave in"); a("Reaktor", "Reactor"); a("Vlna", "Wave"); a("VLNA", "WAVE"); a("zvládnuta!", "cleared!"); a("Odměny jsou v inventáři", "Rewards are in your inventory");
        a("Přichází mimozemský obr!", "An alien giant is coming!"); a("zombíků", "zombies"); a("Zombíci", "Zombies"); a("padl!", "fell!"); a("(oživí se po vlně)", "(respawns after the wave)");
        a("PADL JSI", "YOU FELL"); a("Oživíš se po konci vlny", "You respawn when the wave ends"); a("Oživen!", "Revived!"); a("Zpátky do boje.", "Back to the fight.");
        a("REAKTOR ZNIČEN!", "REACTOR DESTROYED!"); a("Mimozemšťané vyhráli.", "The aliens won."); a("ZÁKLADNA ZACHRÁNĚNA!", "BASE SAVED!"); a("Přežili jste všech", "You survived all");
        a("Všichni padli!", "Everyone fell!"); a("Základna je ztracena.", "The base is lost."); a("Mimozemský obr", "Alien giant");
        a("Speedrun: Free Edition", "Speedrun: Free Edition"); a("Levný vlněný svět, moby jsou jen hlavy. Posbírej díly a odleť raketou!", "A cheap wool world where mobs are just heads. Collect parts and fly away by rocket!");
        a("Svět je z vlny, moby jsou jen hlavy.", "The world is made of wool, mobs are just heads."); a("Posbírej díly a odleť raketou", "Collect parts and fly away by rocket");
        a("Měsíční kámen", "Moon rock"); a("Vesmírné zlato", "Space gold"); a("Krystal", "Crystal"); a("Palivo", "Fuel");
        a("Máš vše! Běž na zlatou rampu k raketě!", "You have everything! Go to the golden pad by the rocket!"); a("odletěl domů!", "flew home!"); a("Čas speedrunu:", "Speedrun time:");
        a("UMŘEL JSI", "YOU DIED"); a("Ztratil jsi půlku věcí. Běž dál!", "You lost half your stuff. Keep going!"); a("Levné nůžky", "Cheap shears"); a("Dřevěný meč", "Wooden sword"); a("(zdarma)", "(free)");
        a("Redstone týden", "Redstone Week"); a("Každý týden nové redstone téma.", "A new redstone theme every week."); a("Postav nápad na svém pozemku,", "Build your idea on your plot,");
        a("v neděli se hlasuje o nejlepší!", "on Sunday everyone votes for the best!"); a("Dnes se hlasuje!", "Voting today!"); a("Hlasování:", "Voting:"); a("neděle", "Sunday"); a("Klikni pro vstup", "Click to enter");
        a("Téma týdne:", "Theme of the week:"); a("Stav na svém pozemku, v neděli se hlasuje.", "Build on your plot, voting is on Sunday."); a("Minulý vítěz:", "Last winner:");
        a("Zpět do lobby", "Back to lobby"); a("Další pozemek", "Next plot"); a("pravý klik = hlas", "right click = vote"); a("Stůj na něčím pozemku.", "Stand on someone's plot.");
        a("Dnes se HLASUJE!", "VOTING today!"); a("Navštiv pozemky:", "Visit plots:"); a("Nejdřív opusť hru:", "Leave your game first:");
        a("odehraná hra", "game played"); a("výhra", "win"); a("Nový level! Jen tak dál!", "New level! Keep it up!"); a("dosáhl", "reached"); a("levelu", "level");
        a("Bonus za level", "Level bonus"); a("klíč k bedně", "crate key"); a("(na spawnu)", "(at spawn)"); a("Počkej chvilku…", "Wait a moment…"); a("Emoty:", "Emotes:");
        a("vyhráli!", "won!"); a("Tvůj tým", "Your team"); a("Přeskočit:", "Skip:"); a("Nízká gravitace", "Low gravity"); a("všude na Marsu", "everywhere on Mars");
        a("18 miniher", "18 minigames"); a("Bed Wars, SkyWars, Build Battle a další", "Bed Wars, SkyWars, Build Battle and more"); a("Vítej na Marsu!", "Welcome to Mars!");
        a("Závod roverů", "Rover Race"); a("Závod Mars roverů po ledové trati. 3 kola, kdo první v cíli!", "Mars rover race on an ice track. 3 laps, first to finish wins!");
        a("POSLEDNÍ KOLO!", "FINAL LAP!"); a("Kolo", "Lap"); a("JEĎ!", "GO!"); a("dojel do cíle jako", "finished as"); a("1. MÍSTO!", "1st PLACE!"); a("místo", "place"); a("Pozice", "Position");
        a("Dropper", "Dropper"); a("Padej šachtou, nenaraz do ničeho a dopadni do vody. 5 levelů!", "Fall down the shaft, hit nothing and land in the water. 5 levels!");
        a("Skoč do šachty, nenaraz do ničeho a dopadni do", "Jump into the shaft, hit nothing and land in the"); a("vody", "water"); a("levelů!", "levels!");
        a("prošel level", "passed level"); a("prošel všechny levely!", "passed all levels!"); a("chyb:", "fails:"); a("čas", "time"); a("Náraz!", "Crash!"); a("Znovu od začátku levelu.", "Back to the start of the level.");
        a("Nárazy", "Crashes"); a("Trámy", "Beams"); a("Díry v podlaze", "Holes in the floor"); a("Les sloupů", "Pillar forest"); a("Spirála", "Spiral"); a("Asteroidy", "Asteroids");
        a("Neonová aréna", "Neon Arena"); a("Ledový talíř", "Ice Plate"); a("Sopečné ostrovy", "Volcanic Islands"); a("Neonová dráha", "Neon Track");
        a("Krystalová mlhovina", "Crystal Nebula"); a("Laboratoř", "Laboratory"); a("Ledová stanice", "Ice Station"); a("Duhová planeta", "Rainbow Planet");
        a("Ledové šachty", "Ice Shafts"); a("Fobos sprint", "Phobos Sprint"); a("Kruhová patra", "Round Floors"); a("Hvězdná patra", "Star Floors");
        a("Sněžný měsíc", "Snowy Moon"); a("Čtvercový krystal", "Square Crystal");
        a("je NEZASTAVITELNÝ!", "is UNSTOPPABLE!");
        a("(5 zabití)", "(5 kills)");
        a("poslal meteorit na všechny před sebou!", "sent a meteor at everyone ahead!");
        a("má hvězdu!", "has a star!");
        a("má sérii", "has a streak of");
        a("3 zabití", "3 kills");
        a("bodů)! Nové téma:", "points)! New theme:");
        a("tě rozbil hvězdou!", "smashed you with a star!");
        a("===== VÝSLEDKY =====", "===== RESULTS =====");
        a("✦ VYHRÁL JSI REDSTONE TÝDEN ✦", "✦ YOU WON REDSTONE WEEK ✦");
        a("✦ VÍTĚZSTVÍ ✦", "✦ VICTORY ✦");
        a("➹ Běž k raketě!", "➹ Run to the rocket!");
        a("Levný mob", "Cheap mob");
        a("Máš", "You have");
        a("Rozbíjej barevnou vlnu", "Break colored wool");
        a("Tvůj kit:", "Your kit:");
        a("W plyn, A/D zatáčení, Shift+A/D drift, Mezerník turbo, pravý klik = item", "W gas, A/D steer, Shift+A/D drift, Space turbo, right click = item");
        a("Zatím tu nikdo nestaví.", "Nobody is building here yet.");
        a("Úkoly vidíš", "Your tasks are");
        a("vpravo", "on the right");
        a("rozbíjej", "break");
        a("šedou", "gray");
        a("žlutou", "yellow");
        a("světle modrou", "light blue");
        a("červenou", "red");
        a("vlnu, zabij", "wool, kill");
        a("3 hlavy", "3 heads");
        a("a doběhni na", "and run to the");
        a("zlatou rampu", "golden pad");
        a("JSI NEVINNÝ", "YOU ARE INNOCENT");
        a("Zelený krunýř", "Green shell");
        a("Červený krunýř", "Red shell");
        a("Banán", "Banana");
        a("+1 zabití", "+1 kill");
        a("Série:", "Streak:");
        a("✔ Vybráno", "✔ Selected");
        a("Klikni pro výběr", "Click to select");
        a("PODÍVEJ SE NA FILM", "WATCH A MOVIE");
        a("filmy", "movies");
        a("kanály", "channels");
        a("hry", "games");
        a("Tornádo!", "Tornado!");
        a("Z tvé sítě už hraje", "Players already online from your network:");
        a("REDSTONE TÝDEN", "REDSTONE WEEK");
        a("vítěz:", "winner:");
        a("Každý týden nové téma", "A new theme every week");
        a("V neděli se hlasuje!", "Voting on Sunday!");
        a("Emoty jen v lobby a v čekárně.", "Emotes only in the lobby and waiting room.");
        a("Zásah krunýřem!", "Hit by a shell!");
        a("2x legendární klíč", "2x legendary key");
        a("Uklouzl jsi po banánu!", "You slipped on a banana!");
        a("Letím!", "Flying!");
        a("Podívej se na film!", "Watch a movie!");
        a("2x zlaté jablko", "2x golden apple");
        a("32 šípů, kamenný meč", "32 arrows, stone sword");
        a("6x meteoritový granát", "6x meteor grenade");
        a("Automatická lampa", "Automatic lamp");
        a("Automatické dveře", "Automatic door");
        a("Bombardér", "Bomber");
        a("Diamantová vesta a kalhoty", "Diamond chestplate and leggings");
        a("Diamantový meč a štít", "Diamond sword and shield");
        a("Farma na cukrovou třtinu", "Sugar cane farm");
        a("Finále", "Finale");
        a("Hudební automat", "Jukebox machine");
        a("Jetpack a kyslíková helma", "Jetpack and oxygen helmet");
        a("Kalkulačka", "Calculator");
        a("Kamenný meč", "Stone sword");
        a("Kaňon Valles", "Valles Canyon");
        a("Kroužkové brnění, rychlost I", "Chainmail armor, Speed I");
        a("Kódový zámek", "Code lock");
        a("Kříže", "Crosses");
        a("Ledová kometa", "Ice Comet");
        a("Ledové měsíce", "Ice Moons");
        a("Lehké černé brnění", "Light black armor");
        a("Levná planeta", "Cheap Planet");
        a("Luk Síla II + Odhoz", "Bow Power II + Punch");
        a("Lučištník", "Archer");
        a("Marsovské šachty", "Mars Shafts");
        a("Měsíční talíř", "Moon Plate");
        a("Měsíční základna", "Moon Base");
        a("Neonové šachty", "Neon Shafts");
        a("Odpočet a raketa", "Countdown and rocket");
        a("Past na kamaráda", "Trap for a friend");
        a("Plazmový meč + laserová pistole", "Plasma sword + laser pistol");
        a("Pravý klik = navede se na hráče před tebou", "Right click = homes in on the player ahead");
        a("Pravý klik = polož za sebe", "Right click = drop behind you");
        a("Pravý klik = vystřel rovně", "Right click = shoot straight");
        a("Pravý klik = zasáhne všechny před tebou", "Right click = hits everyone ahead");
        a("Pravý klik = turbo", "Right click = turbo");
        a("Pravý klik = 6 s nezranitelnost a rychlost", "Right click = 6 s invincibility and speed");
        a("Rudá planeta", "Red Planet");
        a("Rudé kaňony", "Red Canyons");
        a("Rudý talíř", "Red Plate");
        a("Rychlost II + vyšší skok", "Speed II + higher jump");
        a("Rámy", "Frames");
        a("Sekera + štít", "Axe + shield");
        a("Skoro žádný odhoz, 4x jablko", "Almost no knockback, 4x apple");
        a("Sorter itemů", "Item sorter");
        a("Tajný vchod", "Secret entrance");
        a("Temná základna", "Dark Base");
        a("Vesmírný hangár", "Space hangar");
        a("Vlněný měsíc", "Wool Moon");
        a("Válečník", "Warrior");
        a("Výtah", "Elevator");
        a("Základna Alfa", "Alpha Base");
        a("Železné brnění", "Iron armor");
        a("Železný meč, 4x perla", "Iron sword, 4x pearl");
        a("Turbo houba", "Turbo mushroom");
        a("Hvězda", "Star");
        a("Meteorit", "Meteor");
        a("SUPER DRIFT TURBO!", "SUPER DRIFT TURBO!");
        a("Drift turbo!", "Drift turbo!");
        a("TURBO!", "TURBO!");
        a("Vyber kit", "Choose kit");
        a("Kit:", "Kit:");
        a("Opustit hru", "Leave game");
        a("Hlasování o mapě", "Map voting");
        a("Mapy:", "Maps:");
        a("Item:", "Item:");
        a("Polštář", "Pillow");
        a("Zabij hlavy", "Kill heads"); a("Běž k raketě!", "Run to the rocket!"); a("Čas:", "Time:");
        a("Hra:", "Game:");
        a("Mapa:", "Map:");
        a("hlasuje se", "voting");
        a("Start za:", "Starting in:");
        a("Čekáme na", "Waiting for");
        a("hráče", "players");
        a("Naživu:", "Alive:");
        a("Level:", "Level:");
        a("Obsidiánové věže", "Obsidian Towers");
        a("Ledové věže", "Ice Towers");
        a("Lávové věže", "Lava Towers");
        a("Marsovská dílna", "Mars Workshop");
        a("Měsíční studio", "Moon Studio");
        a("Neonové studio", "Neon Studio");
        a("Padej šachtou, nenaraz do ničeho a dopadni do vody. 8 levelů!", "Fall down the shaft, hit nothing and land in the water. 8 levels!");
        a("Mario Kart na Marsu: itemy, drift a turbo. 3 kola!", "Mario Kart on Mars: items, drift and turbo. 3 laps!");
        D.sort((x, y) -> y[0].length() - x[0].length()); // nejdelší fráze první
        for (String[] e : D) P.add(java.util.regex.Pattern.compile("(?<!(?<!&)\\p{L})" + java.util.regex.Pattern.quote(e[0]) + "(?![\\p{L}])"));
    }
}
