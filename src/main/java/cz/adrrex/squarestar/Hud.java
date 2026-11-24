package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Lobby HUD: vanilla srdíčka a hlad schovává malý extra pack (Pack.lobbyHud), který hráč má jen v lobby,
 * tady se místo nich v lobby stále zobrazuje řádek s mincemi a levelem (action bar).
 * V minihrách je pack odebraný, takže jsou vidět normální srdíčka i jídlo.
 */
public final class Hud {
    private final SquareStar pl;

    public Hud(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 40, 20);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            pl.pack.lobbyHud(p, p.getWorld() == pl.mars && pl.games.of(p) == null);   // srdíčka schovaná jen v lobby
            if (p.getWorld() != pl.mars || pl.games.of(p) != null || pl.intro.active(p)) continue;
            U.bar(p, line(p));
        }
    }

    String line(Player p) {
        return "&6● " + pl.coins.get(p) + " &7mincí  &8|  &b✦ Level " + pl.levels.level(p);
    }
}
