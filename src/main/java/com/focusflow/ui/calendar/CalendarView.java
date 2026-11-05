package com.focusflow.ui.calendar;

import com.focusflow.app.AppContext;
import com.focusflow.models.Category;
import com.focusflow.models.Status;
import com.focusflow.models.Task;
import com.focusflow.services.SettingsService;
import com.focusflow.services.TaskService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.ui.dialogs.TaskDialog;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Calendar: day, week, month and agenda views with category and status filters. */
public class CalendarView extends JPanel {
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private LocalDate anchor = Time.today();
    private int mode;
    private final TimeGridPane grid = new TimeGridPane();
    private final MonthGrid month = new MonthGrid();
    private final AgendaPanel agenda = new AgendaPanel();
    private final FadePane host = new FadePane();
    private final Lbl title = Lbl.title(""), sub = Lbl.sub("");
    private final Segmented seg;
    private final JComboBox<String> catBox = new JComboBox<>(), statusBox = new JComboBox<>(new String[]{
            "All statuses", "Not started", "In progress", "Completed", "Overdue"});
    private final List<Category> cats = new ArrayList<>();
    private boolean updating;

    public CalendarView() {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 22, 28));
        mode = Math.max(0, Math.min(3, AppContext.settings.getInt(SettingsService.CAL_MODE, 1)));

        seg = new Segmented(new String[]{"Day", "Week", "Month", "Agenda"}, mode, this::setMode);
        Btn prev = Btn.icon(Icons.Type.CHEV_L, Btn.Kind.TINTED, "Previous");
        Btn next = Btn.icon(Icons.Type.CHEV_R, Btn.Kind.TINTED, "Next");
        Btn today = new Btn("Today", Btn.Kind.TINTED);
        Btn add = new Btn("New task", Btn.Kind.PRIMARY, Icons.Type.PLUS);
        prev.addActionListener(e -> step(-1));
        next.addActionListener(e -> step(1));
        today.addActionListener(e -> goToday());
        add.addActionListener(e -> TaskDialog.create());
        catBox.addActionListener(e -> { if (!updating) refresh(); });
        statusBox.addActionListener(e -> { if (!updating) refresh(); });
        catBox.setPreferredSize(new Dimension(150, 36));
        statusBox.setPreferredSize(new Dimension(140, 36));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 0));
        left.setOpaque(false);
        left.add(title);
        left.add(sub);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nav.setOpaque(false);
        nav.add(prev);
        nav.add(today);
        nav.add(next);
        nav.add(add);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(left, BorderLayout.WEST);
        top.add(nav, BorderLayout.EAST);

        JPanel viewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        viewRow.setOpaque(false);
        viewRow.add(seg);
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(catBox);
        filterRow.add(statusBox);
        JPanel second = new JPanel(new BorderLayout());
        second.setOpaque(false);
        second.add(viewRow, BorderLayout.WEST);
        second.add(filterRow, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.add(top, BorderLayout.NORTH);
        header.add(second, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        Card card = new Card(0, new BorderLayout()).clip();
        card.add(host, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        grid.setOnOpenDay(this::showDay);
        month.setOnOpenDay(this::showDay);
        host.add(currentView());

        bind(KeyEvent.VK_LEFT, "calPrev", () -> step(-1));
        bind(KeyEvent.VK_RIGHT, "calNext", () -> step(1));
        Refresher.install(this, this::refresh, AppContext.tasks, AppContext.categories, AppContext.settings);
        refresh();
    }

    private void bind(int key, String name, Runnable r) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (Sheet.isOpen()) return;
                if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent) return;
                r.run();
            }
        });
    }

    private JComponent currentView() {
        return switch (mode) {
            case 2 -> month;
            case 3 -> agenda;
            default -> grid;
        };
    }

    public void goToday() {
        anchor = Time.today();
        refresh();
        if (mode <= 1) grid.scrollToHour(Math.max(0, java.time.LocalTime.now().getHour() - 2));
    }

    public void showDay(LocalDate d) {
        anchor = d;
        seg.setSelected(0);
        setMode(0);
    }

    private void step(int dir) {
        host.transition(() -> {
            anchor = switch (mode) {
                case 0 -> anchor.plusDays(dir);
                case 1 -> anchor.plusWeeks(dir);
                case 2 -> anchor.plusMonths(dir);
                default -> anchor.plusDays(30L * dir);
            };
            refresh();
        }, dir);
    }

    private void setMode(int m) {
        int dir = m >= mode ? 1 : -1;
        host.transition(() -> {
            mode = m;
            AppContext.settings.setQuiet(SettingsService.CAL_MODE, String.valueOf(m));
            host.removeAll();
            host.add(currentView());
            refresh();
        }, dir);
    }

    private void syncFilters() {
        List<Category> now = AppContext.categories.all();
        boolean same = now.size() == cats.size();
        if (same) for (int i = 0; i < now.size(); i++) if (now.get(i).id != cats.get(i).id || !now.get(i).name.equals(cats.get(i).name)) same = false;
        if (same) return;
        updating = true;
        int sel = catBox.getSelectedIndex();
        cats.clear();
        cats.addAll(now);
        catBox.removeAllItems();
        catBox.addItem("All categories");
        for (Category c : cats) catBox.addItem(c.name);
        catBox.setSelectedIndex(sel > 0 && sel <= cats.size() ? sel : 0);
        updating = false;
    }

    public void refresh() {
        syncFilters();
        List<Task> list = new ArrayList<>();
        int ci = catBox.getSelectedIndex();
        int si = statusBox.getSelectedIndex();
        for (Task t : AppContext.tasks.all()) {
            if (ci > 0 && ci <= cats.size() && (t.categoryId == null || t.categoryId != cats.get(ci - 1).id)) continue;
            boolean ok = switch (si) {
                case 1 -> t.status == Status.NOT_STARTED;
                case 2 -> t.status == Status.IN_PROGRESS;
                case 3 -> t.status == Status.COMPLETED;
                case 4 -> TaskService.isOverdue(t);
                default -> true;
            };
            if (ok) list.add(t);
        }
        DayOfWeek fd = DayOfWeek.valueOf(AppContext.settings.get(SettingsService.WEEK_START, "MONDAY"));
        switch (mode) {
            case 0 -> {
                grid.setData(anchor, 1, list);
                title.setText(Time.dateLong(anchor));
                sub.setText(Time.relativeDay(anchor).equals(Time.dateShort(anchor)) ? " " : Time.relativeDay(anchor));
            }
            case 1 -> {
                LocalDate s = Time.weekStart(anchor, fd);
                grid.setData(s, 7, list);
                title.setText(anchor.format(MONTH_YEAR));
                sub.setText(Time.dateShort(s) + " – " + Time.dateShort(s.plusDays(6)));
            }
            case 2 -> {
                month.setData(anchor, list);
                title.setText(anchor.format(MONTH_YEAR));
                sub.setText(" ");
            }
            default -> {
                agenda.setData(anchor.isBefore(Time.today()) ? Time.today() : anchor, list);
                title.setText("Agenda");
                sub.setText("The next 30 days");
            }
        }
        repaint();
    }
}
