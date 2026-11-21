package com.focusflow.widget;

import com.focusflow.app.AppContext;
import com.focusflow.models.Task;
import com.focusflow.models.Urgency;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.Btn;
import com.focusflow.ui.components.Lbl;
import com.focusflow.ui.components.Pickers;
import com.focusflow.ui.components.Segmented;
import com.focusflow.ui.tasks.TaskActions;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A small window opened from the widget, so basic things can be done without opening the whole app:
 * add a task (just a date, or at a time), and look at / rename / complete / delete an existing one.
 */
public final class QuickTaskDialog extends JDialog {
    private static QuickTaskDialog current;

    /** New task on the given day (today when null). */
    public static void create(LocalDate date) { show(new QuickTaskDialog(null, date == null ? Time.today() : date)); }

    /** Look at an existing task. */
    public static void view(Task t) {
        Task cur = AppContext.tasks.get(t.id);
        if (cur != null) show(new QuickTaskDialog(cur, null));
    }

    private static void show(QuickTaskDialog d) {
        if (current != null) current.dispose();
        current = d;
        d.setVisible(true);
        d.title.requestFocusInWindow();
        d.title.selectAll();
    }

    private final Task task;                       // null when creating
    private final JTextField title = new JTextField();
    private final Segmented kind;
    private final JSpinner date, time;
    private final Segmented length = new Segmented(new String[]{"30 min", "1 hour", "2 hours"}, 1, null);
    private final JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

