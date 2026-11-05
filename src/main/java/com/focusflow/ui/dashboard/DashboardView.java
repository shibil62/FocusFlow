package com.focusflow.ui.dashboard;

import com.focusflow.app.AppContext;
import com.focusflow.models.*;
import com.focusflow.services.AnalyticsService;
import com.focusflow.services.AnalyticsService.*;
import com.focusflow.services.SettingsService;
import com.focusflow.services.TaskService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The overview: everything on it comes from the database. */
public class DashboardView extends JPanel {
    private final VStack stack = new VStack();
    private final Lbl greeting = Lbl.title("");
    private final Lbl dateLine = Lbl.sub(""), message = new Lbl("", Theme.font(13f), Lbl.Role.TEXT2);
    private final JPanel alerts = new JPanel();
    private final StatCard total, done, remaining, overdue, urgent, worked, percent;
    private final TaskList scheduleList = new TaskList(new EmptyState(Icons.Type.CALENDAR, "Nothing planned for today", "Add a task to give the day some shape."));
    private final TaskList upcomingList = new TaskList(new EmptyState(Icons.Type.FLAG, "No deadlines ahead", "Tasks with a deadline show up here."));
    private final Ring ring = new Ring(14);
    private final Lbl ringNote = Lbl.caption("");
    private final Lbl scheduleSub = Lbl.caption("");
    private final Charts.Donut donut = new Charts.Donut();
    private final Charts.BarChart weekly = new Charts.BarChart();
    private final Charts.LineChart monthly = new Charts.LineChart();
    private final CompletionStats stats = new CompletionStats();
    private final Lbl rangeLabel = Lbl.caption("");
    private final Segmented range;
    private LocalDate customFrom = Time.today().minusDays(13), customTo = Time.today();

    public DashboardView() {
        super(new BorderLayout());
        setOpaque(false);
        total = new StatCard(Icons.Type.TASKS, "Tasks today", () -> Theme.accent, StatCard.Kind.COUNT);
        done = new StatCard(Icons.Type.CHECK, "Completed", () -> Theme.success, StatCard.Kind.COUNT);
        remaining = new StatCard(Icons.Type.CLOCK, "Remaining", () -> new Color(0x5E5CE6), StatCard.Kind.COUNT);
        overdue = new StatCard(Icons.Type.WARNING, "Overdue", () -> Theme.danger, StatCard.Kind.COUNT);
        urgent = new StatCard(Icons.Type.FLAME, "Urgent", () -> Theme.warning, StatCard.Kind.COUNT);
        worked = new StatCard(Icons.Type.FOCUS, "Time worked", () -> new Color(0x40C8E0), StatCard.Kind.DURATION);
        percent = new StatCard(Icons.Type.ANALYTICS, "Done today", () -> Theme.success, StatCard.Kind.PERCENT);
        range = new Segmented(new String[]{"Today", "Week", "Month", "Custom"}, 1, i -> {
            if (i == 3) pickCustom(); else refresh();
        });

        stack.setBorder(new EmptyBorder(26, 28, 30, 28));
        buildLayout();
        JScrollPane sp = Refresher.scroll(stack);
        add(sp, BorderLayout.CENTER);
        Refresher.install(this, this::refresh, AppContext.tasks, AppContext.categories, AppContext.focus, AppContext.settings);
    }

    private void buildLayout() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel left = new JPanel(new GridLayout(3, 1, 0, 2));
        left.setOpaque(false);
        left.add(greeting);
        left.add(dateLine);
        left.add(message);
        header.add(left, BorderLayout.WEST);
        Btn add = new Btn("Quick add", Btn.Kind.PRIMARY, Icons.Type.PLUS);
        add.addActionListener(e -> com.focusflow.ui.dialogs.TaskDialog.create());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        right.setOpaque(false);
        right.add(add);
        header.add(right, BorderLayout.EAST);
        stack.add(header, 0);

        alerts.setLayout(new BoxLayout(alerts, BoxLayout.Y_AXIS));
        alerts.setOpaque(false);
        stack.add(alerts, 10);

        JPanel row = new JPanel(new GridLayout(1, 7, 4, 0));
        row.setOpaque(false);
        for (StatCard c : new StatCard[]{total, done, remaining, overdue, urgent, worked, percent}) row.add(c);
        stack.add(row, 8);

        JPanel schedBody = new JPanel(new BorderLayout());
        schedBody.setOpaque(false);
        schedBody.add(scheduleList, BorderLayout.CENTER);
        Card schedule = section("Today's schedule", scheduleSub, schedBody);

