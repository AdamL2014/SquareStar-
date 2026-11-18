package cz.adrrex.squarestar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Hezké GUI: pozadí z resource packu (vesmírný panel) + titulek */
public final class Gui {
    private static final Key FONT = Key.key("squarestar", "ranks");
    public static boolean enabled = true;
    private Gui() {}

    public static Component title(int size, String legacy) {
        Component text = U.c(legacy);
        if (!enabled) return text;
        int rows = Math.max(1, Math.min(6, size / 9));
        Component bg = Component.text("" + (char) (0xE200 + rows) + "").font(FONT).color(NamedTextColor.WHITE);
        return bg.append(text.colorIfAbsent(NamedTextColor.WHITE));
    }
}
