package com.focusflow.ui;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.components.AppIcon;
import com.focusflow.ui.components.Icons;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Collapsible navigation rail with a spring-animated selection pill. */
public class Sidebar extends JComponent {
    private static final int EXP = 224, COL = 76, NAV_Y = 96, ITEM_H = 40, GAP = 4;
    private static final String[] LABELS = {"Dashboard", "Calendar", "Tasks", "Focus Mode", "Analytics", "Settings"};
    private static final Icons.Type[] ICONS = {Icons.Type.DASHBOARD, Icons.Type.CALENDAR, Icons.Type.TASKS, Icons.Type.FOCUS, Icons.Type.ANALYTICS, Icons.Type.SETTINGS};

    private int selected, hover = -1;
    private boolean collapsed;
    private final SpringValue sel, width;
    private final IntConsumer onNav;
    private final Runnable onNew, onTheme;
    private final Consumer<Boolean> onCollapse;
    private final BufferedImage logo = AppIcon.create(68);

    public Sidebar(IntConsumer onNav, Runnable onNew, Runnable onTheme, boolean collapsed, Consumer<Boolean> onCollapse) {
        this.onNav = onNav;
        this.onNew = onNew;
        this.onTheme = onTheme;
        this.onCollapse = onCollapse;
        this.collapsed = collapsed;
        sel = new SpringValue(0, 0.35, 0.8, this::repaint);
        width = new SpringValue(collapsed ? COL : EXP, 0.4, 0.9, () -> { revalidate(); repaint(); });
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = at(e.getY());
                if (h != hover) { hover = h; repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                int h = at(e.getY());
                if (h < 0) return;
                if (h < 6) Sidebar.this.onNav.accept(h);
                else if (h == 10) Sidebar.this.onNew.run();
                else if (h == 11) Sidebar.this.onTheme.run();
                else if (h == 12) setCollapsed(!Sidebar.this.collapsed, true);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setSelected(int i) {
        selected = i;
        sel.to(i);
    }

    public void setCollapsed(boolean c, boolean notify) {
        collapsed = c;
        width.to(c ? COL : EXP);
        if (notify) onCollapse.accept(c);
    }

    public boolean isCollapsed() { return collapsed; }

    private int at(int y) {
        for (int i = 0; i < 6; i++) {
            int top = NAV_Y + i * (ITEM_H + GAP);
            if (y >= top && y < top + ITEM_H) return i;
        }
        for (int i = 0; i < 3; i++) {
            int top = getHeight() - 16 - (3 - i) * (ITEM_H + GAP);
            if (y >= top && y < top + ITEM_H) return 10 + i;
        }
        return -1;
    }

    @Override public Dimension getPreferredSize() { return new Dimension((int) Math.round(width.value), 100); }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (!collapsed) return null;
        int h = at(e.getY());
        if (h >= 0 && h < 6) return LABELS[h];
        return h == 10 ? "New task" : h == 11 ? "Toggle theme" : h == 12 ? "Expand sidebar" : null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(Theme.sidebar);
        g2.fillRect(0, 0, w, h);
        g2.setColor(Theme.separator);
        g2.drawLine(w - 1, 0, w - 1, h);
        double lp = Math.max(0, Math.min(1, (width.value - COL) / (EXP - COL)));
        float la = (float) Math.max(0, (lp - 0.4) / 0.6);

        g2.drawImage(logo, 21, 30, 34, 34, null);
        if (la > 0.02) {
            Graphics2D t = (Graphics2D) g2.create();
            t.setComposite(AlphaComposite.SrcOver.derive(la));
            t.clipRect(0, 0, w - 8, h);
            Gfx.text(t, "FocusFlow", 66, 53, Theme.bold(17f), Theme.text);
            t.dispose();
        }

        int iw = w - 24;
        double py = NAV_Y + sel.value * (ITEM_H + GAP);
        g2.setColor(Gfx.alpha(Theme.accent, Theme.dark ? 0.22 : 0.13));
        g2.fill(Gfx.rr(12, py, iw, ITEM_H, 12));
        for (int i = 0; i < 6; i++) item(g2, 12, NAV_Y + i * (ITEM_H + GAP), iw, LABELS[i], ICONS[i], i == selected, hover == i, la, false);

        int by = h - 16 - 3 * (ITEM_H + GAP);
        Graphics2D b = (Graphics2D) g2.create();
        b.setColor(hover == 10 ? Theme.accent.brighter() : Theme.accent);
        b.fill(Gfx.rr(12, by, iw, ITEM_H, 12));
        b.dispose();
        item(g2, 12, by, iw, "New task", Icons.Type.PLUS, false, false, la, true);
        item(g2, 12, by + ITEM_H + GAP, iw, Theme.dark ? "Light mode" : "Dark mode", Theme.dark ? Icons.Type.SUN : Icons.Type.MOON, false, hover == 11, la, false);
        item(g2, 12, by + 2 * (ITEM_H + GAP), iw, "Collapse", Icons.Type.SIDEBAR, false, hover == 12, la, false);
        g2.dispose();
    }

    private void item(Graphics2D g, int x, int y, int w, String label, Icons.Type icon, boolean active, boolean hov, float la, boolean onAccent) {
        if (hov && !active && !onAccent) {
            g.setColor(Theme.hover);
            g.fill(Gfx.rr(x, y, w, ITEM_H, 12));
        }
        Color c = onAccent ? Color.WHITE : active ? Theme.accent : Theme.text2;
        Icons.paint(g, icon, x + 14, y + (ITEM_H - 20) / 2.0, 20, c);
        if (la > 0.02) {
            Graphics2D t = (Graphics2D) g.create();
            t.setComposite(AlphaComposite.SrcOver.derive(la));
            t.clipRect(x, y, w, ITEM_H);
            FontMetrics fm = t.getFontMetrics(Theme.semi(14f));
            Gfx.text(t, label, x + 48, y + (ITEM_H - fm.getHeight()) / 2 + fm.getAscent(), active || onAccent ? Theme.semi(14f) : Theme.font(14f),
                    onAccent ? Color.WHITE : active ? Theme.accent : Theme.text);
            t.dispose();
        }
    }
}
