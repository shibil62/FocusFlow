package com.focusflow.ui.components;

import com.focusflow.utilities.Gfx;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** The application icon, drawn in code so no image assets are needed. */
public final class AppIcon {
    private AppIcon() {}

    public static BufferedImage create(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Gfx.aa(img.getGraphics());
        double arc = size * 0.23;
        g.setPaint(new GradientPaint(0, 0, new Color(0x5AC8FA), size, size, new Color(0x5E5CE6)));
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
