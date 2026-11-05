package com.focusflow.ui.calendar;

import com.focusflow.app.AppContext;
import com.focusflow.models.Task;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.TaskVisuals;
import com.focusflow.ui.components.Toast;
import com.focusflow.ui.dialogs.TaskDialog;
import com.focusflow.ui.tasks.TaskActions;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Month overview. Click a day to add, click a chip to edit, drag a scheduled chip onto another day to reschedule. */
public class MonthGrid extends JComponent {
    private LocalDate month = Time.today();
    private LocalDate gridStart;
    private int rows = 6;
    private final Map<LocalDate, List<Task>> byDay = new HashMap<>();
    private Consumer<LocalDate> onOpenDay;

    private record Hit(Rectangle r, Task task, LocalDate date, int kind) {} // 0 chip, 1 more, 2 number, 3 cell
    private final List<Hit> hits = new ArrayList<>();
    private Hit pressed;
    private Point pressPt;
    private boolean dragging;
    private LocalDate dropTarget;
    private LocalDate hoverDay;

    public MonthGrid() {
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                pressPt = e.getPoint();
                pressed = hitAt(e.getPoint());
                dragging = false;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (pressed == null || pressed.kind() != 0) return;
                if (!dragging && pressPt.distance(e.getPoint()) < 6) return;
                if (pressed.task().start == null) return;
                dragging = true;
                Hit c = cellAt(e.getPoint());
                dropTarget = c == null ? null : c.date();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                Hit h = pressed;
                pressed = null;
                setCursor(Cursor.getDefaultCursor());
                if (SwingUtilities.isRightMouseButton(e)) {
                    Hit x = hitAt(e.getPoint());
                    if (x != null && x.task() != null) TaskActions.menu(x.task(), MonthGrid.this, e.getX(), e.getY());
                    return;
                }
                if (dragging && dropTarget != null && h != null) {
                    Task t = h.task();
                    if (!dropTarget.equals(t.scheduledDate())) {
                        TaskActions.rescheduleTo(t, dropTarget);
                    }
                    dragging = false;
                    dropTarget = null;
                    repaint();
                    return;
                }
                dragging = false;
                dropTarget = null;
                if (h == null) { repaint(); return; }
                switch (h.kind()) {
                    case 0 -> TaskActions.open(h.task());
                    case 1, 2 -> { if (onOpenDay != null) onOpenDay.accept(h.date()); }
                    default -> {
                        LocalDateTime s = h.date().atTime(9, 0);
                        TaskDialog.open(null, s, s.plusMinutes(AppContext.settings.getInt(SettingsService.DEFAULT_DURATION, 60)), null);
                    }
                }
                repaint();
            }
            @Override public void mouseMoved(MouseEvent e) {
                Hit c = cellAt(e.getPoint());
                LocalDate d = c == null ? null : c.date();
                if (!Objects.equals(d, hoverDay)) { hoverDay = d; repaint(); }
                Hit h = hitAt(e.getPoint());
                setCursor(h != null && h.kind() != 3 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
            @Override public void mouseExited(MouseEvent e) { hoverDay = null; repaint(); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setOnOpenDay(Consumer<LocalDate> c) { onOpenDay = c; }

    public void setData(LocalDate anyDayInMonth, List<Task> tasks) {
        month = anyDayInMonth;
        DayOfWeek first = DayOfWeek.valueOf(AppContext.settings.get(SettingsService.WEEK_START, "MONDAY"));
        LocalDate firstOfMonth = month.withDayOfMonth(1);
        gridStart = Time.weekStart(firstOfMonth, first);
        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(gridStart, firstOfMonth);
        rows = (int) Math.ceil((offset + month.lengthOfMonth()) / 7.0);
        byDay.clear();
        for (Task t : tasks) {
            if (t.start != null) {
                LocalDate s = t.scheduledDate(), e = t.lastDay();
                for (LocalDate d = s; !d.isAfter(e) && !d.isAfter(s.plusDays(90)); d = d.plusDays(1)) {
                    byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(t);
                }
            } else if (t.deadline != null) {
                byDay.computeIfAbsent(t.deadlineDate(), k -> new ArrayList<>()).add(t);
            }
        }
        for (List<Task> l : byDay.values()) {
            l.sort(Comparator.comparing((Task t) -> t.start == null).thenComparing(t -> !t.allDay)
                    .thenComparingLong(t -> t.start == null ? 0 : t.start));
        }
        repaint();
    }

    private Hit hitAt(Point p) {
        for (Hit h : hits) if (h.kind() != 3 && h.r().contains(p)) return h;
        return cellAt(p);
    }

    private Hit cellAt(Point p) {
        for (Hit h : hits) if (h.kind() == 3 && h.r().contains(p)) return h;
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        hits.clear();
        if (gridStart == null) { g2.dispose(); return; }
        int w = getWidth(), h = getHeight(), head = 34;
        g2.setColor(Theme.surface);
        g2.fillRect(0, 0, w, h);
        double cw = w / 7.0, rh = (h - head) / (double) rows;
        LocalDate today = Time.today();
        for (int c = 0; c < 7; c++) {
            String n = gridStart.plusDays(c).getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
            Gfx.textCentered(g2, n, c * cw + cw / 2, head / 2.0, Theme.font(12f), Theme.text2);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 7; c++) {
                LocalDate d = gridStart.plusDays(r * 7L + c);
                int x = (int) (c * cw), y = (int) (head + r * rh), cwi = (int) cw, rhi = (int) rh;
                boolean inMonth = d.getMonth() == month.getMonth();
                if (d.equals(hoverDay) && !dragging) {
                    g2.setColor(Theme.hover);
                    g2.fillRect(x, y, cwi, rhi);
                }
                if (dragging && d.equals(dropTarget)) {
                    g2.setColor(Gfx.alpha(Theme.accent, 0.12));
                    g2.fillRect(x, y, cwi, rhi);
                    g2.setColor(Theme.accent);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x + 1, y + 1, cwi - 2, rhi - 2);
                }
                g2.setColor(Theme.separator);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(x, y, x + cwi, y);
                g2.drawLine(x, y, x, y + rhi);
                hits.add(new Hit(new Rectangle(x, y, cwi, rhi), null, d, 3));
                boolean isToday = d.equals(today);
                Rectangle num = new Rectangle(x + 6, y + 4, 28, 24);
                if (isToday) {
                    g2.setColor(Theme.accent);
                    g2.fill(new java.awt.geom.Ellipse2D.Double(x + 8, y + 4, 24, 24));
                }
                Gfx.textCentered(g2, String.valueOf(d.getDayOfMonth()), x + 20, y + 16, Theme.semi(12f),
                        isToday ? Color.WHITE : inMonth ? Theme.text : Theme.text3);
                hits.add(new Hit(num, null, d, 2));
                List<Task> list = byDay.getOrDefault(d, List.of());
                int slots = Math.max(0, (rhi - 34) / 21);
                int show = list.size() > slots ? Math.max(0, slots - 1) : list.size();
                for (int i = 0; i < show; i++) {
                    Task t = list.get(i);
                    Rectangle cr = new Rectangle(x + 4, y + 32 + i * 21, cwi - 8, 18);
                    Color base = TaskVisuals.base(t);
                    boolean dueOnly = t.start == null;
                    boolean lifted = dragging && pressed != null && pressed.task() == t;
                    g2.setComposite(AlphaComposite.SrcOver.derive(t.isCompleted() || lifted ? 0.5f : 1f));
                    if (dueOnly) {
                        g2.setColor(Gfx.alpha(base, 0.8));
                        g2.draw(Gfx.rr(cr.x + 0.5, cr.y + 0.5, cr.width - 1, cr.height - 1, 7));
                    } else {
                        g2.setColor(TaskVisuals.fill(base));
                        g2.fill(Gfx.rr(cr.x, cr.y, cr.width, cr.height, 7));
                        g2.setColor(base);
                        g2.fill(Gfx.rr(cr.x + 4, cr.y + 5, 3, 8, 3));
                    }
                    String label = (t.isTimed() ? Time.time(t.startDT()).replace(":00", "").toLowerCase() + " " : dueOnly ? "Due: " : "") + t.title;
                    FontMetrics fm = g2.getFontMetrics(Theme.semi(11f));
                    Gfx.text(g2, Gfx.fit(label, fm, cr.width - 18), cr.x + 11, cr.y + 13, Theme.semi(11f), TaskVisuals.ink(base));
                    g2.setComposite(AlphaComposite.SrcOver);
                    hits.add(new Hit(cr, t, d, 0));
                }
                if (show < list.size()) {
                    Rectangle mr = new Rectangle(x + 4, y + 32 + show * 21, cwi - 8, 18);
                    Gfx.text(g2, "+" + (list.size() - show) + " more", mr.x + 8, mr.y + 13, Theme.semi(11f), Theme.text2);
                    hits.add(new Hit(mr, null, d, 1));
                }
            }
        }
        g2.dispose();
    }
}
