package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kosmetika v lobby: stopy částic za hráčem a 3D čepice (modely z resource packu). */
public final class Cosmetics implements Listener {
    public enum Trail {
        FLAME("Ohnivá stopa", Material.BLAZE_POWDER, Particle.FLAME), HEARTS("Srdíčka", Material.RED_DYE, Particle.HEART),
        NOTES("Noty", Material.NOTE_BLOCK, Particle.NOTE), STARS("Hvězdný prach", Material.END_ROD, Particle.END_ROD),
        SOUL("Duše", Material.SOUL_LANTERN, Particle.SOUL_FIRE_FLAME), CHERRY("Sakura", Material.CHERRY_LEAVES, Particle.CHERRY_LEAVES),
        COSMIC("Vesmírná magie", Material.AMETHYST_CLUSTER, Particle.WITCH), RAINBOW("Duha", Material.PRISMARINE_CRYSTALS, Particle.DUST),
        SNOW("Sněžení", Material.SNOWBALL, Particle.SNOWFLAKE), ELECTRIC("Elektřina", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK),
        CLOUD("Obláček", Material.WHITE_WOOL, Particle.CLOUD), SCULK("Sculk duše", Material.SCULK, Particle.SCULK_SOUL),
        ENCHANT("Kouzla", Material.ENCHANTING_TABLE, Particle.ENCHANT), GLOW("Záře", Material.GLOW_INK_SAC, Particle.GLOW),
        PORTAL("Portál", Material.OBSIDIAN, Particle.PORTAL), TOTEM("Totem", Material.TOTEM_OF_UNDYING, Particle.TOTEM_OF_UNDYING),
        HAPPY("Jiskřičky", Material.EMERALD, Particle.HAPPY_VILLAGER);
        final String name; final Material icon; final Particle particle;
        Trail(String n, Material i, Particle p) { name = n; icon = i; particle = p; }
    }
    public enum Hat {
        ROCKET("Mini raketa", "hat_rocket"), UFO("UFO", "hat_ufo"), EARTH("Planeta Země", "earth"), HELMET("Kyslíková helma", "oxygen_helmet"), STAR("Hvězda", "star_shard"),
        CROWN("Koruna", "hat_crown"), HALO("Svatozář", "hat_halo"), ALIEN("Mimozemská tykadla", "hat_alien"), SATURN("Saturn", "hat_saturn"),
        PROPELLER("Čepice s vrtulkou", "hat_propeller"), SATELLITE("Satelit", "hat_satellite"), MARS("Planeta Mars", "hat_mars"), WITCH("Čarodějný klobouk", "hat_witch"),
        SANTA("Čepice Santy", "hat_santa"),
        PET_SPIDER("Pet: Malý pavouk", "pet_spider"), PET_CHICKEN("Pet: Kuřátko", "pet_chicken"), PET_SLIME("Pet: Slime", "pet_slime"), PET_FOX("Pet: Liška", "pet_fox"),
        PET_ENDY("Pet: Little Endy", "pet_endy"), PET_CAT("Pet: Kočka", "pet_cat"), PET_RACCOON("Pet: Mýval", "pet_raccoon"), PET_CTHULHU("Pet: Mini Cthulhu", "pet_cthulhu"), PET_PIGEON("Pet: Holub", "pet_pigeon");
        final String name, model;
        Hat(String n, String m) { name = n; model = m; }
    }
    public enum Balloon {
        RED("Červený balónek", "balloon_red"), BLUE("Modrý balónek", "balloon_blue"), PURPLE("Fialový balónek", "balloon_purple"),
        GOLD("Zlatý balónek", "balloon_gold"), GREEN("Zelený balónek", "balloon_green"), WHITE("Bílý balónek", "balloon_white");
        final String name, model;
        Balloon(String n, String m) { name = n; model = m; }
    }
    /** pláště: 3D vlajka za zády (banner), každý má svou barvu a vzor */
    public enum Cape {
        RED("Rudý plášť", "RED", null, null), BLUE("Modrý plášť", "BLUE", null, null), BLACK("Černý plášť", "BLACK", PatternType.BORDER, DyeColor.RED),
        MARS("Marsovský plášť", "ORANGE", PatternType.GRADIENT, DyeColor.RED), GALAXY("Galaxie", "PURPLE", PatternType.GRADIENT, DyeColor.BLACK),
        ROYAL("Královský plášť", "PURPLE", PatternType.BORDER, DyeColor.YELLOW), CREEPER("Creeper plášť", "LIME", PatternType.CREEPER, DyeColor.BLACK),
        SKULL("Pirátský plášť", "BLACK", PatternType.SKULL, DyeColor.WHITE), FLOWER("Květinový plášť", "PINK", PatternType.FLOWER, DyeColor.MAGENTA),
        ICE("Ledový plášť", "LIGHT_BLUE", PatternType.GRADIENT, DyeColor.WHITE);
        final String name, base; final PatternType pat; final DyeColor patColor;
        Cape(String n, String b, PatternType p, DyeColor c) { name = n; base = b; pat = p; patColor = c; }
        ItemStack item() {
            ItemStack it = new ItemStack(Material.valueOf(base + "_BANNER"));
            if (pat != null) {
                org.bukkit.inventory.meta.BannerMeta m = (org.bukkit.inventory.meta.BannerMeta) it.getItemMeta();
                m.addPattern(new org.bukkit.block.banner.Pattern(patColor, pat));
                it.setItemMeta(m);
            }
            return it;
        }
    }
    /** aury: částice kroužící kolem hráče */
    public enum Aura {
        ORBIT("Kroužící orby", Material.HEART_OF_THE_SEA), HALO("Zářivý prstenec", Material.GLOWSTONE_DUST), FLAMES("Ohnivá aura", Material.MAGMA_CREAM),
        STORM("Bouřková aura", Material.LIGHTNING_ROD), GALAXY("Galaktická aura", Material.ENDER_EYE), LOVE("Aura lásky", Material.PINK_PETALS),
        SAKURA("Sakurová aura", Material.CHERRY_SAPLING), FROST("Ledová aura", Material.BLUE_ICE);
        final String name; final Material icon;
        Aura(String n, Material i) { name = n; icon = i; }
    }
        private final Map<UUID, String> openCat = new HashMap<>();
    private final Map<UUID, org.bukkit.entity.ItemDisplay> balloons = new HashMap<>();

