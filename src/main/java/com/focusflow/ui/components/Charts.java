package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/** Lightweight Java2D charts with animated reveals and hover tooltips. */
public final class Charts {
    private Charts() {}

    static double niceStep(double max, int intervals) {
        double raw = max / intervals;
        if (raw <= 0) return 1;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        double step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return step * mag;
    }

    static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 0.001 ? String.valueOf((long) Math.rint(v)) : String.format("%.1f", v);
    }

    static void tooltip(Graphics2D g, String text, double cx, double top, int maxW) {
        Font f = Theme.semi(12f);
        FontMetrics fm = g.getFontMetrics(f);
        int w = fm.stringWidth(text) + 20, h = 26;
        double x = Math.max(4, Math.min(maxW - w - 4, cx - w / 2.0));
        double y = Math.max(2, top - h - 8);
        g.setColor(Theme.dark ? new Color(0x48484A) : new Color(0x2C2C2E));
        g.fill(Gfx.rr(x, y, w, h, 10));
        Gfx.text(g, text, (int) x + 10, (int) y + (h - fm.getHeight()) / 2 + fm.getAscent(), f, Color.WHITE);
    }

    // ================================================================ bars

    public static class BarChart extends JComponent {
        private String[] labels = {};
        private String[] names = {};
        private Color[] colors = {};
        private double[][] data = {};
        private int hover = -1, highlight = -1;
        private final SpringValue grow = new SpringValue(0, 0.7, 0.9, this::repaint);

        public BarChart() {
            setPreferredSize(new Dimension(300, 240));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hover) { hover = i; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            });
        }

        /** data[series][category]. */
        public void setData(String[] labels, String[] names, Color[] colors, double[][] data) {
            this.labels = labels;
            this.names = names;
            this.colors = colors;
            this.data = data;
            grow.snap(0);
            grow.to(1);
        }

        public void setHighlight(int i) { highlight = i; repaint(); }

        private static final int L = 34, R = 8, T = 30, Bm = 26;

        private int indexAt(int x) {
            if (labels.length == 0) return -1;
            double gw = (getWidth() - L - R) / (double) labels.length;
            int i = (int) ((x - L) / gw);
            return i < 0 || i >= labels.length ? -1 : i;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            int pw = w - L - R, ph = h - T - Bm;
            double max = 0;
            for (double[] s : data) for (double v : s) max = Math.max(max, v);
            double step = niceStep(Math.max(max, 1), 4);
            double top = step * 4;
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 4; i++) {
                double y = T + ph - ph * (i / 4.0);
                g2.setColor(i == 0 ? Theme.separator : Gfx.alpha(Theme.separator, 14));
                g2.draw(new java.awt.geom.Line2D.Double(L, y, L + pw, y));
                String s = num(step * i);
                FontMetrics fm = g2.getFontMetrics(Theme.font(11f));
                Gfx.text(g2, s, L - 8 - fm.stringWidth(s), (int) y + 4, Theme.font(11f), Theme.text3);
            }
            int n = labels.length, ns = data.length;
            if (n == 0) { g2.dispose(); return; }
            double gw = pw / (double) n;
            double barW = Math.min(18, gw * 0.62 / Math.max(1, ns));
            double clusterW = barW * ns + 3 * (ns - 1);
            double ease = Math.max(0, Math.min(1, grow.value));
            for (int i = 0; i < n; i++) {
                double cx = L + gw * i + gw / 2;
                if (i == hover || i == highlight) {
                    g2.setColor(Gfx.alpha(Theme.accent, i == hover ? 0.07 : 0.05));
                    g2.fill(Gfx.rr(L + gw * i + 2, T - 4, gw - 4, ph + 4, 10));
                }
                for (int s = 0; s < ns; s++) {
                    double v = data[s][i];
                    double bh = v / top * ph * ease;
                    double bx = cx - clusterW / 2 + s * (barW + 3);
                    if (bh > 0.5) {
                        g2.setColor(colors[s]);
                        g2.fill(Gfx.rr(bx, T + ph - bh, barW, Math.max(bh, 2), Math.min(8, barW)));
                    }
                }
                Gfx.textCentered(g2, labels[i], cx, h - 10, Theme.font(11f), i == highlight ? Theme.accent : Theme.text2);
            }
            if (ns > 1) {
                int x = w - R;
                for (int s = ns - 1; s >= 0; s--) {
                    FontMetrics fm = g2.getFontMetrics(Theme.font(11f));
                    x -= fm.stringWidth(names[s]);
                    Gfx.text(g2, names[s], x, 14, Theme.font(11f), Theme.text2);
                    x -= 14;
                    g2.setColor(colors[s]);
                    g2.fill(new Ellipse2D.Double(x, 6, 8, 8));
                    x -= 14;
                }
            }
            if (hover >= 0 && hover < n) {
                StringBuilder sb = new StringBuilder(labels[hover]).append(": ");
                for (int s = 0; s < ns; s++) {
                    if (s > 0) sb.append(" · ");
                    sb.append(num(data[s][hover]));
                    if (ns > 1) sb.append(' ').append(names[s].toLowerCase());
                }
                double hv = 0;
                for (int s = 0; s < ns; s++) hv = Math.max(hv, data[s][hover]);
                tooltip(g2, sb.toString(), L + gw * hover + gw / 2, T + ph - hv / top * ph, w);
            }
            g2.dispose();
        }
    }

    // ================================================================ line

    public static class LineChart extends JComponent {
        private String[] labels = {};
        private double[] values = {};
        private String unit = "done";
        private int hover = -1;
        private final SpringValue reveal = new SpringValue(0, 0.9, 0.95, this::repaint);

        public LineChart() {
            setPreferredSize(new Dimension(300, 240));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    if (values.length == 0) return;
                    double step = (getWidth() - L - R) / (double) Math.max(1, values.length - 1);
                    int i = (int) Math.round((e.getX() - L) / step);
                    i = Math.max(0, Math.min(values.length - 1, i));
                    if (i != hover) { hover = i; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            });
        }

        public void setData(String[] labels, double[] values, String unit) {
            this.labels = labels;
            this.values = values;
            this.unit = unit;
            reveal.snap(0);
            reveal.to(1);
        }

        private static final int L = 34, R = 14, T = 16, Bm = 26;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            int pw = w - L - R, ph = h - T - Bm;
            double max = 0;
            for (double v : values) max = Math.max(max, v);
            double step = niceStep(Math.max(max, 1), 4);
            double top = step * 4;
            for (int i = 0; i <= 4; i++) {
                double y = T + ph - ph * (i / 4.0);
                g2.setColor(i == 0 ? Theme.separator : Gfx.alpha(Theme.separator, 14));
                g2.draw(new java.awt.geom.Line2D.Double(L, y, L + pw, y));
                String s = num(step * i);
                FontMetrics fm = g2.getFontMetrics(Theme.font(11f));
                Gfx.text(g2, s, L - 8 - fm.stringWidth(s), (int) y + 4, Theme.font(11f), Theme.text3);
            }
            int n = values.length;
            if (n == 0) { g2.dispose(); return; }
            Point2D.Double[] pts = new Point2D.Double[n];
            for (int i = 0; i < n; i++) {
                double x = n == 1 ? L + pw / 2.0 : L + pw * i / (double) (n - 1);
                pts[i] = new Point2D.Double(x, T + ph - values[i] / top * ph);
            }
            int every = Math.max(1, (int) Math.ceil(n / 8.0));
            for (int i = 0; i < n; i += every) Gfx.textCentered(g2, labels[i], pts[i].x, h - 10, Theme.font(11f), Theme.text2);

            Path2D line = new Path2D.Double();
            line.moveTo(pts[0].x, pts[0].y);
            for (int i = 0; i < n - 1; i++) {
                Point2D.Double p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(n - 1, i + 2)];
                double lo = Math.min(p1.y, p2.y), hi = Math.max(p1.y, p2.y);
                double c1y = Math.max(lo, Math.min(hi, p1.y + (p2.y - p0.y) / 6));
                double c2y = Math.max(lo, Math.min(hi, p2.y - (p3.y - p1.y) / 6));
                line.curveTo(p1.x + (p2.x - p0.x) / 6, c1y, p2.x - (p3.x - p1.x) / 6, c2y, p2.x, p2.y);
            }
            Graphics2D c = (Graphics2D) g2.create();
            c.clipRect(0, 0, (int) (L + pw * Math.max(0.001, Math.min(1, reveal.value)) + 6), h);
            Path2D area = new Path2D.Double(line);
            area.lineTo(pts[n - 1].x, T + ph);
            area.lineTo(pts[0].x, T + ph);
            area.closePath();
            c.setPaint(new GradientPaint(0, T, Gfx.alpha(Theme.accent, 0.28), 0, T + ph, Gfx.alpha(Theme.accent, 0.0)));
            c.fill(area);
            c.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.setColor(Theme.accent);
            c.draw(line);
            c.dispose();
            if (hover >= 0 && hover < n) {
                g2.setColor(Gfx.alpha(Theme.text, 0.15));
                g2.draw(new java.awt.geom.Line2D.Double(pts[hover].x, T, pts[hover].x, T + ph));
                g2.setColor(Theme.surface);
                g2.fill(new Ellipse2D.Double(pts[hover].x - 6, pts[hover].y - 6, 12, 12));
                g2.setColor(Theme.accent);
                g2.fill(new Ellipse2D.Double(pts[hover].x - 4, pts[hover].y - 4, 8, 8));
                tooltip(g2, labels[hover] + ": " + num(values[hover]) + " " + unit, pts[hover].x, pts[hover].y, w);
            }
            g2.dispose();
        }
    }

    // ================================================================ donut

    public static class Donut extends JComponent {
        private String[] names = {};
        private double[] values = {};
        private Color[] colors = {};
        private String emptyText = "No data yet";
        private String centerLabel = "tasks";
        private int hover = -1;
        private final SpringValue sweep = new SpringValue(0, 0.8, 0.95, this::repaint);

        public Donut() {
            setPreferredSize(new Dimension(300, 200));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = segmentAt(e.getX(), e.getY());
                    if (i != hover) { hover = i; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            });
        }

        public void setData(String[] names, double[] values, Color[] colors, String centerLabel, String emptyText) {
            this.names = names;
            this.values = values;
            this.colors = colors;
            this.centerLabel = centerLabel;
            this.emptyText = emptyText;
            sweep.snap(0);
            sweep.to(1);
        }

        private double diameter() { return Math.min(getHeight() - 16, getWidth() * 0.5); }

        private int segmentAt(int mx, int my) {
            double d = diameter(), cx = 8 + d / 2, cy = getHeight() / 2.0;
            double dx = mx - cx, dy = my - cy, r = Math.hypot(dx, dy);
            if (r > d / 2 + 4 || r < d / 2 - d * 0.2) return -1;
            double a = (90 - Math.toDegrees(Math.atan2(-dy, dx)) + 360) % 360;
            double total = 0;
            for (double v : values) total += v;
            if (total <= 0) return -1;
            double acc = 0;
            for (int i = 0; i < values.length; i++) {
                acc += values[i] / total * 360;
                if (a <= acc) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            double d = diameter(), sw = d * 0.16;
            double x = 8 + sw / 2, y = (getHeight() - d) / 2 + sw / 2, rd = d - sw;
            double total = 0;
            for (double v : values) total += v;
            g2.setStroke(new BasicStroke((float) sw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            if (total <= 0) {
                g2.setColor(Theme.surfaceAlt);
                g2.draw(new Ellipse2D.Double(x, y, rd, rd));
                Gfx.textCentered(g2, emptyText, 8 + d / 2, getHeight() / 2.0, Theme.font(12f), Theme.text3);
                g2.dispose();
                return;
            }
            double start = 90, full = 360 * Math.max(0, Math.min(1, sweep.value));
            double used = 0;
            for (int i = 0; i < values.length; i++) {
                double ext = values[i] / total * 360;
                double shown = Math.max(0, Math.min(ext, full - used));
                if (shown > 0.2) {
                    g2.setStroke(new BasicStroke((float) (i == hover ? sw + 4 : sw), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
                    g2.setColor(colors[i]);
                    double gap = values.length > 1 ? Math.min(2.5, shown / 3) : 0;
                    g2.draw(new Arc2D.Double(x, y, rd, rd, start - used - gap / 2, -(shown - gap), Arc2D.OPEN));
                }
                used += ext;
            }
            String big = hover >= 0 ? num(values[hover]) : num(total);
            String small = hover >= 0 ? names[hover] : centerLabel;
            double cx = 8 + d / 2, cy = getHeight() / 2.0;
            Gfx.textCentered(g2, big, cx, cy - 6, Theme.bold((float) (d * 0.2)), Theme.text);
            Gfx.textCentered(g2, Gfx.fit(small, g2.getFontMetrics(Theme.font(11f)), (int) (d * 0.6)), cx, cy + d * 0.14, Theme.font(11f), Theme.text2);
            // legend
            int lx = (int) (d + 32), rowH = 24;
            int shownRows = Math.min(values.length, Math.max(1, (getHeight() - 8) / rowH));
            int ly = (getHeight() - shownRows * rowH) / 2;
            for (int i = 0; i < shownRows; i++) {
                int yy = ly + i * rowH;
                g2.setColor(colors[i]);
                g2.fill(new Ellipse2D.Double(lx, yy + 7, 9, 9));
                FontMetrics fm = g2.getFontMetrics(Theme.font(12f));
                String pct = Math.round(values[i] / total * 100) + "%";
                int pw = fm.stringWidth(pct);
                Gfx.text(g2, Gfx.fit(names[i], fm, getWidth() - lx - 22 - pw - 20), lx + 16, yy + 16, Theme.font(12f), i == hover ? Theme.text : Theme.text2);
                Gfx.text(g2, pct, getWidth() - 8 - pw, yy + 16, Theme.semi(12f), Theme.text);
            }
            g2.dispose();
        }
    }

    // ============================================================ horizontal bars

    public static class HBars extends JComponent {
        private String[] names = {};
        private double[] seconds = {};
        private Color[] colors = {};
        private final SpringValue grow = new SpringValue(0, 0.7, 0.9, this::repaint);

        public void setData(String[] names, double[] seconds, Color[] colors) {
            this.names = names;
            this.seconds = seconds;
            this.colors = colors;
            setPreferredSize(new Dimension(300, Math.max(60, names.length * 34 + 6)));
            grow.snap(0);
            grow.to(1);
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            double max = 1;
            for (double s : seconds) max = Math.max(max, s);
            if (names.length == 0) {
                Gfx.textCentered(g2, "No focus sessions in this period", getWidth() / 2.0, getHeight() / 2.0, Theme.font(12f), Theme.text3);
            }
            int labelW = 110;
            for (int i = 0; i < names.length; i++) {
                int y = i * 34 + 4;
                FontMetrics fm = g2.getFontMetrics(Theme.font(12f));
                Gfx.text(g2, Gfx.fit(names[i], fm, labelW - 8), 0, y + 16, Theme.font(12f), Theme.text2);
                String val = Time.seconds((long) seconds[i]);
                int vw = fm.stringWidth(val);
                double bw = getWidth() - labelW - vw - 16;
                g2.setColor(Theme.surfaceAlt);
                g2.fill(Gfx.rr(labelW, y + 6, bw, 10, 10));
                g2.setColor(colors[i]);
                g2.fill(Gfx.rr(labelW, y + 6, Math.max(10, bw * seconds[i] / max * Math.max(0, Math.min(1, grow.value))), 10, 10));
                Gfx.text(g2, val, getWidth() - vw, y + 16, Theme.semi(12f), Theme.text);
            }
            g2.dispose();
        }
    }
}
