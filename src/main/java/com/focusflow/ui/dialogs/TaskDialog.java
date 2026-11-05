package com.focusflow.ui.dialogs;

import com.focusflow.app.AppContext;
import com.focusflow.models.*;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.ui.tasks.TaskActions;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Create / edit sheet. Scheduled work time and the final deadline are separate on purpose. */
public final class TaskDialog extends JPanel {
    private static final Category NONE = new Category(0, "No category", "#8E8E93");

    private final Task orig;
    private final JTextField title = new JTextField();
    private final JTextArea desc = new JTextArea(2, 20), notes = new JTextArea(3, 20);
    private final JComboBox<Category> cat = new JComboBox<>();
    private final JComboBox<Status> status = new JComboBox<>(Status.values());
    private final JComboBox<Recurrence> recur = new JComboBox<>(Recurrence.values());
    private final Segmented prio;
    private final Toggle schedT, allDayT, dlT;
    private final JSpinner sDate, sStart, sEnd, uDate, dDate, dTime, est;
    private final JPanel subs = new JPanel();
    private final List<SubRow> subRows = new ArrayList<>();
    private final JTextField newSub = new JTextField();
    private final Lbl error = new Lbl("", Theme.font(12f), Lbl.Role.DANGER);
    private final Lbl hint = new Lbl(" ", Theme.font(12f), Lbl.Role.TEXT2);
    private final VStack form = new VStack();
    private Sheet sheet;
    private int row;

    public static void open(Task existing, LocalDateTime defStart, LocalDateTime defEnd, Runnable onClose) {
        TaskDialog d = new TaskDialog(existing, defStart, defEnd);
        d.sheet = Sheet.show(d, 680, 780).onClose(onClose);
        SwingUtilities.invokeLater(d.title::requestFocusInWindow);
    }

    public static void create() { open(null, null, null, null); }

