package cz.adrrex.squarestar.game;

import cz.adrrex.squarestar.ChatFree;
import cz.adrrex.squarestar.SquareStar;
import cz.adrrex.squarestar.U;
import cz.adrrex.squarestar.Lang;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Základ každé minihry: čekání, odpočet, průběh, konec a úklid mapy. */
public abstract class Game {
    public enum State { BUILDING, WAITING, COUNTDOWN, STARTING, RUNNING, ENDING }
    public enum Dmg { NONE, KNOCKBACK, NORMAL }

    protected final SquareStar pl;
    protected final GameManager gm;
    public final GameType type;
    public final int slot;
    public final World w;
    public final int ox, oy = 100, oz;
    public State state = State.BUILDING;
    public final List<Player> players = new ArrayList<>();
    public final Set<Player> alive = new LinkedHashSet<>();
    protected final Random rnd = new Random();
    protected final Set<Long> placed = new HashSet<>();
    protected final List<int[]> placedList = new ArrayList<>();
    protected final List<Entity> spawned = new ArrayList<>();
    protected final Map<Player, Player> lastHit = new HashMap<>();
    protected int countdown, time;
    public int map;
    final Map<Player, Integer> votes = new HashMap<>();
    private BukkitTask ticker;
    private int stuck;   // hlídač: hra zaseknutá při stavbě se po čase sama zruší
    private boolean destroyed;

    protected Game(GameManager gm, GameType type, int slot) {
        this.pl = gm.pl; this.gm = gm; this.type = type; this.slot = slot; this.w = pickWorld(); this.ox = slot * 1000; this.oz = type.ordinal() * 1000;
        // každá minihra má vlastní oblast (řádek Z), takže se mapy různých her nikdy nepřekryjí
    }

    // ---------- co implementuje každá hra ----------
    /** svět, ve kterém se hra odehrává (většina her = sdílená arena, UHC Run = vlastní vanilla overworld) */
    protected World pickWorld() { return pl.world(type); }
    protected abstract void buildMap(U.BlockQueue.Builder b);
    /** názvy variant mapy (hlasování) */
    public List<String> maps() { return List.of("Standard"); }
    /** barevné varianty map: {název, index základní mapy, Material z, Material na, z, na, ...} */
    protected Object[][] themes() { return new Object[0][]; }
    /** všechny mapy k hlasování = základní + varianty */
    public List<String> allMaps() { List<String> l = new ArrayList<>(maps()); for (Object[] t : themes()) l.add((String) t[0]); return l; }
    /** název mapy, která se hraje */
    protected String mapName = null;
    public String mapName() { return mapName != null ? mapName : maps().get(Math.min(map, maps().size() - 1)); }
    protected abstract void start();
    protected void afterBuild() {}
    protected void second() {}
    protected int timeLimit() { return 600; }
    protected void timeUp() { end(new ArrayList<>(alive)); }
    protected GameMode mode() { return GameMode.ADVENTURE; }
    protected int voidY() { return oy - 40; }
    protected Dmg damage() { return Dmg.NONE; }
    protected boolean hunger() { return false; }
    protected boolean canDrop() { return false; }
    protected boolean hideNames() { return false; }
    /** vlastní gravitace hry (null = nízká marsovská) */
    public Double gravity() { return null; }
    // ---- event (pořádá majitel přes /event) ----
    public boolean event, eventHold;
    public int eventMax = -1;
    public Double eventGravity;
    public int maxPlayers() { return event && eventMax > 0 ? eventMax : type.max; }
    /** smí hráč vystoupit z vozidla? */
    public boolean canExitVehicle(Player p) { return true; }
    public boolean canBreak(Player p, Block b) { return false; }
    public boolean canPlace(Player p, Block b) { return false; }
    public boolean allowFlow(Block to) { return true; }
    public void onDeath(Player p, Player killer) { eliminate(p, killer); }
    public void onVoid(Player p) { onDeath(p, lastHit.get(p)); }
    /** vrátí false = útok zrušit */
    public boolean onPvp(Player victim, Player attacker, EntityDamageByEntityEvent e) { return true; }
    public void onMove(Player p, Location to) {}
    public void onInteract(PlayerInteractEvent e) {}
    public void onInteractEntity(PlayerInteractEntityEvent e) {}
    public void onBlockDamage(BlockDamageEvent e) {}
    public void onProjectileHit(ProjectileHitEvent e) {}
    protected String status(Player p) { return status(); }
    protected String status() { return "&7Naživu: &a" + alive.size() + " &8| &7Čas: &e" + fmt(time); }

