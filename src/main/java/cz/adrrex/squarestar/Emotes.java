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
        E.put("hi", new String[]{"&e&l✌ Ahoj!", "mává", "waves"});
        E.put("gg", new String[]{"&a&lGG!", "říká GG", "says GG"});
        E.put("love", new String[]{"&c&l❤ ❤ ❤", "posílá lásku", "sends love"});
        E.put("dance", new String[]{"&d&l♪ ♫ ♪", "tancuje", "is dancing"});
        E.put("clap", new String[]{"&6&l✦ Bravo! ✦", "tleská", "claps"});
        E.put("wow", new String[]{"&b&l★ WOW! ★", "žasne", "is amazed"});
        E.put("lol", new String[]{"&e&l☺ HAHA ☺", "se směje", "laughs"});
        E.put("sad", new String[]{"&9&l☹ ...", "je smutný", "is sad"});
        E.put("rage", new String[]{"&4&l⚡ GRR! ⚡", "zuří", "is angry"});
        E.put("rocket", new String[]{"&6&l➹ START!", "odlétá na Mars", "blasts off"});
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        String id = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (!E.containsKey(id)) { U.msg(p, "&7Emoty: &e" + String.join("&7, &e", E.keySet()) + " &8(/emote hi)"); return true; }
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
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t > 50) { bubble.remove(); cancel(); return; }
                Location head = p.getLocation().add(0, 2, 0);
                switch (id) {
                    case "hi" -> { if (t % 5 == 0 && t < 30) { p.swingMainHand(); p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head, 3, 0.4, 0.2, 0.4); } }
                    case "clap" -> { if (t % 4 == 0 && t < 32) { if (t % 8 == 0) p.swingMainHand(); else p.swingOffHand(); p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_PLACE, 0.6f, 1.6f); } }
                    case "love" -> { if (t % 4 == 0) p.getWorld().spawnParticle(Particle.HEART, head, 2, 0.5, 0.3, 0.5); }
                    case "dance" -> { if (t % 3 == 0) { Location l = p.getLocation(); l.setYaw(l.getYaw() + 45); p.teleport(l); p.getWorld().spawnParticle(Particle.NOTE, head, 1, 0.5, 0.3, 0.5, 1); if (t % 6 == 0) p.swingMainHand(); else p.swingOffHand(); } }
                    case "gg" -> { if (t == 0) p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, head, 30, 0.4, 0.4, 0.4, 0.3); }
                    case "wow" -> { if (t % 5 == 0) p.getWorld().spawnParticle(Particle.END_ROD, head, 4, 0.4, 0.3, 0.4, 0.05); }
                    case "lol" -> { if (t % 6 == 0) { p.getWorld().spawnParticle(Particle.WAX_ON, head, 5, 0.4, 0.3, 0.4); p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.5f, 1.4f); } }
                    case "sad" -> { if (t % 3 == 0) p.getWorld().spawnParticle(Particle.FALLING_WATER, head.clone().add(0, 0.4, 0), 2, 0.3, 0.1, 0.3); }
                    case "rage" -> { if (t % 4 == 0) p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head, 2, 0.4, 0.2, 0.4); }
                    case "rocket" -> { if (t < 20) { p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 6, 0.2, 0, 0.2, 0.02); } if (t == 20 && pl.games.of(p) == null) p.setVelocity(p.getVelocity().setY(1.2)); }
                }
                if (t == 0) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                t++;
            }
        }.runTaskTimer(pl, 0, 1);
    }
}
