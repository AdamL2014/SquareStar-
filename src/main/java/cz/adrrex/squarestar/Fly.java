package cz.adrrex.squarestar;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/** /fly – výhoda ranků (VIP+, influenceři, tým). Funguje jen v lobby, ve hrách se vypne. */
public final class Fly implements CommandExecutor {
    private static final NamespacedKey KEY = new NamespacedKey("squarestar", "fly");
    public static final Set<Ranks.Rank> ALLOWED = EnumSet.complementOf(EnumSet.of(Ranks.Rank.HRAC));
    private final SquareStar pl;
    public Fly(SquareStar pl) { this.pl = pl; }

    public boolean can(Player p) { return p.isOp() || ALLOWED.contains(pl.ranks.of(p)); }
    public boolean on(Player p) { return can(p) && p.getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.BYTE, (byte) 0) == 1; }
    private boolean inLobby(Player p) { return p.getWorld() == pl.mars && pl.games.of(p) == null; }

    /** po návratu do lobby znovu zapnout */
    public void restore(Player p) { if (on(p) && inLobby(p)) p.setAllowFlight(true); }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        if (!can(p)) { U.msg(p, "&cLétání mají ranky &aVIP&c, &bMVP&c, &cYouTuber&c, &5Streamer&c a další. &7Hráč ho nemá."); return true; }
        if (!inLobby(p)) { U.msg(p, "&cLétat můžeš jen v lobby."); return true; }
        boolean now = !on(p);
        p.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) (now ? 1 : 0));
        p.setAllowFlight(now);
        if (!now) p.setFlying(false);
        U.msg(p, now ? "&a&l✈ Létání zapnuto! &7(2× mezerník)" : "&7Létání vypnuto.");
        return true;
    }
}
