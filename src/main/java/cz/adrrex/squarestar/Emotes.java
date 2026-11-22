package cz.adrrex.squarestar;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** /emote <název> – zamávání, tanec, srdíčka… bublina nad hlavou + animace + částice. */
public final class Emotes implements CommandExecutor, TabCompleter, org.bukkit.event.Listener {
    private final SquareStar pl;
    private final Map<UUID, Long> cd = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.ItemStack> held = new HashMap<>();
    private final org.bukkit.NamespacedKey waveKey;
    public Emotes(SquareStar pl) {
        this.pl = pl;
        this.waveKey = new org.bukkit.NamespacedKey(pl, "emote_wave");
        Bukkit.getPluginManager().registerEvents(this, pl);
    }

    // ---------------- opravdové animace těla (BetterModel, jen Java s resource packem) ----------------
    private static final Map<String, Double> BM_LEN = Map.ofEntries(
        Map.entry("hi", 2.6), Map.entry("bow", 2.2), Map.entry("dance", 4.0), Map.entry("twerk", 3.0), Map.entry("spin", 1.6), Map.entry("sleep", 4.4),
        Map.entry("gg", 2.2), Map.entry("love", 3.2), Map.entry("clap", 2.6), Map.entry("rage", 2.4), Map.entry("rocket", 2.4), Map.entry("cheer", 2.4));

    private boolean bmAvailable(Player p) {
        return pl.getConfig().getBoolean("emotes.bettermodel", true) && Bukkit.getPluginManager().isPluginEnabled("BetterModel") && !Ranks.bedrock(p);
    }

    /** spustí animaci z plugins/BetterModel/players/emotes.bbmodel na hráči; vrací true, když příkaz proběhl */
    private boolean bmPlay(Player p, String id) {
        var at = p.addAttachment(pl, "bettermodel.play", true);
        try { return Bukkit.dispatchCommand(p, "bm play emotes " + id + " play_once true"); }
        catch (Throwable t) { pl.getLogger().warning("BetterModel emote selhal: " + t.getMessage()); return false; }
        finally { p.removeAttachment(at); }
    }

