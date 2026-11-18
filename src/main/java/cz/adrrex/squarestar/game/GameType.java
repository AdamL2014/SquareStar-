package cz.adrrex.squarestar.game;

import cz.adrrex.squarestar.game.games.*;
import org.bukkit.Material;

public enum GameType {
    PARKOUR("Mars Parkour", Material.FEATHER, 1, 12, "Skáčeš po plošinách nad propastí. Kdo dřív v cíli, vyhrává."),
    SPLEEF("Spleef", Material.DIAMOND_SHOVEL, 2, 16, "Kopej bloky pod soupeři. Poslední nahoře vyhrává."),
    TNTRUN("TNT Run", Material.TNT, 2, 16, "Bloky pod tebou mizí. Nezastavuj se!"),
    SKYWARS("SkyWars: Asteroidy", Material.END_STONE, 2, 8, "Každý má svůj asteroid s truhlou. Poslední přeživší vyhrává."),
    BEDWARS("Bed Wars: Mars", Material.RED_BED, 2, 16, "Braň svou postel, znič cizí. Týmová hra."),
    MURDER("Murder Mystery", Material.IRON_SWORD, 3, 12, "Mezi vámi je vrah. Detektiv ho musí najít."),
    HIDESEEK("Schovávaná", Material.BARREL, 2, 16, "Proměň se v blok a schovej se před hledačem."),
    LUCKYTOWER("Lucky Tower", Material.GOLD_BLOCK, 2, 12, "Stojíš na věži a dostáváš náhodné itemy. Shoď ostatní!"),
    PVP("PvP Aréna", Material.DIAMOND_SWORD, 2, 16, "Boj všichni proti všem. Máš 3 životy a vybavení."),
    SUMO("Sumo", Material.SLIME_BALL, 2, 10, "Vystrč soupeře z talíře. Jen odhazování, žádné poškození."),
    BUILDBATTLE("Build Battle", Material.BRICKS, 2, 12, "Postav téma za 5 minut. Pak všichni hlasují.");

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
            case SUMO -> new PvpArena(gm, slot, true);
            case BUILDBATTLE -> new BuildBattle(gm, slot);
        };
    }
}
