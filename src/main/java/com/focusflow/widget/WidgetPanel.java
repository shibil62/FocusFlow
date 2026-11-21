package com.focusflow.widget;

import com.focusflow.animations.Animator;
import com.focusflow.animations.SpringValue;
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
import com.focusflow.widget.WidgetPalette.Palette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;

/**
 * The widget itself. Point at a date and the dates around it swell like a lens; the task bubbles for that day
 * pop up on the left. The window can be resized freely: as it gets smaller, details are dropped
 * (first the task bubbles and the big date, then the weekday row and arrows) so it always stays readable.
 * Everything is drawn in logical "card" units and scaled to the window.
 */
final class WidgetPanel extends JComponent {
    static final int M = 14;                    // transparent margin around the card (room for the shadow)
    private static final int FULL = 0, COMPACT = 1, MINI = 2;

    private enum Kind { BUBBLE, CHECK, MORE, ADD, NEWBTN, PREV, NEXT, MONTH }

    private record Hit(Kind kind, Shape shape, LocalDate date, Task task) {}

    private final WidgetWindow win;
    private Palette pal = WidgetPalette.get("Midnight");
    private double bgOpacity = 0.92;

    // layout, recomputed from the window size
    private double s = 1, W = 560, H = 300, leftW = 244, rx = 266, rw = 280, gridY = 80;
    private int tier = FULL;
    private int lastPxW = -1, lastPxH = -1;

    private LocalDate today = Time.today();
    private LocalDate focus, lastFocus, hoverDate, pendingDate;
    private int monthOffset, page;
    private long focusStartNs = System.nanoTime();
    private final SpringValue f = new SpringValue(0, 0.5, 0.78, this::repaint);
    private final SpringValue fx = new SpringValue(0, 0.38, 0.82, this::repaint);
    private final SpringValue fy = new SpringValue(0, 0.38, 0.82, this::repaint);

    private final Map<LocalDate, List<Task>> byDay = new HashMap<>();
    private final Set<Long> hot = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private Hit hover;

    private final javax.swing.Timer anim = new javax.swing.Timer(33, e -> repaint());
    private final javax.swing.Timer clock = new javax.swing.Timer(30_000, e -> refreshData());
    // Pointing at a date only focuses it after a short rest, so sweeping across the grid does not make it flicker.
    private final javax.swing.Timer dwell = new javax.swing.Timer(110, e -> { if (pendingDate != null) focusOn(pendingDate); });
    private final javax.swing.Timer leave = new javax.swing.Timer(380, e -> focusOn(null));
    private final Runnable listener = this::refreshData;
    private int savedTipDelay = -1;

    private Point pressScreen;
    private Rectangle pressBounds;
    private boolean dragged;
    private int resizeEdges;   // bit 1 = left, 2 = right, 4 = top, 8 = bottom

