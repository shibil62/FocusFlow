package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/** iOS-style segmented control with a sliding thumb. */
public class Segmented extends JComponent {
    private final String[] labels;
    private int selected;
    private final IntConsumer onChange;
    private final SpringValue thumb;

    public Segmented(String[] labels, int selected, IntConsumer onChange) {
        this.labels = labels;
        this.selected = selected;
        this.onChange = onChange;
        thumb = new SpringValue(selected, 0.3, 0.85, this::repaint);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int i = (int) ((e.getX() - 3) / segW());
                if (i >= 0 && i < Segmented.this.labels.length && i != Segmented.this.selected) {
                    setSelected(i);
                    if (Segmented.this.onChange != null) Segmented.this.onChange.accept(i);
                }
            }
        });
    }

    private double segW() { return (getWidth() - 6.0) / labels.length; }

    public int getSelected() { return selected; }

    public void setSelected(int i) {
        selected = i;
        thumb.to(i);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.semi(12f));
        int max = 0;
        for (String l : labels) max = Math.max(max, fm.stringWidth(l));
        return new Dimension((max + 28) * labels.length + 6, 32);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(Theme.surfaceAlt);
        g2.fill(Gfx.rr(0, 0, w, h, h));
        double sw = segW();
        double tx = 3 + thumb.value * sw;
        if (!Theme.dark) Gfx.shadow(g2, tx, 3, sw, h - 6, h - 6, 3, 0.10);
        g2.setColor(Theme.dark ? new Color(0x636366) : Theme.surface);
        g2.fill(Gfx.rr(tx, 3, sw, h - 6, h - 6));
        for (int i = 0; i < labels.length; i++) {
            boolean sel = i == selected;
            Gfx.textCentered(g2, labels[i], 3 + sw * i + sw / 2, h / 2.0, Theme.semi(12f), sel ? Theme.text : Theme.text2);
        }
        g2.dispose();
    }
}