    private TaskDialog(Task existing, LocalDateTime defStart, LocalDateTime defEnd) {
        super(new BorderLayout());
        orig = existing;
        setOpaque(false);

        LocalDate baseDate = defStart != null ? defStart.toLocalDate() : Time.today();
        boolean scheduled = existing != null ? existing.start != null : defStart != null;
        boolean allDay = existing != null && existing.allDay;
        LocalDateTime s = existing != null && existing.start != null ? existing.startDT() : defStart != null ? defStart : baseDate.atTime(9, 0);
        LocalDateTime e = existing != null && existing.end != null ? existing.endDT()
                : defEnd != null ? defEnd : s.plusMinutes(AppContext.settings.getInt("calendar.defaultDuration", 60));
        if (!e.toLocalDate().equals(s.toLocalDate())) e = s.toLocalDate().atTime(23, 59);
        boolean hasDl = existing != null && existing.deadline != null;
        LocalDateTime dl = hasDl ? existing.deadlineDT() : baseDate.atTime(18, 0);

        sDate = Pickers.date(s.toLocalDate());
        sStart = Pickers.time(s.toLocalTime());
        sEnd = Pickers.time(e.toLocalTime());
        uDate = Pickers.date(existing != null && existing.allDay && existing.end != null ? existing.endDT().toLocalDate() : s.toLocalDate());
        dDate = Pickers.date(dl.toLocalDate());
        dTime = Pickers.time(dl.toLocalTime());
        int estimate = existing != null ? existing.estimatedMinutes : (defStart != null && defEnd != null ? (int) java.time.Duration.between(defStart, defEnd).toMinutes() : 0);
        est = new JSpinner(new SpinnerNumberModel(estimate, 0, 6000, 15));
        est.setEditor(new JSpinner.NumberEditor(est, "0' min'"));

        schedT = new Toggle(scheduled, v -> { syncEnabled(); updateHint(); });
        allDayT = new Toggle(allDay, v -> { syncEnabled(); updateHint(); });
        dlT = new Toggle(hasDl, v -> { syncEnabled(); updateHint(); });
        prio = new Segmented(new String[]{"Low", "Normal", "High", "Critical"}, existing != null ? existing.priority.ordinal() : 1, i -> {});

        cat.addItem(NONE);
        for (Category c : AppContext.categories.all()) cat.addItem(c);
        if (existing != null && existing.categoryId != null) {
            Category c = AppContext.categories.byId(existing.categoryId);
            if (c != null) cat.setSelectedItem(c);
        }
        ListCellRenderer<Object> rend = new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v instanceof Category c) {
                    lb.setText(c.name);
                    lb.setIcon(c.id == 0 ? null : new Dot(c.awt()));
                } else if (v instanceof Status st) lb.setText(st.label);
                else if (v instanceof Recurrence r) lb.setText(r.label);
                return lb;
            }
        };
        cat.setRenderer(rend);
        status.setRenderer(rend);
        recur.setRenderer(rend);
        if (existing != null) {
            title.setText(existing.title);
            desc.setText(existing.description);
            notes.setText(existing.notes);
            status.setSelectedItem(existing.status);
            recur.setSelectedItem(existing.recurrence);
        }
        title.putClientProperty("JTextField.placeholderText", "What needs to get done?");
        title.setFont(Theme.semi(18f));
        title.setPreferredSize(new Dimension(100, 44));
        newSub.putClientProperty("JTextField.placeholderText", "Add a subtask and press Enter");
        for (JTextArea a : new JTextArea[]{desc, notes}) {
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            a.setOpaque(false);
            a.setBorder(new EmptyBorder(8, 10, 8, 10));
        }
        subs.setLayout(new BoxLayout(subs, BoxLayout.Y_AXIS));
        subs.setOpaque(false);
        if (existing != null) for (Subtask st : existing.subtasks) addSubRow(st.title, st.done, st.doneAt);
        newSub.addActionListener(ev -> {
            String t = newSub.getText().trim();
            if (!t.isEmpty()) { addSubRow(t, false, null); newSub.setText(""); }
        });
        title.addActionListener(ev -> save());

        buildForm();
        JScrollPane sp = Refresher.scroll(form);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
        add(header(), BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        syncEnabled();
        updateHint();
    }

    // ------------------------------------------------------------------ layout

    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.setBorder(new EmptyBorder(22, 28, 6, 18));
        h.add(new Lbl(orig == null ? "New task" : "Edit task", Theme.bold(20f), Lbl.Role.TEXT), BorderLayout.WEST);
        Btn close = Btn.icon(Icons.Type.CLOSE, Btn.Kind.GHOST, "Close");
        close.addActionListener(e -> sheet.close());
        h.add(close, BorderLayout.EAST);
        return h;
    }

    private JComponent footer() {
        JPanel f = new JPanel(new BorderLayout());
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(8, 28, 22, 28));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        if (orig != null) {
            Btn del = new Btn("Delete", Btn.Kind.DANGER_TINTED, Icons.Type.TRASH);
            del.addActionListener(e -> { sheet.close(); TaskActions.confirmDelete(orig); });
            left.add(del);
        }
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        Btn cancel = new Btn("Cancel", Btn.Kind.TINTED);
        cancel.addActionListener(e -> sheet.close());
        Btn save = new Btn(orig == null ? "Add task" : "Save changes", Btn.Kind.PRIMARY);
        save.addActionListener(e -> save());
        right.add(error);
        right.add(cancel);
        right.add(save);
        f.add(left, BorderLayout.WEST);
        f.add(right, BorderLayout.EAST);
        return f;
    }

    private void buildForm() {
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(8, 28, 8, 28));
        addRow(title, 0);
        addRow(field("Description", box(desc)), 12);

        JPanel three = new JPanel(new GridLayout(1, 2, 12, 0));
        three.setOpaque(false);
        three.add(field("Category", cat));
        three.add(field("Status", status));
        addRow(three, 12);
        addRow(field("Priority", prio), 12);

        addRow(switchRow("Schedule on calendar", "A work session you plan to do", schedT), 18);
        JPanel sched = new JPanel(new GridLayout(1, 3, 10, 0));
        sched.setOpaque(false);
        sched.add(field("Date", sDate));
        sched.add(field("Start", sStart));
        sched.add(field("End", sEnd));
        addRow(sched, 8);
        addRow(switchRow("All day", "No specific time. Use \"Until\" for work that spans several days", allDayT), 8);
        addRow(field("Until (last day, optional)", uDate), 8);

        addRow(switchRow("Deadline", "When it has to be finished. Separate from the schedule", dlT), 18);
        JPanel dl = new JPanel(new GridLayout(1, 2, 10, 0));
        dl.setOpaque(false);
        dl.add(field("Due date", dDate));
        dl.add(field("Due time", dTime));
        addRow(dl, 8);
        addRow(hint, 6);

        JPanel two = new JPanel(new GridLayout(1, 2, 12, 0));
        two.setOpaque(false);
        two.add(field("Estimated duration", est));
        two.add(field("Repeat", recur));
        addRow(two, 16);

        addRow(field("Subtasks", wrapSubs()), 16);
        addRow(field("Notes", box(notes)), 16);

        java.awt.event.ActionListener ch = e -> updateHint();
        for (JSpinner sp : new JSpinner[]{sDate, sStart, sEnd, uDate, dDate, dTime, est}) sp.addChangeListener(e -> updateHint());
        prio.setToolTipText("Higher priority raises the urgency score");
        GridBagConstraints f = new GridBagConstraints();
        f.gridy = 9999;
        f.weighty = 1;
        form.add(Box.createGlue(), f);
    }

    private JComponent wrapSubs() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(subs, BorderLayout.NORTH);
        p.add(newSub, BorderLayout.CENTER);
        return p;
    }

    private void addRow(JComponent c, int gap) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row++;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTH;
        g.insets = new Insets(gap, 0, 0, 0);
        form.add(c, g);
    }

    private static JComponent field(String label, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(Lbl.caption(label), BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static JComponent switchRow(String label, String sub, Toggle t) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        JPanel txt = new JPanel(new GridLayout(2, 1));
        txt.setOpaque(false);
        txt.add(new Lbl(label, Theme.semi(14f), Lbl.Role.TEXT));
        txt.add(Lbl.caption(sub));
        p.add(txt, BorderLayout.CENTER);
        p.add(t, BorderLayout.EAST);
        return p;
    }

    private static JComponent box(JTextArea a) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.aa(g);
                g2.setColor(Theme.surfaceAlt);
                g2.fill(Gfx.rr(0, 0, getWidth(), getHeight(), 12));
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.add(a, BorderLayout.CENTER);
        return p;
    }

    private void syncEnabled() {
        boolean sc = schedT.isOn(), ad = allDayT.isOn();
        sDate.setEnabled(sc);
        sStart.setEnabled(sc && !ad);
        sEnd.setEnabled(sc && !ad);
        uDate.setEnabled(sc && ad);
        allDayT.setEnabled(sc);
        boolean dl = dlT.isOn();
        dDate.setEnabled(dl);
        dTime.setEnabled(dl);
    }

    // --------------------------------------------------------------- subtasks

    private final class SubRow extends JPanel {
        final JCheckBox done = new JCheckBox();
        final JTextField text = new JTextField();
        final Long doneAt;

        SubRow(String t, boolean d, Long doneAt) {
            super(new BorderLayout(8, 0));
            setOpaque(false);
            this.doneAt = doneAt;
            setBorder(new EmptyBorder(0, 0, 6, 0));
            done.setSelected(d);
            done.setOpaque(false);
            text.setText(t);
            Btn rm = Btn.icon(Icons.Type.CLOSE, Btn.Kind.GHOST, "Remove subtask");
            rm.addActionListener(e -> { subRows.remove(this); subs.remove(this); subs.revalidate(); form.revalidate(); form.repaint(); });
            add(done, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(rm, BorderLayout.EAST);
        }
    }

    private void addSubRow(String t, boolean done, Long doneAt) {
        SubRow r = new SubRow(t, done, doneAt);
        subRows.add(r);
        subs.add(r);
        subs.revalidate();
        form.revalidate();
        form.repaint();
    }

    // -------------------------------------------------------------- save logic

    private Task build() {
        Task t = orig != null ? orig.copy() : new Task();
        t.title = title.getText().trim();
        t.description = desc.getText().trim();
        t.notes = notes.getText().trim();
        Category c = (Category) cat.getSelectedItem();
        t.categoryId = c == null || c.id == 0 ? null : c.id;
        t.priority = Priority.of(prio.getSelected());
        t.status = (Status) status.getSelectedItem();
        t.recurrence = (Recurrence) recur.getSelectedItem();
        if (schedT.isOn()) {
            LocalDate d = Pickers.getDate(sDate);
            if (allDayT.isOn()) {
                t.allDay = true;
                t.setStart(d.atStartOfDay());
                LocalDate until = Pickers.getDate(uDate);
                t.end = until.isAfter(d) ? Time.ms(until.atStartOfDay()) : null;
            } else {
                t.allDay = false;
                t.setStart(d.atTime(Pickers.getTime(sStart)));
                t.setEnd(d.atTime(Pickers.getTime(sEnd)));
            }
        } else {
            t.start = null;
            t.end = null;
            t.allDay = false;
        }
        if (dlT.isOn()) t.setDeadline(Pickers.getDate(dDate).atTime(Pickers.getTime(dTime)));
        else t.deadline = null;
        t.estimatedMinutes = (Integer) est.getValue();
        t.subtasks = new ArrayList<>();
        for (SubRow r : subRows) {
            String s = r.text.getText().trim();
            if (s.isEmpty()) continue;
            Subtask st = new Subtask(s, r.done.isSelected());
            st.doneAt = !st.done ? null : (r.doneAt != null ? r.doneAt : Time.now());
            t.subtasks.add(st);
        }
        return t;
    }

    private void updateHint() {
        if (hint == null || dlT == null) return;
        if (!dlT.isOn()) {
            hint.setText("No deadline, so urgency isn't calculated.");
            return;
        }
        Urgency u = AppContext.urgency.evaluate(build());
        hint.setText("Urgency " + u.score() + "/100 (" + u.level().label + "). " + u.message() + " This is an estimate, not a prediction.");
    }

    private void save() {
        Task t = build();
        if (t.title.isEmpty()) { error.setText("Give the task a title."); title.requestFocusInWindow(); return; }
        if (t.isTimed() && !t.endDT().isAfter(t.startDT())) { error.setText("The end time must be after the start."); return; }
        if (orig != null) {
            Task cur = AppContext.tasks.get(orig.id);
            if (cur == null) { error.setText("This task no longer exists."); return; }
        }
        try {
            AppContext.tasks.save(t);
        } catch (RuntimeException ex) {
            error.setText("Could not save: " + ex.getMessage());
            return;
        }
        Toast.show(orig == null ? "Task added" : "Changes saved");
        sheet.close();
    }

    private record Dot(Color c) implements Icon {
        @Override public void paintIcon(Component comp, Graphics g, int x, int y) {
            Graphics2D g2 = Gfx.aa(g);
            g2.setColor(c);
            g2.fillOval(x, y + 1, 10, 10);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 12; }
    }
}
