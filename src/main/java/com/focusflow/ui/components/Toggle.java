package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** iOS-style switch. */
public class Toggle extends JComponent {
    private boolean on;
    private final SpringValue k;
    private final Consumer<Boolean> onChange;

    public Toggle(boolean on, Consumer<Boolean> onChange) {
        this.on = on;
        this.onChange = onChange;
        k = new SpringValue(on ? 1 : 0, 0.25, 0.8, this::repaint);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                setOn(!Toggle.this.on);
                if (Toggle.this.onChange != null) Toggle.this.onChange.accept(Toggle.this.on);
            }
        });
    }

    public boolean isOn() { return on; }

    public void setOn(boolean v) {
        on = v;
        k.to(v ? 1 : 0);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(46, 28); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        int y = (h - 28) / 2;
        if (!isEnabled()) g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
        Color off = Theme.dark ? new Color(0x3A3A3C) : new Color(0xE5E5EA);
        g2.setColor(Gfx.mix(off, Theme.success, Math.max(0, Math.min(1, k.value))));
        g2.fill(Gfx.rr(0, y, 46, 28, 28));
        double kx = 2 + 18 * k.value;
        Gfx.shadow(g2, kx, y + 2, 24, 24, 24, 3, 0.18);
        g2.setColor(Color.WHITE);
        g2.fill(new java.awt.geom.Ellipse2D.Double(kx, y + 2, 24, 24));
        g2.dispose();
    }
}
