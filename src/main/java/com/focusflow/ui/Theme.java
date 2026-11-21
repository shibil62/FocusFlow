package com.focusflow.ui;

import com.focusflow.models.Urgency;
import com.focusflow.widget.WidgetPalette;
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

    /** Accent colour choices: name, light-mode colour, dark-mode colour. */
    public static final String[] ACCENT_NAMES = {"Blue", "Purple", "Pink", "Red", "Orange", "Green", "Teal", "Graphite"};
    private static final int[][] ACCENT_RGB = {
            {0x007AFF, 0x0A84FF}, {0x7D4DEB, 0x9B7BFF}, {0xE8398F, 0xFF4FA3}, {0xE5352B, 0xFF5A4F},
            {0xF08200, 0xFF9F0A}, {0x24A148, 0x30D158}, {0x00A5A8, 0x2ED1D4}, {0x5F6368, 0x9AA0A6}};
    public static String accentName = "Blue";

    /** Name of the colour theme ("Midnight", "Ocean"...), or "" for the classic light/dark look. */
    public static String paletteName = "";
    /** Readable text colour to put on top of the accent colour. */
    public static Color onAccent = Color.WHITE;
    /** Two more colours in the accent's family, for charts and stat cards. */
    public static Color accent2 = new Color(0x5E5CE6), accent3 = new Color(0x40C8E0);
    /** App icon gradient. Null means the original blue icon. */
    public static Color iconA, iconB;

    public static double lum(Color c) { return (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0; }

    private static Color hueShift(Color c, float dh, float sMin, float bMin) {
        float[] h = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float hue = (h[0] + dh + 1f) % 1f;
        return Color.getHSBColor(hue, Math.max(sMin, Math.min(0.85f, h[1])), Math.max(bMin, h[2]));
    }

    /** Changes whenever the icon would look different, so cached icons can be rebuilt. */
    public static String iconKey() { return iconA == null ? "default" : iconA.getRGB() + "/" + iconB.getRGB(); }

    public static Color accentColor(String name, boolean darkMode) {
        for (int i = 0; i < ACCENT_NAMES.length; i++) if (ACCENT_NAMES[i].equals(name)) return new Color(ACCENT_RGB[i][darkMode ? 1 : 0]);
        return new Color(ACCENT_RGB[0][darkMode ? 1 : 0]);
    }

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
        WidgetPalette.Palette pal = WidgetPalette.fixed(paletteName);
        if (pal != null) paletteColors(pal); else classicColors(d);
        onAccent = lum(accent) > 0.6 ? new Color(0x14141A) : Color.WHITE;
        accent2 = hueShift(accent, 0.09f, 0.45f, 0.80f);
        accent3 = hueShift(accent, -0.06f, 0.45f, 0.80f);
        boolean classicBlue = pal == null && "Blue".equals(accentName);
        if (classicBlue) { iconA = null; iconB = null; }
        else {
            iconA = hueShift(com.focusflow.utilities.Gfx.mix(accent, Color.WHITE, 0.25), -0.03f, 0.35f, 0.95f);
            iconB = hueShift(com.focusflow.utilities.Gfx.mix(accent, Color.BLACK, 0.30), 0.07f, 0.55f, 0.55f);
        }
        installDefaults();
        if (dark) FlatDarkLaf.setup(); else FlatLightLaf.setup();
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

    private static Color mixc(Color a, Color b, double t) { return com.focusflow.utilities.Gfx.mix(a, b, t); }

    private static void classicColors(boolean d) {
        dark = d;
        if (d) {
            bg = new Color(0x0F0F11); surface = new Color(0x1C1C1E); surfaceAlt = new Color(0x2C2C2E);
            text = new Color(0xF5F5F7); text2 = new Color(0xA1A1A6); text3 = new Color(0x6E6E73);
            separator = new Color(255, 255, 255, 28); sidebar = new Color(0x161618);
            hover = new Color(255, 255, 255, 16);
        } else {
            bg = new Color(0xF5F5F7); surface = Color.WHITE; surfaceAlt = new Color(0xF2F2F7);
            text = new Color(0x1D1D1F); text2 = new Color(0x6E6E73); text3 = new Color(0xA1A1A6);
            separator = new Color(0, 0, 0, 24); sidebar = new Color(0xEBEBF0);
            hover = new Color(0, 0, 0, 12);
        }
        accent = accentColor(accentName, d);
        statusColors(d);
    }

    /** Builds every app colour from one of the widget palettes, so the app and the widget match exactly. */
    private static void paletteColors(WidgetPalette.Palette p) {
        boolean d = lum(p.bg()) < 0.5;
        dark = d;
        Color t = p.text();
        if (d) {
            bg = p.bg();
            surface = mixc(bg, t, 0.075);
            surfaceAlt = mixc(bg, t, 0.14);
            sidebar = mixc(bg, t, 0.035);
        } else {
            bg = mixc(p.bg(), t, 0.06);
            surface = mixc(p.bg(), Color.WHITE, 0.55);
            surfaceAlt = mixc(p.bg(), t, 0.07);
            sidebar = mixc(p.bg(), t, 0.10);
        }
        text = t;
        text2 = p.text2();
        text3 = mixc(p.text2(), p.bg(), 0.45);
        separator = new Color(t.getRed(), t.getGreen(), t.getBlue(), d ? 30 : 26);
        hover = new Color(t.getRed(), t.getGreen(), t.getBlue(), d ? 18 : 14);
        accent = p.accent();
        statusColors(d);
    }

    private static void statusColors(boolean d) {
        if (d) { danger = new Color(0xFF453A); success = new Color(0x30D158); warning = new Color(0xFF9F0A); yellow = new Color(0xFFD60A); }
        else { danger = new Color(0xFF3B30); success = new Color(0x34C759); warning = new Color(0xFF9500); yellow = new Color(0xFFCC00); }
    }

    private static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

    private static void installDefaults() {
        Map<String, String> m = new HashMap<>();
        m.put("@accentColor", hex(accent));
        m.put("@background", hex(bg));
        m.put("@foreground", hex(text));
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
