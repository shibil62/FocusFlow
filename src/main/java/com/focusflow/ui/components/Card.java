package com.focusflow.ui.components;

import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Rounded surface with a soft shadow (light) or hairline (dark). Padding is added inside the shadow margin. */
public class Card extends JPanel {
    public static final int SH = 6;
    public static final int ARC = 20;
    private boolean clipChildren;
    private Color tint;

    public Card(int pad) {
        setOpaque(false);
        setBorder(new EmptyBorder(SH + pad, SH + pad, SH + pad, SH + pad));
    }

    public Card(int pad, LayoutManager lm) {
        this(pad);
        setLayout(lm);
    }

    public Card clip() { clipChildren = true; return this; }
    public void setTint(Color c) { tint = c; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int x = SH, y = SH, w = getWidth() - 2 * SH, h = getHeight() - 2 * SH;
        if (!Theme.dark) Gfx.shadow(g2, x, y, w, h, ARC, SH, 0.09);
        g2.setColor(Theme.surface);
        g2.fill(Gfx.rr(x, y, w, h, ARC));
        if (tint != null) {
            g2.setColor(tint);
            g2.fill(Gfx.rr(x, y, w, h, ARC));
        }
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (clipChildren) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(Gfx.rr(SH, SH, getWidth() - 2 * SH, getHeight() - 2 * SH, ARC));
            super.paintChildren(g2);
            g2.dispose();
        } else {
            super.paintChildren(g);
        }
        Graphics2D o = Gfx.aa(g);
        o.setColor(Theme.separator);
        o.draw(Gfx.rr(SH + 0.5, SH + 0.5, getWidth() - 2 * SH - 1, getHeight() - 2 * SH - 1, ARC));
        o.dispose();
    }
}
