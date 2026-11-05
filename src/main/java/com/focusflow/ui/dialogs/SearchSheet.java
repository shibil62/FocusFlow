package com.focusflow.ui.dialogs;

import com.focusflow.app.AppContext;
import com.focusflow.models.Category;
import com.focusflow.models.Task;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.ui.tasks.TaskActions;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Global task search (Ctrl+K). Matches title, description, notes, subtasks and category. */
public final class SearchSheet {
    private SearchSheet() {}

    public static void open() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(22, 22, 18, 22));
        JTextField field = new JTextField();
        field.putClientProperty("JTextField.placeholderText", "Search all tasks");
        field.setFont(Theme.font(16f));
        field.setPreferredSize(new Dimension(100, 44));
        VStack results = new VStack();
        root.add(field, BorderLayout.NORTH);
        JScrollPane sp = Refresher.scroll(results);
        root.add(sp, BorderLayout.CENTER);
        Sheet[] ref = new Sheet[1];
        List<Task> current = new ArrayList<>();

        Runnable refresh = () -> {
            String q = field.getText().trim().toLowerCase();
            results.clear();
            current.clear();
            if (q.isEmpty()) {
                results.add(Lbl.sub("Type to search titles, notes, subtasks and categories."), 8);
            } else {
                for (Task t : AppContext.tasks.allIncludingArchived()) {
                    if (matches(t, q)) current.add(t);
                    if (current.size() >= 30) break;
                }
                if (current.isEmpty()) results.add(Lbl.sub("No tasks match “" + field.getText().trim() + "”."), 8);
                for (Task t : current) results.add(new Result(t, () -> { ref[0].close(); TaskActions.open(t); }), 2);
            }
            results.revalidate();
            results.repaint();
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh.run(); }
            public void removeUpdate(DocumentEvent e) { refresh.run(); }
            public void changedUpdate(DocumentEvent e) { refresh.run(); }
        });
        field.addActionListener(e -> {
            if (!current.isEmpty()) { Task t = current.get(0); ref[0].close(); TaskActions.open(t); }
        });
        refresh.run();
        ref[0] = Sheet.show(root, 560, 480);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
    }

    private static boolean matches(Task t, String q) {
        if (t.title.toLowerCase().contains(q) || t.description.toLowerCase().contains(q) || t.notes.toLowerCase().contains(q)) return true;
        Category c = AppContext.categories.byId(t.categoryId);
        if (c != null && c.name.toLowerCase().contains(q)) return true;
        return t.subtasks.stream().anyMatch(s -> s.title.toLowerCase().contains(q));
    }

    private static final class Result extends JComponent {
        private final Task t;
        private boolean hover;

        Result(Task t, Runnable onClick) {
            this.t = t;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { onClick.run(); }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 48); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            if (hover) {
                g2.setColor(Theme.hover);
                g2.fill(Gfx.rr(0, 0, getWidth(), getHeight(), 12));
            }
            g2.setColor(TaskVisuals.base(t));
            g2.fillOval(14, getHeight() / 2 - 5, 10, 10);
            FontMetrics fm = g2.getFontMetrics(Theme.semi(14f));
            Gfx.text(g2, Gfx.fit(t.title, fm, getWidth() - 60), 36, 21, Theme.semi(14f), t.isCompleted() ? Theme.text3 : Theme.text);
            String sub = t.start != null ? Time.relativeDay(t.scheduledDate()) : t.deadline != null ? "Due " + Time.relativeDay(t.deadlineDate()) : "Unscheduled";
            if (t.archived) sub += " · Archived";
            Gfx.text(g2, sub, 36, 39, Theme.font(12f), Theme.text2);
            g2.dispose();
        }
    }
}
