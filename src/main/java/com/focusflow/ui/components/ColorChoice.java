package com.focusflow.ui.components;

import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.function.IntConsumer;

/** A row of round colour swatches. A swatch can be two-toned (background + accent) to preview a widget palette. */
public class ColorChoice extends JComponent {
    private static final int D = 28, GAP = 10;

    private final String[] names;
    private final Color[] main, second;
    private int selected;
    private final IntConsumer onPick;

    public ColorChoice(String[] names, Color[] main, Color[] second, int selected, IntConsumer onPick) {
        this.names = names;
        this.main = main;
        this.second = second;
        this.selected = selected;
        this.onPick = onPick;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i >= 0 && i != ColorChoice.this.selected) {
                    ColorChoice.this.selected = i;
                    repaint();
                    if (ColorChoice.this.onPick != null) ColorChoice.this.onPick.accept(i);
                }
            }
        });
    }

    private int indexAt(int x) {
        int i = x / (D + GAP);
        return i >= 0 && i < main.length && x % (D + GAP) < D + 4 ? i : -1;
    }

    @Override public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX());
        return i < 0 ? null : names[i];
    }

    @Override public Dimension getPreferredSize() { return new Dimension(main.length * (D + GAP) - GAP + 8, D + 8); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        for (int i = 0; i < main.length; i++) {
            double x = 4 + i * (D + GAP), y = 4;
            Ellipse2D.Double c = new Ellipse2D.Double(x, y, D, D);
            g2.setColor(main[i]);
            g2.fill(c);
            if (second != null && second[i] != null) {
                Graphics2D h = (Graphics2D) g2.create();
                h.clip(c);
                h.setColor(second[i]);
                h.fillRect((int) (x + D / 2.0), (int) y, D, D);
                h.dispose();
            }
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Theme.separator);
            g2.draw(c);
            if (i == selected) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(Theme.text);
                g2.draw(new Ellipse2D.Double(x - 3.5, y - 3.5, D + 7, D + 7));
            }
        }
        g2.dispose();
    }
}