    // ---------------- ceny a vlastnictví (od v4.4 je zdarma jen kyslíková helma) ----------------
    /** cena v mincích; -1 = nedá se koupit (jen z adventního kalendáře) */
    public static int price(String id) {
        return switch (id) {
            case "hat:HELMET" -> 0;
            case "hat:SANTA", "hat:PET_PIGEON" -> -1;
            case "hat:CROWN", "hat:HALO" -> 300;
            case "hat:EARTH", "hat:SATURN", "hat:MARS" -> 200;
            case "hat:PET_SPIDER", "hat:PET_CHICKEN", "hat:PET_SLIME", "hat:PET_FOX", "hat:PET_ENDY", "hat:PET_CAT", "hat:PET_RACCOON", "hat:PET_CTHULHU" -> 300;   // pety: 2× víc než běžná čepice
            case "trail:RAINBOW", "trail:TOTEM", "trail:COSMIC" -> 150;
            default -> id.startsWith("hat:") ? 150 : id.startsWith("balloon:") ? 100 : id.startsWith("cape:") ? 120 : id.startsWith("aura:") ? 150 : 80;
        };
    }
    private static final org.bukkit.NamespacedKey OWNED = new org.bukkit.NamespacedKey("squarestar", "cos_owned");
    private Set<String> owned(Player p) { return new HashSet<>(Arrays.asList(p.getPersistentDataContainer().getOrDefault(OWNED, PersistentDataType.STRING, "").split(","))); }
    /** bezdomovec dal hráči holuba: odemkne a nasadí */
    public void giveBeggarPet(Player p) { grant(p, "hat:PET_PIGEON"); set(p, "cos_hat", Hat.PET_PIGEON); apply(p); }
    public boolean hasPigeon(Player p) { return owns(p, "hat:PET_PIGEON"); }
    public boolean owns(Player p, String id) { return price(id) == 0 || p.isOp() || pl.ranks.of(p) == Ranks.Rank.OWNER || owned(p).contains(id); }
    public void grant(Player p, String id) {
        Set<String> o = owned(p); o.remove("");
        if (o.add(id)) p.getPersistentDataContainer().set(OWNED, PersistentDataType.STRING, String.join(",", o));
    }
    /** kdo měl kosmetiku nasazenou ještě ze doby, kdy byla zdarma, ji dostane natrvalo */
    private void grandfather(Player p) {
        if (p.getPersistentDataContainer().has(OWNED)) return;
        Trail t = get(p, "cos_trail", Trail.class); Hat h = get(p, "cos_hat", Hat.class); Balloon b = get(p, "cos_balloon", Balloon.class);
        grant(p, "free");
        if (t != null) grant(p, "trail:" + t.name());
        if (h != null) grant(p, "hat:" + h.name());
        if (b != null) grant(p, "balloon:" + b.name());
    }
    private List<String> priceLore(Player p, String id, boolean equipped) {
        if (equipped) return List.of("&aNasazeno", "&7Klikni = sundat");
        if (owns(p, id)) return List.of("&a✔ Vlastníš", "&7Klikni pro nasazení");
        if (id.equals("hat:PET_PIGEON")) return List.of("&e🕊 Dá ti ho vesmírný bezdomovec", "&7(v lobby, za 5 mincí)");
        if (price(id) < 0) return List.of("&d❄ Jen z adventního kalendáře", "&7(1. prosince)");
        return List.of("&7Cena: &6" + price(id) + " " + Coins.ICON, pl.coins.get(p) >= price(id) ? "&eKlikni = koupit a nasadit" : "&cNemáš dost mincí (" + pl.coins.get(p) + ")");
    }
    static final class Holder implements InventoryHolder { Inventory inv; @Override public @NotNull Inventory getInventory() { return inv; } }

