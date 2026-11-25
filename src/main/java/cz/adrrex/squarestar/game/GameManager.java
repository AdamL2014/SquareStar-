package cz.adrrex.squarestar.game;

import cz.adrrex.squarestar.SquareStar;
import cz.adrrex.squarestar.U;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GameManager implements Listener {
    public final SquareStar pl;
    final Map<Player, Game> map = new HashMap<>();
    private final List<Game> games = new ArrayList<>();
    private final BitSet slots = new BitSet();
    public final Map<UUID, List<ItemStack>> pendingRewards = new HashMap<>();

    public static final class MenuHolder implements InventoryHolder {
        Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public static final class KitHolder implements InventoryHolder {
        final Game game; Inventory inv; KitHolder(Game g) { game = g; } @Override public @NotNull Inventory getInventory() { return inv; }
    }
    public static final class TeamHolder implements InventoryHolder {
        final Game game; Inventory inv; TeamHolder(Game g) { game = g; } @Override public @NotNull Inventory getInventory() { return inv; }
    }
    public static final class VoteHolder implements InventoryHolder {
        final Game game; Inventory inv; VoteHolder(Game g) { game = g; } @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public GameManager(SquareStar pl) { this.pl = pl; }
    /** poslední hra hráče (tlačítko „Hrát znovu") */
    public final Map<UUID, GameType> lastType = new HashMap<>();
    public int activePlayers() { return map.size(); }

    /** rychlá hra: kde čeká nejvíc lidí (a je volno), jinak náhodná hra pro 1–2 hráče */
    public void quick(Player p) {
        Game best = games.stream().filter(x -> x.joinable() && !x.event && !x.players.isEmpty()).max(java.util.Comparator.comparingInt(x -> x.players.size())).orElse(null);
        if (best != null) { join(p, best.type); return; }
        List<GameType> easy = java.util.Arrays.stream(GameType.values()).filter(t -> t.min <= 2).toList();
        join(p, easy.get(new java.util.Random().nextInt(easy.size())));
    }

    /** /ss znovu: z konce hry (nebo z lobby) rovnou do další hry stejného typu */
    public void again(Player p) {
        GameType t = lastType.get(p.getUniqueId());
        Game g = map.get(p);
        if (g != null) {
            if (g.state != Game.State.ENDING && g.alive.contains(p)) { U.msg(p, "&cHra ještě běží. &7Odejít: &e/ss leave"); return; }
            t = g.type;
            if (g.state == Game.State.ENDING) g.removePlayer(p, true); else g.leaveEliminated(p);
        }
        if (t == null) { openMenu(p); return; }
        join(p, t);
    }

    public int playingPvp() { int n = 0; for (GameType t : GameType.PVP_MODES) n += playing(t); return n; }
    public int playing(GameType t) { return games.stream().filter(x -> x.type == t).mapToInt(x -> x.players.size()).sum(); }
    public int activeGames() { return games.size(); }

    public Game of(Player p) { return map.get(p); }

    /** kategorie hlavního menu: každá se otevře jako podmenu (stejně jako PvP) */
    public enum Cat {
        BATTLE("&e&l🏰 Bitvy a týmovky", Material.RED_BED, "Týmové bitvy a souboje",
                GameType.BEDWARS, GameType.EGGWARS, GameType.BRIDGE, GameType.ANNIRUN, GameType.DUELS, GameType.SKYWARS, GameType.UHCRUN, GameType.ZOMBIES),
        HIDE("&d&l🎭 Schovávaná a hádanky", Material.BARREL, "Schovávaná, vrah a stavění",
                GameType.HIDESEEK, GameType.MURDER, GameType.BUILDBATTLE),
        RACE("&b&l🏁 Závody a skákání", Material.LADDER, "Parkour, věže, závody a rytmus",
                GameType.PARKOUR, GameType.ONLYUP, GameType.DROPPER, GameType.ROVER, GameType.SPEEDRUN, GameType.BEAT),
        FUN("&a&l🎲 Zábava a přežití", Material.TNT, "Spleef, TNT, šťastná věž",
                GameType.SPLEEF, GameType.TNTRUN, GameType.LUCKYTOWER);
        public final String title; public final Material icon; public final String desc; public final java.util.List<GameType> games;
        Cat(String t, Material i, String d, GameType... g) { title = t; icon = i; desc = d; games = java.util.List.of(g); }
    }

    private ItemStack gameItem(Player p, GameType g) {
        int waiting = games.stream().filter(x -> x.type == g && x.joinable()).mapToInt(x -> x.players.size()).sum();
        int playing = playing(g);
        ItemStack it = U.item(g.icon, 1, "&6&l" + g.display, "&7" + g.desc, "",
                "&7Hráči: &f" + g.min + "–" + g.max, "&7Čeká: &a" + waiting + " &8| &7Celkem hraje: &e" + playing, "", "&e▶ Klikni pro připojení");
        // vlastní ikonka z resource packu (Bedrock ji nevidí -> zůstane obyčejný item)
        if (!cz.adrrex.squarestar.Ranks.bedrock(p)) { var im = it.getItemMeta(); im.setItemModel(new org.bukkit.NamespacedKey("squarestar", "icon_" + g.name().toLowerCase(java.util.Locale.ROOT))); if (playing > 0) im.setEnchantmentGlintOverride(true); it.setItemMeta(im); }
        if (playing > 0) it.setAmount(Math.min(64, playing));
        return U.tag(it, "ss_menu", g.name());
    }

    public void openMenu(Player p) {
        MenuHolder h = new MenuHolder();
        h.inv = Bukkit.createInventory(h, 45, cz.adrrex.squarestar.Gui.title(45, cz.adrrex.squarestar.Lang.t(p, "&f&l✦ Minihry na Marsu")));
        // PvP = podmenu (stejně jako dřív)
        ItemStack pv = U.item(Material.DIAMOND_SWORD, 1, "&c&l⚔ PvP", "&7Vyber si, jaké PvP chceš:", "&f1v1&7, &fvšichni proti všem&7, &f5v5&7, &f10v10&7, &fsumo", "",
                "&7Celkem hraje: &e" + playingPvp(), "", "&e▶ Klikni pro výběr režimu");
        if (!cz.adrrex.squarestar.Ranks.bedrock(p)) { var im = pv.getItemMeta(); im.setItemModel(new org.bukkit.NamespacedKey("squarestar", "icon_pvp")); pv.setItemMeta(im); }
        h.inv.setItem(20, U.tag(pv, "ss_menu", "PVPMENU"));
        h.inv.setItem(4, U.tag(U.item(Material.FIREWORK_ROCKET, 1, "&a&l▶ Rychlá hra", "&7Pošle tě do hry, kde už", "&7čekají hráči (nebo do náhodné).", "", "&e▶ Klikni a hraj"), "ss_menu", "QUICK"));
        if (pl.progress != null) h.inv.setItem(36, U.tag(U.item(Material.WRITABLE_BOOK, 1, "&e&l★ Denní úkoly a profil", "&7Splněno dnes: &a" + pl.progress.questsDone(p) + "&7/3",
                pl.progress.dailyReady(p) ? "&a&lDenní odměna čeká!" : "&7Denní odměna: vyzvednuta", "", "&e▶ Klikni pro otevření"), "ss_menu", "PROFILE"));
        GameType last = lastType.get(p.getUniqueId());
        if (last != null) h.inv.setItem(42, U.tag(U.item(last.icon, 1, "&b&l↻ Hrát znovu", "&7Naposledy: &f" + last.display, "", "&e▶ Klikni pro připojení"), "ss_menu", last.name()));
        int[] pos = {21, 22, 23, 24};
        Cat[] cats = Cat.values();
        for (int i = 0; i < cats.length; i++) {
            Cat c = cats[i];
            int playing = c.games.stream().mapToInt(this::playing).sum();
            StringBuilder names = new StringBuilder();
            for (GameType g : c.games) names.append(names.length() == 0 ? "" : "&7, ").append("&f").append(g.display);
            ItemStack it = U.item(c.icon, 1, c.title, "&7" + c.desc + ":", names.toString(), "", "&7Celkem hraje: &e" + playing, "", "&e▶ Klikni pro výběr hry");
            if (playing > 0) { var im = it.getItemMeta(); im.setEnchantmentGlintOverride(true); it.setItemMeta(im); }
            h.inv.setItem(pos[i], U.tag(it, "ss_menu", "CAT:" + c.name()));
        }
        // Redstone týden (trvalý režim, ne kolo minihry)
        boolean vote = cz.adrrex.squarestar.RedstoneWeek.voting();
        ItemStack rsIcon = U.item(Material.REDSTONE_BLOCK, 1, "&c&l⚡ Redstone týden", "&7Každý týden nové redstone téma.", "&7Postav nápad na svém pozemku,", "&7v neděli se hlasuje o nejlepší!", "",
                "&7Téma: &e" + pl.redstone.theme(), vote ? "&d&lDnes se hlasuje!" : "&7Hlasování: &fneděle", "", "&e▶ Klikni pro vstup");
        if (!cz.adrrex.squarestar.Ranks.bedrock(p)) { var rm = rsIcon.getItemMeta(); rm.setItemModel(new org.bukkit.NamespacedKey("squarestar", "icon_redstone")); rsIcon.setItemMeta(rm); }
        h.inv.setItem(40, U.tag(rsIcon, "ss_menu", "REDSTONE"));
        p.openInventory(h.inv);
    }

    /** podmenu kategorie – všechny hry z kategorie najednou */
    public void openCat(Player p, Cat c) {
        MenuHolder h = new MenuHolder();
        h.inv = Bukkit.createInventory(h, 45, cz.adrrex.squarestar.Gui.title(45, cz.adrrex.squarestar.Lang.t(p, c.title)));
        int[] pos = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < c.games.size(); i++) h.inv.setItem(pos[i], gameItem(p, c.games.get(i)));
        h.inv.setItem(40, U.tag(U.item(Material.ARROW, 1, "&7◀ Zpět do menu", "&7Zpět na všechny minihry"), "ss_menu", "BACK"));
        p.openInventory(h.inv);
    }

    static final class PvpHolder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    /** výběr PvP režimu (otevírá se z NPC „PvP“ i z menu minihry) */
    public void openPvp(Player p) {
        PvpHolder h = new PvpHolder();
        h.inv = Bukkit.createInventory(h, 27, cz.adrrex.squarestar.Gui.title(27, cz.adrrex.squarestar.Lang.t(p, "&f&l⚔ Vyber si PvP")));
        int[] pos = {11, 12, 13, 14, 15};
        for (int i = 0; i < GameType.PVP_MODES.size(); i++) {
            GameType g = GameType.PVP_MODES.get(i);
            int waiting = games.stream().filter(x -> x.type == g && x.joinable()).mapToInt(x -> x.players.size()).sum();
            int playing = playing(g);
            ItemStack it = U.item(g.icon, 1, "&6&l" + g.display, "&7" + g.desc, "", "&7Hráči: &f" + g.min + "–" + g.max, "&7Čeká: &a" + waiting + " &8| &7Hraje: &e" + playing, "", "&e▶ Klikni pro připojení");
            if (!cz.adrrex.squarestar.Ranks.bedrock(p)) { var im = it.getItemMeta(); im.setItemModel(new org.bukkit.NamespacedKey("squarestar", "icon_" + g.name().toLowerCase(java.util.Locale.ROOT))); if (playing > 0) im.setEnchantmentGlintOverride(true); it.setItemMeta(im); }
            h.inv.setItem(pos[i], U.tag(it, "ss_pvp", g.name()));
        }
        p.openInventory(h.inv);
    }
    @EventHandler public void pvpMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PvpHolder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_pvp");
        if (t != null && e.getWhoClicked() instanceof Player p) { p.closeInventory(); join(p, GameType.valueOf(t)); }
    }

    public void join(Player p, GameType type) {
        if (map.containsKey(p)) { U.msg(p, "&cUž jsi ve hře. &7Napiš &e/ss leave"); return; }
        var party = pl.party == null ? null : pl.party.of(p);
        if (party != null && !pl.party.isLeader(p)) { U.msg(p, "&cHru vybírá vůdce party. &7Chceš hrát sám? &e/parta odejit"); return; }
        List<Player> crew = new ArrayList<>();
        if (party != null) for (Player m : pl.party.followers(p)) if (!map.containsKey(m) && m.getWorld() == pl.mars) crew.add(m);
        int need = 1 + crew.size();
        if (need > type.max) { U.msg(p, "&cTvoje parta (" + need + ") je moc velká pro tuhle hru (max " + type.max + ")."); return; }
        Game g = games.stream().filter(x -> x.type == type && x.joinable() && !x.event && x.players.size() + need <= x.maxPlayers()).findFirst().orElse(null);
        if (g == null) {
            int max = pl.getConfig().getInt("max-arenas", 6);
            if (games.size() >= max) { U.msg(p, "&cVšechny arény jsou plné, zkus to za chvíli."); return; }
            int slot = slots.nextClearBit(1);
            slots.set(slot);
            g = type.create(this, slot);
            games.add(g);
            g.build();
        }
        pl.lobby.saveInv(p);
        map.put(p, g);
        g.addPlayer(p);
        lastType.put(p.getUniqueId(), type);
        for (Player m : crew) {
            if (!g.joinable()) { U.msg(m, "&cHra je plná, nevešel ses."); continue; }
            U.msg(m, "&9Parta jde hrát &e" + type.display + "&9!");
            pl.lobby.saveInv(m);
            map.put(m, g);
            g.addPlayer(m);
        }
    }

    /** vytvoří soukromou hru pro event (nikdo se nepřipojí přes menu) */
    public Game createEvent(GameType type, int max, Double gravity) {
        int maxA = pl.getConfig().getInt("max-arenas", 6) + 1;
        if (games.size() >= maxA) return null;
        int slot = slots.nextClearBit(1);
        slots.set(slot);
        Game g = type.create(this, slot);
        g.event = true; g.eventHold = true; g.eventMax = max; g.eventGravity = gravity;
        games.add(g);
        g.build();
        return g;
    }
    public boolean joinEvent(Player p, Game g) {
        if (map.containsKey(p) || g == null || !g.joinable()) return false;
        pl.lobby.saveInv(p);
        map.put(p, g);
        g.addPlayer(p);
        return true;
    }
    public void startEvent(Game g) { g.eventHold = false; g.checkStart(); }

    public void leave(Player p) {
        Game g = map.get(p);
        if (g == null) { U.msg(p, "&7Nejsi v žádné hře."); return; }
        g.removePlayer(p, true);
        U.msg(p, "&7Opustil jsi hru.");
    }

    void free(Game g) { games.remove(g); slots.clear(g.slot); }

    public void stopAll() { for (Game g : new ArrayList<>(games)) g.destroyNow(); games.clear(); }

    public void deliverRewards(Player p) {
        List<ItemStack> l = pendingRewards.remove(p.getUniqueId());
        if (l == null) return;
        for (ItemStack it : l) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    void hideName(Player p, boolean hide) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("ss_hidden");
        if (t == null) { t = sb.registerNewTeam("ss_hidden"); t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER); }
        if (hide) t.addEntry(p.getName()); else { t.removeEntry(p.getName()); pl.ranks.apply(p); }
    }

    private Game gameAt(Location l) {
        if (!pl.isArena(l.getWorld())) return null;
        for (Game g : games) if (g.owns(l)) return g;
        return null;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    // ---------------- události ----------------
    @EventHandler public void voteClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof VoteHolder h)) return;
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == e.getInventory() && map.get(p) == h.game) h.game.vote(p, e.getSlot());
    }

    @EventHandler public void teamClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof TeamHolder h)) return;
        e.setCancelled(true);
        String id = U.tagOf(e.getCurrentItem(), "ss_team");
        if (id != null && e.getWhoClicked() instanceof Player p && map.get(p) == h.game) h.game.chooseTeam(p, Integer.parseInt(id));
    }

    @EventHandler public void kitClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof KitHolder h)) return;
        e.setCancelled(true);
        String id = U.tagOf(e.getCurrentItem(), "ss_kit");
        if (id != null && e.getWhoClicked() instanceof Player p && map.get(p) == h.game) h.game.chooseKit(p, id);
    }

    @EventHandler public void menu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_menu");
        if (t != null && e.getWhoClicked() instanceof Player p) {
            p.closeInventory();
            if (t.equals("PVPMENU")) openPvp(p);
            else if (t.equals("BACK")) openMenu(p);
            else if (t.equals("PROFILE")) pl.progress.open(p);
            else if (t.equals("QUICK")) quick(p);
            else if (t.startsWith("CAT:")) openCat(p, Cat.valueOf(t.substring(4)));
            else if (t.equals("REDSTONE")) { if (cz.adrrex.squarestar.RedstoneWeek.voting()) pl.redstone.visit(p, 0); else pl.redstone.join(p); }
            else join(p, GameType.valueOf(t));
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) { Game g = map.get(e.getPlayer()); if (g != null) g.removePlayer(e.getPlayer(), false); }

    @EventHandler(priority = EventPriority.HIGH) public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Game g = map.get(v);
        if (g == null) return;
        if (g.state != Game.State.RUNNING || !g.alive.contains(v)) { e.setCancelled(true); return; }
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.FALL || c == EntityDamageEvent.DamageCause.VOID) { e.setCancelled(true); return; }
        Player killer = null;
        if (e instanceof EntityDamageByEntityEvent ed) {
            Player a = attacker(ed.getDamager());
            if (a != null) {
                if (a == v && ed.getDamager() instanceof TNTPrimed) { /* vlastní TNT je OK */ }
                else if (map.get(a) != g || !g.alive.contains(a)) { e.setCancelled(true); return; }
                if (!g.onPvp(v, a, ed)) { e.setCancelled(true); return; }
                if (e.isCancelled()) return;
                if (a != v) g.lastHit.put(v, a);
                killer = a;
            }
        }
        switch (g.damage()) {
            case NONE -> { e.setCancelled(true); return; }
            case KNOCKBACK -> { e.setDamage(0); return; }
            default -> {}
        }
        if (e.getFinalDamage() >= v.getHealth()) {
            e.setCancelled(true);
            g.onDeath(v, killer != null ? killer : g.lastHit.get(v));
        }
    }

    @EventHandler public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = map.get(p);
        if (g == null) return;
        if (e.getTo().getY() < g.voidY()) {
            if (g.state == Game.State.RUNNING && g.alive.contains(p)) g.onVoid(p);
            else p.teleport(g.state == Game.State.RUNNING ? g.specSpot() : g.waitSpot());
            return;
        }
        if (g.state == Game.State.RUNNING && g.alive.contains(p)) g.onMove(p, e.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH) public void brk(BlockBreakEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) { if (pl.isArena(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("squarestar.admin")) e.setCancelled(true); return; }
        if (g.state != Game.State.RUNNING || !g.alive.contains(e.getPlayer()) || !g.canBreak(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    /** díry v mapě (např. v asteroidu ve SkyWars) se po 5 minutách samy opraví */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void heal(BlockBreakEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null || g.isPlaced(e.getBlock()) || EnumSet.of(GameType.SPLEEF, GameType.TNTRUN, GameType.BUILDBATTLE, GameType.SPEEDRUN, GameType.UHCRUN).contains(g.type)) return;
        Block b = e.getBlock();
        if (b.getType().name().endsWith("_BED")) return;      // zničená postel v BedWars zůstane zničená
        org.bukkit.block.data.BlockData was = b.getBlockData().clone();
        healLater(g, b, was, 6000);
    }
    private void healLater(Game g, Block b, org.bukkit.block.data.BlockData was, long delay) {
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!games.contains(g) || g.state != Game.State.RUNNING || !b.getType().isAir()) return;
            // hráč stojí v místě -> zkusit za 5 s (ať se nezasekne v bloku)
            if (!b.getWorld().getNearbyEntities(b.getBoundingBox().expand(0.3), x -> x instanceof Player).isEmpty()) { healLater(g, b, was, 100); return; }
            b.setBlockData(was, false);
            b.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0);
        }, delay);
    }

    @EventHandler(priority = EventPriority.HIGH) public void place(BlockPlaceEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) { if (pl.isArena(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("squarestar.admin")) e.setCancelled(true); return; }
        Block b = e.getBlock();
        if (g.state != Game.State.RUNNING || !g.alive.contains(e.getPlayer()) || !g.owns(b.getLocation()) || !g.canPlace(e.getPlayer(), b)) { e.setCancelled(true); return; }
        g.markPlaced(b);
    }

    @EventHandler(priority = EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) return;
        String tag = U.tagOf(e.getItem(), "ss_game");
        if ("leave".equals(tag)) { e.setCancelled(true); leave(e.getPlayer()); return; }
        if ("vote".equals(tag)) { e.setCancelled(true); g.openVote(e.getPlayer()); return; }
        if ("kit".equals(tag)) { e.setCancelled(true); g.openKits(e.getPlayer()); return; }
        if ("team".equals(tag)) { e.setCancelled(true); g.openTeams(e.getPlayer()); return; }
        if (g.state == Game.State.RUNNING) g.onInteract(e);
    }

    @EventHandler public void interactEntity(PlayerInteractEntityEvent e) {
        Game g = map.get(e.getPlayer());
        if (g != null && g.state == Game.State.RUNNING) g.onInteractEntity(e);
    }

    @EventHandler public void blockDamage(BlockDamageEvent e) {
        Game g = map.get(e.getPlayer());
        if (g != null && g.state == Game.State.RUNNING && g.alive.contains(e.getPlayer())) g.onBlockDamage(e);
    }

    @EventHandler public void projectile(ProjectileHitEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) { Game g = map.get(p); if (g != null && g.state == Game.State.RUNNING) g.onProjectileHit(e); }
    }

    @EventHandler public void food(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) { Game g = map.get(p); if (g != null && (!g.hunger() || g.state != Game.State.RUNNING)) e.setCancelled(true); }
    }

    @EventHandler public void drop(PlayerDropItemEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) return;
        if (g.state != Game.State.RUNNING || !g.canDrop() || "leave".equals(U.tagOf(e.getItemDrop().getItemStack(), "ss_game"))) e.setCancelled(true);
    }

    @EventHandler public void explode(EntityExplodeEvent e) {
        if (!pl.isArena(e.getLocation().getWorld())) return;
        Game g = gameAt(e.getLocation());
        e.blockList().removeIf(b -> g == null || !g.isPlaced(b) || b.getType().name().endsWith("_BED") || b.getType() == Material.BARRIER || b.getType().name().contains("GLASS"));
    }

    @EventHandler public void blockExplode(BlockExplodeEvent e) { if (pl.isArena(e.getBlock().getWorld())) e.blockList().clear(); }

    @EventHandler public void creature(CreatureSpawnEvent e) {
        if (pl.isArena(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM && !(e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN && pl.uhcWorlds.contains(e.getLocation().getWorld()))) e.setCancelled(true);
    }

    @EventHandler public void teleport(PlayerTeleportEvent e) {
        // spektátor nesmí utéct z arény
        Game g = map.get(e.getPlayer());
        if (g != null && e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && !g.owns(e.getTo())) e.setCancelled(true);
    }

    @EventHandler public void bucket(PlayerBucketEmptyEvent e) {
        Game g = map.get(e.getPlayer());
        if (g == null) return;
        if (g.state != Game.State.RUNNING || !g.canPlace(e.getPlayer(), e.getBlock())) { e.setCancelled(true); return; }
        g.markPlaced(e.getBlock());
    }

    @EventHandler public void flow(BlockFromToEvent e) {
        if (!pl.isArena(e.getBlock().getWorld())) return;
        Game g = gameAt(e.getToBlock().getLocation());
        if (g == null || !g.allowFlow(e.getToBlock())) { e.setCancelled(true); return; }
        g.markPlaced(e.getToBlock());
    }

    // ---- růst stromů/hub/trávy, tvorba bloků: zaznamenat, aby se po hře uklidily ----
    @EventHandler(ignoreCancelled = true) public void grow(org.bukkit.event.world.StructureGrowEvent e) {
        Game g = gameAt(e.getLocation());
        if (g == null) return;
        e.getBlocks().removeIf(st -> gameAt(st.getLocation()) != g);
        for (org.bukkit.block.BlockState st : e.getBlocks()) g.markPlaced(st.getBlock());
    }
    @EventHandler(ignoreCancelled = true) public void spread(org.bukkit.event.block.BlockSpreadEvent e) { track(e.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void form(org.bukkit.event.block.BlockFormEvent e) { track(e.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void growB(org.bukkit.event.block.BlockGrowEvent e) { track(e.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void fert(org.bukkit.event.block.BlockFertilizeEvent e) {
        for (org.bukkit.block.BlockState st : e.getBlocks()) track(st.getBlock());
    }
    private void track(org.bukkit.block.Block b) {
        Game g = gameAt(b.getLocation());
        if (g != null) g.markPlaced(b);
    }
    /** levné moby (slizy pod hlavami) se po smrti nedělí */
    @EventHandler public void slimeSplit(org.bukkit.event.entity.SlimeSplitEvent e) { if (pl.isArena(e.getEntity().getWorld())) e.setCancelled(true); }
    @EventHandler public void exitVehicle(org.bukkit.event.vehicle.VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) { Game g = of(p); if (g != null && !g.canExitVehicle(p)) e.setCancelled(true); }
    }
    @EventHandler public void vehicleDamage(org.bukkit.event.vehicle.VehicleDamageEvent e) { if (pl.isArena(e.getVehicle().getWorld())) e.setCancelled(true); }
    @EventHandler public void vehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent e) { if (pl.isArena(e.getVehicle().getWorld())) e.setCancelled(true); }
}
