package com.focusflow.ui.calendar;

import com.focusflow.app.AppContext;
import com.focusflow.models.Task;
import com.focusflow.models.Urgency;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.Icons;
import com.focusflow.ui.components.TaskVisuals;
import com.focusflow.ui.dialogs.TaskDialog;
import com.focusflow.ui.tasks.TaskActions;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Day and week view: a vertical time grid with overlapping events, an all-day row, a live "now" line,
 * drag-to-create on empty space, drag-to-move and drag-the-bottom-edge-to-resize on events.
 */
public class TimeGridPane extends JScrollPane {
    static final int HOUR_H = 56, GUTTER = 64, SNAP = 15;

    private int days = 7;
    private LocalDate first = Time.today();
    private List<Task> tasks = List.of();
    private final List<Placed> placed = new ArrayList<>();
    private final List<Task>[] allDay;
    private final Body body = new Body();
    private final Header header = new Header();
    private LocalDateTime ghostStart, ghostEnd;
    private Task ghostTask;

    private static final class Placed {
        Task t;
        int col, lane, lanes = 1, startMin, endMin;
        Rectangle r = new Rectangle();
        boolean deadline;
    }

    @SuppressWarnings("unchecked")
    public TimeGridPane() {
        allDay = new List[7];
        for (int i = 0; i < 7; i++) allDay[i] = new ArrayList<>();
        setBorder(null);
        setOpaque(false);
        getViewport().setOpaque(false);
        setViewportView(body);
        setColumnHeaderView(header);
        getVerticalScrollBar().setUnitIncrement(18);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        new Timer(30_000, e -> { if (isShowing()) body.repaint(); }).start();
        scrollToHour(Math.max(0, java.time.LocalTime.now().getHour() - 2));
    }

    public void scrollToHour(int h) {
        SwingUtilities.invokeLater(() -> getVerticalScrollBar().setValue(h * HOUR_H));
    }

    public LocalDate firstDay() { return first; }

    public void setData(LocalDate first, int days, List<Task> tasks) {
        this.first = first;
        this.days = days;
        this.tasks = tasks;
        layoutEvents();
        header.revalidate();
        header.repaint();
        body.repaint();
    }

    // ------------------------------------------------------------- layout model

    private void layoutEvents() {
        placed.clear();
        for (List<Task> l : allDay) l.clear();
        for (int c = 0; c < days; c++) {
            LocalDate d = first.plusDays(c);
            List<Placed> day = new ArrayList<>();
            for (Task t : tasks) {
                if (t.isTimed() && t.scheduledDate().equals(d)) {
                    Placed p = new Placed();
                    p.t = t;
                    p.col = c;
                    p.startMin = t.startDT().toLocalTime().toSecondOfDay() / 60;
                    p.endMin = Math.max(p.startMin + 20, t.endDT().toLocalTime().toSecondOfDay() / 60);
                    if (t.endDT().toLocalTime().equals(LocalTime.MIDNIGHT)) p.endMin = 1440;
                    day.add(p);
                } else if (t.allDay && t.start != null && t.coversDay(d)) {
                    allDay[c].add(t);
                } else if (t.start == null && t.deadline != null && t.deadlineDate().equals(d)) {
                    allDay[c].add(t);
                }
                if (t.deadline != null && !t.isCompleted() && t.start != null && !t.allDay && t.deadlineDate().equals(d)) {
                    Placed p = new Placed();
                    p.t = t;
                    p.col = c;
                    p.deadline = true;
                    p.startMin = t.deadlineDT().toLocalTime().toSecondOfDay() / 60;
                    p.endMin = p.startMin;
                    placed.add(p);
                }
            }
            day.sort(Comparator.<Placed>comparingInt(p -> p.startMin).thenComparingInt(p -> -p.endMin));
            List<Placed> cluster = new ArrayList<>();
            int clusterEnd = -1;
            for (Placed p : day) {
                if (!cluster.isEmpty() && p.startMin >= clusterEnd) {
                    assignLanes(cluster);
                    cluster = new ArrayList<>();
                    clusterEnd = -1;
                }
                cluster.add(p);
                clusterEnd = Math.max(clusterEnd, p.endMin);
            }
            if (!cluster.isEmpty()) assignLanes(cluster);
            placed.addAll(day);
        }
    }

