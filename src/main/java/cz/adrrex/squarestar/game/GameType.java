package cz.adrrex.squarestar.game;

import cz.adrrex.squarestar.game.games.*;
import org.bukkit.Material;

public enum GameType {
    PARKOUR("Parkour na Marsu", Material.FEATHER, 1, 12, "Skáčeš po plošinách nad propastí. Kdo dřív v cíli, vyhrává."),
    SPLEEF("Spleef", Material.DIAMOND_SHOVEL, 2, 16, "Kopej bloky pod soupeři. Poslední nahoře vyhrává."),
    TNTRUN("Útěk před TNT", Material.TNT, 2, 16, "Bloky pod tebou mizí. Nezastavuj se!"),
    SKYWARS("Války asteroidů", Material.END_STONE, 2, 8, "Každý má svůj asteroid s truhlou. Poslední přeživší vyhrává."),
    BEDWARS("Bitva o postele: Mars", Material.RED_BED, 2, 16, "Braň svou postel, znič cizí. Týmová hra."),
    MURDER("Vrah mezi námi", Material.IRON_SWORD, 3, 12, "Mezi vámi je vrah. Detektiv ho musí najít."),
    HIDESEEK("Schovávaná", Material.BARREL, 2, 16, "Proměň se v blok a schovej se před hledačem."),
    LUCKYTOWER("Šťastná věž", Material.GOLD_BLOCK, 2, 12, "Stojíš na věži a dostáváš náhodné itemy. Shoď ostatní!"),
    PVP("PvP: Všichni proti všem", Material.DIAMOND_SWORD, 2, 16, "Boj všichni proti všem. Máš 3 životy a vybavení."),
    PVP1V1("PvP: 1v1", Material.IRON_SWORD, 2, 2, "Souboj jeden na jednoho. 3 životy."),
    PVP5V5("PvP: 5v5", Material.SHIELD, 2, 10, "Dva týmy po 5. Spoluhráče nezraníš."),
    PVP10V10("PvP: 10v10", Material.NETHERITE_SWORD, 2, 20, "Velká bitva dvou týmů po 10."),
    SUMO("Sumo", Material.SLIME_BALL, 2, 10, "Vystrč soupeře z talíře. Jen odhazování, žádné poškození."),
    BUILDBATTLE("Stavitelská bitva", Material.BRICKS, 2, 12, "Postav téma za 5 minut. Pak všichni hlasují."),
    ZOMBIES("Vesmírní zombíci", Material.ZOMBIE_HEAD, 1, 8, "Tým brání reaktor před vlnami mimozemských zombíků."),
    SPEEDRUN("Speedrun: Edice zdarma", Material.WHITE_WOOL, 1, 8, "Levný vlněný svět, moby jsou jen hlavy. Posbírej díly a odleť raketou!"),
    ROVER("Závod roverů", Material.MINECART, 1, 9, "Mario Kart na Marsu: itemy, drift a turbo. 3 kola!"),
    DROPPER("Pád šachtou", Material.FEATHER, 1, 12, "Padej šachtou, nenaraz do ničeho a dopadni do vody. 8 levelů!"),
    ONLYUP("Only Up", Material.LADDER, 1, 12, "Věž plná překážek: skákej jen nahoru! Spadneš = zpět na checkpoint. Kdo první dolezne na smaragd, vyhrává."),
    DUELS("Duels", Material.GOLDEN_SWORD, 2, 2, "Duel 1v1 na kola s kity. Kdo první vyhraje 3 kola, vyhrává."),
    EGGWARS("EggWars", Material.DRAGON_EGG, 2, 16, "Jako Bed Wars, ale tvé srdce týmu je dračí vejce. Klikni na cizí vejce a znič ho, vlastní braň!"),
    UHCRUN("UHC Run", Material.GOLDEN_APPLE, 2, 16, "Rychlé UHC v pravém overworldu: těž, rudy se samy taví a padají dvojnásobně. Po klidu začne PvP a hrací plocha se zmenšuje. Přežije poslední!"),
    ANNIRUN("AnniRun", Material.BEACON, 2, 16, "Zkrácená Annihilation: dva týmy, každý má Nexus (maják). Po 3 min mlať do cizího Nexusu, dokud nepadne!"),
    BRIDGE("The Bridge", Material.DIAMOND_PICKAXE, 2, 8, "Dva týmy na ostrovech spojených mostem. Skoč do cizí brány, první na 5 bodů vyhrává."),
    BEAT("Neon Beat", Material.NOTE_BLOCK, 1, 1, "Beat Saber v Minecraftu (pro 1 hráče): červené kostky letí v rytmu hudby z Minecraftu, sekej je světelným mečem a uhýbej zdem do stran!");

    /** PvP režimy (v lobby jedno NPC / jedna položka menu s výběrem) */
    public boolean isPvp() { return this == PVP || this == PVP1V1 || this == PVP5V5 || this == PVP10V10 || this == SUMO; }
    public static final java.util.List<GameType> PVP_MODES = java.util.List.of(PVP1V1, PVP, PVP5V5, PVP10V10, SUMO);

    public final String display; public final Material icon; public final int min, max; public final String desc;
    GameType(String d, Material i, int min, int max, String desc) { display = d; icon = i; this.min = min; this.max = max; this.desc = desc; }

    Game create(GameManager gm, int slot) {
        return switch (this) {
            case PARKOUR -> new Parkour(gm, slot);
            case SPLEEF -> new Spleef(gm, slot, false);
            case TNTRUN -> new Spleef(gm, slot, true);
            case SKYWARS -> new SkyWars(gm, slot);
            case BEDWARS -> new BedWars(gm, slot);
            case MURDER -> new Station(gm, slot, false);
            case HIDESEEK -> new Station(gm, slot, true);
            case LUCKYTOWER -> new LuckyTower(gm, slot);
            case PVP -> new PvpArena(gm, slot, false);
            case PVP1V1 -> new PvpArena(gm, slot, this, 1);
            case PVP5V5 -> new PvpArena(gm, slot, this, 5);
            case PVP10V10 -> new PvpArena(gm, slot, this, 10);
            case SUMO -> new PvpArena(gm, slot, true);
            case BUILDBATTLE -> new BuildBattle(gm, slot);
            case ZOMBIES -> new Zombies(gm, slot);
            case SPEEDRUN -> new SpeedrunFE(gm, slot);
            case ROVER -> new RoverRace(gm, slot);
            case DROPPER -> new Dropper(gm, slot);
            case BEAT -> new Beat(gm, slot);
            case ONLYUP -> new OnlyUp(gm, slot);
            case DUELS -> new Duels(gm, slot);
            case BRIDGE -> new TheBridge(gm, slot);
            case EGGWARS -> new EggWars(gm, slot);
            case UHCRUN -> new UHCRun(gm, slot);
            case ANNIRUN -> new AnniRun(gm, slot);
        };
    }
}
