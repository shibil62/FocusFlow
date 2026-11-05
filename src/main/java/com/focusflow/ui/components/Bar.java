package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;

/** Rounded linear progress bar with animated fill. */
public class Bar extends JComponent {
    private final SpringValue p = new SpringValue(0, 0.5, 0.95, this::repaint);
    private Color color;
    private final int thickness;

    public Bar(int thickness) {
        this.thickness = thickness;
    }

    public void setColor(Color c) { color = c; repaint(); }
    public void setValue(double v) { p.to(Math.max(0, Math.min(1, v))); }
    public void snapValue(double v) { p.snap(Math.max(0, Math.min(1, v))); repaint(); }

    @Override public Dimension getPreferredSize() { return new Dimension(100, thickness); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, thickness); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(Theme.surfaceAlt);
        g2.fill(Gfx.rr(0, 0, w, h, h));
        double fw = Math.max(0, Math.min(1, p.value)) * w;
        if (fw > 0.5) {
            g2.setColor(color != null ? color : Theme.accent);
            g2.fill(Gfx.rr(0, 0, Math.max(fw, h), h, h));
        }
        g2.dispose();
    }
}
