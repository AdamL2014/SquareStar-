package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;

/** Ochrana proti multiúčtům: z jedné IP adresy se připojí max N hráčů (výchozí 5). Owner a Admin mají výjimku. */
public final class IpLimit implements Listener {
    private final SquareStar pl;
    public IpLimit(SquareStar pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGH)
    public void pre(AsyncPlayerPreLoginEvent e) {
        int max = pl.getConfig().getInt("security.max-per-ip", 5);
        if (max <= 0 || e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (pl.ranks.staffByUuid(e.getUniqueId())) return;
        InetAddress ip = e.getAddress();
        long same = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            var a = p.getAddress();
            if (a != null && a.getAddress().equals(ip) && !p.getUniqueId().equals(e.getUniqueId())) same++;
        }
        if (same >= max) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, U.c("&c&lSQUARESTAR\n\n&fZ tvé sítě už hraje &e" + same + " &fhráčů (max &e" + max + "&f).\n&7Max " + max + " hráčů z jedné IP adresy.\n\n&7From your network &e" + same + " &7players are already online (max " + max + ")."));
            pl.getLogger().info("IP limit: " + e.getName() + " odmitnut (" + same + " hracu z " + ip.getHostAddress() + ")");
        }
    }
}
