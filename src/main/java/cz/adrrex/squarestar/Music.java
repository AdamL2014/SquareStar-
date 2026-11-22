package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Hudba v lobby. /hudba otevře menu: hráč si vybere skladbu (levý klik = hned přehrát),
 * pravým klikem skládá vlastní playlist, přepíná režim (automaticky / můj playlist / opakovat) a míchání.
 * Hlasitost = posuvník „Jukebox/Note Blocks“ v nastavení zvuku.
 */
public final class Music implements CommandExecutor, Listener {
    private static final NamespacedKey OFF = new NamespacedKey("squarestar", "music_off");
    private static final NamespacedKey PL = new NamespacedKey("squarestar", "music_pl");      // "id,id,id"
    private static final NamespacedKey MODE = new NamespacedKey("squarestar", "music_mode");  // auto | playlist | repeat
    private static final NamespacedKey VOL = new NamespacedKey("squarestar", "music_vol");    // 10–100 (%)
    private static final NamespacedKey SHUF = new NamespacedKey("squarestar", "music_shuf");

    /** skladba: id, zvuk (klíč), název, délka v sekundách, ikonka v menu */
    private record Track(String id, String sound, String name, int sec, Material icon) {}
    private static Track vanilla(String id, Sound s, String name, int sec, Material icon) { return new Track(id, s.key().asString(), name, sec, icon); }
    private static final List<Track> CAT = List.of(
            vanilla("far", Sound.MUSIC_DISC_FAR, "Far – C418", 174, Material.MUSIC_DISC_FAR),
            vanilla("relic", Sound.MUSIC_DISC_RELIC, "Relic – Aaron Cherof", 218, Material.MUSIC_DISC_RELIC),
            vanilla("otherside", Sound.MUSIC_DISC_OTHERSIDE, "Otherside – Lena Raine", 195, Material.MUSIC_DISC_OTHERSIDE),
            vanilla("mall", Sound.MUSIC_DISC_MALL, "Mall – C418", 197, Material.MUSIC_DISC_MALL),
            vanilla("precipice", Sound.MUSIC_DISC_PRECIPICE, "Precipice – Aaron Cherof", 299, Material.MUSIC_DISC_PRECIPICE),
            vanilla("creator", Sound.MUSIC_DISC_CREATOR, "Creator – Lena Raine", 176, Material.MUSIC_DISC_CREATOR),
            vanilla("5", Sound.MUSIC_DISC_5, "5 – Samuel Åberg", 178, Material.MUSIC_DISC_5),
            vanilla("pigstep", Sound.MUSIC_DISC_PIGSTEP, "Pigstep – Lena Raine", 149, Material.MUSIC_DISC_PIGSTEP),
            vanilla("wait", Sound.MUSIC_DISC_WAIT, "Wait – C418", 238, Material.MUSIC_DISC_WAIT),
            vanilla("ward", Sound.MUSIC_DISC_WARD, "Ward – C418", 251, Material.MUSIC_DISC_WARD),
            vanilla("stal", Sound.MUSIC_DISC_STAL, "Stal – C418", 150, Material.MUSIC_DISC_STAL),
            vanilla("strad", Sound.MUSIC_DISC_STRAD, "Strad – C418", 188, Material.MUSIC_DISC_STRAD),
            vanilla("chirp", Sound.MUSIC_DISC_CHIRP, "Chirp – C418", 185, Material.MUSIC_DISC_CHIRP),
            vanilla("cat", Sound.MUSIC_DISC_CAT, "Cat – C418", 185, Material.MUSIC_DISC_CAT),
            vanilla("blocks", Sound.MUSIC_DISC_BLOCKS, "Blocks – C418", 345, Material.MUSIC_DISC_BLOCKS),
            vanilla("mellohi", Sound.MUSIC_DISC_MELLOHI, "Mellohi – C418", 96, Material.MUSIC_DISC_MELLOHI),
            vanilla("13", Sound.MUSIC_DISC_13, "13 – C418", 178, Material.MUSIC_DISC_13),
            vanilla("11", Sound.MUSIC_DISC_11, "11 – C418", 71, Material.MUSIC_DISC_11),
            vanilla("tears", Sound.MUSIC_DISC_TEARS, "Tears – Amos Roddy", 175, Material.MUSIC_DISC_TEARS),
            vanilla("lavachicken", Sound.MUSIC_DISC_LAVA_CHICKEN, "Lava Chicken – Hyper Potions", 135, Material.MUSIC_DISC_LAVA_CHICKEN),
            new Track("xmas1", "squarestar:music.xmas1", "❄ Vánoce na Marsu – SquareStar", 60, Material.MUSIC_DISC_CAT),
            new Track("xmas2", "squarestar:music.xmas2", "❄ Sněžná noc – SquareStar", 60, Material.MUSIC_DISC_13),
            new Track("bounce", "squarestar:music.bounce", "Bounce – fingerspit", 235, Material.MUSIC_DISC_STAL),
            new Track("flurry", "squarestar:music.flurry", "Flurry – Hyper Potions", 155, Material.MUSIC_DISC_MELLOHI),
            new Track("calm4", "squarestar:music.calm4", "Magnetic Circuit (calm4) – Notch", 194, Material.MUSIC_DISC_11));
    private static final List<String> AUTO = List.of("far", "relic", "otherside", "mall", "precipice", "creator");
    /** o Vánocích: vlastní vánoční písničky střídané s klidnými deskami */
    private static final List<String> AUTO_XMAS = List.of("xmas1", "far", "xmas2", "otherside");
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40};

    private static Track track(String id) { for (Track t : CAT) if (t.id.equals(id)) return t; return null; }

    private final SquareStar pl;
    private final Map<UUID, Object[]> state = new HashMap<>();   // {Track, konec v ms}

    public Music(SquareStar pl) {
        this.pl = pl;
        if (pl.getConfig().getBoolean("lobby.music", true)) Bukkit.getScheduler().runTaskTimer(pl, this::tick, 100, 20);
        Bukkit.getScheduler().runTaskTimer(pl, this::ghastChoir, 20, 6);
    }

    // ---------------- zpívající ghasti v menu, když hraje Tears ----------------
    private static final int[] GHAST_SLOTS = {41, 42, 43, 44};
    private int ghastFrame = 0;
    private static final String[] GHAST_NOTES = {"&b♪", "&d♫", "&e♪", "&a♫"};

    private ItemStack ghastItem(int idx, int frame) {
        Material m = Material.matchMaterial("HAPPY_GHAST_SPAWN_EGG");
        if (m == null) m = Material.GHAST_SPAWN_EGG;
        boolean open = ((frame + idx) % 4) < 2;   // „otevřená pusa“ se střídá
        ItemStack it = U.item(m, 1, GHAST_NOTES[(frame + idx) % GHAST_NOTES.length] + " &f&lGast zpívá " + (open ? "&7(áááá)" : "&7(ú-ú-ú)"),
                "&7Ghasti si prozpěvují &fTears", "&8♪ ♫ ♪ ♫");
        var mm = it.getItemMeta();
        mm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        mm.setEnchantmentGlintOverride(open);
        it.setItemMeta(mm);
        return it;
    }

    private static final net.kyori.adventure.key.Key GHAST_FONT = net.kyori.adventure.key.Key.key("squarestar", "ghast");
    /** dva zpívající ghasti dole po stranách obrazovky (glyfy z resource packu, jen Java) */
    private net.kyori.adventure.text.Component ghastHud(int frame) {
        boolean a = (frame / 1) % 2 == 0;
        String l = a ? "\uE200" : "\uE201", r = a ? "\uE201" : "\uE200";
        return net.kyori.adventure.text.Component.text(l + "\uE210" + r).font(GHAST_FONT).color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }

    private boolean tearsPlaying(Player p) {
        Object[] cur = state.get(p.getUniqueId());
        return cur != null && ((Track) cur[0]).id.equals("tears");
    }

    private void ghastChoir() {
        ghastFrame++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (tearsPlaying(p) && !Ranks.bedrock(p)) p.sendActionBar(ghastHud(ghastFrame));
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) continue;
            var inv = p.getOpenInventory().getTopInventory();
            boolean on = tearsPlaying(p);
            for (int i = 0; i < GHAST_SLOTS.length; i++) inv.setItem(GHAST_SLOTS[i], on ? ghastItem(i, ghastFrame) : null);
            if (on && ghastFrame % 3 == 0) p.playSound(p.getLocation(), Sound.ENTITY_GHAST_AMBIENT, SoundCategory.MASTER, 0.12f, 1.3f + (float) Math.random() * 0.5f);
        }
    }

    // ---------------- uložené nastavení hráče ----------------
    private int vol(Player p) { return Math.max(10, Math.min(100, p.getPersistentDataContainer().getOrDefault(VOL, PersistentDataType.INTEGER, 60))); }
    private boolean wants(Player p) { return p.getPersistentDataContainer().getOrDefault(OFF, PersistentDataType.BYTE, (byte) 0) == 0; }
    private boolean inLobby(Player p) { return p.getWorld() == pl.mars && pl.games.of(p) == null; }
    private List<String> playlist(Player p) {
        String s = p.getPersistentDataContainer().get(PL, PersistentDataType.STRING);
        List<String> l = new ArrayList<>();
        if (s != null && !s.isEmpty()) for (String id : s.split(",")) if (track(id) != null && !l.contains(id)) l.add(id);
        return l;
    }
    private void savePlaylist(Player p, List<String> l) { p.getPersistentDataContainer().set(PL, PersistentDataType.STRING, String.join(",", l)); }
    private String mode(Player p) { return p.getPersistentDataContainer().getOrDefault(MODE, PersistentDataType.STRING, "auto"); }
    private boolean shuffle(Player p) { return p.getPersistentDataContainer().getOrDefault(SHUF, PersistentDataType.BYTE, (byte) 0) == 1; }

    // ---------------- přehrávání ----------------
    private Track next(Player p, Track cur) {
        String mode = mode(p);
        List<String> ids;
        if (mode.equals("repeat") && cur != null) return cur;
        if (mode.equals("playlist") && !playlist(p).isEmpty()) ids = playlist(p);
        else ids = pl.xmas != null && pl.xmas.active() ? AUTO_XMAS : AUTO;
        if (shuffle(p) && ids.size() > 1) {
            String pick;
            do { pick = ids.get(new Random().nextInt(ids.size())); } while (cur != null && pick.equals(cur.id));
            return track(pick);
        }
        int i = cur == null ? -1 : ids.indexOf(cur.id);
        return track(ids.get((i + 1) % ids.size()));
    }

    private void play(Player p, Track t) {
        p.stopSound(SoundCategory.RECORDS);
        p.playSound(p, t.sound, SoundCategory.RECORDS, vol(p) / 100f, 1f);
        state.put(p.getUniqueId(), new Object[]{t, System.currentTimeMillis() + (t.sec + 4) * 1000L});
        U.bar(p, "&d♫ &7Hraje: &f" + t.name + " &8(/hudba = menu)");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        state.keySet().removeIf(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p != null && inLobby(p) && wants(p)) return false;
            if (p != null) p.stopSound(SoundCategory.RECORDS);
            return true;
        });
        for (Player p : pl.mars.getPlayers()) {
            if (!inLobby(p) || !wants(p) || (pl.intro != null && pl.intro.active(p))) continue;
            Object[] s = state.get(p.getUniqueId());
            if (s != null && now < (long) s[1]) continue;
            play(p, next(p, s == null ? null : (Track) s[0]));
        }
    }

    // ---------------- menu ----------------
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Gui.title(54, Lang.t(p, "&f&l♫ Hudba")));
        Object[] cur = state.get(p.getUniqueId());
        Track now = cur == null ? null : (Track) cur[0];
        List<String> pls = playlist(p);
        h.inv.setItem(4, U.item(Material.JUKEBOX, 1, "&d&l♫ Tvoje hudba", now == null ? "&7Právě nic nehraje" : "&7Hraje: &f" + now.name, "",
                "&7Levý klik na skladbu = &fpřehrát hned", "&7Pravý klik = &fpřidat / odebrat z playlistu", "&7Hlasitost: Nastavení → Zvuk → Jukebox"));
        for (int i = 0; i < CAT.size() && i < SLOTS.length; i++) {
            Track t = CAT.get(i);
            int pos = pls.indexOf(t.id);
            boolean playing = now != null && now.id.equals(t.id);
            String warn = null;
            List<String> lore = new ArrayList<>();
            lore.add(pos >= 0 ? "&a✔ V playlistu &7(#" + (pos + 1) + ")" : "&8Není v playlistu");
            if (warn != null) lore.add(warn);
            lore.add(""); lore.add("&eLevý klik: &7přehrát hned"); lore.add("&ePravý klik: &7" + (pos >= 0 ? "odebrat z playlistu" : "přidat do playlistu"));
            ItemStack it = U.item(t.icon, 1, (playing ? "&a▶ " : "&f") + t.name, lore.toArray(new String[0]));
            if (t.id.startsWith("xmas") || t.id.equals("bounce") || t.id.equals("flurry") || t.id.equals("calm4")) it = Gui.skin(p, it, "disc_" + t.id);   // vlastní disk (Java s resource packem)
            var m = it.getItemMeta();
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (playing || pos >= 0) m.setEnchantmentGlintOverride(true);
            it.setItemMeta(m);
            h.inv.setItem(SLOTS[i], U.tag(it, "ss_mus", "t:" + t.id));
        }
        boolean on = wants(p);
        h.inv.setItem(45, U.tag(U.item(on ? Material.LIME_DYE : Material.GRAY_DYE, 1, on ? "&a&lHudba: ZAPNUTÁ" : "&c&lHudba: VYPNUTÁ", "&eKlikni pro " + (on ? "vypnutí" : "zapnutí")), "ss_mus", "toggle"));
        String md = mode(p);
        h.inv.setItem(47, U.tag(U.item(Material.COMPARATOR, 1, "&b&lRežim: " + (md.equals("auto") ? "Automaticky" : md.equals("playlist") ? "Můj playlist" : "Opakovat skladbu"),
                md.equals("auto") ? "&aAutomaticky" : "&7Automaticky", md.equals("playlist") ? "&aMůj playlist &7(" + pls.size() + " skladeb)" : "&7Můj playlist &8(" + pls.size() + " skladeb)",
                md.equals("repeat") ? "&aOpakovat skladbu" : "&7Opakovat skladbu", "", "&eKlikni pro přepnutí"), "ss_mus", "mode"));
        int vl = vol(p);
        String bar = "&a" + "▌".repeat(vl / 10) + "&8" + "▌".repeat(10 - vl / 10);
        h.inv.setItem(46, U.tag(U.item(Material.NOTE_BLOCK, 1, "&b&l🔉 Ztišit", "&7Hlasitost: &f" + vl + " %", bar, "", "&eKlikni: &7−10 %"), "ss_mus", "voldown"));
        h.inv.setItem(48, U.tag(U.item(Material.BELL, 1, "&b&l🔊 Zesílit", "&7Hlasitost: &f" + vl + " %", bar, "", "&eKlikni: &7+10 %"), "ss_mus", "volup"));
        h.inv.setItem(49, U.tag(U.item(Material.ARROW, 1, "&e&l⏭ Další skladba", "&7Přeskočí na další"), "ss_mus", "skip"));
        h.inv.setItem(51, U.tag(U.item(Material.REPEATER, 1, shuffle(p) ? "&a&lMíchání: ZAPNUTÉ" : "&7&lMíchání: vypnuté", "&eKlikni pro přepnutí"), "ss_mus", "shuf"));
        h.inv.setItem(52, U.tag(U.item(Material.LAVA_BUCKET, 1, "&c&lSmazat playlist", "&7Vyprázdní tvůj playlist"), "ss_mus", "clear"));
        if (now != null && now.id.equals("tears")) for (int i = 0; i < GHAST_SLOTS.length; i++) h.inv.setItem(GHAST_SLOTS[i], ghastItem(i, ghastFrame));
        p.openInventory(h.inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_mus");
        if (t == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        switch (t) {
            case "toggle" -> setOn(p, !wants(p));
            case "mode" -> {
                String m = mode(p), n = m.equals("auto") ? "playlist" : m.equals("playlist") ? "repeat" : "auto";
                if (n.equals("playlist") && playlist(p).isEmpty()) U.msg(p, "&7Playlist je prázdný – pravým klikem na skladby si ho sestav. &8(zatím hraje automaticky)");
                p.getPersistentDataContainer().set(MODE, PersistentDataType.STRING, n);
            }
            case "voldown", "volup" -> {
                int nv = Math.max(10, Math.min(100, vol(p) + (t.equals("volup") ? 10 : -10)));
                p.getPersistentDataContainer().set(VOL, PersistentDataType.INTEGER, nv);
                Object[] s = state.get(p.getUniqueId());
                if (s != null && wants(p) && inLobby(p)) { p.stopSound(SoundCategory.RECORDS); play(p, (Track) s[0]); }   // zní hned novou hlasitostí
                U.bar(p, "&b🔊 Hlasitost hudby: &f" + nv + " %");
            }
            case "shuf" -> p.getPersistentDataContainer().set(SHUF, PersistentDataType.BYTE, (byte) (shuffle(p) ? 0 : 1));
            case "clear" -> { savePlaylist(p, new ArrayList<>()); U.msg(p, "&7Playlist vymazán."); }
            case "skip" -> {
                if (!wants(p)) setOn(p, true);
                Object[] s = state.get(p.getUniqueId());
                play(p, next(p, s == null ? null : (Track) s[0]));
            }
            default -> {
                if (!t.startsWith("t:")) return;
                Track tr = track(t.substring(2));
                if (tr == null) return;
                if (e.isRightClick()) {
                    List<String> l = playlist(p);
                    if (!l.remove(tr.id)) l.add(tr.id);
                    savePlaylist(p, l);
                } else {
                    if (!wants(p)) p.getPersistentDataContainer().set(OFF, PersistentDataType.BYTE, (byte) 0);
                    if (!inLobby(p)) U.msg(p, "&7Hudba hraje jen v lobby, ale je uložená. &8Vrať se na /spawn.");
                    else play(p, tr);
                }
            }
        }
        open(p);
    }

    private void setOn(Player p, boolean on) {
        p.getPersistentDataContainer().set(OFF, PersistentDataType.BYTE, (byte) (on ? 0 : 1));
        if (!on) { p.stopSound(SoundCategory.RECORDS); state.remove(p.getUniqueId()); }
        U.msg(p, on ? "&aHudba v lobby zapnuta!" : "&7Hudba v lobby &cvypnuta&7.");
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length > 0 && (a[0].equalsIgnoreCase("off") || a[0].equalsIgnoreCase("on"))) { setOn(p, a[0].equalsIgnoreCase("on")); return true; }
        open(p);
        return true;
    }
}