    WidgetPanel(WidgetWindow win) {
        this.win = win;
        setOpaque(false);
        dwell.setRepeats(false);
        leave.setRepeats(false);
        ToolTipManager.sharedInstance().registerComponent(this);
        AppContext.tasks.addListener(listener);
        AppContext.categories.addListener(listener);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                leave.stop();
                ToolTipManager tm = ToolTipManager.sharedInstance();
                if (savedTipDelay < 0) { savedTipDelay = tm.getInitialDelay(); tm.setInitialDelay(250); }
            }
            @Override public void mousePressed(MouseEvent e) {
                pressScreen = e.getLocationOnScreen();
                pressBounds = win.getBounds();
                dragged = false;
                resizeEdges = SwingUtilities.isLeftMouseButton(e) ? edgesAt(e.getX(), e.getY()) : 0;
                if (e.isPopupTrigger()) menu(e);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (pressScreen == null || !SwingUtilities.isLeftMouseButton(e)) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - pressScreen.x, dy = now.y - pressScreen.y;
                if (!dragged && Math.abs(dx) + Math.abs(dy) < 4) return;
                dragged = true;
                if (resizeEdges != 0) { resizeTo(dx, dy); return; }
                win.setLocation(pressBounds.x + dx, pressBounds.y + dy);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { menu(e); return; }
                if (dragged) { win.saveBounds(); dragged = false; resizeEdges = 0; return; }
                resizeEdges = 0;
                if (SwingUtilities.isLeftMouseButton(e)) click(hitAt(e));
            }
            @Override public void mouseMoved(MouseEvent e) { pointerMoved(e); }
            @Override public void mouseExited(MouseEvent e) {
                hover = null;
                hoverDate = null;
                dwell.stop();
                pendingDate = null;
                leave.restart();
                if (savedTipDelay >= 0) { ToolTipManager.sharedInstance().setInitialDelay(savedTipDelay); savedTipDelay = -1; }
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { shiftMonth(e.getWheelRotation() > 0 ? 1 : -1); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        f.onSettled(this::updateAnim);
        clock.start();
        refreshData();
    }

    void shutdown() {
        anim.stop();
        clock.stop();
        dwell.stop();
        leave.stop();
        if (savedTipDelay >= 0) ToolTipManager.sharedInstance().setInitialDelay(savedTipDelay);
        AppContext.tasks.removeListener(listener);
        AppContext.categories.removeListener(listener);
    }

    // ----------------------------------------------------------------- settings

    void applySettings() {
        SettingsService st = AppContext.settings;
        pal = WidgetPalette.get(st.get(SettingsService.WIDGET_PALETTE, "Midnight"));
        bgOpacity = Math.max(0.35, Math.min(1.0, st.getInt(SettingsService.WIDGET_OPACITY, 92) / 100.0));
        refreshData();
    }

    // ------------------------------------------------------------------- layout

    /** Works out scale and which details fit, from the current window size. */
    private void computeLayout() {
        int ww = Math.max(1, getWidth()), wh = Math.max(1, getHeight());
        s = clamp(Math.min(ww / (560.0 + 2 * M), wh / (300.0 + 2 * M)), 0.78, 4.0);
        W = ww / s - 2 * M;
        H = wh / s - 2 * M;
        tier = (W >= 480 && H >= 260) ? FULL : (W >= 235 && H >= 175) ? COMPACT : MINI;
        switch (tier) {
            case FULL -> {
                leftW = clamp(W * 0.435, 200, 340);
                rx = leftW + 22;
                rw = W - rx - 14;
                gridY = 80;
            }
            case COMPACT -> { leftW = 0; rx = 16; rw = W - 32; gridY = 70; }
            default -> { leftW = 0; rx = 8; rw = W - 16; gridY = 34; }
        }
        if (ww != lastPxW || wh != lastPxH) {
            lastPxW = ww;
            lastPxH = wh;
            if (focus != null) {
                Point2D.Double c = cellCenter(focus);
                fx.snap(c.x);
                fy.snap(c.y);
            }
        }
    }

    private LocalDate monthShown() { return YearMonth.from(today).plusMonths(monthOffset).atDay(1); }

    private DayOfWeek weekStart() {
        return DayOfWeek.valueOf(AppContext.settings.get(SettingsService.WEEK_START, "MONDAY"));
    }

    private LocalDate gridStart() { return Time.weekStart(monthShown(), weekStart()); }

    private int rows() {
        LocalDate first = monthShown();
        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(gridStart(), first);
        return (int) Math.ceil((offset + first.lengthOfMonth()) / 7.0);
    }

    private double cellW() { return rw / 7.0; }

    private double cellH() {
        double bottomPad = tier == FULL ? 16 : tier == COMPACT ? 12 : 8;
        return Math.max(14, (H - gridY - bottomPad) / rows());
    }

    /** How big the day numbers are drawn, so they shrink and grow with the cells. */
    private double cellScale() { return clamp(Math.min(cellW() / 40.0, cellH() / 34.0), 0.5, 1.5); }

    private Point2D.Double cellCenter(LocalDate d) {
        int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(gridStart(), d);
        return new Point2D.Double(rx + (idx % 7 + 0.5) * cellW(), gridY + (idx / 7 + 0.5) * cellH());
    }

    /** The date under a point, using the plain (un-zoomed) grid so the lens cannot make the pointer jitter. */
    private LocalDate baseDateAt(double x, double y) {
        double cw = cellW(), ch = cellH();
        if (x < rx || x >= rx + rw || y < gridY || y >= gridY + ch * rows()) return null;
        int col = (int) ((x - rx) / cw), row = (int) ((y - gridY) / ch);
        LocalDate d = gridStart().plusDays(row * 7L + col);
        return d.getMonth() == monthShown().getMonth() ? d : null;
    }

    // --------------------------------------------------------------------- data

    private void refreshData() {
        today = Time.today();
        byDay.clear();
        hot.clear();
        LocalDate start = gridStart();
        for (int i = 0; i < 42; i++) {
            LocalDate d = start.plusDays(i);
            byDay.put(d, AppContext.tasks.forDay(d));
        }
        if (focus != null && !byDay.containsKey(focus)) byDay.put(focus, AppContext.tasks.forDay(focus));
        if (lastFocus != null && !byDay.containsKey(lastFocus)) byDay.put(lastFocus, AppContext.tasks.forDay(lastFocus));
        for (List<Task> l : byDay.values()) {
            for (Task t : l) {
                if (t.isCompleted() || t.deadline == null) continue;
                Urgency.Level lv = AppContext.urgency.evaluate(t).level();
                if (lv == Urgency.Level.CRITICAL || lv == Urgency.Level.OVERDUE) hot.add(t.id);
            }
        }
        updateAnim();
        repaint();
    }

    private void updateAnim() {
        boolean zooming = focus != null || f.value > 0.01;
        boolean need = !Animator.reduceMotion && (zooming || !hot.isEmpty());
        // smooth while zooming, gentle (10 frames a second) when only an urgent dot is pulsing
        int delay = zooming ? 33 : 100;
        if (anim.getDelay() != delay) anim.setDelay(delay);
        if (need && !anim.isRunning()) anim.start();
        if (!need && anim.isRunning()) anim.stop();
    }

    private List<Task> tasksOn(LocalDate d) { return byDay.getOrDefault(d, List.of()); }

    // -------------------------------------------------------------- interaction

    private void focusOn(LocalDate d) {
        if (d == null) {
            focus = null;
            f.to(0);
        } else {
            computeLayout();
            Point2D.Double c = cellCenter(d);
            boolean first = focus == null && f.value < 0.02;
            if (!d.equals(focus)) { page = 0; focusStartNs = System.nanoTime(); }
            focus = d;
            lastFocus = d;
            if (first) { fx.snap(c.x); fy.snap(c.y); } else { fx.to(c.x); fy.to(c.y); }
            if (!byDay.containsKey(d)) byDay.put(d, AppContext.tasks.forDay(d));
            f.to(1);
        }
        updateAnim();
        repaint();
    }

    private void shiftMonth(int delta) {
        monthOffset += delta;
        focusOn(null);
        refreshData();
    }

    private void pointerMoved(MouseEvent e) {
        computeLayout();
        double x = e.getX() / s - M, y = e.getY() / s - M;
        leave.stop();

        int edges = edgesAt(e.getX(), e.getY());
        if (edges != 0) {
            setCursor(Cursor.getPredefinedCursor(cursorFor(edges)));
            hover = null;
            hoverDate = null;
            dwell.stop();
            repaint();
            return;
        }

        LocalDate d = baseDateAt(x, y);
        if (!Objects.equals(d, hoverDate)) {
            hoverDate = d;
            repaint();
        }
        if (d != null) {
            if (!d.equals(focus) && !d.equals(pendingDate)) {
                pendingDate = d;
                dwell.setInitialDelay(focus == null ? 70 : 110);
                dwell.restart();
            } else if (d.equals(focus)) {
                dwell.stop();
                pendingDate = null;
            }
        } else {
            dwell.stop();
            pendingDate = null;
        }

        Hit h = hitAt(e);
        hover = h;
        setCursor(h != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    private void click(Hit h) {
        if (h == null) return;
        switch (h.kind) {
            case PREV -> shiftMonth(-1);
            case NEXT -> shiftMonth(1);
            case MONTH -> { monthOffset = 0; focusOn(null); refreshData(); }
            case MORE -> { page++; focusStartNs = System.nanoTime() - 200_000_000L; repaint(); }
            case ADD, NEWBTN -> QuickTaskDialog.create(h.date != null ? h.date : (focus != null ? focus : today));
            case CHECK -> QuickTaskDialog.toggle(h.task);
            case BUBBLE -> QuickTaskDialog.view(h.task);
        }
    }

    private static void openApp() { AppContext.showMain(); }

    private Hit hitAt(MouseEvent e) {
        double x = e.getX() / s - M, y = e.getY() / s - M;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.shape.contains(x, y)) return h;
        }
        return null;
    }

    // ---- resizing from the edges and corners

    private int edgesAt(int px, int py) {
        double ring = Math.max(10, M * s) + 2;
        int e = 0;
        if (px < ring) e |= 1;
        if (px > getWidth() - ring) e |= 2;
        if (py < ring) e |= 4;
        if (py > getHeight() - ring) e |= 8;
        return e;
    }

    private static int cursorFor(int e) {
        return switch (e) {
            case 1 -> Cursor.W_RESIZE_CURSOR;
            case 2 -> Cursor.E_RESIZE_CURSOR;
            case 4 -> Cursor.N_RESIZE_CURSOR;
            case 8 -> Cursor.S_RESIZE_CURSOR;
            case 5 -> Cursor.NW_RESIZE_CURSOR;
            case 6 -> Cursor.NE_RESIZE_CURSOR;
            case 9 -> Cursor.SW_RESIZE_CURSOR;
            case 10 -> Cursor.SE_RESIZE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
    }

    private void resizeTo(int dx, int dy) {
        Rectangle b = new Rectangle(pressBounds);
        if ((resizeEdges & 1) != 0) { b.x += dx; b.width -= dx; }
        if ((resizeEdges & 2) != 0) b.width += dx;
        if ((resizeEdges & 4) != 0) { b.y += dy; b.height -= dy; }
        if ((resizeEdges & 8) != 0) b.height += dy;
        if (b.width < WidgetWindow.MIN_W) { if ((resizeEdges & 1) != 0) b.x -= WidgetWindow.MIN_W - b.width; b.width = WidgetWindow.MIN_W; }
        if (b.height < WidgetWindow.MIN_H) { if ((resizeEdges & 4) != 0) b.y -= WidgetWindow.MIN_H - b.height; b.height = WidgetWindow.MIN_H; }
        b.width = Math.min(b.width, WidgetWindow.MAX_W);
        b.height = Math.min(b.height, WidgetWindow.MAX_H);
        win.setBounds(b);
    }

    // ---- tooltip (used when the widget is too small to show bubbles)

    @Override public String getToolTipText(MouseEvent e) {
        computeLayout();
        Hit h = hitAt(e);
        if (h != null && h.kind == Kind.BUBBLE) return h.task.title;
        if (tier == FULL) return null;
        LocalDate d = baseDateAt(e.getX() / s - M, e.getY() / s - M);
        if (d == null) return null;
        StringBuilder sb = new StringBuilder("<html><b>").append(Time.dateLong(d)).append("</b>");
        List<Task> l = tasksOn(d);
        if (l.isEmpty()) sb.append("<br>Nothing planned");
        int n = 0;
        for (Task t : l) {
            if (n++ >= 6) { sb.append("<br>+ ").append(l.size() - 6).append(" more"); break; }
            String title = t.title.length() > 36 ? t.title.substring(0, 35) + "…" : t.title;
            sb.append("<br>").append(t.isCompleted() ? "✓ " : "• ").append(title.replace("&", "&amp;").replace("<", "&lt;"))
                    .append("  <i>").append(label(t, d)).append("</i>");
        }
        return sb.append("</html>").toString();
    }

    // ---- right-click menu

    private void menu(MouseEvent e) {
        JPopupMenu m = new JPopupMenu();
        LocalDate target = hoverDate != null ? hoverDate : (focus != null ? focus : today);
        Hit under = hitAt(e);
        if (under != null && (under.kind == Kind.BUBBLE || under.kind == Kind.CHECK) && under.task != null) {
            Task t = under.task;
            add(m, t.isCompleted() ? "Reopen" : "Mark done", () -> QuickTaskDialog.toggle(t));
            add(m, "View or rename", () -> QuickTaskDialog.view(t));
            add(m, "Open in the app", () -> AppContext.inMain(() -> TaskActions.open(t)));
            m.addSeparator();
        }
        add(m, "New task on " + Time.dateShort(target), () -> QuickTaskDialog.create(target));
        add(m, "Open FocusFlow", () -> openApp());
        add(m, "Go to today", () -> { monthOffset = 0; refreshData(); focusOn(today); });
        m.addSeparator();
        JMenu colours = new JMenu("Colour");
        String cur = AppContext.settings.get(SettingsService.WIDGET_PALETTE, "Midnight");
        for (String n : WidgetPalette.names()) {
            JCheckBoxMenuItem it = new JCheckBoxMenuItem(n, n.equals(cur));
            it.addActionListener(a -> AppContext.settings.set(SettingsService.WIDGET_PALETTE, n));
            colours.add(it);
        }
        m.add(colours);
        JMenu op = new JMenu("Transparency");
        int curOp = AppContext.settings.getInt(SettingsService.WIDGET_OPACITY, 92);
        for (int v : new int[]{100, 92, 80, 65, 50}) {
            JCheckBoxMenuItem it = new JCheckBoxMenuItem(v + "% solid", Math.abs(v - curOp) < 4);
            it.addActionListener(a -> AppContext.settings.set(SettingsService.WIDGET_OPACITY, v));
            op.add(it);
        }
        m.add(op);
        add(m, "Reset size", () -> {
            AppContext.settings.removeQuiet(SettingsService.WIDGET_W);
            AppContext.settings.removeQuiet(SettingsService.WIDGET_H);
            WidgetController.sync();
        });
        m.addSeparator();
        add(m, "Hide widget", () -> WidgetController.setEnabled(false));
        add(m, "Quit FocusFlow", AppContext::quit);
        m.show(this, e.getX(), e.getY());
    }

    private static void add(JPopupMenu m, String text, Runnable r) {
        JMenuItem it = new JMenuItem(text);
        it.addActionListener(a -> r.run());
        m.add(it);
    }

    // ----------------------------------------------------------------- painting

    private final Map<Long, Font> fontCache = new HashMap<>();

    /** Fonts at fractional sizes, made once instead of on every animation frame. */
    private Font fnt(int style, float size) {
        long key = ((long) style << 32) | Math.round(size * 4);
        return fontCache.computeIfAbsent(key, k -> (style == 0 ? Theme.font(10f) : style == 1 ? Theme.semi(10f) : Theme.bold(10f)).deriveFont(size));
    }

    private Color onAccent() { return Theme.lum(pal.accent()) > 0.6 ? new Color(0x14141A) : Color.WHITE; }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static double easeOutBack(double t) {
        double c1 = 1.70158, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        computeLayout();
        Graphics2D g = Gfx.aa(g0);
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        g.scale(s, s);
        hits.clear();

        double WW = W + 2 * M, HH = H + 2 * M;
        double arc = tier == MINI ? 22 : 34;
        var card = Gfx.rr(M, M, W, H, arc);
        Graphics2D sh = (Graphics2D) g.create();
        Area outside = new Area(new Rectangle2D.Double(0, 0, WW, HH));
        outside.subtract(new Area(card));
        sh.clip(outside);
        Gfx.shadow(sh, M, M, W, H, arc, M - 2, 0.30);
        sh.dispose();
        g.setColor(Gfx.alpha(pal.bg(), bgOpacity));
        g.fill(card);
        g.setColor(Gfx.alpha(pal.text(), 0.10));
        g.setStroke(new BasicStroke(1f));
        g.draw(Gfx.rr(M + 0.5, M + 0.5, W - 1, H - 1, arc));

        Graphics2D c = (Graphics2D) g.create();
        c.translate(M, M);
        c.clip(Gfx.rr(0, 0, W, H, arc));
        if (tier == FULL) paintLeft(c);
        paintMonth(c);
        c.dispose();
        g.dispose();
    }

    // ---- left zone (big date + bubbles), only when there is room

    private void paintLeft(Graphics2D g) {
        double k = clamp(f.value, 0, 1.05);
        double kc = clamp(f.value, 0, 1);
        boolean focused = focus != null || f.value > 0.02;
        LocalDate d = focused && lastFocus != null ? lastFocus : today;
        List<Task> list = tasksOn(d);
        int open = 0, urgent = 0;
        for (Task t : list) {
            if (!t.isCompleted()) open++;
            if (hot.contains(t.id)) urgent++;
        }

        int px = 30;
        double hs = lerp(34, 20, k), hy = lerp(52, 40, k);
        hs = Math.min(hs, (leftW - 90) / 4.4);
        String wd = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String mo = d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        g.setFont(fnt(1, (float) hs));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(pal.accent());
        g.drawString(wd, px, (float) hy);
        g.setColor(pal.text2());
        g.drawString(" " + mo, px + fm.stringWidth(wd), (float) hy);

        String num = String.valueOf(d.getDayOfMonth());
        double bigMax = Math.min(150, (leftW - 20) / (num.length() * 0.62));
        double ns = lerp(clamp(Math.min(H * 0.5, bigMax), 70, 150), 70, k), ny = lerp(H - 64, 112, k);
        g.setFont(fnt(1, (float) ns));
        FontMetrics nm = g.getFontMetrics();
        g.setColor(pal.text());
        g.drawString(num, px - 4, (float) ny);
        int numW = nm.stringWidth(num);

        String summary = open == 0 ? (list.isEmpty() ? "Nothing planned" : "All done") : open + (open == 1 ? " task" : " tasks") + (focused ? "" : " today");
        double sx = lerp(px, px + numW + 12, k), sy = lerp(H - 28, 88, k);
        g.setFont(fnt(0, (float) lerp(15, 16, k)));
        g.setColor(pal.text2());
        g.drawString(summary, (float) sx, (float) sy);
        if (urgent > 0) {
            g.setFont(fnt(1, 14f));
            g.setColor(Theme.danger);
            if (kc > 0.5) g.drawString(urgent + " urgent", (float) sx, (float) (sy + 20));
            else g.drawString(urgent + " urgent", (float) (sx + g.getFontMetrics(fnt(0, 15f)).stringWidth(summary) + 10), (float) sy);
        }

        // "+" button: quick-add a task for this day without opening the app
        double bx = leftW - 40, by = 14;
        boolean hvp = hover != null && hover.kind == Kind.NEWBTN;
        g.setColor(Gfx.alpha(pal.accent(), hvp ? 0.34 : 0.20));
        g.fill(new Ellipse2D.Double(bx, by, 28, 28));
        g.setColor(pal.accent());
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(bx + 8.5, by + 14, bx + 19.5, by + 14));
        g.draw(new java.awt.geom.Line2D.Double(bx + 14, by + 8.5, bx + 14, by + 19.5));
        hits.add(new Hit(Kind.NEWBTN, new Ellipse2D.Double(bx, by, 28, 28), d, null));

        if (kc < 0.02) return;
        paintBubbles(g, d, list, kc);
    }

    private void paintBubbles(Graphics2D g, LocalDate d, List<Task> list, double alpha) {
        double nowSec = System.nanoTime() / 1e9;
        double sinceMs = (System.nanoTime() - focusStartNs) / 1e6;
        boolean still = Animator.reduceMotion;
        Graphics2D a = (Graphics2D) g.create();
        a.setComposite(AlphaComposite.SrcOver.derive((float) clamp(alpha, 0, 1)));

        int px = 30, y0 = 138;
        if (list.isEmpty()) {
            pill(a, px, y0, "Nothing planned", pal.text2(), Gfx.alpha(pal.text(), 0.07));
            if (H >= 262) {
                Rectangle2D.Double r = new Rectangle2D.Double(px + 8, y0 + 44, 130, 30);
                boolean hv = hover != null && hover.kind == Kind.ADD;
                a.setColor(Gfx.alpha(pal.accent(), hv ? 0.34 : 0.22));
                a.fill(Gfx.rr(r.x, r.y, r.width, r.height, 30));
                a.setFont(Theme.semi(13f));
                a.setColor(pal.accent());
                a.drawString("+  Add a task", (float) (r.x + 16), (float) (r.y + 20));
                hits.add(new Hit(Kind.ADD, new Rectangle2D.Double(r.x, r.y, r.width, r.height), d, null));
            }
            a.dispose();
            return;
        }

        // how many bubbles fit under the date; the rest go on extra "pages"
        int fit = (int) Math.max(1, Math.min(6, (H - y0 - 10) / 34));
        int per = list.size() <= fit ? list.size() : (int) Math.max(1, Math.min(6, (H - y0 - 34) / 34));
        int pages = (list.size() + per - 1) / per;
        int pg = Math.floorMod(page, pages);
        int from = pg * per, to = Math.min(list.size(), from + per);
        for (int i = from; i < to; i++) {
            Task t = list.get(i);
            int slot = i - from;
            double tt = still ? 1 : clamp((sinceMs - 70.0 * slot) / 450.0, 0, 1);
            if (tt <= 0) continue;
            double sc = still ? 1 : easeOutBack(tt);
            double bob = still ? 0 : Math.sin(nowSec * 1.7 + slot * 1.3) * 1.8;
            double x = px + (slot % 2 == 1 ? 12 : 0), y = y0 + slot * 34 + bob;
            Rectangle2D.Double r = bubble(a, t, d, x, y, sc, tt);
            if (tt > 0.85) {
                hits.add(new Hit(Kind.BUBBLE, r, d, t));
                hits.add(new Hit(Kind.CHECK, new Ellipse2D.Double(r.x + 2, r.y + 2, 26, 26), d, t));
            }
        }
        if (pages > 1) {
            a.setFont(Theme.semi(12f));
            a.setColor(pal.accent());
            int yy = y0 + per * 34 + 14;
            a.drawString("Page " + (pg + 1) + " of " + pages + "  ·  click for more", px + 6, yy);
            hits.add(new Hit(Kind.MORE, new Rectangle2D.Double(px, yy - 14, 190, 20), d, null));
        }
        a.dispose();
    }

    /** Draws one task bubble and returns its (unscaled) bounds. */
    private Rectangle2D.Double bubble(Graphics2D g0, Task t, LocalDate d, double x, double y, double sc, double tt) {
        Graphics2D g = (Graphics2D) g0.create();
        Color base = TaskVisuals.base(t);
        boolean done = t.isCompleted(), isHot = hot.contains(t.id);
        String label = label(t, d);
        Font tf = Theme.semi(13f), lf = Theme.font(11f);
        FontMetrics tm = g.getFontMetrics(tf), lm = g.getFontMetrics(lf);
        int lw = lm.stringWidth(label);
        int h = 30, maxW = (int) (leftW - 6 - x);
        int want = 14 + 8 + 12 + tm.stringWidth(t.title) + 10 + lw + 14 + (isHot ? 16 : 0);
        int w = Math.min(maxW, Math.max(120, want));
        double cx = x, cy = y + h / 2.0;
        g.translate(cx, cy);
        g.scale(sc, sc);
        g.translate(-cx, -cy);
        g.setComposite(AlphaComposite.SrcOver.derive((float) clamp(tt * 1.6, 0, 1)));

        g.setColor(Gfx.mix(pal.bg(), base, done ? 0.14 : 0.30));
        g.fill(Gfx.rr(x, y, w, h, h));
        double pulse = isHot && !Animator.reduceMotion ? 0.55 + 0.45 * Math.sin(System.nanoTime() / 1e9 * 4.0) : 1;
        g.setStroke(new BasicStroke(isHot ? 1.6f : 1f));
        g.setColor(isHot ? Gfx.alpha(Theme.danger, 0.9 * pulse) : Gfx.alpha(base, 0.45));
        g.draw(Gfx.rr(x + 0.5, y + 0.5, w - 1, h - 1, h));

        g.setColor(done ? Gfx.alpha(base, 0.6) : base);
        g.fill(new Ellipse2D.Double(x + 8, y + 8, 14, 14));
        if (done) Icons.paint(g, Icons.Type.CHECK, x + 10, y + 10, 10, Color.WHITE);

        int avail = w - 14 - 8 - 12 - 10 - lw - 14 - (isHot ? 16 : 0);
        String title = Gfx.fit(t.title, tm, avail);
        g.setFont(tf);
        g.setColor(done ? pal.text2() : pal.text());
        int tx = (int) x + 32, ty = (int) y + 19;
        g.drawString(title, tx, ty);
        if (done) g.drawLine(tx, ty - 4, tx + tm.stringWidth(title), ty - 4);
        g.setFont(lf);
        g.setColor(isHot ? Theme.danger : pal.text2());
        g.drawString(label, (float) (x + w - 14 - lw - (isHot ? 16 : 0)), (float) (y + 19));
        if (isHot) Icons.paint(g, Icons.Type.FLAME, x + w - 28, y + 8, 14, Theme.danger);
        g.dispose();
        return new Rectangle2D.Double(x, y, w, h);
    }

    private static String label(Task t, LocalDate d) {
        if (t.isCompleted()) return "Done";
        if (t.isDayEvent()) return d.equals(Time.today()) ? "Today" : "";
        if (t.start == null) return t.isDueDayOnly() ? (d.equals(Time.today()) ? "Due today" : "Due") : "Due " + Time.time(t.deadlineDT());
        if (t.allDay) return "All day";
        if (!t.scheduledDate().equals(d)) return "Continues";
        return Time.time(t.startDT());
    }

    private void pill(Graphics2D g, int x, int y, String text, Color fg, Color bg) {
        g.setFont(Theme.semi(13f));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 32;
        g.setColor(bg);
        g.fill(Gfx.rr(x, y, w, 30, 30));
        g.setColor(fg);
        g.drawString(text, x + 16, y + 20);
    }

    // ---- the month

    private void paintMonth(Graphics2D g) {
        LocalDate first = monthShown();
        boolean thisYear = first.getYear() == today.getYear();
        String title;
        if (tier == MINI) {
            title = first.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
        } else {
            title = first.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ENGLISH) + (thisYear ? "" : " " + first.getYear());
        }
        float tsz = tier == FULL ? 17f : tier == COMPACT ? 15f : 12f;
        double ty = tier == FULL ? 38 : tier == COMPACT ? 32 : 22;
        g.setFont(fnt(2, tsz));
        g.setColor(pal.accent());
        g.drawString(title, (float) rx, (float) ty);
        FontMetrics tm = g.getFontMetrics();
        hits.add(new Hit(Kind.MONTH, new Rectangle2D.Double(rx - 4, ty - 18, tm.stringWidth(title) + 8, 24), null, null));

        if (tier == COMPACT && tm.stringWidth(title) < rw - 120) {
            double ny = ty - 18, bx = rx + rw - 82;
            boolean hvp = hover != null && hover.kind == Kind.NEWBTN;
            g.setColor(Gfx.alpha(pal.accent(), hvp ? 0.34 : 0.20));
            g.fill(new Ellipse2D.Double(bx, ny, 24, 24));
            g.setColor(pal.accent());
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(bx + 7, ny + 12, bx + 17, ny + 12));
            g.draw(new java.awt.geom.Line2D.Double(bx + 12, ny + 7, bx + 12, ny + 17));
            hits.add(new Hit(Kind.NEWBTN, new Ellipse2D.Double(bx, ny, 24, 24), focus != null ? focus : today, null));
        }
        if (tier != MINI) {
            double ny = ty - 18;
            Shape prev = new Ellipse2D.Double(rx + rw - 52, ny, 24, 24), next = new Ellipse2D.Double(rx + rw - 24, ny, 24, 24);
            for (Object[] b : new Object[][]{{prev, Kind.PREV, Icons.Type.CHEV_L}, {next, Kind.NEXT, Icons.Type.CHEV_R}}) {
                Shape sp = (Shape) b[0];
                boolean hv = hover != null && hover.kind == b[1];
                if (hv) { g.setColor(Gfx.alpha(pal.text(), 0.12)); g.fill(sp); }
                Rectangle2D r = sp.getBounds2D();
                Icons.paint(g, (Icons.Type) b[2], r.getX() + 5, r.getY() + 5, 14, pal.text2());
                hits.add(new Hit((Kind) b[1], sp, null, null));
            }
        }