    private static void assignLanes(List<Placed> cluster) {
        List<Integer> laneEnds = new ArrayList<>();
        for (Placed p : cluster) {
            int lane = -1;
            for (int i = 0; i < laneEnds.size(); i++) {
                if (laneEnds.get(i) <= p.startMin) { lane = i; break; }
            }
            if (lane < 0) { laneEnds.add(p.endMin); lane = laneEnds.size() - 1; } else laneEnds.set(lane, p.endMin);
            p.lane = lane;
        }
        for (Placed p : cluster) p.lanes = laneEnds.size();
    }

    private int colW(int width) { return Math.max(40, (width - GUTTER) / days); }

    private Rectangle rectOf(Placed p, int width) {
        int cw = colW(width);
        if (p.deadline) {
            int y = (int) (p.startMin * HOUR_H / 60.0);
            return new Rectangle(GUTTER + p.col * cw + cw - 20, y - 8, 16, 16);
        }
        int avail = cw - 8;
        int lw = avail / p.lanes;
        int x = GUTTER + p.col * cw + 4 + p.lane * lw;
        int w = p.lanes > 1 ? lw - 2 : avail;
        int y = (int) (p.startMin * HOUR_H / 60.0) + 1;
        int h = Math.max(20, (int) ((p.endMin - p.startMin) * HOUR_H / 60.0) - 2);
        return new Rectangle(x, y, w, h);
    }

    // =================================================================== header

    private final class Header extends JComponent {
        private final List<Object[]> hits = new ArrayList<>();

