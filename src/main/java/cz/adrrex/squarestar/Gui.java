package cz.adrrex.squarestar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Hezké GUI: pozadí z resource packu + titulek.  = posun -8,  = posun -169 */
public final class Gui {
    private static final Key FONT = Key.key("squarestar", "ranks");
    public static boolean enabled = true;
    private Gui() {}

    /** vesmírný panel (minihry, kosmetika, hlasování, obchod) */
    public static Component title(int size, String legacy) { return make((char) (0xE200 + rows(size)), legacy); }
    /** zlato-modrý panel pro bedny */
    public static Component crate(int size, String legacy) { return make((char) (0xE210 + rows(size)), legacy); }
    /** otevírání bedny se zvýrazněným výherním políčkem */
    public static Component crateOpen(String legacy) { return make('', legacy); }

    private static int rows(int size) { return Math.max(1, Math.min(6, size / 9)); }

    private static Component make(char glyph, String legacy) {
        Component text = U.c(legacy);
        if (!enabled) return text;
        Component bg = Component.text("" + glyph + "").font(FONT).color(NamedTextColor.WHITE);
        return bg.append(text.colorIfAbsent(NamedTextColor.WHITE));
    }
}
