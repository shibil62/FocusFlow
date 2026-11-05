package com.focusflow.ui.analytics;

import com.focusflow.app.AppContext;
import com.focusflow.models.FocusSession;
import com.focusflow.services.AnalyticsService.*;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.ui.focus.FocusView;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Productivity analytics computed from stored tasks and focus sessions. */
public class AnalyticsView extends JPanel {
    private static final int[] DAYS = {7, 30, 90};

    private final Segmented range = new Segmented(new String[]{"7 days", "30 days", "90 days"}, 1, i -> refresh());
    private final Tile completed = new Tile("Completed tasks"), overdue = new Tile("Overdue now"), rate = new Tile("Completion rate"),
            avg = new Tile("Average per day"), focus = new Tile("Focus time"), best = new Tile("Most productive day"), subs = new Tile("Subtasks done");
    private final ProgressList progressList = new ProgressList();
    private final Lbl rangeLabel = Lbl.sub("");
    private final Charts.BarChart daily = new Charts.BarChart(), weekly = new Charts.BarChart(), weekday = new Charts.BarChart();
    private final Charts.LineChart trend = new Charts.LineChart();
    private final Charts.HBars catTime = new Charts.HBars();
    private final Card dailyCard, trendCard;
    private final VStack sessions = new VStack();
    private final JPanel dailyHost = new JPanel(new CardLayout());

    public AnalyticsView() {
        super(new BorderLayout());
        setOpaque(false);
        VStack page = new VStack();
        page.setBorder(new EmptyBorder(26, 28, 30, 28));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel l = new JPanel(new GridLayout(2, 1));
        l.setOpaque(false);
        l.add(Lbl.title("Analytics"));
        l.add(rangeLabel);
        head.add(l, BorderLayout.WEST);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        r.setOpaque(false);
        r.add(range);
        head.add(r, BorderLayout.EAST);
        page.add(head, 0);

        JPanel tiles = new JPanel(new GridLayout(1, 7, 4, 0));
        tiles.setOpaque(false);
        for (Tile t : new Tile[]{completed, overdue, rate, avg, focus, best, subs}) tiles.add(t);
        page.add(tiles, 10);

        dailyHost.setOpaque(false);
        daily.setPreferredSize(new Dimension(300, 250));
        trend.setPreferredSize(new Dimension(300, 250));
        dailyHost.add(daily, "bars");
        dailyHost.add(trend, "line");
        dailyCard = card("Daily productivity", dailyHost);
        weekly.setPreferredSize(new Dimension(300, 250));
        trendCard = card("Weekly productivity", weekly);
        page.add(new Cols(4, new double[]{1.4, 1}, dailyCard, trendCard), 4);

        weekday.setPreferredSize(new Dimension(300, 230));
        catTime.setPreferredSize(new Dimension(300, 100));
        page.add(new Cols(4, new double[]{1, 1}, card("Productivity by weekday", weekday), card("Focus time by category", catTime)), 4);

        page.add(card("Subtask progress", progressList), 4);

        page.add(Lbl.h2("Focus sessions"), 24);
        page.add(sessions, 8);
        add(Refresher.scroll(page), BorderLayout.CENTER);
        Refresher.install(this, this::refresh, AppContext.tasks, AppContext.categories, AppContext.focus);
        refresh();
    }