    /** do ruky dá dočasně trojzubec – klient s ním zvedne ruku nad hlavu (mávání) */
    private void waveStart(Player p) {
        if (held.containsKey(p.getUniqueId())) return;
        held.put(p.getUniqueId(), p.getInventory().getItemInMainHand().clone());
        var it = new org.bukkit.inventory.ItemStack(Material.TRIDENT);
        var m = it.getItemMeta();
        m.getPersistentDataContainer().set(waveKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        p.getInventory().setItemInMainHand(it);
    }

    private void waveRestore(Player p) {
        var orig = held.remove(p.getUniqueId());
        if (orig == null) return;
        p.clearActiveItem();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            var it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(waveKey)) inv.setItem(i, null);
        }
        var off = p.getItemOnCursor();
        if (off.hasItemMeta() && off.getItemMeta().getPersistentDataContainer().has(waveKey)) p.setItemOnCursor(null);
        inv.setItemInMainHand(orig);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) { waveRestore(e.getPlayer()); if (invis.remove(e.getPlayer().getUniqueId())) e.getPlayer().setInvisible(false); }
    @org.bukkit.event.EventHandler public void onJoinFix(org.bukkit.event.player.PlayerJoinEvent e) { Player p = e.getPlayer(); if (p.isInvisible() && !p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY) && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && !invis.contains(p.getUniqueId())) p.setInvisible(false); }
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) { if (held.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent e) { if (held.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onInvClick(org.bukkit.event.inventory.InventoryClickEvent e) { if (held.containsKey(e.getWhoClicked().getUniqueId())) e.setCancelled(true); }
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) { if (held.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) { if (e.getEntity().getShooter() instanceof Player p && held.containsKey(p.getUniqueId())) e.setCancelled(true); }

    /** název -> {bublina, zpráva cs, zpráva en} */
    private static final Map<String, String[]> E = new LinkedHashMap<>();
    static {
        // název -> {bublina, zpráva cs, zpráva en}
        E.put("cheer", new String[]{"&6&l★ JUPÍ! ★", "jásá", "cheers"});
        E.put("hi", new String[]{"&e&l✌ Ahoj!", "mává", "waves"});
        E.put("dance", new String[]{"&d&l♪ ♫ ♪", "tancuje", "is dancing"});
        E.put("twerk", new String[]{"&d&l♫ ♫ ♫", "twerkuje", "twerks"});
        E.put("spin", new String[]{"&b&l✺ Tornádo!", "se točí jako tornádo", "spins like a tornado"});
        E.put("sleep", new String[]{"&9&lZzz...", "jde spát", "goes to sleep"});
        E.put("swim", new String[]{"&3&l≈ ≈ ≈", "plave vzduchem", "swims through the air"});
        E.put("fly", new String[]{"&f&l✈ Letím!", "dělá letadlo", "flies like a plane"});
        E.put("sit", new String[]{"&7&l☕", "si sedl", "sits down"});
        E.put("dead", new String[]{"&c&l☠ ...", "umřel (naoko)", "plays dead"});
        E.put("bow", new String[]{"&6&l✦ Poklona ✦", "se klaní", "bows"});
        E.put("gg", new String[]{"&a&lGG!", "říká GG", "says GG"});
        E.put("love", new String[]{"&c&l❤ ❤ ❤", "posílá lásku", "sends love"});
        E.put("clap", new String[]{"&6&l✦ Bravo! ✦", "tleská", "claps"});
        E.put("rage", new String[]{"&4&l⚡ GRR! ⚡", "zuří", "is angry"});
        E.put("rocket", new String[]{"&6&l➹ START!", "odlétá na Mars", "blasts off"});
    }
    private final Map<UUID, org.bukkit.entity.ArmorStand> seats = new HashMap<>();

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        String id = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (!E.containsKey(id)) { U.msg(p, "&7Emoty: &e" + String.join("&7, &e", E.keySet()) + " &8(/emote hi)"); return true; }
        var g = pl.games.of(p);
        if (g != null && g.state == cz.adrrex.squarestar.game.Game.State.RUNNING) { U.bar(p, "&cEmoty jen v lobby a v čekárně."); return true; }
        long now = System.currentTimeMillis();
        if (now - cd.getOrDefault(p.getUniqueId(), 0L) < 3000) { U.bar(p, "&cPočkej chvilku…"); return true; }
        cd.put(p.getUniqueId(), now);
        play(p, id);
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (a.length != 1) return List.of();
        return E.keySet().stream().filter(k -> k.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
    }

    private void play(Player p, String id) {
        String[] e = E.get(id);
        // zpráva lidem okolo (každý ve svém jazyce)
        for (Player o : p.getWorld().getPlayers())
            if (o.getLocation().distanceSquared(p.getLocation()) < 48 * 48)
                { o.sendMessage(U.c("&8» &f" + p.getName() + " &7" + (Lang.en(o) ? e[2] : e[1]) + " " + e[0])); if (Ranks.bedrock(o)) U.bar(o, "&f" + p.getName() + " &7" + (Lang.en(o) ? e[2] : e[1]) + " " + e[0]); }
        // bublina nad hlavou
        TextDisplay bubble = p.getWorld().spawn(p.getLocation().add(0, 2.3, 0), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(150, 10, 10, 20));
            d.setTransformation(new Transformation(new Vector3f(0, 0.2f, 0), new AxisAngle4f(), new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
            d.setPersistent(false);
        });
        p.addPassenger(bubble);
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (bubble.isValid()) { bubble.setInterpolationDelay(0); bubble.setInterpolationDuration(7); bubble.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f())); } }, 1);
        boolean quiet = id.equals("sleep") || id.equals("dead");
        if (quiet) bubble.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        Holo.dual(bubble, quiet ? "&r " : e[0]);
        boolean bmOk = BM_LEN.containsKey(id) && bmAvailable(p);
        final boolean bm = bmOk && bmPlay(p, id);
        if (bm) { p.setInvisible(true); invis.add(p.getUniqueId()); }      // původní tělo schovat (jinak jsou vidět dvě postavy přes sebe); model BetterModelu zůstává (follow-mob-invisibility: false)
        else if (bmOk) pl.getLogger().warning("Emote " + id + ": /bm play selhal pro " + p.getName());
        if (!bm && BM_LEN.containsKey(id) && !Ranks.bedrock(p)) {
            // Java hráč dostal jen základní verzi – řekni proč (aby šlo opravit instalaci)
            String why = !Bukkit.getPluginManager().isPluginEnabled("BetterModel")
                    ? (Bukkit.getPluginManager().getPlugin("BetterModel") == null ? "plugin BetterModel není nainstalovaný (plugins/BetterModel-2.2.0.jar)" : "BetterModel se nespustil (koukni do konzole)")
                    : bmOk ? "příkaz /bm play selhal" : "vypnuto v config.yml (emotes.bettermodel)";
            U.msg(p, "&7Základní emote: &c" + why + "&7.");
        }
        final int dur = bm ? (int) (BM_LEN.get(id) * 20) + 6 : switch (id) { case "sleep", "sit" -> 100; case "dance", "twerk", "swim", "fly" -> 70; case "spin" -> 50; case "dead" -> 60; default -> 45; };
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t > dur) {
                    if (bubble.isValid()) { bubble.setInterpolationDelay(0); bubble.setInterpolationDuration(7); bubble.setTransformation(new Transformation(new Vector3f(0, 0.9f, 0), new AxisAngle4f(), new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f())); Bukkit.getScheduler().runTaskLater(pl, bubble::remove, 8); }
                    stand(p); cancel(); return;
                }
                Location head = p.getLocation().add(0, 2, 0);
                if (!bm) switch (id) {
                    // skutečný pohyb těla (ostatní hráči to vidí na tvé postavě)
                    case "hi" -> { if (t < 36 && t % 3 == 0) { if ((t / 3) % 2 == 0) p.swingMainHand(); else p.swingOffHand(); } if (t % 8 == 0) p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head, 2, 0.4, 0.2, 0.4); }
                    case "cheer" -> { if (t % 12 == 0) { p.setVelocity(p.getVelocity().setY(0.42)); p.swingMainHand(); p.swingOffHand(); p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f + (t % 24) / 60f); } p.setSneaking(t % 12 > 8); }
                    case "dance" -> { p.setSneaking(t % 6 < 3); if (t % 6 == 0) { if (t % 12 == 0) p.swingMainHand(); else p.swingOffHand(); p.getWorld().spawnParticle(Particle.NOTE, head, 1, 0.5, 0.3, 0.5, 1); }
                        if (t % 20 == 0 && p.isOnGround()) p.setVelocity(p.getVelocity().setY(0.35)); }
                    case "twerk" -> { p.setSneaking(t % 4 < 2); if (t % 8 == 0) p.getWorld().spawnParticle(Particle.NOTE, head, 1, 0.4, 0.2, 0.4, 1); }
                    case "spin" -> { if (t == 0) { p.setVelocity(p.getVelocity().setY(0.6)); p.startRiptideAttack(45, 0f, null); } if (t % 2 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 4, 0.5, 0.6, 0.5, 0.05); }
                    case "sleep" -> {
                        // ospale se sesune: nejdřív pokles hlavy, pak se svalí na zem a spí
                        if (t == 0) { p.setSneaking(true); p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PANDA_SNEEZE, 0.4f, 0.6f); }
                        if (t > 0 && t < 12) { var lo = p.getLocation(); p.setRotation(lo.getYaw(), Math.min(45f, lo.getPitch() + 5f)); }
                        if (t == 14) {
                            p.setSneaking(false); p.setPose(org.bukkit.entity.Pose.SLEEPING, true);
                            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_WOOL_FALL, 1f, 0.8f);
                            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 10, 0.5, 0.05, 0.5, 0.02);
                        }
                        if (t > 14 && t % 25 == 0) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CAT_PURR, 0.25f, 0.5f);
                    }
                    case "swim" -> { if (t == 0) p.setPose(org.bukkit.entity.Pose.SWIMMING, true); if (t % 3 == 0) p.getWorld().spawnParticle(Particle.BUBBLE_POP, p.getLocation().add(0, 0.6, 0), 3, 0.5, 0.2, 0.5, 0.02); }
                    case "fly" -> { if (t == 0) p.setPose(org.bukkit.entity.Pose.FALL_FLYING, true); if (t % 3 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.8, 0), 2, 0.6, 0.1, 0.6, 0.01); }
                    case "dead" -> { if (t == 0) p.setPose(org.bukkit.entity.Pose.DYING, true); if (t == 5) p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 0.5, 0), 12, 0.4, 0.2, 0.4, 0.02); if (t == 50) stand(p); }
                    case "sit" -> { if (t == 0) sit(p); if (!p.isInsideVehicle() && t > 2) t = dur; }
                    case "bow" -> { if (t == 2) p.setSneaking(true); if (t == 30) p.setSneaking(false); if (t == 4) p.getWorld().spawnParticle(Particle.END_ROD, head, 8, 0.4, 0.3, 0.4, 0.02); }
                    case "gg" -> { if (t == 0) { p.setVelocity(p.getVelocity().setY(0.5)); p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, head, 30, 0.4, 0.4, 0.4, 0.3); } if (t % 5 == 0 && t < 25) p.swingMainHand(); }
                    case "love" -> { p.setSneaking(t % 20 < 10); if (t % 4 == 0) p.getWorld().spawnParticle(Particle.HEART, head, 2, 0.5, 0.3, 0.5); }
                    case "clap" -> { p.setSneaking(t % 8 < 2); if (t % 4 == 0 && t < 36) { if (t % 8 == 0) p.swingMainHand(); else p.swingOffHand(); p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_PLACE, 0.6f, 1.6f); } }
                    case "rage" -> { p.setSneaking(t % 4 < 2); if (t % 4 == 0) p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head, 2, 0.4, 0.2, 0.4); }
                    case "rocket" -> { if (t < 20) p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 6, 0.2, 0, 0.2, 0.02); if (t == 20) p.setVelocity(p.getVelocity().setY(1.2)); }
                }
                extra(p, id, t);
                if (t == 0) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                t++;
            }
        }.runTaskTimer(pl, 0, 1);
    }

    // ---------------- hezčí efekty (spirály, prstence, plovoucí symboly) ----------------
    private static Particle.DustOptions dust(int rgb, float size) { return new Particle.DustOptions(Color.fromRGB(rgb), size); }
    private static int rainbow(double h) { return java.awt.Color.HSBtoRGB((float) (h - Math.floor(h)), 0.85f, 1f) & 0xFFFFFF; }

    /** symbol, který vyplave nad hráče (neviditelný armor stand se jménem – vidí ho Java i Bedrock) */
    private void floatText(Location l, String text, int life, float rise, float size) {
        var st = l.getWorld().spawn(l, org.bukkit.entity.ArmorStand.class, x -> {
            x.setInvisible(true); x.setMarker(true); x.setGravity(false); x.setSmall(true); x.setPersistent(false);
            x.setInvulnerable(true); x.setSilent(true); x.setCanPickupItems(false);
            x.customName(U.c(text)); x.setCustomNameVisible(true);
        });
        new BukkitRunnable() {
            int k = 0;
            @Override public void run() {
                if (!st.isValid() || k >= life) { st.remove(); cancel(); return; }
                st.teleport(st.getLocation().add(0, rise / life, 0));
                k++;
            }
        }.runTaskTimer(pl, 1, 1);
    }

    private void ring(Location c, double r, int pts, Particle.DustOptions o) {
        for (int i = 0; i < pts; i++) { double a = Math.PI * 2 * i / pts; c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, o); }
    }

    /** přídavné efekty nad základní animací těla */
    private void extra(Player p, String id, int t) {
        World w = p.getWorld();
        Location f = p.getLocation(), up = f.clone().add(0, 2.4, 0);
        switch (id) {
            case "love" -> {
                double a = t * 0.45;
                for (int k = 0; k < 2; k++) w.spawnParticle(Particle.DUST, f.clone().add(Math.cos(a + k * Math.PI) * 0.8, 0.2 + (t * 0.06) % 2.0, Math.sin(a + k * Math.PI) * 0.8), 1, 0, 0, 0, 0, dust(k == 0 ? 0xFF4D8D : 0xFF9EC4, 1.3f));
                if (t % 10 == 0) floatText(up, "&c❤", 22, 0.9f, 1.3f);
            }
            case "cheer" -> { if (t % 6 == 0) for (int k = 0; k < 6; k++) w.spawnParticle(Particle.DUST, f.clone().add((Math.random() - .5) * 1.4, 1.0 + Math.random() * 1.8, (Math.random() - .5) * 1.4), 1, 0, 0, 0, 0, dust(rainbow(Math.random()), 1.4f)); if (t % 12 == 0) { w.spawnParticle(Particle.FIREWORK, f.clone().add(0, 2, 0), 12, 0.3, 0.3, 0.3, 0.1); if (t % 24 == 0) floatText(up, "&6★", 20, 0.9f, 1f); } }
            case "dance", "twerk" -> {
                if (t % 2 == 0) for (int k = 0; k < 2; k++) { double a = t * 0.35 + k * Math.PI; w.spawnParticle(Particle.DUST, f.clone().add(Math.cos(a) * 1.0, 0.1 + 0.9 * Math.abs(Math.sin(t * 0.2 + k)), Math.sin(a) * 1.0), 1, 0, 0, 0, 0, dust(rainbow(t * 0.03 + k * 0.5), 1.2f)); }
                if (t % 14 == 0) floatText(up, id.equals("twerk") ? "&d♫" : "&b♪", 20, 0.8f, 1.2f);
            }
            case "hi" -> { if (t % 5 == 0 && t < 30) w.spawnParticle(Particle.END_ROD, f.clone().add(f.getDirection().multiply(0.5)).add(0, 1.9, 0), 3, 0.25, 0.2, 0.25, 0.01); if (t == 2) floatText(up, "&e✌", 24, 0.7f, 1.4f); }
            case "spin" -> {
                for (int k = 0; k < 4; k++) { double y = ((t * 0.15 + k * 0.5) % 2.2), a = t * 0.9 + k * Math.PI / 2, rr = 0.3 + y * 0.45; w.spawnParticle(Particle.DUST, f.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr), 1, 0, 0, 0, 0, dust(k % 2 == 0 ? 0x9AD8FF : 0xFFFFFF, 1.2f)); }
            }
            case "rocket" -> {
                if (t > 20) { w.spawnParticle(Particle.FLAME, f, 6, 0.15, 0.1, 0.15, 0.03); w.spawnParticle(Particle.SMOKE, f, 3, 0.2, 0.1, 0.2, 0.02); }
                if (t == 38) { w.spawnParticle(Particle.FIREWORK, f.clone().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.25); w.playSound(f, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f); }
            }
            case "gg" -> {
                if (t % 3 == 0 && t < 30) for (int k = 0; k < 5; k++) w.spawnParticle(Particle.DUST, f.clone().add((Math.random() - .5) * 1.6, 1.2 + Math.random() * 1.4, (Math.random() - .5) * 1.6), 1, 0, 0, 0, 0, dust(rainbow(Math.random()), 1.3f));
                if (t == 2) floatText(up, "&a&lGG", 26, 1.0f, 1.8f);
            }
            case "clap" -> { if (t % 8 == 0 && t < 36) { w.spawnParticle(Particle.CRIT, f.clone().add(0, 1.4, 0).add(f.getDirection().multiply(0.6)), 14, 0.25, 0.2, 0.25, 0.25); if (t % 16 == 0) floatText(up, "&6✦", 16, 0.6f, 1.2f); } }
            case "sleep" -> { if (t > 16 && t % 28 == 0) floatText(f.clone().add(0, 0.9, 0), "&9&lz", 36, 1.0f, 1f); }
            case "rage" -> { if (t % 8 == 0) ring(f.clone().add(0, 0.2, 0), 0.6 + (t % 16) * 0.05, 18, dust(0xFF2020, 1.4f)); if (t % 12 == 0) floatText(up, "&4&l⚡", 16, 0.5f, 1.5f); }
            case "bow" -> { if (t == 2) for (int k = 0; k < 3; k++) ring(f.clone().add(0, 0.1 + k * 0.05, 0), 0.8 + k * 0.5, 18 + k * 6, dust(k == 1 ? 0xFFD60A : 0xFFFFFF, 1.1f)); }
            case "fly" -> { if (t % 2 == 0) w.spawnParticle(Particle.DUST, f.clone().add(Math.cos(t * 0.6) * 0.7, 0.9, Math.sin(t * 0.6) * 0.7), 1, 0, 0, 0, 0, dust(0xFFFFFF, 1.2f)); }
            case "dead" -> { if (t == 6) floatText(up, "&7☠", 30, 1.0f, 1.5f); }
            case "swim" -> { if (t % 10 == 0) floatText(up, "&b≈", 18, 0.6f, 1.2f); }
            default -> { }
        }
    }

    /** vrátí postavu do normálního postoje */
    private final java.util.Set<UUID> invis = new java.util.HashSet<>();
    private void stand(Player p) {
        waveRestore(p);
        if (invis.remove(p.getUniqueId())) p.setInvisible(false);
        if (!p.isOnline()) return;
        if (p.hasFixedPose() || p.getPose() != org.bukkit.entity.Pose.STANDING) p.setPose(org.bukkit.entity.Pose.STANDING, false);
        p.setSneaking(false);
        var seat = seats.remove(p.getUniqueId());
        if (seat != null) { seat.eject(); seat.remove(); }
    }

    /** sednutí: hráč si opravdu sedne na neviditelnou židli (Shift = vstát) */
    private void sit(Player p) {
        if (!p.isOnGround()) return;
        org.bukkit.entity.ArmorStand seat = p.getWorld().spawn(p.getLocation().add(0, -0.4, 0), org.bukkit.entity.ArmorStand.class, a -> {
            a.setInvisible(true); a.setMarker(true); a.setGravity(false); a.setSmall(true); a.setPersistent(false); a.setInvulnerable(true);
        });
        seat.addPassenger(p);
        seats.put(p.getUniqueId(), seat);
    }
}
