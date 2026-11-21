package com.focusflow.ui.components;

import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** The application icon, drawn in code so no image assets are needed. */
public final class AppIcon {
    private AppIcon() {}

    private static final Map<Integer, BufferedImage> CACHE = new HashMap<>();
    private static String cacheKey = "";

    /** Same as create(), but reused until the theme changes the icon colours. */
    public static synchronized BufferedImage cached(int size) {
        String k = Theme.iconKey();
        if (!k.equals(cacheKey)) { CACHE.clear(); cacheKey = k; }
        return CACHE.computeIfAbsent(size, AppIcon::create);
    }

    /** The icon follows the theme: its gradient is built from the accent colour (the original blue by default). */
    public static BufferedImage create(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Gfx.aa(img.getGraphics());
        double arc = size * 0.23;
        Color a = Theme.iconA != null ? Theme.iconA : new Color(0x5AC8FA);
        Color b = Theme.iconB != null ? Theme.iconB : new Color(0x5E5CE6);
        g.setPaint(new GradientPaint(0, 0, a, size, size, b));
        g.fill(Gfx.rr(0, 0, size, size, arc * 2));
        double stroke = size * 0.1, pad = size * 0.24, d = size - 2 * pad;
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(new Ellipse2D.Double(pad, pad, d, d));
        g.setColor(Color.WHITE);
        g.draw(new Arc2D.Double(pad, pad, d, d, 90, -250, Arc2D.OPEN));
        g.fill(new Ellipse2D.Double(size / 2.0 - size * 0.06, size / 2.0 - size * 0.06, size * 0.12, size * 0.12));
        g.dispose();
        return img;
    }
}
