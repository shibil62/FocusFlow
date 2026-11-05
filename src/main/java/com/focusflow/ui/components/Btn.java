package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Custom-painted button with instant press feedback (on mouse-down) and spring hover/press motion. */
public class Btn extends JButton {
    public enum Kind { PRIMARY, TINTED, GHOST, DANGER, DANGER_TINTED }

    private Kind kind;
    private Icons.Type icon;
    private final SpringValue hover = new SpringValue(0, 0.25, 1, this::repaint);
    private final SpringValue press = new SpringValue(0, 0.18, 1, this::repaint);

    public Btn(String text, Kind kind) { this(text, kind, null); }

    public Btn(String text, Kind kind, Icons.Type icon) {
        super(text);
        this.kind = kind;
        this.icon = icon;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.semi(13f));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.to(1); }
            @Override public void mouseExited(MouseEvent e) { hover.to(0); press.to(0); }
            @Override public void mousePressed(MouseEvent e) { press.to(1); }
            @Override public void mouseReleased(MouseEvent e) { press.to(0); }
        });
    }

    public static Btn icon(Icons.Type icon, Kind kind, String tooltip) {
        Btn b = new Btn("", kind, icon);
        b.setToolTipText(tooltip);
        return b;
    }

    public void setKind(Kind k) { kind = k; repaint(); }
    public void setIconType(Icons.Type t) { icon = t; repaint(); }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        String t = getText();
        boolean hasText = t != null && !t.isEmpty();
        int w = hasText ? fm.stringWidth(t) + 32 + (icon != null ? 22 : 0) : 36;
        return new Dimension(w, 36);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        double s = 1 - 0.04 * press.value;
        g2.translate(w / 2.0, h / 2.0);
        g2.scale(s, s);
        g2.translate(-w / 2.0, -h / 2.0);

        Color fill, fg;
        switch (kind) {
            case PRIMARY -> { fill = Theme.accent; fg = Color.WHITE; }
            case TINTED -> { fill = Gfx.alpha(Theme.accent, 0.14); fg = Theme.accent; }
            case DANGER -> { fill = Theme.danger; fg = Color.WHITE; }
            case DANGER_TINTED -> { fill = Gfx.alpha(Theme.danger, 0.14); fg = Theme.danger; }
            default -> { fill = new Color(0, 0, 0, 0); fg = Theme.text; }
        }
        if (!isEnabled()) g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
        g2.setColor(fill);
        g2.fill(Gfx.rr(0, 0, w, h, 12));
        if (kind == Kind.GHOST) {
            g2.setColor(Gfx.alpha(Theme.text, 0.06 * hover.value + 0.06 * press.value));
        } else {
            g2.setColor(Theme.dark ? Gfx.alpha(Color.WHITE, 0.10 * hover.value) : Gfx.alpha(Color.BLACK, 0.07 * hover.value));
        }
        g2.fill(Gfx.rr(0, 0, w, h, 12));
        if (isFocusOwner()) {
            g2.setColor(Gfx.alpha(Theme.accent, 0.5));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(Gfx.rr(1, 1, w - 2, h - 2, 12));
        }

        String t = getText();
        boolean hasText = t != null && !t.isEmpty();
        FontMetrics fm = g2.getFontMetrics(getFont());
        int tw = hasText ? fm.stringWidth(t) : 0;
        int iw = icon != null ? 18 : 0, gap = hasText && icon != null ? 6 : 0;
        int x = (w - (iw + gap + tw)) / 2;
        if (icon != null) {
            Icons.paint(g2, icon, x, (h - 18) / 2.0, 18, fg);
            x += iw + gap;
        }
        if (hasText) Gfx.text(g2, t, x, (h - fm.getHeight()) / 2 + fm.getAscent(), getFont(), fg);
        g2.dispose();
    }
}