        JPanel ringBody = new JPanel(new BorderLayout(0, 8));
        ringBody.setOpaque(false);
        ring.setPreferredSize(new Dimension(170, 170));
        ringBody.add(ring, BorderLayout.CENTER);
        ringNote.setHorizontalAlignment(SwingConstants.CENTER);
        ringBody.add(ringNote, BorderLayout.SOUTH);
        Card progress = section("Daily progress", null, ringBody);
        stack.add(new Cols(4, new double[]{2.1, 1}, schedule, progress), 4);

        JPanel insHead = new JPanel(new BorderLayout());
        insHead.setOpaque(false);
        JPanel il = new JPanel(new GridLayout(2, 1));
        il.setOpaque(false);
        il.add(new Lbl("Insights", Theme.bold(20f), Lbl.Role.TEXT));
        il.add(rangeLabel);
        insHead.add(il, BorderLayout.WEST);
        JPanel ir = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        ir.setOpaque(false);
        ir.add(range);
        insHead.add(ir, BorderLayout.EAST);
        stack.add(insHead, 22);

        Card upcoming = section("Upcoming deadlines", null, upcomingList);
        donut.setPreferredSize(new Dimension(300, 200));
        Card dist = section("Task distribution", null, donut);
        stack.add(new Cols(4, new double[]{1.25, 1}, upcoming, dist), 4);

