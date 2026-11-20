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
public final class Emotes implements CommandExecutor, TabCompleter {
    private final SquareStar pl;
    private final Map<UUID, Long> cd = new HashMap<>();
    public Emotes(SquareStar pl) { this.pl = pl; }

    /** název -> {bublina, zpráva cs, zpráva en} */
    private static final Map<String, String[]> E = new LinkedHashMap<>();
    static {
        // název -> {bublina, zpráva cs, zpráva en}
        E.put("hi", new String[]{"&e&l✌ Ahoj!", "mává", "waves"});
        E.put("dance", new String[]{"&d&l♪ ♫ ♪", "tancuje", "is dancing"});
        E.put("twerk", new String[]{"&d&l♫ ♫ ♫", "twerkuje", "twerks"});
        E.put("spin", new String[]{"&b&l🌀 Tornádo!", "se točí jako tornádo", "spins like a tornado"});
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
                o.sendMessage(U.c("&8» &f" + p.getName() + " &7" + (Lang.en(o) ? e[2] : e[1]) + " " + e[0]));
        // bublina nad hlavou
        TextDisplay bubble = p.getWorld().spawn(p.getLocation().add(0, 2.3, 0), TextDisplay.class, d -> {
            d.text(U.c(e[0]));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(150, 10, 10, 20));
            d.setTransformation(new Transformation(new Vector3f(0, 0.4f, 0), new AxisAngle4f(), new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
            d.setPersistent(false);
        });
        p.addPassenger(bubble);
        final int dur = switch (id) { case "sleep", "sit" -> 100; case "dance", "twerk", "swim", "fly" -> 70; case "spin" -> 50; case "dead" -> 60; default -> 45; };
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t > dur) { bubble.remove(); stand(p); cancel(); return; }
                Location head = p.getLocation().add(0, 2, 0);
                switch (id) {
                    // skutečný pohyb těla (ostatní hráči to vidí na tvé postavě)
                    case "hi" -> { if (t < 32 && t % 4 == 0) p.swingMainHand(); if (t == 36) p.setSneaking(true); if (t == 42) p.setSneaking(false); if (t % 8 == 0) p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head, 2, 0.4, 0.2, 0.4); }
                    case "dance" -> { p.setSneaking(t % 6 < 3); if (t % 6 == 0) { if (t % 12 == 0) p.swingMainHand(); else p.swingOffHand(); p.getWorld().spawnParticle(Particle.NOTE, head, 1, 0.5, 0.3, 0.5, 1); }
                        if (t % 20 == 0 && p.isOnGround()) p.setVelocity(p.getVelocity().setY(0.35)); }
                    case "twerk" -> { p.setSneaking(t % 4 < 2); if (t % 8 == 0) p.getWorld().spawnParticle(Particle.NOTE, head, 1, 0.4, 0.2, 0.4, 1); }
                    case "spin" -> { if (t == 0) { p.setVelocity(p.getVelocity().setY(0.6)); p.startRiptideAttack(45, 0f, null); } if (t % 2 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 4, 0.5, 0.6, 0.5, 0.05); }
                    case "sleep" -> { if (t == 0) p.setPose(org.bukkit.entity.Pose.SLEEPING, true); if (t % 15 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.8, 0), 1, 0.1, 0.1, 0.1, 0.01); }
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
                if (t == 0) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                t++;
            }
        }.runTaskTimer(pl, 0, 1);
    }

    /** vrátí postavu do normálního postoje */
    private void stand(Player p) {
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