    // ---------- společná logika ----------
    /** výška čekárny (nesmí zasahovat do mapy) */
    protected int waitY() { return oy + 45; }
    public Location waitSpot() { return U.center(w, ox, waitY() + 1, oz); }
    protected Location specSpot() { return U.center(w, ox, oy + 15, oz); }
    public boolean joinable() { return (state == State.BUILDING || state == State.WAITING || state == State.COUNTDOWN) && players.size() < maxPlayers(); }
    public boolean isPlaced(Block b) { return placed.contains(U.pack(b.getX(), b.getY(), b.getZ())); }
    public void markPlaced(Block b) { if (placed.add(U.pack(b.getX(), b.getY(), b.getZ()))) placedList.add(new int[]{b.getX(), b.getY(), b.getZ()}); }
    public boolean owns(Location l) { return l.getWorld() == w && Math.abs(l.getBlockX() - ox) < 450 && Math.abs(l.getBlockZ() - oz) < 450; }

    void build() {
        U.BlockQueue.Builder b = pl.queue.builder(w);
        // čekárna nad mapou
        int y = waitY();
        b.fill(ox - 5, y, oz - 5, ox + 5, y, oz + 5, Material.LIGHT_BLUE_STAINED_GLASS);
        b.fill(ox - 5, y + 1, oz - 5, ox + 5, y + 4, oz - 5, Material.BARRIER);
        b.fill(ox - 5, y + 1, oz + 5, ox + 5, y + 4, oz + 5, Material.BARRIER);
        b.fill(ox - 5, y + 1, oz - 5, ox - 5, y + 4, oz + 5, Material.BARRIER);
        b.fill(ox + 5, y + 1, oz - 5, ox + 5, y + 4, oz + 5, Material.BARRIER);
        b.fill(ox - 5, y + 5, oz - 5, ox + 5, y + 5, oz + 5, Material.BARRIER);
        b.set(ox, y, oz, Material.SEA_LANTERN);
        record(b);
        b.submit(() -> {
            if (destroyed) return;
            state = State.WAITING;
            for (Player p : players) p.teleport(waitSpot());
            if (players.isEmpty()) destroy(); else checkStart();
        });
        ticker = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20, 20);
    }

    private void record(U.BlockQueue.Builder b) {
        for (Object[] e : b.entries()) { int bx = (int) e[0], by = (int) e[1], bz = (int) e[2]; if (placed.add(U.pack(bx, by, bz))) placedList.add(new int[]{bx, by, bz}); }
    }

    // ---------------- kity ----------------
    /** {id, název, ikona, popis...} – prázdné = hra nemá kity */
    public List<Object[]> kits() { return List.of(); }
    protected final Map<Player, String> kit = new HashMap<>();
    public String kitOf(Player p) {
        String id = kit.get(p);
        for (Object[] k : kits()) if (k[0].equals(id)) return (String) k[1];
        return kits().isEmpty() ? "-" : (String) kits().get(0)[1];
    }
    protected String kitId(Player p) { return kit.getOrDefault(p, kits().isEmpty() ? "" : (String) kits().get(0)[0]); }
    public void openKits(Player p) {
        GameManager.KitHolder h = new GameManager.KitHolder(this);
        h.inv = Bukkit.createInventory(h, 27, cz.adrrex.squarestar.Gui.shop(27, cz.adrrex.squarestar.Lang.t(p, "&f&lVyber kit")));
        int[] pos = {10, 11, 12, 13, 14, 15, 16};
        List<Object[]> ks = kits();
        for (int i = 0; i < ks.size() && i < pos.length; i++) {
            Object[] k = ks.get(i);
            List<String> lore = new ArrayList<>();
            for (int j = 3; j < k.length; j++) lore.add("&7" + k[j]);
            lore.add("");
            lore.add(k[0].equals(kitId(p)) ? "&a✔ Vybráno" : "&eKlikni pro výběr");
            ItemStack it = U.item((Material) k[2], 1, "&6&l" + k[1], lore.toArray(new String[0]));
            if (k[0].equals(kitId(p))) { var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(pos[i], U.tag(it, "ss_kit", (String) k[0]));
        }
        p.openInventory(h.inv);
    }
    public void chooseKit(Player p, String id) {
        kit.put(p, id);
        U.msg(p, "&aKit: &6&l" + kitOf(p));
        U.sound(p, Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.2f);
        if (state != State.RUNNING) p.getInventory().setItem(0, cz.adrrex.squarestar.Gui.skin(p, U.tag(U.item(Material.CHEST, 1, "&6&lVyber kit &7(pravý klik)", "&7Tvůj kit: &f" + kitOf(p)), "ss_game", "kit"), "icon_menu_kit"));
        p.closeInventory();
    }

    // ---------------- výběr týmu (např. BedWars) ----------------
    public final Map<Player, Integer> teamPick = new HashMap<>();
    /** barevné názvy týmů, které si hráč může vybrat (prázdné = bez výběru) */
    public List<String> teamNames() { return List.of(); }
    protected List<Material> teamIcons() { return List.of(); }
    public void openTeams(Player p) {
        GameManager.TeamHolder h = new GameManager.TeamHolder(this);
        h.inv = Bukkit.createInventory(h, 27, cz.adrrex.squarestar.Gui.title(27, cz.adrrex.squarestar.Lang.t(p, "&f&lVyber tým")));
        List<String> t = teamNames();
        int start = 13 - t.size() / 2;
        int cap = (int) Math.ceil(Math.max(players.size(), 2) / (double) t.size());
        for (int i = 0; i < t.size(); i++) {
            final int idx = i;
            List<String> who = teamPick.entrySet().stream().filter(x -> x.getValue() == idx).map(x -> x.getKey().getName()).toList();
            List<String> lore = new ArrayList<>();
            lore.add("&7Hráči: &f" + who.size());
            for (String n : who) lore.add("&8- &f" + n);
            lore.add("");
            lore.add(Integer.valueOf(idx).equals(teamPick.get(p)) ? "&a✔ Tvůj tým" : "&eKlikni pro výběr");
            ItemStack it = U.tag(U.item(teamIcons().get(i), Math.max(1, who.size()), t.get(i), lore.toArray(new String[0])), "ss_team", String.valueOf(i));
            if (Integer.valueOf(idx).equals(teamPick.get(p))) { var m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            h.inv.setItem(start + i, it);
        }
        h.inv.setItem(22, U.tag(U.item(Material.ENDER_EYE, 1, "&7&lNáhodný tým", "&7Rozdělí tě hra"), "ss_team", "-1"));
        p.openInventory(h.inv);
    }
    void chooseTeam(Player p, int i) {
        if (state != State.WAITING && state != State.COUNTDOWN && state != State.BUILDING) { U.msg(p, "&cHra už začala."); p.closeInventory(); return; }
        if (i < 0) { teamPick.remove(p); U.msg(p, "&7Tým ti vybere hra."); openTeams(p); return; }
        int cap = (int) Math.ceil(Math.max(players.size(), 2) / (double) Math.min(teamNames().size(), Math.max(2, players.size())));
        long in = teamPick.entrySet().stream().filter(x -> x.getValue() == i && x.getKey() != p).count();
        if (in >= cap) { U.msg(p, "&cTento tým je plný – vyber jiný, ať je to fér."); return; }
        teamPick.put(p, i);
        U.msg(p, "&aVybral sis tým " + teamNames().get(i));
        U.sound(p, Sound.UI_BUTTON_CLICK, 1.5f);
        openTeams(p);
    }

    public void openVote(Player p) {
        GameManager.VoteHolder h = new GameManager.VoteHolder(this);
        h.inv = Bukkit.createInventory(h, 27, cz.adrrex.squarestar.Gui.vote(27, cz.adrrex.squarestar.Lang.t(p, "&f&lHlasování o mapě")));
        List<String> m = allMaps();
        Material[] icons = {Material.RED_TERRACOTTA, Material.LIGHT_BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.CYAN_CONCRETE, Material.ORANGE_CONCRETE};
        int start = 13 - m.size() / 2;
        for (int i = 0; i < m.size() && i < 7; i++) {
            final int idx = i;
            long n = votes.values().stream().filter(v -> v == idx).count();
            h.inv.setItem(Math.max(10, start + i), cz.adrrex.squarestar.Gui.skin(p, U.item(icons[i % icons.length], (int) Math.max(1, n), "&e&l" + m.get(i), "&7Hlasů: &a" + n, votes.get(p) != null && votes.get(p) == i ? "&a✔ Tvůj hlas" : "&eKlikni pro hlas"), "icon_menu_vote"));
        }
        p.openInventory(h.inv);
    }

    void vote(Player p, int slot) {
        int start = Math.max(10, 13 - allMaps().size() / 2);
        int i = slot - start;
        if (i < 0 || i >= allMaps().size() || slot > 16) return;
        if (state != State.WAITING && state != State.COUNTDOWN) { U.msg(p, "&cHlasování už skončilo."); return; }
        votes.put(p, i);
        U.msg(p, "&aHlasuješ pro mapu &e" + allMaps().get(i));
        U.sound(p, Sound.UI_BUTTON_CLICK, 1.5f);
        openVote(p);
    }

    void addPlayer(Player p) {
        players.add(p);
        U.reset(p, GameMode.ADVENTURE);
        p.getInventory().setItem(8, cz.adrrex.squarestar.Gui.skin(p, U.tag(U.item(Material.RED_BED, 1, "&c&lOpustit hru &7(pravý klik)"), "ss_game", "leave"), "icon_menu_leave"));
        if (allMaps().size() > 1) p.getInventory().setItem(4, cz.adrrex.squarestar.Gui.skin(p, U.tag(U.item(Material.MAP, 1, "&e&lHlasování o mapě &7(pravý klik)", "&7Mapy: &f" + String.join("&7, &f", allMaps())), "ss_game", "vote"), "icon_menu_vote"));
        if (!kits().isEmpty()) p.getInventory().setItem(0, cz.adrrex.squarestar.Gui.skin(p, U.tag(U.item(Material.CHEST, 1, "&6&lVyber kit &7(pravý klik)", "&7Tvůj kit: &f" + kitOf(p)), "ss_game", "kit"), "icon_menu_kit"));
        if (!teamNames().isEmpty()) p.getInventory().setItem(2, cz.adrrex.squarestar.Gui.skin(p, U.tag(U.item(Material.WHITE_WOOL, 1, "&b&lVyber tým &7(pravý klik)", "&7S kým chceš hrát?"), "ss_game", "team"), "icon_menu_team"));
        if (state == State.BUILDING) U.msg(p, "&ePřipravuji čekárnu... &7(" + type.display + ")");
        else { p.teleport(waitSpot()); U.msg(p, "&7Mapa: &a" + waitMap() + (allMaps().size() > 1 ? " &8| &7hlasuj mapou v hotbaru" : "")); }
        if (hideNames()) gm.hideName(p, true);
        pl.applyGravity(p);
        onJoin(p);
        broadcast("&a" + p.getName() + " &7se připojil &8(" + players.size() + "/" + maxPlayers() + ")");
        checkStart();
    }

    void removePlayer(Player p, boolean toLobby) {
        players.remove(p);
        votes.remove(p);
        teamPick.remove(p);
        kit.remove(p);
        lastHit.remove(p);
        boolean wasAlive = alive.remove(p);
        gm.map.remove(p);
        gm.hideName(p, false);
        dropBoard(p);
        onLeave(p);
        if (toLobby && p.isOnline()) pl.lobby.send(p);
        if (state == State.ENDING) return;
        if (players.isEmpty() && state != State.BUILDING) { destroy(); return; }
        if (state == State.RUNNING && wasAlive) { broadcast("&c" + p.getName() + " &7odešel ze hry."); checkWin(); }
    }

    protected void onLeave(Player p) {}
    /** hráč se právě připojil do čekárny */
    protected void onJoin(Player p) {}

    void checkStart() {
        if (eventHold) return;
        if (state == State.WAITING && players.size() >= type.min) { state = State.COUNTDOWN; countdown = pl.getConfig().getInt("lobby-countdown", 30); }   // 30 s: stihne se dorazit víc hráčů
    }

    /** mapa v čekárně: jediná mapa, nebo ta, která teď vede v hlasování */
    String waitMap() {
        List<String> m = allMaps();
        if (m.size() <= 1) return m.get(0);
        int[] c = new int[m.size()];
        for (int v : votes.values()) if (v >= 0 && v < c.length) c[v]++;
        int best = 0;
        for (int i = 1; i < c.length; i++) if (c[i] > c[best]) best = i;
        return c[best] == 0 ? m.get(0) + " &7(zatím nikdo nehlasoval)" : m.get(best) + " &7(" + c[best] + " hlasů)";
    }

    // ---------------- sidebar vpravo (jako na Hypixelu) ----------------
    private final Map<Player, org.bukkit.scoreboard.Scoreboard> boards = new HashMap<>();
    /** hra má vlastní sidebar (např. Speedrun) */
    protected boolean ownBoard() { return false; }
    /** řádky sidebaru – hry si můžou přidat vlastní */
    protected List<String> boardLines(Player p) {
        List<String> l = new ArrayList<>();
        l.add("&7" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " &8#" + slot);
        l.add(" ");
        l.add("&fHra: &6" + type.display);
        if (state == State.WAITING || state == State.COUNTDOWN) {
            l.add("&fMapa: &a" + (allMaps().size() > 1 ? "hlasuje se" : maps().get(0)));
            if (allMaps().size() > 1) l.add("&fVede: &a" + waitMap().replaceAll("&7\\(.*\\)", "").trim());
            l.add("&fHráči: &a" + players.size() + "&7/" + maxPlayers());
            if (event) l.add("&d&l★ EVENT");
            l.add(state == State.COUNTDOWN ? "&fStart za: &e" + countdown + " s" : "&fČekáme na &e" + Math.max(0, type.min - players.size()) + " &fhráče");
        } else {
            l.add("&fMapa: &a" + mapName());
            l.add("&fNaživu: &a" + alive.size() + "&7/" + players.size());
            l.add("&fČas: &e" + fmt(time));
        }
        if (!kits().isEmpty()) l.add("&fKit: &6" + kitOf(p));
        l.add("  ");
        l.add("&fLevel: &b✦" + pl.levels.level(p));
        l.add("   ");
        l.add("&e&lSQUARESTAR");
        return l;
    }
    private void updateBoard(Player p) {
        if (ownBoard() || !p.isOnline()) return;
        var sb = boards.computeIfAbsent(p, x -> { var b = Bukkit.getScoreboardManager().getNewScoreboard(); p.setScoreboard(b); return b; });
        if (time % 5 == 0 || hideNames() || sb.getTeams().isEmpty()) copyRankTeams(sb);
        var old = sb.getObjective("ss");
        if (old != null) old.unregister();
        var o = sb.registerNewObjective("ss", org.bukkit.scoreboard.Criteria.DUMMY, U.c(cz.adrrex.squarestar.Lang.t(p, "&6&l✦ &e&lSQUARE&6&lSTAR &6&l✦")));
        o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        List<String> lines = boardLines(p);
        int sc = lines.size();
        for (String s : lines) o.getScore(cz.adrrex.squarestar.Lang.t(p, s).replace('&', '§')).setScore(sc--);
    }
    /** obrázky ranků nad hlavami i během hry */
    public static void copyRankTeams(org.bukkit.scoreboard.Scoreboard sb) {
        var main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (var t : main.getTeams()) {
            if (!t.getName().startsWith("ss_")) continue;
            var c = sb.getTeam(t.getName());
            if (c == null) c = sb.registerNewTeam(t.getName());
            c.prefix(t.prefix()); c.suffix(t.suffix());
            try { if (t.hasColor() && t.color() instanceof net.kyori.adventure.text.format.NamedTextColor nc) c.color(nc); } catch (IllegalStateException ignored) {}
            for (var opt : org.bukkit.scoreboard.Team.Option.values()) c.setOption(opt, t.getOption(opt));
            for (String e : t.getEntries()) if (!c.hasEntry(e)) c.addEntry(e);
        }
    }
    private void dropBoard(Player p) {
        if (boards.remove(p) != null && p.isOnline()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void tick() {
        for (Player p : players) updateBoard(p);
        switch (state) {
            case WAITING -> { for (Player p : players) U.bar(p, "&eČekáme na hráče &f" + players.size() + "&7/&f" + type.min + " &8| &6" + type.display + " &8| &aMapa: &f" + waitMap()); }
            case COUNTDOWN -> {
                if (players.size() < type.min) { state = State.WAITING; broadcast("&cMálo hráčů, odpočet zrušen."); return; }
                if (players.size() >= maxPlayers() && countdown > 5) countdown = 5;
                for (Player p : players) {
                    U.bar(p, "&aStart za &f" + countdown + " s &8| &6" + type.display + " &8| &aMapa: &f" + waitMap());
                    if (countdown <= 5) { U.title(p, "&6" + countdown, "&7" + type.display); U.sound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.5f); }
                }
                if (--countdown < 0) begin();
            }
            case STARTING -> {
                for (Player p : players) U.bar(p, "&eStavím mapu &6" + mapName() + "&e...");
                if (++stuck > 90) { pl.getLogger().warning("Hra " + type + " #" + slot + " se zasekla pri stavbe mapy - rusim"); broadcast("&cMapu se nepodařilo postavit, hra se ruší. Zkus to znovu."); destroy(); }
            }
            case BUILDING -> { if (++stuck > 120) { pl.getLogger().warning("Hra " + type + " #" + slot + " se zasekla v cekarne - rusim"); broadcast("&cČekárnu se nepodařilo připravit. Zkus to znovu."); destroy(); } }
            case RUNNING -> {
                time++;
                second();
                if (state != State.RUNNING) return;
                if (time >= timeLimit()) { broadcast("&eČas vypršel!"); timeUp(); return; }
                for (Player p : players) U.bar(p, status(p));
            }
            default -> {}
        }
    }

    private void begin() {
        List<String> m = allMaps();
        int[] count = new int[m.size()];
        for (int v : votes.values()) if (v < count.length) count[v]++;
        int best = 0;
        for (int i = 1; i < count.length; i++) if (count[i] > count[best] || (count[i] == count[best] && rnd.nextBoolean())) best = i;
        int base = maps().size();
        Map<Material, Material> remap = null;
        mapName = m.get(best);
        if (best >= base) { // barevná varianta: postav základní mapu se záměnou bloků
            Object[] t = themes()[best - base];
            map = (int) t[1];
            remap = new EnumMap<>(Material.class);
            for (int k = 2; k + 1 < t.length; k += 2) remap.put((Material) t[k], (Material) t[k + 1]);
        } else map = best;
        state = State.STARTING; stuck = 0;
        if (m.size() > 1) broadcast("&eVyhrála mapa: &6&l" + mapName + " &7(" + count[best] + " hlasů)");
        U.BlockQueue.Builder b = pl.queue.builder(w);
        b.remap = remap;
        buildMap(b);
        record(b);
        b.submit(() -> {
            if (destroyed || state != State.STARTING) return;
            if (players.size() < Math.min(type.min, 1)) { destroy(); return; }
            afterBuild();
            launch();
        });
    }

    private void launch() {
        state = State.RUNNING; time = 0;
        alive.addAll(players);
        for (Player p : players) {
            U.reset(p, mode());
            pl.applyGravity(p);
            U.title(p, "&a&lSTART!", "&7" + type.display);
            U.sound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f);
        }
        start();
        // krátké „jak se hraje" pro všechny na začátku (jako na velkých serverech)
        for (Player p : players) {
            ChatFree.send(p, U.c("&8&m                                             "));
            ChatFree.send(p, U.c(Lang.t(p, "  &6&l✦ " + type.display.toUpperCase(java.util.Locale.ROOT) + " ✦")));
            ChatFree.send(p, U.c(Lang.t(p, "  &f" + type.desc)));
            ChatFree.send(p, U.c(Lang.t(p, "  &7Mapa: &a" + mapName() + "  &7Hráčů: &a" + players.size() + "  &7Hodně štěstí!")));
            ChatFree.send(p, U.c("&8&m                                             "));
        }
    }

    /** hráč vyřazený ze hry může rovnou jít do další (nemusí čekat na konec) */
    public void leaveEliminated(Player p) {
        if (state == State.RUNNING && players.contains(p) && !alive.contains(p)) {
            int kills = gameKills.getOrDefault(p, 0);
            if (pl.stats != null) pl.stats.add(p, "games", 1);
            if (pl.coins != null) pl.coins.add(p, 5 + Math.min(kills, 10) * 2, "odehraná hra");
            pl.levels.add(p, 25 + Math.min(kills, 10) * 5, "odehraná hra", true);
            if (pl.progress != null) pl.progress.track(p, "games", type, 1);
            gm.lastType.put(p.getUniqueId(), type);
        }
        removePlayer(p, true);
    }

    // ---------------- zabití: statistiky, úkoly, série, první krev ----------------
    protected final Map<Player, Integer> gameKills = new HashMap<>();
    private final Map<Player, Integer> killStreak = new HashMap<>();
    private boolean firstBlood;
    /** připíše zabití (statistiky, denní úkoly, série). Hry s oživením to volají při každém zabití. */
    public void creditKill(Player killer, Player victim) {
        if (victim != null) killStreak.remove(victim);
        if (killer == null || killer == victim || !players.contains(killer)) return;
        if (pl.stats != null) pl.stats.add(killer, "kills", 1);
        if (pl.progress != null) pl.progress.track(killer, "kills", type, 1);
        int total = gameKills.merge(killer, 1, Integer::sum);
        int s = killStreak.merge(killer, 1, Integer::sum);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f + Math.min(s, 8) * 0.12f);
        U.bar(killer, "&c⚔ +1 zabití &8| &7celkem &f" + total + (s >= 2 ? " &8| &6série " + s : ""));
        if (!firstBlood) { firstBlood = true; broadcast("&4&l🩸 PRVNÍ KREV! &f" + killer.getName() + " &7vyřadil &f" + (victim == null ? "?" : victim.getName())); if (pl.coins != null) pl.coins.add(killer, 5, "první krev", true); }
        if (s == 3) broadcast("&6⚡ " + killer.getName() + " &7má sérii &c3 zabití&7!");
        else if (s == 5) { broadcast("&c&l🔥 " + killer.getName() + " je NEZASTAVITELNÝ! &7(5 v řadě)"); if (pl.coins != null) pl.coins.add(killer, 10, "série 5", true); }
        else if (s == 10) { broadcast("&d&l☄ " + killer.getName() + " je LEGENDÁRNÍ! &7(10 v řadě)"); if (pl.coins != null) pl.coins.add(killer, 25, "série 10", true); }
    }

    public void eliminate(Player p, Player killer) {
        if (!alive.remove(p)) return;
        creditKill(killer, p);
        if (killer != null && killer != p) broadcast("&c☠ " + p.getName() + " &7byl vyřazen hráčem &c" + killer.getName());
        else broadcast("&c☠ " + p.getName() + " &7je vyřazen!");
        if (canDrop()) for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() != Material.AIR) w.dropItemNaturally(p.getLocation(), it);
        U.reset(p, GameMode.SPECTATOR);
        p.teleport(specSpot());
        U.title(p, "&c&lVYŘAZEN", "&7Sleduješ hru. &e/ss znovu &7= další hra");
        ChatFree.send(p, U.c(Lang.t(p, U.PREFIX + "&7Vyřazen. ")).append(U.c(Lang.t(p, "&a&l[▶ HRÁT ZNOVU]")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ss znovu")))
                .append(U.c("  ")).append(U.c(Lang.t(p, "&7[Sledovat dál]"))));
        checkWin();
    }

    protected void checkWin() {
        if (state != State.RUNNING) return;
        if (alive.size() <= 1) end(new ArrayList<>(alive));
    }

    protected void end(List<Player> winners) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        String names = winners.isEmpty() ? "&7nikdo" : String.join("&7, &a", winners.stream().map(Player::getName).toList());
        for (Player p : players) {
            boolean win = winners.contains(p);
            U.title(p, win ? "&6&l✦ VÍTĚZSTVÍ ✦" : "&c&lKONEC HRY", "&7Vyhrál: &a" + names);
            U.sound(p, win ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 1f);
        }
        if (pl.stats != null) for (Player p : players) { pl.stats.add(p, "games", 1); if (winners.contains(p)) pl.stats.add(p, "wins", 1); }
        for (Player p : players) {
            boolean win = winners.contains(p);
            int kills = gameKills.getOrDefault(p, 0);
            int coins = (win ? 25 : 5) + Math.min(kills, 10) * 2, xp = (win ? 100 : 25) + Math.min(kills, 10) * 5;
            if (pl.coins != null) pl.coins.add(p, coins, win ? "výhra" : "odehraná hra", true);
            pl.levels.add(p, xp, win ? "výhra" : "odehraná hra", true);
            summary(p, win, names, kills, coins, xp);
            if (pl.progress != null) { pl.progress.track(p, "games", type, 1); if (win) pl.progress.track(p, "wins", type, 1); }
            gm.lastType.put(p.getUniqueId(), type);
        }
        String keyType = pl.getConfig().getString("win-reward.key", "common");
        int amount = pl.getConfig().getInt("win-reward.amount", 1);
        for (Player p : winners) {
            ItemStack k = pl.crates.key(keyType, amount);
            if (k != null) gm.pendingRewards.computeIfAbsent(p.getUniqueId(), x -> new ArrayList<>()).add(k);
            U.msg(p, "&aVyhrál jsi! Dostaneš &e" + amount + "x klíč &ana spawnu.");
            for (int i = 0; i < 3; i++) {
                final int d = i;
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (!p.isOnline() || p.getWorld() != w) return;
                    Firework fw = w.spawn(p.getLocation().add(rnd.nextInt(5) - 2, 1, rnd.nextInt(5) - 2), Firework.class, f -> {
                        var meta = f.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.values()[(d + rnd.nextInt(3)) % FireworkEffect.Type.values().length])
                                .withColor(Color.RED, Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).trail(true).flicker(true).build());
                        meta.setPower(1);
                        f.setFireworkMeta(meta);
                    });
                    spawned.add(fw);
                }, 10L + i * 15L);
            }
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (Player p : new ArrayList<>(players)) removePlayer(p, true);
            destroy();
        }, 120);
    }

    /** přehledné shrnutí na konci hry (místo spousty zpráv) + tlačítko „Hrát znovu" */
    private void summary(Player p, boolean win, String names, int kills, int coins, int xp) {
        String line = "&8&m                                             ";
        ChatFree.send(p, U.c(line));
        ChatFree.send(p, U.c(Lang.t(p, "  " + (win ? "&6&l✦ VÍTĚZSTVÍ ✦" : "&c&lKONEC HRY") + " &8– &e" + type.display)));
        ChatFree.send(p, U.c(Lang.t(p, "  &7Mapa: &f" + mapName() + "  &7Čas: &f" + fmt(time))));
        ChatFree.send(p, U.c(Lang.t(p, "  &7Vítěz: &a" + names)));
        if (!gameKills.isEmpty()) {
            List<Map.Entry<Player, Integer>> top = new ArrayList<>(gameKills.entrySet());
            top.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder("  &7Nejvíc zabití: ");
            String[] medal = {"&6①", "&7②", "&c③"};
            for (int i = 0; i < Math.min(3, top.size()); i++) sb.append(medal[i]).append(" &f").append(top.get(i).getKey().getName()).append(" &8(").append(top.get(i).getValue()).append(") ");
            ChatFree.send(p, U.c(Lang.t(p, sb.toString())));
        }
        ChatFree.send(p, U.c(Lang.t(p, "  &7Tvoje zabití: &c" + kills + "  &7Odměna: &6+" + coins + " " + cz.adrrex.squarestar.Coins.ICON + " &b+" + xp + " XP")));
        ChatFree.send(p, U.c(Lang.t(p, "  ")).append(U.c(Lang.t(p, "&a&l[▶ HRÁT ZNOVU]")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ss znovu"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(U.c(Lang.t(p, "&eDalší hra: " + type.display))))).append(U.c("   "))
                .append(U.c(Lang.t(p, "&e&l[✦ JINÁ HRA]")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ss hry"))));
        ChatFree.send(p, U.c(line));
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        state = State.ENDING;
        if (ticker != null) ticker.cancel();
        for (Player p : new ArrayList<>(players)) removePlayer(p, true);
        cleanupEntities();
        U.BlockQueue.Builder b = pl.queue.builder(w);
        for (int[] c : placedList) b.set(c[0], c[1], c[2], Material.AIR);
        b.submit(() -> gm.free(this));
    }

    void destroyNow() {
        destroyed = true;
        if (ticker != null) ticker.cancel();
        for (Player p : new ArrayList<>(players)) removePlayer(p, true);
        cleanupEntities();
        for (int[] c : placedList) w.getBlockAt(c[0], c[1], c[2]).setType(Material.AIR, false);
    }

    protected void onDestroy() {}

    private void cleanupEntities() {
        for (Player p : new ArrayList<>(boards.keySet())) dropBoard(p);
        onDestroy();
        for (Entity e : spawned) if (e.isValid()) e.remove();
        for (Entity e : w.getEntities()) if (!(e instanceof Player) && owns(e.getLocation())) e.remove();
    }

    protected <T extends Entity> T spawn(Location l, Class<T> c, java.util.function.Consumer<T> fn) {
        T e = w.spawn(l, c, en -> { en.setPersistent(false); fn.accept(en); });
        spawned.add(e);
        return e;
    }

    public void broadcast(String s) { for (Player p : players) U.msg(p, s); }
    protected static String fmt(int s) { return String.format("%d:%02d", s / 60, s % 60); }
    protected Location loc(double x, double y, double z) { return new Location(w, ox + x, oy + y, oz + z); }
}
