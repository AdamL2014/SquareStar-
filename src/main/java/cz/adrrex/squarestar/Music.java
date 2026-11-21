package cz.adrrex.squarestar;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hudba v lobby: vesmírný playlist z Minecraft desek. Hlasitost = posuvník „Jukebox/Note Blocks“. /hudba = vyp/zap. */
public final class Music implements CommandExecutor {
    private static final NamespacedKey OFF = new NamespacedKey("squarestar", "music_off");
    /** zvuk, název, délka v sekundách */
    private static final Object[][] LIST = {
            {"minecraft:music_disc.far", "Far – C418", 174},
            {"minecraft:music_disc.relic", "Relic – Aaron Cherof", 218},
            {"minecraft:music_disc.otherside", "Otherside – Lena Raine", 195},
            {"minecraft:music_disc.mall", "Mall – C418", 197},
            {"minecraft:music_disc.precipice", "Precipice – Aaron Cherof", 299},
            {"minecraft:music_disc.creator", "Creator – Lena Raine", 176}};
    private final SquareStar pl;
    private final Map<UUID, long[]> state = new HashMap<>();   // [index skladby, konec v ms]

    public Music(SquareStar pl) {
        this.pl = pl;
        if (pl.getConfig().getBoolean("lobby.music", true)) Bukkit.getScheduler().runTaskTimer(pl, this::tick, 100, 20);
    }

    private boolean wants(Player p) { return p.getPersistentDataContainer().getOrDefault(OFF, PersistentDataType.BYTE, (byte) 0) == 0; }
    private boolean inLobby(Player p) { return p.getWorld() == pl.mars && pl.games.of(p) == null; }

    private void tick() {
        long now = System.currentTimeMillis();
        state.keySet().removeIf(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p != null && inLobby(p) && wants(p)) return false;
            if (p != null) p.stopSound(SoundCategory.RECORDS);
            return true;
        });
        for (Player p : pl.mars.getPlayers()) {
            if (!inLobby(p) || !wants(p)) continue;
            long[] s = state.get(p.getUniqueId());
            if (s != null && now < s[1]) continue;
            int i = s == null ? (int) (Math.random() * LIST.length) : (int) ((s[0] + 1) % LIST.length);
            p.stopSound(SoundCategory.RECORDS);
            p.playSound(p, (String) LIST[i][0], SoundCategory.RECORDS, 0.6f, 1f);
            state.put(p.getUniqueId(), new long[]{i, now + ((int) LIST[i][2] + 4) * 1000L});
            U.bar(p, "&d♫ &7Hraje: &f" + LIST[i][1] + " &8(/hudba = vypnout)");
        }
    }

    @Override public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        boolean off = wants(p);
        p.getPersistentDataContainer().set(OFF, PersistentDataType.BYTE, (byte) (off ? 1 : 0));
        if (off) { p.stopSound(SoundCategory.RECORDS); state.remove(p.getUniqueId()); }
        U.msg(p, off ? "&7Hudba v lobby &cvypnuta&7." : "&aHudba v lobby zapnuta! &7Hlasitost: Nastavení → Zvuk → Jukebox");
        return true;
    }
}
