package com.focusflow.ui.focus;

import com.focusflow.animations.Animator;
import com.focusflow.animations.SpringValue;
import com.focusflow.app.AppContext;
import com.focusflow.models.Task;
import com.focusflow.services.FocusService;
import com.focusflow.ui.components.Icons;
import com.focusflow.ui.components.Sheet;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;
import com.focusflow.ui.Theme;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** Distraction-free countdown: a huge timer, one ring, one button. Always dark for calm. */
public class ImmersivePanel extends JComponent {
    private static final Color BG = new Color(0x07070C);

    private final SpringValue appear = new SpringValue(0, 0.5, 0.95, this::repaint);
    private final SpringValue done = new SpringValue(0, 0.7, 0.6, this::repaint);
    private final SpringValue burst = new SpringValue(0, 1.1, 1.0, this::repaint);
    private final Timer ticker = new Timer(50, e -> repaint());
    private BufferedImage ambient;
    private long ambientAt;
    private FocusService.State lastState = FocusService.State.IDLE;
    private int hover = -1;
    private final Rectangle[] hit = new Rectangle[6]; // 0 pause 1 reset 2 end 3 close 4 again 5 done
    private Runnable afterHide;

    public ImmersivePanel() {
        setOpaque(false);
        for (int i = 0; i < hit.length; i++) hit[i] = new Rectangle();
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = at(e.getPoint());
                if (h != hover) { hover = h; setCursor(h >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor()); repaint(); }
            }
            @Override public void mousePressed(MouseEvent e) { click(at(e.getPoint())); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(e -> {});
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit");
        getActionMap().put("toggle", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (!Sheet.isOpen()) click(0); }
        });
        getActionMap().put("exit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (!Sheet.isOpen()) click(3); }
        });
    }

    private int at(Point p) {
        for (int i = 0; i < hit.length; i++) if (hit[i].contains(p)) return i;
        return -1;
    }

    private void click(int i) {
        FocusService f = AppContext.focus;
        FocusService.State st = f.state();
        boolean fin = st == FocusService.State.FINISHED;
        switch (i) {
            case 0 -> { if (st == FocusService.State.RUNNING) f.pause(); else if (st == FocusService.State.PAUSED) f.resume(); }
            case 1 -> { if (!fin) f.reset(); }
            case 2, 3 -> {
                if (fin) { f.dismiss(); return; }
                Sheet.confirm("End this session?", "The time you've focused so far is saved. Your task is not marked complete.",
                        "End session", "Keep focusing", false, f::stop);
            }
            case 4 -> {
                if (!fin) return;
                int minutes = (int) (f.plannedMs() / 60000);
                Long tid = f.taskId();
                f.dismiss();
                f.start(minutes, tid);
            }
            case 5 -> { if (fin) f.dismiss(); }
            default -> { }
        }
    }

    public void appear() {
        appear.snap(0);
        appear.to(1);
    }

    public void disappear(Runnable after) {
        afterHide = after;
        appear.onSettled(() -> { if (appear.target == 0 && afterHide != null) { Runnable r = afterHide; afterHide = null; r.run(); } });
        appear.to(0);
    }

    @Override public void addNotify() { super.addNotify(); ticker.start(); }
    @Override public void removeNotify() { ticker.stop(); super.removeNotify(); }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, appear.value))));
        super.paint(g2);
        g2.dispose();
    }

    private void paintAmbient(Graphics2D g, int w, int h) {
        long now = System.currentTimeMillis();
        int aw = Math.max(1, w / 3), ah = Math.max(1, h / 3);
        if (ambient == null || ambient.getWidth() != aw || ambient.getHeight() != ah || (now - ambientAt > 120 && !Animator.reduceMotion)) {
            if (ambient == null || ambient.getWidth() != aw || ambient.getHeight() != ah) ambient = new BufferedImage(aw, ah, BufferedImage.TYPE_INT_RGB);
            ambientAt = now;
            Graphics2D a = ambient.createGraphics();
            a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            a.setColor(BG);
            a.fillRect(0, 0, aw, ah);
            double t = Animator.reduceMotion ? 0 : now / 9000.0;
            blob(a, aw * (0.30 + 0.12 * Math.sin(t)), ah * (0.35 + 0.10 * Math.cos(t * 1.3)), aw * 0.55, new Color(0x0A84FF), 0.30);
            blob(a, aw * (0.75 + 0.10 * Math.cos(t * 0.8)), ah * (0.70 + 0.10 * Math.sin(t * 1.1)), aw * 0.50, new Color(0x5E5CE6), 0.28);
            blob(a, aw * (0.55 + 0.08 * Math.sin(t * 1.6)), ah * (0.15 + 0.08 * Math.cos(t)), aw * 0.35, new Color(0x40C8E0), 0.14);
            a.dispose();
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(ambient, 0, 0, w, h, null);
    }

    private static void blob(Graphics2D g, double cx, double cy, double r, Color c, double a) {
        RadialGradientPaint p = new RadialGradientPaint((float) cx, (float) cy, (float) r, new float[]{0f, 1f},
                new Color[]{Gfx.alpha(c, a), Gfx.alpha(c, 0.0)});
        g.setPaint(p);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        paintAmbient(g2, w, h);
        FocusService f = AppContext.focus;
        FocusService.State st = f.state();
        if (st != lastState) {
            if (st == FocusService.State.FINISHED) {
                done.snap(0); done.to(1);
                burst.snap(0); burst.to(1);
            }
            lastState = st;
        }
        for (Rectangle r : hit) r.setBounds(0, 0, 0, 0);
        boolean fin = st == FocusService.State.FINISHED;
        boolean paused = st == FocusService.State.PAUSED;

        double d = Math.min(Math.min(w * 0.5, h * 0.5), 520);
        double cx = w / 2.0, cy = h * 0.43;
        double sw = Math.max(8, d * 0.032);
        double rx = cx - d / 2, ry = cy - d / 2;
        long planned = Math.max(1, f.plannedMs());
        double prog = fin ? 1 : Math.min(1, f.elapsedMs() / (double) planned);

        g2.setStroke(new BasicStroke((float) sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 255, 255, 26));
        g2.draw(new Ellipse2D.Double(rx, ry, d, d));
        Color ring = fin ? new Color(0x30D158) : paused ? new Color(255, 255, 255, 120) : new Color(0x64B5FF);
        if (prog > 0.001) {
            g2.setStroke(new BasicStroke((float) (sw * 2.6), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Gfx.alpha(ring, 0.12));
            g2.draw(new Arc2D.Double(rx, ry, d, d, 90, -360 * prog, Arc2D.OPEN));
            g2.setStroke(new BasicStroke((float) sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(ring);
            g2.draw(new Arc2D.Double(rx, ry, d, d, 90, -360 * prog, Arc2D.OPEN));
        }

        Task task = f.taskId() == null ? null : AppContext.tasks.get(f.taskId());
        if (fin) {
            double s = Math.max(0, done.value);
            double ic = d * 0.28 * Math.min(1.2, s);
            Icons.paint(g2, Icons.Type.CHECK, cx - ic / 2, cy - ic / 2 - d * 0.06, ic, new Color(0x30D158));
            Gfx.textCentered(g2, "Session complete", cx, cy + d * 0.2, Theme.semi((float) (d * 0.075)), Color.WHITE);
            long mins = Math.round(planned / 60000.0);
            Gfx.textCentered(g2, "You focused for " + Time.minutes(mins) + (task != null ? " on " + task.title : ""), cx, cy + d * 0.29,
                    Theme.font((float) Math.max(13, d * 0.04)), new Color(255, 255, 255, 170));
            double b = Math.max(0, Math.min(1, burst.value));
            for (int i = 0; i < 3; i++) {
                double p = Math.max(0, Math.min(1, b * 1.25 - i * 0.12));
                if (p <= 0 || p >= 1) continue;
                double rr = d / 2 + p * d * 0.45;
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(0x30, 0xD1, 0x58, (int) (150 * (1 - p))));
                g2.draw(new Ellipse2D.Double(cx - rr, cy - rr, 2 * rr, 2 * rr));
            }
            int by = (int) (cy + d / 2 + 56);
            button(g2, 4, cx - 150, by, 190, 48, "Start another", true);
            button(g2, 5, cx + 50, by, 100, 48, "Done", false);
        } else {
            long rem = (f.remainingMs() + 999) / 1000;
            timeText(g2, rem, cx, cy - d * 0.04, d);
            String cap = task != null ? task.title : "Focus";
            Font cf = Theme.semi((float) Math.max(14, d * 0.055));
            Gfx.textCentered(g2, Gfx.fit(cap, g2.getFontMetrics(cf), (int) (d * 0.8)), cx, cy + d * 0.20, cf, new Color(255, 255, 255, 220));
            Gfx.textCentered(g2, paused ? "Paused" : "of " + Time.minutes(Math.round(planned / 60000.0)), cx, cy + d * 0.28,
                    Theme.font((float) Math.max(12, d * 0.038)), paused ? new Color(0xFF9F0A) : new Color(255, 255, 255, 120));
            int by = (int) (cy + d / 2 + 60);
            circle(g2, 1, cx - 110, by, 44, Icons.Type.RESET);
            circle(g2, 0, cx, by, 68, paused ? Icons.Type.PLAY : Icons.Type.PAUSE);
            circle(g2, 2, cx + 110, by, 44, Icons.Type.STOP);
        }
        circle(g2, 3, w - 52, 52, 40, Icons.Type.CLOSE);
        g2.dispose();
    }

    private void timeText(Graphics2D g, long secs, double cx, double cy, double d) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        String t = h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
        float size = (float) (d * (h > 0 ? 0.2 : 0.27));
        Font f = Theme.light(size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int digit = 0;
        for (char c = '0'; c <= '9'; c++) digit = Math.max(digit, fm.charWidth(c));
        double total = 0;
        for (char c : t.toCharArray()) total += c == ':' ? fm.charWidth(c) : digit;
        double x = cx - total / 2;
        double y = cy + (fm.getAscent() - fm.getDescent()) / 2.0;
        g.setColor(Color.WHITE);
        for (char c : t.toCharArray()) {
            String sc = String.valueOf(c);
            double cwid = c == ':' ? fm.charWidth(c) : digit;
            g.drawString(sc, (float) (x + (cwid - fm.charWidth(c)) / 2), (float) y);
            x += cwid;
        }
    }

    private void circle(Graphics2D g, int idx, double cx, double cy, double size, Icons.Type icon) {
        hit[idx].setBounds((int) (cx - size / 2), (int) (cy - size / 2), (int) size, (int) size);
        boolean hv = hover == idx;
        g.setColor(new Color(255, 255, 255, hv ? 56 : 30));
        g.fill(new Ellipse2D.Double(cx - size / 2, cy - size / 2, size, size));
        double is = size * (idx == 0 ? 0.42 : 0.5);
        Icons.paint(g, icon, cx - is / 2, cy - is / 2, is, Color.WHITE);
    }

    private void button(Graphics2D g, int idx, double x, double y, double w, double hgt, String label, boolean primary) {
        hit[idx].setBounds((int) x, (int) y, (int) w, (int) hgt);
        boolean hv = hover == idx;
        g.setColor(primary ? new Color(0x0A84FF).brighter() : new Color(255, 255, 255, hv ? 56 : 30));
        if (primary && hv) g.setColor(new Color(0x409CFF));
        else if (primary) g.setColor(new Color(0x0A84FF));
        g.fill(Gfx.rr(x, y, w, hgt, hgt));
        Gfx.textCentered(g, label, x + w / 2, y + hgt / 2, Theme.semi(15f), Color.WHITE);
    }
}
