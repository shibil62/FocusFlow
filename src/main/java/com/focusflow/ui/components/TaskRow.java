package com.focusflow.ui.components;

import com.focusflow.animations.Animator;
import com.focusflow.animations.SpringValue;
import com.focusflow.app.AppContext;
import com.focusflow.models.*;
import com.focusflow.ui.Theme;
import com.focusflow.ui.tasks.TaskActions;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.time.LocalDate;

/** One task as a card: completion circle, title, schedule/category chips and a live urgency badge. */
public class TaskRow extends JComponent {
    public static final int H = 68;

    private final Task task;
    private final Urgency urg;
    private final Category cat;
    private boolean hover, busy;
    private int zone;
    private final SpringValue check;

    public TaskRow(Task t) {
        this.task = t;
        this.urg = AppContext.urgency.evaluate(t);
        this.cat = AppContext.categories.byId(t.categoryId);
        this.check = new SpringValue(t.isCompleted() ? 1 : 0, 0.3, 0.7, this::repaint);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; zone = 0; repaint(); }
            @Override public void mouseMoved(MouseEvent e) {
                int z = zoneAt(e.getX(), e.getY());
                if (z != zone) { zone = z; repaint(); }
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) { TaskActions.menu(task, TaskRow.this, e.getX(), e.getY()); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int z = zoneAt(e.getX(), e.getY());
                if (z == 1) toggle();
                else if (z == 2) TaskActions.menu(task, TaskRow.this, getWidth() - 30, H - 14);
                else TaskActions.open(task);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private int zoneAt(int x, int y) {
        if (Math.hypot(x - 30, y - H / 2.0) <= 15) return 1;
        if (x >= getWidth() - 40) return 2;
        return 0;
    }

    private void toggle() {
        if (busy) return;
        busy = true;
        check.to(task.isCompleted() ? 0 : 1);
        Timer tm = new Timer(Animator.reduceMotion ? 0 : 260, e -> TaskActions.toggle(task));
        tm.setRepeats(false);
        tm.start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(100, H); }
    @Override public Dimension getMinimumSize() { return new Dimension(100, H); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, H); }

    private String when() {
        if (task.start == null) {
            if (task.deadline == null) return "Unscheduled";
            return "Due " + Time.relativeDay(task.deadlineDate()) + ", " + Time.time(task.deadlineDT());
        }
        LocalDate d = task.scheduledDate();
        if (task.allDay) {
            LocalDate last = task.lastDay();
            return (last.isAfter(d) ? Time.relativeDay(d) + " – " + Time.relativeDay(last) : Time.relativeDay(d)) + " · All day";
        }
        return Time.relativeDay(d) + ", " + Time.range(task.startDT(), task.endDT());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        boolean done = task.isCompleted();
        var rr = Gfx.rr(0.5, 0.5, w - 1, h - 1, 16);
        g2.setColor(hover ? Gfx.mix(Theme.surface, Theme.text, 0.03) : Theme.surface);
        g2.fill(rr);
        Color uc = done ? null : Theme.urgencyBlend(urg);
        if (uc != null) {
            g2.setColor(Gfx.alpha(uc, (Theme.dark ? 0.06 : 0.04) + urg.score() / 100.0 * (Theme.dark ? 0.16 : 0.12)));
            g2.fill(rr);
            Graphics2D s = (Graphics2D) g2.create();
            s.clip(rr);
            s.setColor(uc);
            s.fillRect(0, 0, 4, h);
            s.dispose();
        }
        g2.setColor(Theme.separator);
        g2.draw(rr);

        // completion circle
        double cx = 30, cy = h / 2.0;
        double fill = Math.max(0, Math.min(1, check.value));
        Color ring = task.priority == Priority.CRITICAL ? Theme.danger : task.priority == Priority.HIGH ? Theme.warning : Theme.text3;
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Gfx.mix(ring, Theme.success, fill));
        g2.draw(new Ellipse2D.Double(cx - 10, cy - 10, 20, 20));
        if (fill > 0.01) {
            double r = 10 * Math.min(1.0, fill);
            g2.setColor(Gfx.alpha(Theme.success, Math.min(1, fill)));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            Icons.paint(g2, Icons.Type.CHECK, cx - 8 * fill, cy - 8 * fill, 16 * fill, Color.WHITE);
        } else if (zone == 1) {
            Icons.paint(g2, Icons.Type.CHECK, cx - 7, cy - 7, 14, Gfx.alpha(Theme.text3, 0.7));
        }