    private final SquareStar pl;
    private final Map<UUID, Location> last = new HashMap<>();
    private int hue;

    public Cosmetics(SquareStar pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tickTrails, 40, 2);
        Bukkit.getScheduler().runTaskTimer(pl, this::tickBalloons, 40, 2);
        Bukkit.getScheduler().runTaskTimer(pl, this::tickAuras, 40, 2);
        Bukkit.getScheduler().runTaskTimer(pl, this::tickPets, 40, 1);
        if (System.getProperty("ssdebug") != null) Bukkit.getScheduler().runTaskTimer(pl, () -> {   // jen pro testování
            java.io.File f = new java.io.File("setpet.txt");
            if (!f.exists()) return;
            try { String[] a = java.nio.file.Files.readString(f.toPath()).trim().split(" "); f.delete(); Player p = Bukkit.getPlayerExact(a[0]); if (p != null) set(p, "cos_hat", Hat.valueOf(a[1])); } catch (Exception ex) { ex.printStackTrace(); }
        }, 20, 10);
        for (var e : pl.mars.getEntities()) if ((e.getScoreboardTags().contains("ss_balloon") || e.getScoreboardTags().contains("ss_cape") || e.getScoreboardTags().contains("ss_pet"))) e.remove();
    }

    private <T extends Enum<T>> T get(Player p, String key, Class<T> c) {
        String v = p.getPersistentDataContainer().get(U.key(key), PersistentDataType.STRING);
        if (v == null) return null;
        try { return Enum.valueOf(c, v); } catch (IllegalArgumentException e) { return null; }
    }
    private void set(Player p, String key, Enum<?> v) {
        if (v == null) p.getPersistentDataContainer().remove(U.key(key));
        else p.getPersistentDataContainer().set(U.key(key), PersistentDataType.STRING, v.name());
    }

    public static ItemStack model(Material base, String model, String name, String... lore) {
        ItemStack it = U.item(base, 1, name, lore);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("squarestar", model));
        it.setItemMeta(m);
        return it;
    }

    public ItemStack menuItem() { return U.tag(model(Material.NETHER_STAR, "star_shard", "&d&l✦ Kosmetika &7(pravý klik)", "&7Stopy, čepice, pety, balónky a aury"), "ss_lobby", "cosmetics"); }

    /** nasadí čepici v lobby */
    public void apply(Player p) {
        Hat h = get(p, "cos_hat", Hat.class);
        p.getInventory().setHelmet(h == null ? null : U.tag(model(Material.PAPER, h.model, "&d" + h.name, "&7Kosmetika"), "ss_cos", "hat"));
    }

    private static final String[][] TABS = {
        {"trail", "&6&lStopy", "BLAZE_POWDER"}, {"hat", "&b&l3D čepice", "LEATHER_HELMET"}, {"balloon", "&d&lBalónky", "LEAD"},
        {"aura", "&5&lAury", "ENDER_EYE"}};
    private static final int[] TAB_SLOTS = {0, 9, 18, 27};
    private static final int[] GRID = new int[25];
    static { int k = 0; for (int r = 0; r < 5; r++) for (int c = 2; c < 7; c++) GRID[k++] = r * 9 + c; }

    private ItemStack pane(Material m) { return U.item(m, 1, "&r "); }

    /** otevře kosmetiku na naposledy použité kategorii */
    public void open(Player p) { open(p, openCatLast.getOrDefault(p.getUniqueId(), "trail")); }
    private final Map<UUID, String> openCatLast = new HashMap<>();

    /** menu ve stylu Essential Wardrobe: záložky vlevo, mřížka předmětů vpravo, dole stav */
    public void open(Player p, String cat) {
        grandfather(p);
        openCat.put(p.getUniqueId(), cat);
        openCatLast.put(p.getUniqueId(), cat);
        Holder h = new Holder();
        String tn = switch (cat) { case "trail" -> "Stopy"; case "hat" -> "3D čepice"; case "balloon" -> "Balónky"; default -> "Aury"; };
        h.inv = Bukkit.createInventory(h, 54, cz.adrrex.squarestar.Gui.wardrobe(cz.adrrex.squarestar.Lang.t(p, "&f&lKosmetika &8» &b" + tn)));
        // pozadí: Java s packem má vlastní panel (jako Essential), Bedrock dostane skleněné panely
        if (Ranks.bedrock(p)) for (int i = 0; i < 54; i++) h.inv.setItem(i, pane(i % 9 == 1 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
        // záložky vlevo
        for (int i = 0; i < TABS.length; i++) {
            boolean sel = TABS[i][0].equals(cat);
            ItemStack t = U.item(Material.valueOf(TABS[i][2]), 1, (sel ? "&a▶ " : "") + TABS[i][1], sel ? "&7Právě otevřeno" : "&eKlikni pro otevření");
            if (sel) glint(t);
            var tm = t.getItemMeta(); tm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES); t.setItemMeta(tm);
            h.inv.setItem(TAB_SLOTS[i], U.tag(t, "ss_cos", "cat:" + TABS[i][0]));
            if (sel && Ranks.bedrock(p)) h.inv.setItem(TAB_SLOTS[i] + 1, pane(Material.LIME_STAINED_GLASS_PANE));
        }
        int i = 0;
        switch (cat) {
            case "trail" -> { Trail c = get(p, "cos_trail", Trail.class);
                for (Trail t : Trail.values()) { if (i >= GRID.length) break; String id = "trail:" + t.name();
                    ItemStack it = U.item(t.icon, 1, (t == c ? "&a✔ " : owns(p, id) ? "&e" : "&7") + t.name, priceLore(p, id, t == c).toArray(new String[0]));
                    if (t == c) glint(it); h.inv.setItem(GRID[i++], U.tag(it, "ss_cos", id)); } }
            case "hat" -> { Hat c = get(p, "cos_hat", Hat.class);
                for (Hat t : Hat.values()) { if (i >= GRID.length) break; String id = "hat:" + t.name();
                    ItemStack it = cz.adrrex.squarestar.Gui.skin(p, U.item(Material.LEATHER_HELMET, 1, (t == c ? "&a✔ " : owns(p, id) ? "&e" : "&7") + t.name, priceLore(p, id, t == c).toArray(new String[0])), t.model);
                    if (t == c) glint(it); h.inv.setItem(GRID[i++], U.tag(it, "ss_cos", id)); } }
            case "balloon" -> { Balloon c = get(p, "cos_balloon", Balloon.class);
                for (Balloon t : Balloon.values()) { if (i >= GRID.length) break; String id = "balloon:" + t.name();
                    ItemStack it = cz.adrrex.squarestar.Gui.skin(p, U.item(Material.LEAD, 1, (t == c ? "&a✔ " : owns(p, id) ? "&e" : "&7") + t.name, priceLore(p, id, t == c).toArray(new String[0])), t.model);
                    if (t == c) glint(it); h.inv.setItem(GRID[i++], U.tag(it, "ss_cos", id)); } }
            default -> { Aura c = get(p, "cos_aura", Aura.class);
                for (Aura t : Aura.values()) { if (i >= GRID.length) break; String id = "aura:" + t.name();
                    ItemStack it = U.item(t.icon, 1, (t == c ? "&a✔ " : owns(p, id) ? "&e" : "&7") + t.name, priceLore(p, id, t == c).toArray(new String[0]));
                    if (t == c) glint(it); h.inv.setItem(GRID[i++], U.tag(it, "ss_cos", id)); } }
        }
        // spodní lišta: mince, co mám nasazené, sundat, zavřít
        h.inv.setItem(46, U.tag(U.item(Material.GOLD_NUGGET, 1, "&6&lMince: &e" + pl.coins.get(p) + " " + Coins.ICON, "&7Klikni = obchod s klíči"), "ss_cos", "shop"));
        Trail ct = get(p, "cos_trail", Trail.class); Hat chat = get(p, "cos_hat", Hat.class); Balloon cb = get(p, "cos_balloon", Balloon.class); Aura ca = get(p, "cos_aura", Aura.class);
        h.inv.setItem(48, U.item(Material.ARMOR_STAND, 1, "&f&lNasazeno",
                "&6Stopa: &f" + (ct == null ? "–" : ct.name), "&bČepice: &f" + (chat == null ? "–" : chat.name), "&dBalónek: &f" + (cb == null ? "–" : cb.name),
                "&5Aura: &f" + (ca == null ? "–" : ca.name)));
        h.inv.setItem(50, U.tag(U.item(Material.BARRIER, 1, "&c&lSundat vše"), "ss_cos", "off"));
        h.inv.setItem(53, cz.adrrex.squarestar.Gui.closeButton());
        preview(p, h.inv, ct, chat, cb, ca);
        p.openInventory(h.inv);
    }
    /** náhled „jak právě vypadáš“ vpravo: figurka (čepice, hlava, tělo, nohy, boty) + ikony nasazených věcí */
    private void preview(Player p, Inventory inv, Trail ct, Hat ch, Balloon cb, Aura ca) {
        String who = "&f&l" + p.getName();
        ItemStack hat = ch == null ? U.item(Material.GRAY_DYE, 1, "&8Bez čepice") : cz.adrrex.squarestar.Gui.skin(p, U.item(Material.LEATHER_HELMET, 1, "&b" + ch.name, "&7Nasazená čepice"), ch.model);
        ItemStack head = U.item(Material.PLAYER_HEAD, 1, who, "&7Tvůj vzhled právě teď");
        if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) { sm.setPlayerProfile(p.getPlayerProfile()); head.setItemMeta(sm); }
        inv.setItem(7, hat); inv.setItem(16, head);
        inv.setItem(25, U.item(Material.IRON_CHESTPLATE, 1, who, "&7Tvůj vzhled právě teď"));
        inv.setItem(34, U.item(Material.IRON_LEGGINGS, 1, who));
        inv.setItem(43, U.item(Material.IRON_BOOTS, 1, who));
        inv.setItem(8, ct == null ? U.item(Material.GRAY_DYE, 1, "&8Bez stopy") : U.item(ct.icon, 1, "&6" + ct.name, "&7Nasazená stopa"));
        inv.setItem(17, cb == null ? U.item(Material.GRAY_DYE, 1, "&8Bez balónku") : cz.adrrex.squarestar.Gui.skin(p, U.item(Material.LEAD, 1, "&d" + cb.name, "&7Nasazený balónek"), cb.model));
        inv.setItem(26, ca == null ? U.item(Material.GRAY_DYE, 1, "&8Bez aury") : U.item(ca.icon, 1, "&5" + ca.name, "&7Nasazená aura"));
        for (int s : new int[]{7, 8, 16, 17, 25, 26, 34, 43}) { ItemStack it = inv.getItem(s); var m = it.getItemMeta(); m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP); it.setItemMeta(m); inv.setItem(s, it); }
    }
    private static void glint(ItemStack it) { ItemMeta m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }

    // ---------------- balónky ----------------
    private void tickBalloons() {
        for (var it = balloons.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !show(p) || get(p, "cos_balloon", Balloon.class) == null || !e.getValue().isValid()) { e.getValue().remove(); it.remove(); }
        }
        for (Player p : pl.mars.getPlayers()) {
            Balloon b = get(p, "cos_balloon", Balloon.class);
            if (b == null || !show(p)) continue;
            Location l = p.getLocation();
            double yaw = Math.toRadians(l.getYaw());
            // vpravo za ramenem, lehce se houpe
            double bob = Math.sin(Bukkit.getCurrentTick() / 12.0 + p.getEntityId()) * 0.12;
            Location at = l.clone().add(-Math.cos(yaw) * 0.9 + Math.sin(yaw) * 0.5, 2.2 + bob, -Math.sin(yaw) * 0.9 - Math.cos(yaw) * 0.5);
            at.setYaw(l.getYaw()); at.setPitch(0);
            var d = balloons.get(p.getUniqueId());
            String model = "squarestar:" + b.model;
            if (d == null || !d.isValid() || d.getWorld() != at.getWorld()) {
                if (d != null) d.remove();
                d = pl.mars.spawn(at, org.bukkit.entity.ItemDisplay.class, x -> {
                    x.setPersistent(false); x.addScoreboardTag("ss_balloon");
                    x.setTeleportDuration(3);
                    x.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                });
                balloons.put(p.getUniqueId(), d);
            }
            ItemStack cur = d.getItemStack();
            if (cur == null || !cur.hasItemMeta() || cur.getItemMeta().getItemModel() == null || !model.equals(cur.getItemMeta().getItemModel().toString()))
                d.setItemStack(model(Material.LEAD, b.model, b.name));
            d.teleport(at);
        }
    }
    /** aury: částice kroužící kolem hráče (vidí je Java i Bedrock) */
    private void tickAuras() {
        long tk = Bukkit.getCurrentTick();
        for (Player p : pl.mars.getPlayers()) {
            if (pl.games.of(p) != null || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) continue;
            Aura a = get(p, "cos_aura", Aura.class);
            if (a == null) continue;
            World w = p.getWorld(); Location c = p.getLocation();
            double t = tk * 0.18;
            switch (a) {
                case ORBIT -> { for (int k = 0; k < 3; k++) { double an = t + k * Math.PI * 2 / 3; w.spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(an) * 1.1, 1.0 + Math.sin(t * 0.7 + k) * 0.5, Math.sin(an) * 1.1), 1, 0, 0, 0, 0); } }
                case HALO -> { for (int k = 0; k < 10; k++) { double an = t + k * Math.PI / 5; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(an) * 0.45, 2.25, Math.sin(an) * 0.45), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 225, 90), 0.8f)); } }
                case FLAMES -> { for (int k = 0; k < 2; k++) { double an = t * 1.4 + k * Math.PI; double y = (tk * 0.04 + k) % 2.0; w.spawnParticle(Particle.FLAME, c.clone().add(Math.cos(an) * 0.8, y, Math.sin(an) * 0.8), 1, 0.02, 0.02, 0.02, 0.005); } }
                case STORM -> {
                    w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(t) * 0.9, 2.6, Math.sin(t) * 0.9), 2, 0.25, 0.05, 0.25, 0, new Particle.DustOptions(Color.fromRGB(90, 90, 110), 1.8f));
                    if (tk % 4 == 0) for (int n = 0; n < 2; n++) miniBolt(w, c.clone().add(0, 0.2 + Math.random() * 1.6, 0), Math.random() * Math.PI * 2);
                }
                case GALAXY -> { for (int k = 0; k < 3; k++) { double an = t * 1.2 + k * 2.1, y = ((tk * 0.03) + k * 0.7) % 2.2, rr = 0.5 + y * 0.35; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(an) * rr, y, Math.sin(an) * rr), 1, 0, 0, 0, 0, new Particle.DustOptions(k % 2 == 0 ? Color.fromRGB(150, 80, 255) : Color.fromRGB(80, 160, 255), 1.1f)); } }
                case LOVE -> {
                    for (int k = 0; k < 2; k++) { double an = t * 1.3 + k * Math.PI; w.spawnParticle(Particle.HEART, c.clone().add(Math.cos(an) * 0.8, 0.6 + ((tk * 0.04 + k) % 1.6), Math.sin(an) * 0.8), 1, 0, 0, 0, 0); }
                    if (tk % 8 == 0) w.spawnParticle(Particle.DUST, c.clone().add(0, 2.3, 0), 4, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255, 90, 150), 1.2f));
                }
                case SAKURA -> { w.spawnParticle(Particle.CHERRY_LEAVES, c.clone().add(0, 2.3, 0), 1, 0.7, 0.1, 0.7, 0); }
                case FROST -> { for (int k = 0; k < 2; k++) { double an = -t + k * Math.PI; w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(Math.cos(an) * 0.9, 0.2 + ((tk * 0.03 + k) % 2.0), Math.sin(an) * 0.9), 1, 0, 0, 0, 0); } }
            }
        }
    }

    /** malý klikatý blesk (4 úseky) kolem hráče z jiskřivých částic */
    private void miniBolt(World w, Location from, double ang) {
        Location p = from.clone().add(Math.cos(ang) * 0.5, 0, Math.sin(ang) * 0.5);
        double dx = Math.cos(ang) * 0.18, dz = Math.sin(ang) * 0.18;
        for (int seg = 0; seg < 4; seg++) {
            Location q = p.clone().add(dx + (Math.random() - .5) * 0.25, -0.22 - Math.random() * 0.12, dz + (Math.random() - .5) * 0.25);
            for (int i = 0; i <= 4; i++) {
                Location x = p.clone().add((q.getX() - p.getX()) * i / 4, (q.getY() - p.getY()) * i / 4, (q.getZ() - p.getZ()) * i / 4);
                w.spawnParticle(Particle.ELECTRIC_SPARK, x, 1, 0, 0, 0, 0);
                if (i % 2 == 0) w.spawnParticle(Particle.DUST, x, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 245, 140), 0.6f));
            }
            p = q;
        }
    }

    // ---------------- pety (3D modelek nad hlavou s animací) ----------------
    /** pety jsou 3D čepice (helmet s modelem); tohle jen uklidí staré entity z dřívějších verzí */
    private void tickPets() {
        if (Bukkit.getCurrentTick() % 100 != 0) return;
        for (var e : pl.mars.getEntities()) if (e.getScoreboardTags().contains("ss_pet")) e.remove();
    }

    private final Map<UUID, org.bukkit.entity.ItemDisplay[]> capeParts = new HashMap<>();

    /** plášť = 2 vlnící se 3D díly (ItemDisplay s modelem z resource packu), jen pro Java s packem */
    private void tickCapes() {
        for (var it = capeParts.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !show(p) || get(p, "cos_cape", Cape.class) == null || !e.getValue()[0].isValid() || !e.getValue()[1].isValid()) { for (var d : e.getValue()) d.remove(); it.remove(); }
        }
        for (Player p : pl.mars.getPlayers()) {
            Cape c = get(p, "cos_cape", Cape.class);
            if (c == null || !show(p)) continue;
            Location l = p.getLocation();
            double yaw = Math.toRadians(l.getYaw());
            double bx = Math.sin(yaw), bz = -Math.cos(yaw);          // směr dozadu
            boolean sn = p.isSneaking();
            double sp = Math.min(1.0, p.getVelocity().setY(0).length() * 3.2);
            if (!p.isOnGround()) sp = Math.max(sp, 0.55);
            double ph = Bukkit.getCurrentTick() / 5.0 + p.getEntityId();
            double a1 = (sn ? 0.55 : 0.08) + sp * 0.5 + Math.sin(ph) * 0.03;
            double a2 = a1 + 0.06 + sp * 0.45 + Math.sin(ph + 1.3) * 0.06;
            double back = sn ? 0.26 : 0.17;
            Location p1 = l.clone().add(bx * back, sn ? 1.22 : 1.4, bz * back);
            // spodní díl začíná tam, kde končí horní
            Location p2 = p1.clone().add(bx * 0.5 * Math.sin(a1), -0.5 * Math.cos(a1), bz * 0.5 * Math.sin(a1));
            var parts = capeParts.get(p.getUniqueId());
            if (parts == null || !parts[0].isValid() || parts[0].getWorld() != p1.getWorld()) {
                if (parts != null) for (var d : parts) d.remove();
                parts = new org.bukkit.entity.ItemDisplay[2];
                for (int i = 0; i < 2; i++) parts[i] = pl.mars.spawn(p1, org.bukkit.entity.ItemDisplay.class, x -> {
                    x.setPersistent(false); x.addScoreboardTag("ss_cape");
                    x.setTeleportDuration(1);
                    x.setInterpolationDuration(2);
                    x.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                    x.setBrightness(new org.bukkit.entity.Display.Brightness(14, 14));
                });
                capeParts.put(p.getUniqueId(), parts);
            }
            String key = c.name().toLowerCase(Locale.ROOT);
            String[] mdl = {"cape_" + key + "_top", "cape_" + key + "_bot"};
            double[] ang = {a1, a2};
            Location[] at = {p1, p2};
            for (int i = 0; i < 2; i++) {
                var d = parts[i];
                ItemStack cur = d.getItemStack();
                if (cur == null || !cur.hasItemMeta() || cur.getItemMeta().getItemModel() == null || !cur.getItemMeta().getItemModel().getKey().equals(mdl[i]))
                    d.setItemStack(model(Material.PAPER, mdl[i], c.name));
                Location t = at[i].clone(); t.setYaw(l.getYaw()); t.setPitch(0);
                d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f((float) ang[i], 1, 0, 0), new org.joml.Vector3f(1f, 1f, 1f), new org.joml.AxisAngle4f()));
                d.teleport(t);
            }
        }
    }
    private boolean show(Player p) { return p.getWorld() == pl.mars && pl.games.of(p) == null && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible(); }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // čepici v lobby nejde sundat
        if (p.getWorld() == pl.mars && e.getSlotType() == InventoryType.SlotType.ARMOR && "hat".equals(U.tagOf(e.getCurrentItem(), "ss_cos"))) { e.setCancelled(true); return; }
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        String t = U.tagOf(e.getCurrentItem(), "ss_cos");
        if (t == null) return;
        if (t.equals("close")) { p.closeInventory(); return; }
        if (t.equals("shop")) { pl.coins.open(p); return; }
        if (t.equals("hub")) { open(p); return; }
        if (t.startsWith("cat:")) { U.sound(p, Sound.UI_BUTTON_CLICK, 1.2f); open(p, t.substring(4)); return; }
        if (!t.equals("off")) {
            String id = t.replace("trail:", "trail:").trim();
            boolean equippedNow = (t.startsWith("trail:") && get(p, "cos_trail", Trail.class) != null && t.equals("trail:" + get(p, "cos_trail", Trail.class).name()))
                    || (t.startsWith("hat:") && get(p, "cos_hat", Hat.class) != null && t.equals("hat:" + get(p, "cos_hat", Hat.class).name()))
                    || (t.startsWith("balloon:") && get(p, "cos_balloon", Balloon.class) != null && t.equals("balloon:" + get(p, "cos_balloon", Balloon.class).name()))
                    || (t.startsWith("cape:") && get(p, "cos_cape", Cape.class) != null && t.equals("cape:" + get(p, "cos_cape", Cape.class).name()))
                    || (t.startsWith("aura:") && get(p, "cos_aura", Aura.class) != null && t.equals("aura:" + get(p, "cos_aura", Aura.class).name()));
            if (!equippedNow && !owns(p, id)) {
                if (id.equals("hat:PET_PIGEON")) { U.msg(p, "&eHoluba ti dá &6vesmírný bezdomovec &ev lobby za 5 mincí."); return; }
                if (price(id) < 0) { U.msg(p, "&dTuhle kosmetiku dostaneš jen z adventního kalendáře (&e/advent&d)."); return; }
                if (!pl.coins.pay(p, price(id))) return;
                grant(p, id);
                U.msg(p, "&a&l✔ Koupeno! &7Teď je tvoje navždy.");
            }
        }
        if (t.equals("off")) { set(p, "cos_trail", null); set(p, "cos_hat", null); set(p, "cos_balloon", null); set(p, "cos_cape", null); set(p, "cos_aura", null); }
        else if (t.startsWith("aura:")) { Aura v = Aura.valueOf(t.substring(5)); set(p, "cos_aura", get(p, "cos_aura", Aura.class) == v ? null : v); }
        else if (t.startsWith("cape:")) { Cape v = Cape.valueOf(t.substring(5)); set(p, "cos_cape", get(p, "cos_cape", Cape.class) == v ? null : v); }
        else if (t.startsWith("balloon:")) { Balloon v = Balloon.valueOf(t.substring(8)); set(p, "cos_balloon", get(p, "cos_balloon", Balloon.class) == v ? null : v); }
        else if (t.startsWith("trail:")) { Trail v = Trail.valueOf(t.substring(6)); set(p, "cos_trail", get(p, "cos_trail", Trail.class) == v ? null : v); }
        else if (t.startsWith("hat:")) { Hat v = Hat.valueOf(t.substring(4)); set(p, "cos_hat", get(p, "cos_hat", Hat.class) == v ? null : v); }
        U.sound(p, Sound.ENTITY_ITEM_PICKUP, 1.4f);
        if (pl.games.of(p) == null) apply(p);
        String c = openCat.get(p.getUniqueId());
        if (c == null || t.equals("off")) open(p); else open(p, c);
    }

    private void tickTrails() {
        hue = (hue + 7) % 360;
        for (Player p : pl.mars.getPlayers()) {
            if (pl.games.of(p) != null || p.getGameMode() == GameMode.SPECTATOR) continue;
            Trail t = get(p, "cos_trail", Trail.class);
            if (t == null) continue;
            Location now = p.getLocation(), prev = last.put(p.getUniqueId(), now);
            boolean moving = prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) > 0.004;
            Location at = now.clone().add(0, moving ? 0.15 : 2.1, 0);
            if (!moving && (Bukkit.getCurrentTick() % 8) != 0) continue;
            switch (t) {
                case RAINBOW -> {
                    java.awt.Color c = java.awt.Color.getHSBColor(hue / 360f, 1f, 1f);
                    p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.2, 0.1, 0.2, 0, new Particle.DustOptions(Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue()), 1.3f));
                }
                case NOTES -> p.getWorld().spawnParticle(Particle.NOTE, at.add(0, 0.3, 0), 1, 0.3, 0.1, 0.3, hue / 360.0);
                default -> p.getWorld().spawnParticle(t.particle, at, moving ? 2 : 1, 0.2, 0.05, 0.2, 0.01);
            }
        }
    }
}