    private Card card(String title, JComponent body) {
        Card c = new Card(18, new BorderLayout(0, 12));
        c.add(Lbl.h2(title), BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    public void refresh() {
        int n = DAYS[range.getSelected()];
        LocalDate to = Time.today(), from = to.minusDays(n - 1);
        DayOfWeek fd = DayOfWeek.valueOf(AppContext.settings.get(SettingsService.WEEK_START, "MONDAY"));
        rangeLabel.setText(Time.dateMed(from) + " – " + Time.dateMed(to));

        Totals t = AppContext.analytics.totals(from, to);
        completed.set(String.valueOf(AppContext.analytics.totalCompleted()), "all time");
        overdue.set(String.valueOf(AppContext.analytics.totalOverdue()), "right now");
        rate.set(Math.round(t.rate()) + "%", t.completed() + " of " + (t.completed() + t.pending()) + " planned");
        avg.set(String.format("%.1f", AppContext.analytics.avgDailyCompletion(from, to)), "tasks a day");
        focus.set(Time.seconds(AppContext.analytics.focusSeconds(from, to)), "in this period");
        subs.set(String.valueOf(AppContext.analytics.subtasksDone(from, to)), "in this period");
        int[] wd = AppContext.analytics.weekdayCounts(from, to);
        int bi = 0;
        for (int i = 1; i < 7; i++) if (wd[i] > wd[bi]) bi = i;
        best.set(wd[bi] == 0 ? "None yet" : DayOfWeek.of(bi + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH), wd[bi] == 0 ? "complete a task" : wd[bi] + " completed");

        List<DayStat> days = AppContext.analytics.daily(from, to);
        CardLayout cl = (CardLayout) dailyHost.getLayout();
        if (n <= 7) {
            String[] lb = new String[days.size()];
            double[] p = new double[days.size()], c = new double[days.size()];
            for (int i = 0; i < days.size(); i++) {
                lb[i] = days.get(i).date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                p[i] = days.get(i).planned();
                c[i] = days.get(i).completed();
            }
            int[] sub = AppContext.analytics.subtasksDaily(from, to);
            double[] sd = new double[days.size()];
            for (int i = 0; i < sd.length; i++) sd[i] = sub[i];
            daily.setData(lb, new String[]{"Planned", "Completed", "Subtasks"},
                    new Color[]{Gfx.mix(Theme.surface, Theme.text, 0.2), Theme.accent, new Color(0x40C8E0)}, new double[][]{p, c, sd});
            cl.show(dailyHost, "bars");
        } else {
            String[] lb = new String[days.size()];
            double[] v = new double[days.size()];
            for (int i = 0; i < days.size(); i++) {
                lb[i] = days.get(i).date().getDayOfMonth() + " " + days.get(i).date().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                v[i] = days.get(i).completed();
            }
            trend.setData(lb, v, "done");
            cl.show(dailyHost, "line");
        }

        int[] wk = AppContext.analytics.weeklyTotals(8, fd);
        String[] wl = new String[8];
        double[] wv = new double[8];
        LocalDate ws = Time.weekStart(Time.today(), fd);
        for (int i = 0; i < 8; i++) {
            wv[i] = wk[i];
        }
        for (int i = 0; i < 8; i++) {
            LocalDate d = ws.minusWeeks(7 - i);
            wl[i] = i == 7 ? "Now" : d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        }
        weekly.setData(wl, new String[]{"Completed"}, new Color[]{Theme.accent}, new double[][]{wv});

        String[] dn = new String[7];
        double[] dv = new double[7];
        for (int i = 0; i < 7; i++) {
            dn[i] = DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            dv[i] = wd[i];
        }
        weekday.setData(dn, new String[]{"Completed"}, new Color[]{new Color(0x5E5CE6)}, new double[][]{dv});
        weekday.setHighlight(wd[bi] == 0 ? -1 : bi);

        List<CatTime> ct = AppContext.analytics.focusByCategory(from, to);
        String[] cn = new String[ct.size()];
        double[] cs = new double[ct.size()];
        Color[] cc = new Color[ct.size()];
        for (int i = 0; i < ct.size(); i++) {
            cn[i] = ct.get(i).category() == null ? "No category" : ct.get(i).category().name;
            cs[i] = ct.get(i).seconds();
            cc[i] = ct.get(i).category() == null ? new Color(0x8E8E93) : ct.get(i).category().awt();
        }
        catTime.setData(cn, cs, cc);

        progressList.setData(AppContext.analytics.subtaskProgress());
        sessions.clear();
        List<FocusSession> all = AppContext.focus.sessions();
        if (all.isEmpty()) sessions.add(new EmptyState(Icons.Type.FOCUS, "No focus sessions yet", "Start one from Power Mode and it will show up here.", 150), 0);
        for (int i = 0; i < Math.min(15, all.size()); i++) sessions.add(new FocusView.SessionRow(all.get(i)), i == 0 ? 0 : 6);
        sessions.revalidate();
        sessions.repaint();
        repaint();
    }

    private static final class ProgressList extends JComponent {
        private List<Progress> items = List.of();

        void setData(List<Progress> l) {
            items = l;
            setPreferredSize(new Dimension(300, Math.max(70, Math.min(l.size(), 8) * 56 + 4)));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth();
            if (items.isEmpty()) {
                Gfx.textCentered(g2, "No open tasks with subtasks. Add subtasks to a task to track its progress here.", w / 2.0, getHeight() / 2.0, Theme.font(12f), Theme.text3);
                g2.dispose();
                return;
            }
            for (int i = 0; i < Math.min(8, items.size()); i++) {
                Progress p = items.get(i);
                int y = i * 56 + 4;
                double pr = p.total() == 0 ? 0 : p.done() / (double) p.total();
                String right = p.done() + " / " + p.total() + "   " + Math.round(pr * 100) + "%";
                FontMetrics fm = g2.getFontMetrics(Theme.semi(13f));
                int rw = fm.stringWidth(right);
                Gfx.text(g2, Gfx.fit(p.task().title, fm, w - rw - 20), 0, y + 14, Theme.semi(13f), Theme.text);
                Gfx.text(g2, right, w - rw, y + 14, Theme.semi(13f), Theme.text2);
                g2.setColor(Theme.surfaceAlt);
                g2.fill(Gfx.rr(0, y + 22, w, 8, 8));
                g2.setColor(pr >= 1 ? Theme.success : com.focusflow.ui.components.TaskVisuals.base(p.task()));
                g2.fill(Gfx.rr(0, y + 22, Math.max(8, w * pr), 8, 8));
                String due = p.task().deadline == null ? "No deadline"
                        : "Due " + Time.dateShort(p.task().deadlineDate()) + "  ·  " + AppContext.urgency.evaluate(p.task()).countdown();
                Gfx.text(g2, due, 0, y + 46, Theme.font(11f), Theme.text3);
            }
            g2.dispose();
        }
    }

    private static final class Tile extends Card {
        private final String label;
        private String value = "–", note = "";

        Tile(String label) {
            super(14);
            this.label = label;
        }

        void set(String v, String n) {
            value = v;
            note = n;
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(120, 112); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = Gfx.aa(g);
            int x = SH + 16;
            Gfx.text(g2, label, x, SH + 30, Theme.font(12f), Theme.text2);
            FontMetrics fm = g2.getFontMetrics(Theme.bold(22f));
            Gfx.text(g2, Gfx.fit(value, fm, getWidth() - 2 * SH - 28), x, SH + 62, Theme.bold(22f), Theme.text);
            Gfx.text(g2, Gfx.fit(note, g2.getFontMetrics(Theme.font(11f)), getWidth() - 2 * SH - 28), x, SH + 82, Theme.font(11f), Theme.text3);
            g2.dispose();
        }
    }
}
