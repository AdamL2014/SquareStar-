package cz.adrrex.squarestar;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Ochrana lobby (Mars): hráč nesmí nic zapnout, otevřít, rozbít ani položit.
 * Ničení a pokládání bloků hlídá Lobby, tady jsou věci okolo: páky, tlačítka, dveře, truhly, kbelíky,
 * rámečky, stojany, oheň, výbuchy, zašlapání políčka. Admin v creativu (squarestar.admin) smí všechno.
 */
public final class Protect implements Listener {
    private final SquareStar pl;
    public Protect(SquareStar pl) { this.pl = pl; }

    private boolean lobby(org.bukkit.World w) { return w == pl.mars; }
    private boolean admin(Player p) { return p.hasPermission("squarestar.admin") && p.getGameMode() == GameMode.CREATIVE; }

    /** bloky, které lobby samo používá (bedny s klíči) se nezamykají */
    private boolean usedByLobby(Material m) { return m == Material.CHEST || m == Material.ENDER_CHEST || m == Material.BARREL || m == Material.TRAPPED_CHEST; }

    private static boolean deniedUse(Material m) {
        String n = m.name();
        return m.isInteractable() && !n.endsWith("_STAIRS") && !n.endsWith("_FENCE") && !n.endsWith("_WALL") && !n.endsWith("_SLAB") && !n.endsWith("_SIGN_NEVER");
    }
    private static boolean dangerousItem(Material m) {
        String n = m.name();
        return n.endsWith("_BUCKET") || n.endsWith("_SPAWN_EGG") || n.endsWith("_BOAT") || n.endsWith("_CHEST_BOAT") || n.endsWith("MINECART") || n.equals("ARMOR_STAND") || n.endsWith("ITEM_FRAME")
                || n.equals("PAINTING") || n.equals("END_CRYSTAL") || n.equals("FLINT_AND_STEEL") || n.equals("FIRE_CHARGE") || n.equals("BONE_MEAL") || n.equals("TNT") || n.equals("LEAD") || n.equals("NAME_TAG")
                || n.endsWith("_HOE") || n.endsWith("_SHOVEL") || n.endsWith("_AXE") || n.equals("SHEARS") || n.equals("GLASS_BOTTLE");
    }

    @EventHandler(priority = EventPriority.HIGH) public void use(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!lobby(p.getWorld()) || admin(p)) return;
        var b = e.getClickedBlock();
        if (e.getAction() == Action.PHYSICAL) {                                      // zašlapání pole, tlaková deska, struna, vejce
            if (b != null) { Material m = b.getType(); if (m == Material.FARMLAND || m.name().endsWith("_PRESSURE_PLATE") || m == Material.TRIPWIRE || m == Material.TURTLE_EGG || m == Material.SNIFFER_EGG || m == Material.TRIPWIRE_HOOK) e.setCancelled(true); }
            return;
        }
        if (e.getItem() != null && dangerousItem(e.getItem().getType()) && Lobby.tagless(e.getItem())) e.setUseItemInHand(Event.Result.DENY);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null && deniedUse(b.getType()) && !usedByLobby(b.getType())) e.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH) public void bucket(PlayerBucketEmptyEvent e) { if (lobby(e.getPlayer().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH) public void bucketFill(PlayerBucketFillEvent e) { if (lobby(e.getPlayer().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH) public void hangPlace(HangingPlaceEvent e) { if (lobby(e.getEntity().getWorld()) && (e.getPlayer() == null || !admin(e.getPlayer()))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH) public void hangBreak(HangingBreakByEntityEvent e) {
        if (lobby(e.getEntity().getWorld()) && !(e.getRemover() instanceof Player p && admin(p))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH) public void stand(PlayerArmorStandManipulateEvent e) { if (lobby(e.getPlayer().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true); }
    /** rámečky: neotáčet, nevytahovat itemy */
    @EventHandler(priority = EventPriority.HIGH) public void frame(PlayerInteractEntityEvent e) {
        if (lobby(e.getPlayer().getWorld()) && !admin(e.getPlayer()) && e.getRightClicked() instanceof org.bukkit.entity.ItemFrame) e.setCancelled(true);
    }
    @EventHandler public void ignite(BlockIgniteEvent e) { if (lobby(e.getBlock().getWorld()) && (e.getPlayer() == null || !admin(e.getPlayer()))) e.setCancelled(true); }
    @EventHandler public void burn(BlockBurnEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler public void spread(BlockSpreadEvent e) { if (lobby(e.getBlock().getWorld()) && e.getSource().getType() == Material.FIRE) e.setCancelled(true); }
    @EventHandler public void boom(EntityExplodeEvent e) { if (lobby(e.getLocation().getWorld())) e.blockList().clear(); }
    @EventHandler public void blockBoom(BlockExplodeEvent e) { if (lobby(e.getBlock().getWorld())) e.blockList().clear(); }
    @EventHandler public void trample(EntityChangeBlockEvent e) { if (lobby(e.getBlock().getWorld()) && e.getBlock().getType() == Material.FARMLAND) e.setCancelled(true); }
}
