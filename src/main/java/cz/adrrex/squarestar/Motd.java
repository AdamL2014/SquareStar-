package cz.adrrex.squarestar;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Hezká zpráva v seznamu serverů (MOTD) + režim údržby (připojí se jen tým: builder, helper, mod, admin, owner). */
public final class Motd implements Listener {
    private final SquareStar pl;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public Motd(SquareStar pl) { this.pl = pl; }

    public boolean maintenance() { return pl.getConfig().getBoolean("maintenance.enabled", false); }
    public void setMaintenance(boolean on) {
        pl.getConfig().set("maintenance.enabled", on);
        pl.saveConfig();
        if (on) for (Player p : Bukkit.getOnlinePlayers()) if (!staff(p)) p.kick(kickMsg());
    }

    public boolean staff(Player p) {
        if (p.isOp() || p.hasPermission("squarestar.admin")) return true;
        return switch (pl.ranks.of(p)) { case OWNER, ADMIN, DEVELOPER, MODERATOR, HELPER, BUILDER -> true; default -> false; };
    }

    private Component kickMsg() {
        return MM.deserialize("<gradient:#ff8a1f:#ff2d2d><bold>SQUARESTAR</bold></gradient>\n\n<red><bold>⚠ ÚDRŽBA</bold></red>\n<gray>Server se právě vylepšuje. Zkus to za chvíli!</gray>\n\n<dark_gray>Připojit se teď může jen tým (Builder, Helper, Moderátor, Admin, Owner).");
    }

    @EventHandler public void ping(PaperServerListPingEvent e) {
        String top = "<gradient:#ffd23f:#ff8a1f:#ff2d2d><bold>        ✦ SQUARESTAR ✦</bold></gradient> <dark_gray>[<gray>1.21.11</gray>]";
        String bottom;
        if (maintenance()) {
            bottom = "       <red><bold>⚠ ÚDRŽBA</bold></red> <gray>– brzy zpět, stavíme!</gray>";
            e.setVersion("§c⚠ Údržba");
            e.setProtocolVersion(-1); // místo počtu hráčů ukáže červeně „Údržba"
        } else {
            int n = e.getNumPlayers();
            String w = n == 1 ? "hráč" : (n >= 2 && n <= 4) ? "hráči" : "hráčů";
            bottom = "      <green>●</green> <white><bold>" + n + "</bold></white> <gray>" + w + " online</gray> <dark_gray>•</dark_gray> <aqua>18 miniher</aqua>";
        }
        e.motd(MM.deserialize(top + "\n" + bottom));
    }

    @EventHandler(priority = EventPriority.LOWEST) public void join(PlayerJoinEvent e) {
        if (maintenance() && !staff(e.getPlayer())) {
            e.joinMessage(null);
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(pl, () -> p.kick(kickMsg()));
        }
    }
}