        // right block
        int rx = w - 16 - (hover ? 28 : 0), rightW = 0;
        Font pf = Theme.semi(11f), cf = Theme.font(11f);
        if (done || urg.level() != Urgency.Level.NONE) {
            Color pc = done ? Theme.success : Theme.urgencyColor(urg.level());
            String label = done ? "Done" : urg.level().label;
            int pw = g2.getFontMetrics(pf).stringWidth(label) + 16;
            Gfx.pill(g2, rx - pw, 14, label, pf, Gfx.alpha(pc, 0.16), Theme.dark ? pc : Gfx.mix(pc, Color.BLACK, 0.25));
            String sub = done ? (task.completedAt != null ? Time.relativeDay(Time.dt(task.completedAt).toLocalDate()) : "") : urg.countdown();
            int sw = g2.getFontMetrics(cf).stringWidth(sub);
            Gfx.text(g2, sub, rx - sw, 50, cf, Theme.text2);
            rightW = Math.max(pw, sw) + 16;
        }
        if (hover) Icons.paint(g2, Icons.Type.MORE, w - 36, h / 2.0 - 10, 20, zone == 2 ? Theme.text : Theme.text3);

        int tx = 54, limit = w - 16 - rightW - (hover ? 28 : 0);
        Font tf = Theme.semi(14f);
        FontMetrics fm = g2.getFontMetrics(tf);
        String title = Gfx.fit(task.title, fm, limit - tx);
        Color tc = done ? Theme.text3 : Theme.text;
        Gfx.text(g2, title, tx, 29, tf, tc);
        if (done) {
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(tc);
            g2.drawLine(tx, 24, tx + fm.stringWidth(title), 24);
        }

        // meta line
        int x = tx, my = 50;
        Font mf = Theme.font(12f);
        FontMetrics mm = g2.getFontMetrics(mf);
        String when = Gfx.fit(when(), mm, Math.max(40, limit - x - 60));
        Gfx.text(g2, when, x, my, mf, Theme.text2);
        x += mm.stringWidth(when) + 10;
        Font chip = Theme.semi(11f);
        if (cat != null && x + 60 < limit) {
            Color cc = cat.awt();
            x += Gfx.pill(g2, x, my - 13, cat.name, chip, Gfx.alpha(cc, 0.16), TaskVisuals.ink(cc)) + 6;
        }
        if ((task.priority == Priority.HIGH || task.priority == Priority.CRITICAL) && x + 60 < limit) {
            Color pc = task.priority == Priority.CRITICAL ? Theme.danger : Theme.warning;
            x += Gfx.pill(g2, x, my - 13, task.priority.label, chip, Gfx.alpha(pc, 0.16), pc) + 6;
        }
        if (!task.subtasks.isEmpty() && x + 90 < limit) {
            double pr = task.progress();
            g2.setColor(Theme.surfaceAlt);
            g2.fill(Gfx.rr(x, my - 8, 44, 4, 4));
            g2.setColor(pr >= 1 ? Theme.success : Theme.accent);
            g2.fill(Gfx.rr(x, my - 8, Math.max(4, 44 * pr), 4, 4));
            String st = task.subtasksDone() + "/" + task.subtasks.size();
            Gfx.text(g2, st, x + 52, my, Theme.font(11f), Theme.text2);
            x += 52 + g2.getFontMetrics(Theme.font(11f)).stringWidth(st) + 10;
        }
        if (task.recurrence != Recurrence.NONE && x + 20 < limit) {
            Icons.paint(g2, Icons.Type.REPEAT, x, my - 12, 14, Theme.text3);
        }
        g2.dispose();
    }
}
