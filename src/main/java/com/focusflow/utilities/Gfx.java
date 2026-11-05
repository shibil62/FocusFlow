package com.focusflow.utilities;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Small Java2D helpers shared by every custom-painted component. */
public final class Gfx {
    private Gfx() {}

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g2;
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    public static Color alpha(Color c, double a) { return alpha(c, (int) Math.round(a * 255)); }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static RoundRectangle2D rr(double x, double y, double w, double h, double arc) {
        return new RoundRectangle2D.Double(x, y, w, h, arc, arc);
    }

    /** Soft layered shadow drawn just outside the given rounded rectangle. */
    public static void shadow(Graphics2D g, double x, double y, double w, double h, double arc, int spread, double strength) {
        Graphics2D g2 = (Graphics2D) g.create();
        for (int i = spread; i >= 1; i--) {
            double a = strength / spread * (1.0 - (double) i / (spread + 2)) * 1.6;
            g2.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, (int) Math.round(a * 255)))));
            g2.fill(rr(x - i, y - i + 2, w + 2 * i, h + 2 * i, arc + 2 * i));
        }
        g2.dispose();
    }

    public static String fit(String s, FontMetrics fm, int maxW) {
        if (s == null) return "";
        if (maxW <= 0) return "";
        if (fm.stringWidth(s) <= maxW) return s;
        String ell = "…";
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid)) + fm.stringWidth(ell) <= maxW) lo = mid; else hi = mid - 1;
        }
        return lo == 0 ? ell : s.substring(0, lo).stripTrailing() + ell;
    }

    public static void text(Graphics2D g, String s, int x, int baseline, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, x, baseline);
    }

    public static void textCentered(Graphics2D g, String s, double cx, double cy, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    /** Draws a pill label and returns its width. (x, y) is the top-left corner. */
    public static int pill(Graphics2D g, int x, int y, String text, Font f, Color bg, Color fg) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 16;
        int h = fm.getAscent() + fm.getDescent() + 4;
        g.setColor(bg);
        g.fill(rr(x, y, w, h, h));
        g.setColor(fg);
        g.drawString(text, x + 8, y + 2 + fm.getAscent());
        return w;
    }

    public static int pillHeight(Graphics2D g, Font f) {
        FontMetrics fm = g.getFontMetrics(f);
        return fm.getAscent() + fm.getDescent() + 4;
    }
}
