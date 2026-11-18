package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.game.Game;
import cz.adrrex.squarestar.game.GameManager;
import cz.adrrex.squarestar.game.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/** Marsovská základna: Murder Mystery a Schovávaná (proměna v blok). */
public final class Station extends Game {
    private final boolean hide;
    private static final int H = 22;
    private final Set<Long> solid = new HashSet<>();
    private static final Material[] PROPS = {Material.BARREL, Material.CRAFTING_TABLE, Material.SMITHING_TABLE, Material.BLAST_FURNACE, Material.IRON_BLOCK,
            Material.CAULDRON, Material.BOOKSHELF, Material.REDSTONE_LAMP, Material.OBSERVER, Material.TARGET, Material.LODESTONE, Material.COMPOSTER, Material.FLETCHING_TABLE};
    // Murder
    private Player murderer, detective;
    // Schovávaná
    private final Set<Player> seekers = new LinkedHashSet<>();
    private final Map<Player, BlockDisplay> disguise = new HashMap<>();
    private static final int HIDE_TIME = 30;

    public Station(GameManager gm, int slot, boolean hide) {
        super(gm, hide ? GameType.HIDESEEK : GameType.MURDER, slot);
        this.hide = hide;
    }

    @Override public List<String> maps() { return List.of("Základna Alfa", "Temná základna"); }

    @Override protected void buildMap(U.BlockQueue.Builder b) {
        int y = oy;
        boolean dark = map == 1;
        Material light = dark ? Material.SHROOMLIGHT : Material.SEA_LANTERN, grid = dark ? Material.RED_NETHER_BRICKS : Material.LIGHT_GRAY_CONCRETE,
                floor = dark ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_QUARTZ, wallM = dark ? Material.DEEPSLATE_TILES : Material.WHITE_CONCRETE,
                topM = dark ? Material.POLISHED_BLACKSTONE_BRICKS : Material.IRON_BLOCK, window = dark ? Material.RED_STAINED_GLASS : Material.CYAN_STAINED_GLASS;
        for (int x = -H; x <= H; x++) for (int z = -H; z <= H; z++) {
            Material f = (x % 6 == 0 && z % 6 == 0) ? light : (x % 6 == 0 || z % 6 == 0) ? grid : floor;
            b.set(ox + x, y, oz + z, f);
            b.set(ox + x, y + 7, oz + z, dark ? Material.GRAY_STAINED_GLASS : Material.LIGHT_GRAY_STAINED_GLASS);
            b.set(ox + x, y - 1, oz + z, Material.RED_TERRACOTTA);
        }
        for (int i = -H; i <= H; i++) for (int h = 1; h <= 6; h++) {
            Material m = h == 3 && Math.abs(i) % 4 == 1 ? window : wallM;
            wall(b, ox + i, y + h, oz - H, m); wall(b, ox + i, y + h, oz + H, m);
            wall(b, ox - H, y + h, oz + i, m); wall(b, ox + H, y + h, oz + i, m);
        }
        // vnitřní stěny s dveřmi -> 16 místností
        int[] lines = {-11, 0, 11};
        for (int l : lines) {
            for (int seg = 0; seg < 4; seg++) {
                int from = -H + 1 + seg * 11, to = from + 10;
                int door = from + 2 + rnd.nextInt(6);
                for (int i = from; i <= to; i++) {
                    if (i >= door && i <= door + 2) continue;
                    for (int h = 1; h <= 5; h++) {
                        Material m = h == 5 ? topM : wallM;
                        wall(b, ox + l, y + h, oz + i, m);
                        wall(b, ox + i, y + h, oz + l, m);
                    }
                }
            }
        }
        // rekvizity
        int props = hide ? 110 : 40;
        for (int i = 0; i < props; i++) {
            int x = rnd.nextInt(2 * H - 3) - H + 2, z = rnd.nextInt(2 * H - 3) - H + 2;
            if (solid.contains(U.pack(ox + x, y + 1, oz + z)) || Math.abs(x) % 11 == 0 || Math.abs(z) % 11 == 0) continue;
            Material m = PROPS[rnd.nextInt(PROPS.length)];
            wall(b, ox + x, y + 1, oz + z, m);
            if (rnd.nextInt(4) == 0) wall(b, ox + x, y + 2, oz + z, PROPS[rnd.nextInt(PROPS.length)]);
        }
    }

    private void wall(U.BlockQueue.Builder b, int x, int y, int z, Material m) { b.set(x, y, z, m); solid.add(U.pack(x, y, z)); }

