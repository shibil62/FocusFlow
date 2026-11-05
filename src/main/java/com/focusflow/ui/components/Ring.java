package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/** Animated circular progress indicator. */
public class Ring extends JComponent {
    private final SpringValue p = new SpringValue(0, 0.7, 0.9, this::repaint);
    private float stroke;
    private String big = "", small = "";
    private Color color;

    public Ring(float stroke) { this.stroke = stroke; }

    public void setColor(Color c) { color = c; repaint(); }

    public void setProgress(double v) { p.to(Math.max(0, Math.min(1, v))); }

    public void setCenter(String big, String small) {
        this.big = big;
        this.small = small;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(150, 150); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        double d = Math.min(getWidth(), getHeight()) - stroke - 2;
        double x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.surfaceAlt);
        g2.draw(new Ellipse2D.Double(x, y, d, d));
        double prog = Math.max(0, Math.min(1, p.value));
        if (prog > 0.002) {
            g2.setColor(color != null ? color : Theme.accent);
            g2.draw(new Arc2D.Double(x, y, d, d, 90, -360 * prog, Arc2D.OPEN));
        }
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        if (small.isEmpty()) {
            Gfx.textCentered(g2, big, cx, cy, Theme.bold((float) (d * 0.24)), Theme.text);
        } else {
            Gfx.textCentered(g2, big, cx, cy - d * 0.06, Theme.bold((float) (d * 0.24)), Theme.text);
            Gfx.textCentered(g2, small, cx, cy + d * 0.20, Theme.font((float) Math.max(11, d * 0.085)), Theme.text2);
        }
        g2.dispose();
    }
}
