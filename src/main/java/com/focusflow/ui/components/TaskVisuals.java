package com.focusflow.ui.components;

import com.focusflow.app.AppContext;
import com.focusflow.models.Category;
import com.focusflow.models.Task;
import com.focusflow.models.Urgency;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Shared look of a task on calendar surfaces: pastel block with a stronger bar on the left. */
public final class TaskVisuals {
    private TaskVisuals() {}

    public static Color base(Task t) {
        Category c = AppContext.categories.byId(t.categoryId);
        return c == null ? Theme.accent : c.awt();
    }

    public static Color fill(Color base) { return Gfx.mix(Theme.surface, base, Theme.dark ? 0.32 : 0.20); }

    public static Color ink(Color base) {
        return Theme.dark ? Gfx.mix(base, Color.WHITE, 0.62) : Gfx.mix(base, Color.BLACK, 0.52);
    }

    public static void paintBlock(Graphics2D g0, Task t, int x, int y, int w, int h, boolean hover, boolean ghost, String subtitle) {
        Graphics2D g = (Graphics2D) g0.create();
        Color base = base(t);
        boolean done = t.isCompleted();
        RoundRectangle2D rr = Gfx.rr(x, y, w, h, 8);
        g.setComposite(AlphaComposite.SrcOver.derive(ghost ? 0.78f : done ? 0.55f : 1f));
        g.setColor(fill(base));
        g.fill(rr);
        if (hover) {
            g.setColor(Gfx.alpha(base, 38));
            g.fill(rr);
        }
        Graphics2D bar = (Graphics2D) g.create();
        bar.clip(rr);
        bar.setColor(base);
        bar.fillRect(x, y, 4, h);
        bar.dispose();

        Color ink = ink(base);
        boolean hot = false;
        if (!done && t.deadline != null) {
            Urgency u = AppContext.urgency.evaluate(t);
            hot = u.level() == Urgency.Level.CRITICAL || u.level() == Urgency.Level.OVERDUE;
        }
        int tx = x + 10, avail = w - 14 - (hot && w > 70 ? 16 : 0);
        Graphics2D tg = (Graphics2D) g.create();
        tg.clipRect(x + 6, y, Math.max(0, w - 8), h);
        boolean two = h >= 34 && subtitle != null;
        Font tf = Theme.semi(12f);
        FontMetrics fm = tg.getFontMetrics(tf);
        int base1 = two ? y + 6 + fm.getAscent() : y + (h - fm.getHeight()) / 2 + fm.getAscent();
        String title = Gfx.fit(t.title, fm, avail);
        Gfx.text(tg, title, tx, base1, tf, ink);
        if (done) {
            tg.setColor(ink);
            tg.drawLine(tx, base1 - fm.getAscent() / 3, tx + fm.stringWidth(title), base1 - fm.getAscent() / 3);
        }
        if (two) {
            Font sf = Theme.font(11f);
            FontMetrics sm = tg.getFontMetrics(sf);
            Gfx.text(tg, Gfx.fit(subtitle, sm, avail), tx, base1 + sm.getHeight() + 1, sf, Gfx.alpha(ink, 0.8));
        }
        tg.dispose();
        if (hot && w > 70) Icons.paint(g, Icons.Type.FLAME, x + w - 18, y + 5, 12, Theme.danger);
        g.dispose();
    }
}