    private Location randomSpot() {
        for (int i = 0; i < 200; i++) {
            int x = rnd.nextInt(2 * H - 3) - H + 2, z = rnd.nextInt(2 * H - 3) - H + 2;
            if (!solid.contains(U.pack(ox + x, oy + 1, oz + z)) && !solid.contains(U.pack(ox + x, oy + 2, oz + z)))
                return new Location(w, ox + x + 0.5, oy + 1, oz + z + 0.5, rnd.nextFloat() * 360, 0);
        }
        return U.center(w, ox + 3, oy + 1, oz + 3);
    }

    @Override protected boolean hideNames() { return true; }
    @Override protected int voidY() { return oy - 20; }
    @Override protected int timeLimit() { return hide ? 240 : 300; }

    @Override protected void start() {
        List<Player> list = new ArrayList<>(players);
        Collections.shuffle(list, rnd);
        for (Player p : players) p.teleport(randomSpot());
        if (hide) startHide(list); else startMurder(list);
    }

    @Override public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b != null && (b.getState() instanceof Container || b.getType() == Material.CRAFTING_TABLE || b.getType() == Material.SMITHING_TABLE || b.getType() == Material.FLETCHING_TABLE
                || b.getType() == Material.COMPOSTER || b.getType() == Material.CAULDRON || b.getType() == Material.LODESTONE)) e.setCancelled(true);
    }

    // ================= MURDER MYSTERY =================
    private void startMurder(List<Player> list) {
        murderer = list.get(0);
        detective = list.size() >= 3 ? list.get(1) : null;
        for (Player p : players) {
            if (p == murderer) U.title(p, "&c&lJSI VRAH", "&7Za 10 s dostaneš meč. Zabij všechny!");
            else if (p == detective) { U.title(p, "&9&lJSI DETEKTIV", "&7Najdi vraha a zastřel ho lukem!"); giveBow(p); }
            else U.title(p, "&a&lJSI NEVINNÝ", "&7Přežij! Za 10 zlatých dostaneš luk.");
        }
    }

    private static void giveBow(Player p) {
        p.getInventory().setItem(0, U.item(Material.BOW, 1, "&9Detektivův luk"));
        p.getInventory().setItem(9, new ItemStack(Material.ARROW));
    }

    @Override protected void second() {
        if (hide) { secondHide(); return; }
        if (time == 10 && murderer != null && alive.contains(murderer)) {
            murderer.getInventory().setItem(1, U.item(Material.IRON_SWORD, 1, "&cVražedný meč"));
            U.msg(murderer, "&cMáš meč! Jeden zásah = smrt.");
        }
        if (time % 8 == 0 && w.getNearbyEntitiesByType(Item.class, loc(0, 1, 0), 30).size() < 20)
            w.dropItem(randomSpot().add(0, 0.5, 0), new ItemStack(Material.GOLD_INGOT));
        for (Player p : alive) {
            if (p == murderer) continue;
            if (p.getInventory().containsAtLeast(new ItemStack(Material.GOLD_INGOT), 10)) {
                p.getInventory().removeItem(new ItemStack(Material.GOLD_INGOT, 10));
                if (!p.getInventory().contains(Material.BOW)) giveBow(p);
                U.msg(p, "&6Za 10 zlatých máš luk!");
            }
            if (p.getInventory().contains(Material.BOW) && !p.getInventory().contains(Material.ARROW) && time % 3 == 0) p.getInventory().addItem(new ItemStack(Material.ARROW));
        }
    }

    @Override public boolean onPvp(Player victim, Player attacker, EntityDamageByEntityEvent e) {
        if (hide) return pvpHide(victim, attacker);
        if (e.getDamager() instanceof Arrow arrow) {
            arrow.remove();
            if (victim == murderer) { kill(victim); }
            else { kill(victim); if (attacker != murderer && alive.contains(attacker)) { U.msg(attacker, "&cZastřelil jsi nevinného! Umíráš taky."); kill(attacker); } }
        } else if (attacker == murderer && attacker.getInventory().getItemInMainHand().getType() == Material.IRON_SWORD) {
            kill(victim);
        }
        return false;
    }

    private void kill(Player v) {
        if (!alive.contains(v)) return;
        if (v == detective || v.getInventory().contains(Material.BOW)) {
            Item it = w.dropItem(v.getLocation(), U.item(Material.BOW, 1, "&9Detektivův luk"));
            it.setGlowing(true);
            broadcast("&9Luk spadl na zem! Nevinní ho můžou sebrat.");
        }
        eliminate(v, null);
    }

    private void checkMurder() {
        if (murderer == null) return;
        if (!alive.contains(murderer)) { end(players.stream().filter(p -> p != murderer).toList()); return; }
        if (alive.size() <= 1) end(List.of(murderer));
    }

    // ================= SCHOVÁVANÁ =================
    private void startHide(List<Player> list) {
        int n = players.size() >= 8 ? 2 : 1;
        for (int i = 0; i < list.size(); i++) {
            Player p = list.get(i);
            if (i < n) {
                seekers.add(p);
                p.teleport(randomSpot());
                p.setWalkSpeed(0f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, HIDE_TIME * 20, 0, false, false));
                U.title(p, "&c&lHLEDÁŠ", "&7Za " + HIDE_TIME + " s tě pustíme. Najdi všechny bloky!");
            } else {
                disguise(p);
                U.title(p, "&a&lSCHOVEJ SE", "&7Jsi blok! Máš " + HIDE_TIME + " s na úkryt.");
            }
        }
    }

    private void disguise(Player p) {
        Material m = PROPS[rnd.nextInt(PROPS.length)];
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        BlockDisplay d = spawn(p.getLocation(), BlockDisplay.class, bd -> {
            bd.setBlock(m.createBlockData());
            bd.setTransformation(new Transformation(new Vector3f(-0.5f, -1.8f, -0.5f), new AxisAngle4f(), new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            bd.setTeleportDuration(1);
        });
        p.addPassenger(d);
        disguise.put(p, d);
        U.msg(p, "&7Jsi &a" + m.name().toLowerCase(Locale.ROOT).replace('_', ' ') + "&7. Postav se mezi stejné bloky a nehýbej se!");
    }

    private void undisguise(Player p) {
        BlockDisplay d = disguise.remove(p);
        if (d != null) d.remove();
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void secondHide() {
        if (time == HIDE_TIME) {
            for (Player s : seekers) { s.setWalkSpeed(0.2f); s.getInventory().setItem(0, U.item(Material.IRON_SWORD, 1, "&cHledačův meč", "&7Zásah = našel jsi ho")); }
            broadcast("&c&lHledači vyrazili!");
        }
        for (Player p : alive) if (!seekers.contains(p) && time % 30 == 0 && time > HIDE_TIME) {
            w.spawnParticle(Particle.NOTE, p.getLocation().add(0, 1.2, 0), 3);
            w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
        }
    }

    private boolean pvpHide(Player victim, Player attacker) {
        if (time < HIDE_TIME || !seekers.contains(attacker) || seekers.contains(victim)) return false;
        undisguise(victim);
        seekers.add(victim);
        victim.getInventory().setItem(0, U.item(Material.IRON_SWORD, 1, "&cHledačův meč"));
        broadcast("&e" + victim.getName() + " &7byl nalezen hráčem &c" + attacker.getName() + "&7 a teď hledá taky!");
        U.title(victim, "&c&lNALEZEN!", "&7Teď hledáš ostatní.");
        checkWin();
        return false;
    }

    // ================= společné =================
    @Override protected void checkWin() {
        if (state != State.RUNNING) return;
        if (!hide) { checkMurder(); return; }
        List<Player> hiders = alive.stream().filter(p -> !seekers.contains(p)).toList();
        if (hiders.isEmpty()) end(new ArrayList<>(seekers.stream().filter(players::contains).toList()));
        else if (seekers.stream().noneMatch(alive::contains)) end(new ArrayList<>(hiders));
    }

    @Override protected void timeUp() {
        if (hide) end(alive.stream().filter(p -> !seekers.contains(p)).toList());
        else end(players.stream().filter(p -> p != murderer).toList());
    }

    @Override protected void onLeave(Player p) { undisguise(p); p.setWalkSpeed(0.2f); seekers.remove(p); }

    @Override protected String status() {
        if (hide) {
            long h = alive.stream().filter(p -> !seekers.contains(p)).count();
            String phase = time < HIDE_TIME ? "&eSchovávání: " + (HIDE_TIME - time) + " s" : "&7Zbývá: &e" + fmt(timeLimit() - time);
            return "&cHledači: " + seekers.size() + " &8| &aSchovaní: " + h + " &8| " + phase;
        }
        return "&7Naživu: &a" + alive.size() + " &8| &7Zbývá: &e" + fmt(timeLimit() - time);
    }
}
