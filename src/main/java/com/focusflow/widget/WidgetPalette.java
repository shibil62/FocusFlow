package com.focusflow.widget;

import com.focusflow.ui.Theme;

import java.awt.Color;
import java.util.List;

/** Colour sets for the desktop widget. "Match app" follows the light/dark theme and accent colour of the app itself. */
public final class WidgetPalette {
    private WidgetPalette() {}

    public record Palette(String name, Color bg, Color text, Color text2, Color accent) {}

    public static final String MATCH_APP = "Match app";

    private static Palette p(String name, int bg, int text, int text2, int accent) {
        return new Palette(name, new Color(bg), new Color(text), new Color(text2), new Color(accent));
    }

    private static final List<Palette> FIXED = List.of(
            p("Midnight", 0x000000, 0xFFFFFF, 0x8E8E93, 0xFF453A),
            p("Ocean", 0x0B1A2B, 0xEAF4FF, 0x7F9BB8, 0x3DB9FF),
            p("Forest", 0x0D1F17, 0xE9F7EF, 0x78A28B, 0x34D399),
            p("Sunset", 0x2A1320, 0xFFF1E8, 0xC99AA6, 0xFF8A4C),
            p("Violet", 0x1C1433, 0xF3EEFF, 0x9C8FC4, 0xA78BFA),
            p("Paper", 0xFFFFFF, 0x1D1D1F, 0x8E8E93, 0xFF3B30),
            p("Sky", 0xEAF3FF, 0x0B2545, 0x6B88AA, 0x0A84FF),
            p("Blush", 0xFFF1F4, 0x3A1F29, 0xA07C88, 0xFF375F));

    /** Names in the order they are offered in menus and settings. */
    public static List<String> names() {
        java.util.ArrayList<String> n = new java.util.ArrayList<>();
        for (Palette p : FIXED) n.add(p.name());
        n.add(MATCH_APP);
        return n;
    }

    /** A fixed palette by name, or null for "Match app" and unknown names (used by the app theme itself). */
    public static Palette fixed(String name) {
        if (name == null) return null;
        for (Palette p : FIXED) if (p.name().equals(name)) return p;
        return null;
    }

    public static Palette get(String name) {
        if (MATCH_APP.equals(name)) return new Palette(MATCH_APP, Theme.surface, Theme.text, Theme.text2, Theme.accent);
        for (Palette p : FIXED) if (p.name().equals(name)) return p;
        return FIXED.get(0);
    }
}
