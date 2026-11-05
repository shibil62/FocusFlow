package com.focusflow.ui.dashboard;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.Card;
import com.focusflow.ui.components.Icons;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import java.awt.*;
import java.util.function.Supplier;

/** Small summary tile with a count-up number. */
final class StatCard extends Card {
    enum Kind { COUNT, PERCENT, DURATION }

    private final Icons.Type icon;
    private final String label;
    private final Supplier<Color> color;
    private final Kind kind;
    private final SpringValue v = new SpringValue(0, 0.7, 0.95, this::repaint);

    StatCard(Icons.Type icon, String label, Supplier<Color> color, Kind kind) {
        super(14);
        this.icon = icon;
        this.label = label;
        this.color = color;
        this.kind = kind;
    }

    void setValue(double x) { v.to(x); }

    @Override public Dimension getPreferredSize() { return new Dimension(110, 108); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Gfx.aa(g);
        int x = SH + 16, y = SH + 14;
        Color c = color.get();
        g2.setColor(Gfx.alpha(c, 0.15));
        g2.fill(new java.awt.geom.Ellipse2D.Double(x, y, 30, 30));
        Icons.paint(g2, icon, x + 6, y + 6, 18, c);
        String val = switch (kind) {
            case COUNT -> String.valueOf(Math.round(v.value));
            case PERCENT -> Math.round(v.value) + "%";
            case DURATION -> Time.seconds(Math.round(v.value));
        };
        Gfx.text(g2, val, x, getHeight() - SH - 36, Theme.bold(24f), Theme.text);
        Gfx.text(g2, label, x, getHeight() - SH - 16, Theme.font(12f), Theme.text2);
        g2.dispose();
    }
}
