package com.focusflow.ui;

import com.focusflow.models.Urgency;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Colours, fonts and the light/dark switch. Custom components read these static fields at paint time,
 * so a theme change only needs a repaint.
 */
public final class Theme {
    private Theme() {}

    public static boolean dark;
    public static Color bg, surface, surfaceAlt, text, text2, text3, separator, accent, sidebar, hover;
    public static Color danger, success, warning, yellow;
    public static String family = "Segoe UI";

    private static Set<String> families = new HashSet<>();
    private static final Map<String, Font> cache = new HashMap<>();

    public static void init(boolean darkMode) {
        families = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        family = pick("SF Pro Display", "SF Pro Text", "Segoe UI", "Arial");
        apply(darkMode);
    }

    private static String pick(String... names) {
        for (String n : names) if (families.contains(n)) return n;
        return Font.DIALOG;
    }

    public static void apply(boolean d) {
        dark = d;
        if (d) {
            bg = new Color(0x0F0F11); surface = new Color(0x1C1C1E); surfaceAlt = new Color(0x2C2C2E);
            text = new Color(0xF5F5F7); text2 = new Color(0xA1A1A6); text3 = new Color(0x6E6E73);
            separator = new Color(255, 255, 255, 28); accent = new Color(0x0A84FF); sidebar = new Color(0x161618);
            hover = new Color(255, 255, 255, 16);
            danger = new Color(0xFF453A); success = new Color(0x30D158); warning = new Color(0xFF9F0A); yellow = new Color(0xFFD60A);
        } else {
            bg = new Color(0xF5F5F7); surface = Color.WHITE; surfaceAlt = new Color(0xF2F2F7);
            text = new Color(0x1D1D1F); text2 = new Color(0x6E6E73); text3 = new Color(0xA1A1A6);
            separator = new Color(0, 0, 0, 24); accent = new Color(0x007AFF); sidebar = new Color(0xEBEBF0);
            hover = new Color(0, 0, 0, 12);
            danger = new Color(0xFF3B30); success = new Color(0x34C759); warning = new Color(0xFF9500); yellow = new Color(0xFFCC00);
        }
        installDefaults();
        if (d) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        // UIManager.put survives LAF changes, so field colours are set after setup too.
        // Must be ColorUIResource: Swing only replaces colours that are UIResource when the theme changes.
        javax.swing.plaf.ColorUIResource field = new javax.swing.plaf.ColorUIResource(surfaceAlt);
        for (String k : new String[]{"TextField.background", "TextArea.background", "FormattedTextField.background",
                "ComboBox.background", "ComboBox.buttonBackground", "Spinner.background", "PasswordField.background"}) {
            UIManager.put(k, field);
        }
        UIManager.put("Panel.background", new javax.swing.plaf.ColorUIResource(bg));
        UIManager.put("defaultFont", font(13f));
    }

    private static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

    private static void installDefaults() {
        Map<String, String> m = new HashMap<>();
        m.put("@accentColor", hex(accent));
        m.put("Component.arc", "12");
        m.put("Button.arc", "12");
        m.put("TextComponent.arc", "12");
        m.put("CheckBox.arc", "6");
        m.put("ScrollBar.thumbArc", "999");
        m.put("ScrollBar.thumbInsets", "2,2,2,2");
        m.put("ScrollBar.width", "12");
        m.put("ScrollBar.showButtons", "false");
        m.put("ScrollBar.trackArc", "999");
        m.put("ScrollPane.smoothScrolling", "true");
        FlatLaf.setGlobalExtraDefaults(m);
    }

    // ------------------------------------------------------------------ fonts

    private static Font get(String fam, int style, float size) {
        String key = fam + "|" + style + "|" + size;
        return cache.computeIfAbsent(key, k -> new Font(fam, style, 1).deriveFont(size));
    }

    public static Font font(float size) { return get(family, Font.PLAIN, size); }
    public static Font bold(float size) { return get(family, Font.BOLD, size); }

    public static Font semi(float size) {
        String f = family + " Semibold";
        if (families.contains(f)) return get(f, Font.PLAIN, size);
        return bold(size);
    }

    public static Font light(float size) {
        String f = family + " Light";
        if (families.contains(f)) return get(f, Font.PLAIN, size);
        return font(size);
    }

    // ----------------------------------------------------------------- colours

    public static Color urgencyColor(Urgency.Level l) {
        return switch (l) {
            case COMFORTABLE -> success;
            case APPROACHING -> yellow;
            case URGENT -> warning;
            case CRITICAL -> danger;
            case OVERDUE -> dark ? new Color(0xD7263D) : new Color(0xA4101F);
            case DONE -> success;
            default -> text3;
        };
    }

    /** Colour that drifts continuously green, yellow, orange, red as the urgency score climbs. */
    public static Color urgencyBlend(Urgency u) {
        if (u.level() == Urgency.Level.OVERDUE) return urgencyColor(Urgency.Level.OVERDUE);
        if (u.level() == Urgency.Level.NONE || u.level() == Urgency.Level.DONE) return null;
        Color[] stops = {success, yellow, warning, danger};
        double[] at = {0, 30, 60, 90};
        int s = u.score();
        if (s <= at[0]) return stops[0];
        for (int i = 1; i < at.length; i++) {
            if (s <= at[i]) return com.focusflow.utilities.Gfx.mix(stops[i - 1], stops[i], (s - at[i - 1]) / (at[i] - at[i - 1]));
        }
        return stops[stops.length - 1];
    }
}
