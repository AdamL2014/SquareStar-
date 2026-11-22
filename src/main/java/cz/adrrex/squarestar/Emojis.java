package cz.adrrex.squarestar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Chatové emoji: :smile: / :) apod. se v chatu změní na obrázek (font squarestar:emoji). Bedrock a hráči bez packu dostanou textovou náhradu. */
public final class Emojis {
    private Emojis() {}

    private static final Key FONT = Key.key("squarestar", "emoji");
    /** název, znak ve fontu, textová náhrada, zkratky */
    private record E(String name, char glyph, String text, String... alias) {}
    private static final E[] ALL = {
        new E("smile", '', "☺", ":)", ":-)"), new E("laugh", '', "☻", ":D", ":-D"), new E("cry", '', "☹", ":'("),
        new E("cool", '', "☻", "8)"), new E("angry", '', "☹", ">:("), new E("wink", '', "☺", ";)", ";-)"),
        new E("heart", '', "❤", "<3"), new E("star", '', "★"), new E("fire", '', "🔥"),
        new E("astronaut", '', "☻"), new E("rocket", '', "➤"), new E("alien", '', "☻"),
        new E("skull", '', "☠"), new E("planet", '', "●")
    };

    public static Component apply(Component msg, boolean glyph) {
        for (E e : ALL) {
            Component rep = glyph
                    ? Component.text(String.valueOf(e.glyph)).font(FONT).color(NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text(":" + e.name + ":")))
                    : Component.text(e.text);
            msg = replace(msg, ":" + e.name + ":", rep);
            for (String a : e.alias) msg = replace(msg, a, rep);
        }
        return msg;
    }

    private static Component replace(Component msg, String lit, Component rep) {
        return msg.replaceText(b -> b.matchLiteral(lit).replacement(rep));
    }

    public static String list() {
        StringBuilder sb = new StringBuilder("&eEmoji v chatu: ");
        for (E e : ALL) sb.append("&f:").append(e.name).append(": ");
        return sb.append("&7| zkratky: &f:) :D ;) <3 :'( 8) >:(").toString();
    }
}