        Header() {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) {
                    for (Object[] h : hits) {
                        Rectangle r = (Rectangle) h[0];
                        if (r.contains(e.getPoint())) {
                            if (h[1] instanceof Task t) TaskActions.open(t);
                            else if (h[1] instanceof LocalDate d && days > 1) fireOpenDay(d);
                            return;
                        }
                    }
                }
            });
        }

        private int chipRows() {
            int m = 0;
            for (int i = 0; i < days; i++) m = Math.max(m, allDay[i].size());
            return Math.min(3, m);
        }

        @Override
        public Dimension getPreferredSize() {
            int rows = chipRows();
            return new Dimension(200, 62 + (rows > 0 ? rows * 22 + 10 : 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            hits.clear();
            int w = getWidth(), h = getHeight(), cw = colW(w);
            g2.setColor(Theme.surface);
            g2.fillRect(0, 0, w, h);
            LocalDate today = Time.today();
            for (int c = 0; c < days; c++) {
                LocalDate d = first.plusDays(c);
                double cx = GUTTER + c * cw + cw / 2.0;
                boolean isToday = d.equals(today);
                String dn = d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
                Gfx.textCentered(g2, dn, cx, 18, Theme.font(12f), isToday ? Theme.accent : Theme.text2);
                if (isToday) {
                    g2.setColor(Theme.accent);
                    g2.fill(new java.awt.geom.Ellipse2D.Double(cx - 16, 28, 32, 32));
                }
                Gfx.textCentered(g2, String.valueOf(d.getDayOfMonth()), cx, 44, Theme.semi(17f), isToday ? Color.WHITE : Theme.text);
                hits.add(new Object[]{new Rectangle(GUTTER + c * cw, 0, cw, 62), d});
                g2.setColor(Theme.separator);
                g2.drawLine(GUTTER + c * cw, 0, GUTTER + c * cw, h);
                int rows = chipRows();
                List<Task> list = allDay[c];
                for (int i = 0; i < Math.min(list.size(), rows); i++) {
                    Task t = list.get(i);
                    boolean more = i == rows - 1 && list.size() > rows;
                    Rectangle r = new Rectangle(GUTTER + c * cw + 4, 64 + i * 22, cw - 8, 19);
                    if (more) {
                        Gfx.text(g2, "+" + (list.size() - rows + 1) + " more", r.x + 6, r.y + 14, Theme.font(11f), Theme.text2);
                        hits.add(new Object[]{r, d});
                    } else {
                        Color base = TaskVisuals.base(t);
                        boolean dueOnly = t.start == null;
                        if (dueOnly) {
                            g2.setColor(Gfx.alpha(base, 0.8));
                            g2.setStroke(new BasicStroke(1.2f));
                            g2.draw(Gfx.rr(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 8));
                        } else {
                            g2.setColor(TaskVisuals.fill(base));
                            g2.fill(Gfx.rr(r.x, r.y, r.width, r.height, 8));
                        }
                        FontMetrics fm = g2.getFontMetrics(Theme.semi(11f));
                        String label = (dueOnly ? "Due: " : "") + t.title;
                        Gfx.text(g2, Gfx.fit(label, fm, r.width - 12), r.x + 6, r.y + 14, Theme.semi(11f), TaskVisuals.ink(base));
                        hits.add(new Object[]{r, t});
                    }
                }
            }
            Gfx.text(g2, "all-day", 10, h - 8, Theme.font(10f), Theme.text3);
            g2.setColor(Theme.separator);
            g2.drawLine(0, h - 1, w, h - 1);
            g2.dispose();
        }
    }

    // Day-header clicks (week view) ask the owner to switch to the day view.
    private Runnable[] openDayHook = new Runnable[1];
    private java.util.function.Consumer<LocalDate> onOpenDay;

    public void setOnOpenDay(java.util.function.Consumer<LocalDate> c) { onOpenDay = c; }

    private void fireOpenDay(LocalDate d) { if (onOpenDay != null) onOpenDay.accept(d); }

    // ===================================================================== body

    private enum Mode { NONE, PRESS_EVENT, MOVE, RESIZE, CREATE }

    private final class Body extends JComponent implements Scrollable {
        private Mode mode = Mode.NONE;
        private Placed pressed, hover;
        private Point pressPt;
        private int grabMin;
        private boolean moved;

        Body() {
            setAutoscrolls(true);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { press(e); }
                @Override public void mouseDragged(MouseEvent e) { drag(e); }
                @Override public void mouseReleased(MouseEvent e) { release(e); }
                @Override public void mouseMoved(MouseEvent e) { hoverAt(e); }
                @Override public void mouseExited(MouseEvent e) { if (hover != null) { hover = null; repaint(); } }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(400, 24 * HOUR_H + 2); }

        private Placed hit(Point p) {
            for (int i = placed.size() - 1; i >= 0; i--) {
                Placed pl = placed.get(i);
                if (pl.deadline && pl.r.contains(p)) return pl;
            }
            for (int i = placed.size() - 1; i >= 0; i--) {
                Placed pl = placed.get(i);
                if (!pl.deadline && pl.r.contains(p)) return pl;
            }
            return null;
        }

        private int minuteAt(int y, boolean snap) {
            double m = y * 60.0 / HOUR_H;
            int v = snap ? (int) (Math.round(m / SNAP) * SNAP) : (int) m;
            return Math.max(0, Math.min(1440, v));
        }

        private int colAt(int x) { return Math.max(0, Math.min(days - 1, (x - GUTTER) / colW(getWidth()))); }

        private LocalDateTime at(int col, int minute) { return first.plusDays(col).atStartOfDay().plusMinutes(minute); }

        private void hoverAt(MouseEvent e) {
            Placed h = hit(e.getPoint());
            if (h != hover) { hover = h; repaint(); }
            if (h == null) setCursor(Cursor.getDefaultCursor());
            else if (!h.deadline && e.getY() > h.r.y + h.r.height - 7 && h.r.height > 24) setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
            else setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        private void press(MouseEvent e) {
            requestFocusInWindow();
            pressPt = e.getPoint();
            moved = false;
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            Placed h = hit(e.getPoint());
            if (h != null) {
                pressed = h;
                if (h.deadline) { mode = Mode.PRESS_EVENT; return; }
                grabMin = minuteAt(e.getY(), false) - h.startMin;
                mode = e.getY() > h.r.y + h.r.height - 7 && h.r.height > 24 ? Mode.RESIZE : Mode.PRESS_EVENT;
            } else if (e.getX() >= GUTTER) {
                mode = Mode.CREATE;
                int c = colAt(e.getX());
                int s = Math.min(1440 - SNAP, minuteAt(e.getY(), true));
                ghostStart = at(c, s);
                ghostEnd = ghostStart.plusMinutes(SNAP);
                ghostTask = null;
            }
        }

        private void drag(MouseEvent e) {
            if (mode == Mode.NONE) return;
            if (!moved && pressPt.distance(e.getPoint()) < 5) return;
            moved = true;
            scrollRectToVisible(new Rectangle(e.getX(), e.getY() - 24, 1, 48));
            switch (mode) {
                case PRESS_EVENT, MOVE -> {
                    if (pressed.deadline) return;
                    mode = Mode.MOVE;
                    int dur = pressed.endMin - pressed.startMin;
                    Task t = pressed.t;
                    dur = (int) ((t.end - t.start) / 60000);
                    int start = Math.max(0, Math.min(1440 - Math.min(dur, 1440), (int) (Math.round((minuteAt(e.getY(), false) - grabMin) / (double) SNAP) * SNAP)));
                    int c = colAt(e.getX());
                    ghostStart = at(c, start);
                    ghostEnd = ghostStart.plusMinutes(dur);
                    ghostTask = t;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                case RESIZE -> {
                    Task t = pressed.t;
                    ghostStart = t.startDT();
                    int end = Math.max(pressed.startMin + SNAP, minuteAt(e.getY(), true));
                    ghostEnd = ghostStart.toLocalDate().atStartOfDay().plusMinutes(Math.min(1440, end));
                    if (end >= 1440) ghostEnd = ghostStart.toLocalDate().atTime(23, 59);
                    ghostTask = t;
                }
                case CREATE -> {
                    int c = colAt(e.getX());
                    LocalDate day = ghostStart.toLocalDate();
                    int sm = ghostStart.toLocalTime().toSecondOfDay() / 60;
                    int em = Math.max(sm + SNAP, minuteAt(e.getY(), true));
                    ghostEnd = day.atStartOfDay().plusMinutes(Math.min(em, 1439));
                }
                default -> { }
            }
            repaint();
        }

        private void release(MouseEvent e) {
            Mode m = mode;
            Placed p = pressed;
            mode = Mode.NONE;
            pressed = null;
            if (SwingUtilities.isRightMouseButton(e)) {
                Placed h = hit(e.getPoint());
                if (h != null) TaskActions.menu(h.t, this, e.getX(), e.getY());
                return;
            }
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            switch (m) {
                case PRESS_EVENT -> { ghostStart = ghostEnd = null; ghostTask = null; if (p != null) TaskActions.open(p.t); }
                case MOVE, RESIZE -> {
                    LocalDateTime s = ghostStart, en = ghostEnd;
                    Task t = ghostTask;
                    ghostStart = ghostEnd = null;
                    ghostTask = null;
                    if (t != null && (!s.equals(t.startDT()) || !en.equals(t.endDT()))) {
                        AppContext.tasks.reschedule(t, s, en);
                        com.focusflow.ui.components.Toast.show(m == Mode.MOVE ? "Task moved" : "Duration changed", "Undo", AppContext.tasks::undoLast);
                    }
                }
                case CREATE -> {
                    LocalDateTime s = ghostStart, en = ghostEnd;
                    if (!moved) en = s.plusMinutes(AppContext.settings.getInt(SettingsService.DEFAULT_DURATION, 60));
                    if (!en.toLocalDate().equals(s.toLocalDate())) en = s.toLocalDate().atTime(23, 59);
                    ghostEnd = en;
                    TaskDialog.open(null, s, en, () -> { ghostStart = ghostEnd = null; repaint(); });
                }
                default -> { }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight(), cw = colW(w);
            g2.setColor(Theme.surface);
            g2.fillRect(0, 0, w, h);
            LocalDate today = Time.today();
            Font lf = Theme.font(11f);
            for (int c = 0; c < days; c++) {
                if (first.plusDays(c).equals(today)) {
                    g2.setColor(Gfx.alpha(Theme.accent, Theme.dark ? 0.07 : 0.045));
                    g2.fillRect(GUTTER + c * cw, 0, cw, h);
                }
            }
            for (int hr = 0; hr <= 24; hr++) {
                int y = hr * HOUR_H;
                g2.setColor(Theme.separator);
                g2.drawLine(hr == 0 ? GUTTER : GUTTER - 6, y, w, y);
                if (hr > 0 && hr < 24) {
                    String label = (hr % 12 == 0 ? 12 : hr % 12) + (hr < 12 ? " AM" : " PM");
                    FontMetrics fm = g2.getFontMetrics(lf);
                    Gfx.text(g2, label, GUTTER - 12 - fm.stringWidth(label), y + 4, lf, Theme.text3);
                }
                if (hr < 24) {
                    g2.setColor(Gfx.alpha(Theme.separator, 12));
                    g2.drawLine(GUTTER, y + HOUR_H / 2, w, y + HOUR_H / 2);
                }
            }
            g2.setColor(Theme.separator);
            for (int c = 0; c <= days; c++) g2.drawLine(GUTTER + c * cw, 0, GUTTER + c * cw, h);

            for (Placed p : placed) {
                p.r = rectOf(p, w);
                if (p.deadline) continue;
                boolean ghosted = (mode == Mode.MOVE || mode == Mode.RESIZE) && pressed != null && pressed.t == p.t;
                String sub = Time.range(p.t.startDT(), p.t.endDT());
                TaskVisuals.paintBlock(g2, p.t, p.r.x, p.r.y, p.r.width, p.r.height, p == hover && !ghosted, false, sub);
                if (ghosted) {
                    g2.setColor(Gfx.alpha(Theme.surface, 0.55));
                    g2.fill(Gfx.rr(p.r.x, p.r.y, p.r.width, p.r.height, 8));
                }
            }
            for (Placed p : placed) {
                if (!p.deadline) continue;
                p.r = rectOf(p, w);
                Urgency u = AppContext.urgency.evaluate(p.t);
                Color c = Theme.urgencyColor(u.level());
                g2.setColor(Gfx.alpha(c, 0.5));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0));
                g2.drawLine(GUTTER + p.col * cw + 4, p.r.y + 8, GUTTER + (p.col + 1) * cw - 4, p.r.y + 8);
                Icons.paint(g2, Icons.Type.FLAG, p.r.x, p.r.y, 16, c);
            }

            if (ghostStart != null && ghostEnd != null) {
                long off = java.time.temporal.ChronoUnit.DAYS.between(first, ghostStart.toLocalDate());
                if (off >= 0 && off < days) {
                    int c = (int) off;
                    int sm = ghostStart.toLocalTime().toSecondOfDay() / 60;
                    int em = ghostEnd.toLocalTime().equals(LocalTime.MIDNIGHT) ? 1440 : ghostEnd.toLocalTime().toSecondOfDay() / 60;
                    int y = (int) (sm * HOUR_H / 60.0) + 1;
                    int hh = Math.max(18, (int) ((em - sm) * HOUR_H / 60.0) - 2);
                    int x = GUTTER + c * cw + 4;
                    String sub = Time.range(ghostStart, ghostEnd);
                    if (ghostTask != null) {
                        TaskVisuals.paintBlock(g2, ghostTask, x, y, cw - 8, hh, false, true, sub);
                        g2.setColor(Theme.accent);
                        g2.setStroke(new BasicStroke(1.6f));
                        g2.draw(Gfx.rr(x, y, cw - 8, hh, 8));
                    } else {
                        g2.setColor(Gfx.alpha(Theme.accent, 0.22));
                        g2.fill(Gfx.rr(x, y, cw - 8, hh, 8));
                        g2.setColor(Theme.accent);
                        g2.setStroke(new BasicStroke(1.6f));
                        g2.draw(Gfx.rr(x, y, cw - 8, hh, 8));
                        Gfx.text(g2, "New task", x + 10, y + 15, Theme.semi(12f), Theme.accent);
                        if (hh >= 34) Gfx.text(g2, sub, x + 10, y + 30, Theme.font(11f), Theme.accent);
                    }
                }
            }

            // current time
            LocalDateTime now = LocalDateTime.now();
            long off = java.time.temporal.ChronoUnit.DAYS.between(first, now.toLocalDate());
            if (off >= 0 && off < days) {
                int y = (int) (now.toLocalTime().toSecondOfDay() / 60.0 * HOUR_H / 60.0);
                g2.setColor(Gfx.alpha(Theme.danger, 0.35));
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(GUTTER, y, w, y);
                g2.setColor(Theme.danger);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(GUTTER + (int) off * cw, y, GUTTER + (int) (off + 1) * cw, y);
                g2.fill(new java.awt.geom.Ellipse2D.Double(GUTTER + off * cw - 5, y - 5, 10, 10));
                String tl = Time.time(now);
                FontMetrics fm = g2.getFontMetrics(Theme.semi(10f));
                int tw = fm.stringWidth(tl) + 8;
                g2.fill(Gfx.rr(GUTTER - tw - 6, y - 8, tw, 16, 8));
                Gfx.text(g2, tl, GUTTER - tw - 2, y + 4, Theme.semi(10f), Color.WHITE);
            }
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 28; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return HOUR_H * 4; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