        double cw = cellW(), ch = cellH(), cs = cellScale();
        if (tier != MINI) {
            DayOfWeek ws = weekStart();
            g.setFont(fnt(1, (float) clamp(13 * cs, 10, 16)));
            for (int i = 0; i < 7; i++) {
                DayOfWeek dw = ws.plus(i);
                boolean we = dw == DayOfWeek.SATURDAY || dw == DayOfWeek.SUNDAY;
                String chs = dw.getDisplayName(TextStyle.NARROW, Locale.ENGLISH);
                g.setColor(we ? pal.text2() : pal.text());
                g.drawString(chs, (float) (rx + (i + 0.5) * cw - g.getFontMetrics().stringWidth(chs) / 2.0), (float) (gridY - 12));
            }
        }

        double k = clamp(f.value, 0, 1.08);
        boolean lens = k > 0.004;
        double lx = fx.value, ly = fy.value;
        LocalDate start = gridStart();

        record Cell(LocalDate d, double x, double y, double mag, double alpha) {}
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < rows() * 7; i++) {
            LocalDate d = start.plusDays(i);
            if (d.getMonth() != first.getMonth()) continue;
            Point2D.Double c = cellCenter(d);
            double mag = 1, px = c.x, py = c.y, al = 1;
            if (lens && lastFocus != null) {
                double dx = (c.x - lx) / cw, dy = (c.y - ly) / ch, d2 = dx * dx + dy * dy;
                mag = 1 + k * 1.1 * Math.exp(-d2 / (2 * 0.85 * 0.85));
                double push = 1 + k * 0.40 * Math.exp(-d2 / (2 * 1.05 * 1.05));
                px = lx + (c.x - lx) * push;
                py = ly + (c.y - ly) * push;
                double rad = 14 * mag * cs;
                px = clamp(px, rx - 4 + rad, rx + rw + 4 - rad);
                py = clamp(py, gridY - 4 + rad, H - 4 - rad);
                al = 1 - 0.38 * Math.min(1, k) * (1 - Math.exp(-d2 / (2 * 1.6 * 1.6)));
            }
            cells.add(new Cell(d, px, py, mag, al));
        }
        cells.sort(Comparator.comparingDouble(Cell::mag));
        for (Cell c : cells) paintDay(g, c.d, c.x, c.y, c.mag * cs, c.alpha, cw, ch);
    }

    /** {@code mag} already includes the size of the cell, so numbers stay in proportion at any window size. */
    private void paintDay(Graphics2D g0, LocalDate d, double x, double y, double mag, double alpha, double cw, double ch) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setComposite(AlphaComposite.SrcOver.derive((float) clamp(alpha, 0, 1)));
        boolean isToday = d.equals(today), sel = d.equals(focus);
        boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean hv = d.equals(hoverDate) && !sel;
        double r = 14.5;

        g.translate(x, y);
        g.scale(mag, mag);
        double ny = -3;
        if (isToday) {
            g.setColor(pal.accent());
            g.fill(new Ellipse2D.Double(-r, ny - r, r * 2, r * 2));
        } else if (sel) {
            g.setColor(Gfx.alpha(pal.accent(), 0.20));
            g.fill(new Ellipse2D.Double(-r, ny - r, r * 2, r * 2));
        } else if (hv) {
            g.setColor(Gfx.alpha(pal.text(), 0.12));
            g.fill(new Ellipse2D.Double(-r, ny - r, r * 2, r * 2));
        }
        if (sel) {
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(isToday ? Gfx.alpha(onAccent(), 0.85) : pal.accent());
            g.draw(new Ellipse2D.Double(-r + 0.8, ny - r + 0.8, r * 2 - 1.6, r * 2 - 1.6));
        }
        String str = String.valueOf(d.getDayOfMonth());
        g.setFont(isToday || sel ? fnt(1, 15f) : fnt(0, 15f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(isToday ? onAccent() : weekend ? pal.text2() : pal.text());
        g.drawString(str, (float) (-fm.stringWidth(str) / 2.0), (float) (ny + (fm.getAscent() - fm.getDescent()) / 2.0));

        // task dots (dropped when the cells get too small to hold them)
        List<Task> list = tasksOn(d);
        if (!list.isEmpty() && ch >= 17) {
            int n = Math.min(3, list.size());
            double gap = 6.2, x0 = -(n - 1) * gap / 2.0, dy = 14.5;
            List<Task> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparing((Task t) -> !hot.contains(t.id)).thenComparing(Task::isCompleted));
            for (int i = 0; i < n; i++) {
                Task t = sorted.get(i);
                boolean h2 = hot.contains(t.id);
                Color c = h2 ? Theme.danger : TaskVisuals.base(t);
                double a = t.isCompleted() ? 0.35 : 1;
                if (h2 && !Animator.reduceMotion) a = 0.6 + 0.4 * Math.sin(System.nanoTime() / 1e9 * 4.0 + i);
                g.setColor(Gfx.alpha(c, a));
                double rr = 2.0;
                g.fill(new Ellipse2D.Double(x0 + i * gap - rr, dy - rr, rr * 2, rr * 2));
            }
        }
        g.dispose();
    }
}