        weekly.setPreferredSize(new Dimension(300, 250));
        monthly.setPreferredSize(new Dimension(300, 250));
        stack.add(new Cols(4, new double[]{1, 1}, section("This week", null, weekly), section("This month", null, monthly)), 4);
        stats.setPreferredSize(new Dimension(300, 110));
        stack.add(section("Completion statistics", null, stats), 4);
    }

    private Card section(String title, Lbl sub, JComponent body) {
        Card c = new Card(18, new BorderLayout(0, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Lbl.h2(title), BorderLayout.WEST);
        if (sub != null) {
            sub.setHorizontalAlignment(SwingConstants.RIGHT);
            head.add(sub, BorderLayout.EAST);
        }
        c.add(head, BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    // ---------------------------------------------------------------- refresh

    private LocalDate[] rangeDates() {
        LocalDate t = Time.today();
        DayOfWeek first = DayOfWeek.valueOf(AppContext.settings.get(SettingsService.WEEK_START, "MONDAY"));
        return switch (range.getSelected()) {
            case 0 -> new LocalDate[]{t, t};
            case 1 -> { LocalDate s = Time.weekStart(t, first); yield new LocalDate[]{s, s.plusDays(6)}; }
            case 2 -> new LocalDate[]{t.withDayOfMonth(1), t.withDayOfMonth(t.lengthOfMonth())};
            default -> new LocalDate[]{customFrom, customTo};
        };
    }

    public void refresh() {
        LocalDate today = Time.today();
        int hr = java.time.LocalTime.now().getHour();
        greeting.setText(hr < 12 ? "Good morning" : hr < 18 ? "Good afternoon" : "Good evening");
        dateLine.setText(Time.dateLong(today));

        Today t = AppContext.analytics.today();
        total.setValue(t.total());
        done.setValue(t.completed());
        remaining.setValue(t.remaining());
        overdue.setValue(t.overdue());
        urgent.setValue(t.urgent());
        worked.setValue(t.focusSeconds());
        percent.setValue(t.percent());
        message.setText(messageFor(t));
        buildAlerts(t);

        List<Task> todays = AppContext.tasks.forDay(today);
        scheduleList.setTasks(todays);
        scheduleSub.setText(todays.isEmpty() ? "" : todays.size() + (todays.size() == 1 ? " task" : " tasks"));

        ring.setProgress(t.percent() / 100.0);
        ring.setColor(t.percent() >= 100 ? Theme.success : Theme.accent);
        ring.setCenter(Math.round(t.percent()) + "%", t.completed() + " of " + t.total());
        ringNote.setText(t.total() == 0 ? "Plan a task to start tracking" : t.remaining() == 0 ? "Everything planned is done" : t.remaining() + " to go today");

        List<Task> up = new ArrayList<>();
        for (Task x : AppContext.tasks.all()) if (!x.isCompleted() && x.deadline != null) up.add(x);
        up.sort(Comparator.comparingLong(x -> x.deadline));
        upcomingList.setTasks(up.size() > 5 ? up.subList(0, 5) : up);

        LocalDate[] r = rangeDates();
        rangeLabel.setText(r[0].equals(r[1]) ? Time.dateLong(r[0]) : Time.dateMed(r[0]) + " – " + Time.dateMed(r[1]));
        List<CatCount> dist = AppContext.analytics.distribution(r[0], r[1]);
        String[] names = new String[dist.size()];
        double[] vals = new double[dist.size()];
        Color[] cols = new Color[dist.size()];
        for (int i = 0; i < dist.size(); i++) {
            CatCount cc = dist.get(i);
            names[i] = cc.category() == null ? "No category" : cc.category().name;
            vals[i] = cc.count();
            cols[i] = cc.category() == null ? new Color(0x8E8E93) : cc.category().awt();
        }
        donut.setData(names, vals, cols, "tasks", "No tasks in this range");
        stats.set(AppContext.analytics.totals(r[0], r[1]));

        DayOfWeek first = DayOfWeek.valueOf(AppContext.settings.get(SettingsService.WEEK_START, "MONDAY"));
        LocalDate ws = Time.weekStart(today, first);
        List<DayStat> days = AppContext.analytics.daily(ws, ws.plusDays(6));
        String[] dl = new String[7];
        double[] planned = new double[7], completed = new double[7];
        for (int i = 0; i < 7; i++) {
            dl[i] = days.get(i).date().getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
            planned[i] = days.get(i).planned();
            completed[i] = days.get(i).completed();
        }
        weekly.setData(dl, new String[]{"Planned", "Completed"},
                new Color[]{Gfx.mix(Theme.surface, Theme.text, 0.2), Theme.accent}, new double[][]{planned, completed});

        YearMonth ym = YearMonth.from(today);
        List<DayStat> md = AppContext.analytics.daily(ym.atDay(1), ym.atEndOfMonth());
        String[] ml = new String[md.size()];
        double[] mv = new double[md.size()];
        for (int i = 0; i < md.size(); i++) {
            ml[i] = String.valueOf(md.get(i).date().getDayOfMonth());
            mv[i] = md.get(i).completed();
        }
        monthly.setData(ml, mv, "done");
        repaint();
    }

    private String messageFor(Today t) {
        if (t.overdue() > 0) return t.overdue() + (t.overdue() == 1 ? " task is" : " tasks are") + " overdue. Finish, reschedule or archive them when you're ready.";
        if (t.total() == 0) return "A clear day. Add something small to build momentum.";
        if (t.remaining() == 0) return "Everything planned for today is done. Nice work.";
        if (t.completed() == 0) return t.remaining() + " to do today. Start with the one that matters most.";
        return t.completed() + " done, " + t.remaining() + " to go. Keep the momentum.";
    }

    private void buildAlerts(Today t) {
        alerts.removeAll();
        List<Task> roll = AppContext.tasks.rolloverCandidates();
        String today = Time.today().toString();
        if (!roll.isEmpty() && !today.equals(AppContext.settings.get(SettingsService.ROLLOVER_DISMISSED, ""))) {
            Btn move = new Btn("Move to today", Btn.Kind.PRIMARY);
            move.addActionListener(e -> {
                for (Task x : roll) com.focusflow.ui.tasks.TaskActions.rescheduleTo(x, Time.today());
            });
            Btn dismiss = new Btn("Not now", Btn.Kind.TINTED);
            dismiss.addActionListener(e -> AppContext.settings.set(SettingsService.ROLLOVER_DISMISSED, today));
            alerts.add(new Banner(Theme.accent, Icons.Type.REPEAT,
                    roll.size() + (roll.size() == 1 ? " unfinished task is" : " unfinished tasks are") + " scheduled on earlier days. Move them to today?",
                    move, dismiss));
            alerts.add(Box.createVerticalStrut(8));
        }
        if (t.overdue() > 0) {
            Btn review = new Btn("Review", Btn.Kind.DANGER);
            review.addActionListener(e -> AppContext.frame.openTasks(2));
            alerts.add(new Banner(Theme.danger, Icons.Type.WARNING, t.overdue() + (t.overdue() == 1 ? " overdue task needs" : " overdue tasks need") + " attention.", review));
            alerts.add(Box.createVerticalStrut(8));
        }
        int critical = 0;
        for (Task x : AppContext.tasks.all()) {
            if (!x.isCompleted() && AppContext.urgency.evaluate(x).level() == Urgency.Level.CRITICAL) critical++;
        }
        if (critical > 0) {
            Btn show = new Btn("Show", Btn.Kind.TINTED);
            show.addActionListener(e -> AppContext.frame.openTasks(0));
            alerts.add(new Banner(Theme.warning, Icons.Type.FLAME, critical + (critical == 1 ? " task is" : " tasks are") + " due in less than a day.", show));
            alerts.add(Box.createVerticalStrut(8));
        }
        alerts.revalidate();
        alerts.repaint();
    }

    private void pickCustom() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(24, 26, 20, 26));
        JSpinner from = Pickers.date(customFrom), to = Pickers.date(customTo);
        Sheet[] ref = new Sheet[1];
        Btn apply = new Btn("Apply", Btn.Kind.PRIMARY);
        Btn cancel = new Btn("Cancel", Btn.Kind.TINTED);
        Lbl err = new Lbl(" ", Theme.font(12f), Lbl.Role.DANGER);
        apply.addActionListener(e -> {
            LocalDate a = Pickers.getDate(from), b = Pickers.getDate(to);
            if (b.isBefore(a)) { err.setText("The end date is before the start date."); return; }
            customFrom = a;
            customTo = b;
            ref[0].close();
            refresh();
        });
        cancel.addActionListener(e -> { ref[0].close(); range.setSelected(1); refresh(); });
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0; p.add(new Lbl("Custom range", Theme.bold(18f), Lbl.Role.TEXT), g);
        g.gridy = 1; g.insets = new Insets(14, 0, 0, 0); p.add(Lbl.caption("From"), g);
        g.gridy = 2; g.insets = new Insets(4, 0, 0, 0); p.add(from, g);
        g.gridy = 3; g.insets = new Insets(10, 0, 0, 0); p.add(Lbl.caption("To"), g);
        g.gridy = 4; g.insets = new Insets(4, 0, 0, 0); p.add(to, g);
        g.gridy = 5; g.insets = new Insets(8, 0, 0, 0); p.add(err, g);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(cancel);
        btns.add(apply);
        g.gridy = 6; g.insets = new Insets(10, 0, 0, 0); p.add(btns, g);
        ref[0] = Sheet.show(p, 360, 330).onClose(() -> { if (range.getSelected() == 3 && customFrom == null) range.setSelected(1); });
    }

    // --------------------------------------------------------------- pieces

    private static final class Banner extends JPanel {
        private final Color color;
        private final Icons.Type icon;

        Banner(Color color, Icons.Type icon, String text, Btn... buttons) {
            super(new BorderLayout(10, 0));
            this.color = color;
            this.icon = icon;
            setOpaque(false);
            setBorder(new EmptyBorder(10, 46, 10, 12));
            Lbl l = new Lbl(text, Theme.semi(13f), Lbl.Role.TEXT);
            add(l, BorderLayout.CENTER);
            JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            b.setOpaque(false);
            for (Btn x : buttons) b.add(x);
            add(b, BorderLayout.EAST);
            setAlignmentX(0f);
        }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            g2.setColor(Gfx.alpha(color, Theme.dark ? 0.16 : 0.10));
            g2.fill(Gfx.rr(0, 0, getWidth(), getHeight(), 16));
            Icons.paint(g2, icon, 16, getHeight() / 2.0 - 10, 20, color);
            g2.dispose();
        }
    }

    private static final class CompletionStats extends JComponent {
        private Totals t = new Totals(0, 0, 0, 0, 0, 0);
        private final com.focusflow.animations.SpringValue p = new com.focusflow.animations.SpringValue(0, 0.8, 0.95, this::repaint);

        void set(Totals t) {
            this.t = t;
            p.snap(0);
            p.to(1);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), col = w / 3;
            double e = Math.max(0, Math.min(1, p.value));
            block(g2, 0, col, "Completed vs pending", t.completed() + " / " + t.pending(), t.completed(), t.pending(), Theme.success, Theme.surfaceAlt, e);
            block(g2, col, col, "On time vs late", t.onTime() + " / " + t.late(), t.onTime(), t.late(), Theme.accent, Theme.warning, e);
            double rate = t.rate();
            int x = col * 2 + 20;
            Gfx.text(g2, "Overall completion rate", x, 14, Theme.font(12f), Theme.text2);
            Gfx.text(g2, Math.round(rate * e) + "%", x, 52, Theme.bold(28f), Theme.text);
            g2.setColor(Theme.surfaceAlt);
            g2.fill(Gfx.rr(x, 68, col - 40, 10, 10));
            g2.setColor(rate >= 80 ? Theme.success : Theme.accent);
            g2.fill(Gfx.rr(x, 68, Math.max(10, (col - 40) * rate / 100.0 * e), 10, 10));
            g2.dispose();
        }

        private void block(Graphics2D g2, int x0, int w, String title, String value, int a, int b, Color ca, Color cb, double e) {
            int x = x0 + (x0 == 0 ? 0 : 20);
            int bw = w - 40 + (x0 == 0 ? 20 : 0);
            Gfx.text(g2, title, x, 14, Theme.font(12f), Theme.text2);
            Gfx.text(g2, value, x, 52, Theme.bold(28f), Theme.text);
            g2.setColor(cb);
            g2.fill(Gfx.rr(x, 68, bw, 10, 10));
            double tot = a + b;
            if (tot > 0) {
                g2.setColor(ca);
                g2.fill(Gfx.rr(x, 68, Math.max(10, bw * (a / tot) * e), 10, 10));
            }
        }
    }
}