    private QuickTaskDialog(Task task, LocalDate day) {
        super((Frame) null, false);
        this.task = task;
        setUndecorated(true);
        setAlwaysOnTop(true);
        setType(Window.Type.UTILITY);
        try { setBackground(new Color(0, 0, 0, 0)); } catch (UnsupportedOperationException ignored) { }

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.aa(g);
                g2.setColor(Theme.surface);
                g2.fill(Gfx.rr(0.5, 0.5, getWidth() - 1, getHeight() - 1, 22));
                g2.setColor(Theme.separator);
                g2.draw(Gfx.rr(0.5, 0.5, getWidth() - 1, getHeight() - 1, 22));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 18, 22));

        boolean editing = task != null;
        card.add(left(new Lbl(editing ? "Task" : "New task", Theme.bold(20f), Lbl.Role.TEXT)));
        card.add(Box.createVerticalStrut(14));

        title.setText(editing ? task.title : "");
        title.putClientProperty("JTextField.placeholderText", "What is happening?");
        title.putClientProperty("JComponent.roundRect", true);
        title.setFont(Theme.font(14f));
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        title.setPreferredSize(new Dimension(300, 38));
        card.add(left(title));

        if (editing) {
            card.add(Box.createVerticalStrut(12));
            card.add(left(new Lbl(describe(task), Theme.font(13f), Lbl.Role.TEXT2)));
            kind = null;
            date = null;
            time = null;
        } else {
            card.add(Box.createVerticalStrut(14));
            kind = new Segmented(new String[]{"Just a date", "At a time"}, 0, i -> { timeRow.setVisible(i == 1); pack(); });
            card.add(left(kind));
            card.add(Box.createVerticalStrut(12));
            date = Pickers.date(day);
            date.setPreferredSize(new Dimension(170, 34));
            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            dateRow.setOpaque(false);
            dateRow.add(new Lbl("Date", Theme.font(13f), Lbl.Role.TEXT2));
            dateRow.add(date);
            card.add(left(dateRow));
            card.add(Box.createVerticalStrut(10));
            time = Pickers.time(LocalTime.of(Math.min(23, Math.max(8, LocalTime.now().getHour() + 1)), 0));
            time.setPreferredSize(new Dimension(120, 34));
            timeRow.setOpaque(false);
            timeRow.add(new Lbl("Start", Theme.font(13f), Lbl.Role.TEXT2));
            timeRow.add(time);
            timeRow.add(length);
            timeRow.setVisible(false);
            card.add(left(timeRow));
            card.add(Box.createVerticalStrut(8));
            card.add(left(new Lbl("<html><div style='width:290px'>A date-only task just happens on that day (an exam, say). It is highlighted that day and goes by itself afterwards.</div></html>",
                    Theme.font(12f), Lbl.Role.TEXT3)));
        }

        card.add(Box.createVerticalStrut(18));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        if (editing) {
            Btn del = new Btn("Delete", Btn.Kind.DANGER_TINTED);
            del.addActionListener(e -> { AppContext.tasks.delete(task); close(); });
            Btn full = new Btn("Open in app", Btn.Kind.TINTED);
            full.addActionListener(e -> { close(); AppContext.inMain(() -> TaskActions.open(task)); });
            Btn done = new Btn(task.isCompleted() ? "Reopen" : "Mark done", Btn.Kind.TINTED);
            done.addActionListener(e -> { toggleDone(task); close(); });
            Btn save = new Btn("Save", Btn.Kind.PRIMARY);
            save.addActionListener(e -> save());
            buttons.add(del);
            buttons.add(full);
            buttons.add(done);
            buttons.add(save);
        } else {
            Btn cancel = new Btn("Cancel", Btn.Kind.TINTED);
            cancel.addActionListener(e -> close());
            Btn add = new Btn("Add task", Btn.Kind.PRIMARY);
            add.addActionListener(e -> save());
            buttons.add(cancel);
            buttons.add(add);
        }
        card.add(left(buttons));

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        root.add(card, BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setOpaque(false);

        title.addActionListener(e -> save());
        getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { if (current == QuickTaskDialog.this) current = null; }
        });
        pack();
        setSize(Math.max(getWidth(), editing ? 470 : 360), getHeight());
        placeNearWidget();
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void placeNearWidget() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = screen.x + (screen.width - getWidth()) / 2, y = screen.y + (screen.height - getHeight()) / 3;
        setLocation(x, y);
    }

    private void close() { dispose(); }

    private void save() {
        String text = title.getText().trim();
        if (text.isEmpty()) { title.requestFocusInWindow(); return; }
        if (task != null) {
            Task c = task.copy();
            c.title = text;
            AppContext.tasks.save(c);
            close();
            return;
        }
        Task t = new Task();
        t.title = text;
        LocalDate d = Pickers.getDate(date);
        if (kind.getSelected() == 0) {
            t.setDeadlineDay(d);
        } else {
            LocalDateTime start = d.atTime(Pickers.getTime(time));
            int mins = switch (length.getSelected()) { case 0 -> 30; case 2 -> 120; default -> 60; };
            t.setStart(start);
            t.setEnd(start.plusMinutes(mins));
            t.allDay = false;
        }
        AppContext.tasks.save(t);
        close();
    }

    private static void toggleDone(Task t) {
        Task cur = AppContext.tasks.get(t.id);
        if (cur == null) return;
        if (cur.isCompleted()) AppContext.tasks.reopen(cur); else AppContext.tasks.complete(cur);
    }

    /** Marks a task done or open again straight from the widget. */
    static void toggle(Task t) { toggleDone(t); }

    private static String describe(Task t) {
        StringBuilder sb = new StringBuilder();
        if (t.isDayEvent()) sb.append(Time.relativeDay(t.deadlineDate()));
        else if (t.start != null && t.allDay) sb.append(Time.relativeDay(t.scheduledDate())).append(" · all day");
        else if (t.start != null) sb.append(Time.relativeDay(t.scheduledDate())).append(", ").append(Time.time(t.startDT()));
        else if (t.deadline != null) sb.append("Due ").append(Time.relativeDay(t.deadlineDate())).append(", ").append(Time.time(t.deadlineDT()));
        else sb.append("No date");
        if (t.isCompleted()) sb.append("  ·  Done");
        else if (t.deadline != null) {
            Urgency u = AppContext.urgency.evaluate(t);
            sb.append("  ·  ").append(u.level().label).append(" (").append(u.countdown()).append(")");
        }
        return sb.toString();
    }
}
