package com.focusflow.ui.components;

import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;

/** Friendly placeholder: an icon in a soft circle, a title and a hint. */
public class EmptyState extends JComponent {
    private final Icons.Type icon;
    private final String title, hint;
    private final int height;

    public EmptyState(Icons.Type icon, String title, String hint) { this(icon, title, hint, 150); }

    public EmptyState(Icons.Type icon, String title, String hint, int height) {
        this.icon = icon;
        this.title = title;
        this.hint = hint;
        this.height = height;
    }

    @Override public Dimension getPreferredSize() { return new Dimension(200, height); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, height); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0 - 22;
        g2.setColor(Gfx.alpha(Theme.accent, 0.12));
        g2.fill(new java.awt.geom.Ellipse2D.Double(cx - 24, cy - 24, 48, 48));
        Icons.paint(g2, icon, cx - 12, cy - 12, 24, Theme.accent);
        Gfx.textCentered(g2, title, cx, cy + 44, Theme.semi(14f), Theme.text);
        Gfx.textCentered(g2, hint, cx, cy + 64, Theme.font(12f), Theme.text2);
        g2.dispose();
    }
}
