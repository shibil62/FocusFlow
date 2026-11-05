package com.focusflow.ui.tasks;

import com.focusflow.app.AppContext;
import com.focusflow.models.Category;
import com.focusflow.models.Task;
import com.focusflow.services.TaskService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.ui.dialogs.TaskDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Full task list with search, status and category filters and sorting. */
public class TasksView extends JPanel {
    private static final String[] FILTERS = {"Active", "Today", "Overdue", "Completed", "Archived", "All"};
    private static final String[] SORTS = {"Urgency", "Deadline", "Scheduled", "Priority", "Newest", "Title"};

    private final JTextField search = new JTextField();
    private final Segmented filter;
    private final JComboBox<String> catBox = new JComboBox<>(), sortBox = new JComboBox<>(SORTS);
    private final TaskList list = new TaskList(null);
    private final Lbl count = Lbl.sub("");
    private final List<Category> cats = new ArrayList<>();
    private boolean updating;

    public TasksView() {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 22, 28));
        filter = new Segmented(FILTERS, 0, i -> refresh());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(Lbl.title("Tasks"));
        left.add(count);
        top.add(left, BorderLayout.WEST);
        Btn add = new Btn("New task", Btn.Kind.PRIMARY, Icons.Type.PLUS);
        add.addActionListener(e -> TaskDialog.create());
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        r.setOpaque(false);
        r.add(add);
        top.add(r, BorderLayout.EAST);

        search.putClientProperty("JTextField.placeholderText", "Search tasks");
        search.setPreferredSize(new Dimension(200, 36));
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        catBox.addActionListener(e -> { if (!updating) refresh(); });
        sortBox.addActionListener(e -> { if (!updating) refresh(); });
        catBox.setPreferredSize(new Dimension(150, 36));
        sortBox.setPreferredSize(new Dimension(120, 36));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filterRow.setOpaque(false);
        filterRow.add(filter);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(catBox);
        right.add(new Lbl("Sort", Theme.font(12f), Lbl.Role.TEXT2));
        right.add(sortBox);
        JPanel toolRow = new JPanel(new BorderLayout(10, 0));
        toolRow.setOpaque(false);
        toolRow.add(search, BorderLayout.CENTER);
        toolRow.add(right, BorderLayout.EAST);
        JPanel bar = new JPanel(new BorderLayout(0, 10));
        bar.setOpaque(false);
        bar.add(filterRow, BorderLayout.NORTH);
        bar.add(toolRow, BorderLayout.SOUTH);

        JPanel head = new JPanel(new BorderLayout(0, 14));
        head.setOpaque(false);
        head.add(top, BorderLayout.NORTH);
        head.add(bar, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        list.setBorder(new EmptyBorder(4, 0, 12, 0));
        add(Refresher.scroll(list), BorderLayout.CENTER);
        Refresher.install(this, this::refresh, AppContext.tasks, AppContext.categories);
        refresh();
    }

    public void setFilter(int i) {
        filter.setSelected(i);
        refresh();
    }

    private void syncCats() {
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
        syncCats();
        String q = search.getText().trim().toLowerCase();
        int f = filter.getSelected();
        int ci = catBox.getSelectedIndex();
        LocalDate today = com.focusflow.utilities.Time.today();
        List<Task> out = new ArrayList<>();
        for (Task t : AppContext.tasks.allIncludingArchived()) {
            if (f == 4 ? !t.archived : (f != 5 && t.archived)) continue;
            boolean ok = switch (f) {
                case 0 -> !t.isCompleted();
                case 1 -> today.equals(t.start != null ? t.scheduledDate() : t.deadline != null ? t.deadlineDate() : null);
                case 2 -> TaskService.isOverdue(t);
                case 3 -> t.isCompleted();
                default -> true;
            };
            if (!ok) continue;
            if (ci > 0 && ci <= cats.size() && (t.categoryId == null || t.categoryId != cats.get(ci - 1).id)) continue;
            if (!q.isEmpty() && !(t.title.toLowerCase().contains(q) || t.description.toLowerCase().contains(q)
                    || t.notes.toLowerCase().contains(q) || t.subtasks.stream().anyMatch(s -> s.title.toLowerCase().contains(q)))) continue;
            out.add(t);
        }
        out.sort(comparator(sortBox.getSelectedIndex()));
        count.setText(out.size() + (out.size() == 1 ? " task" : " tasks"));
        if (out.isEmpty()) {
            list.setTasks(List.of());
            list.clear();
            String[] msg = switch (f) {
                case 2 -> new String[]{"Nothing is overdue", "You're on top of your deadlines."};
                case 3 -> new String[]{"No completed tasks yet", "Finished tasks will collect here."};
                case 4 -> new String[]{"No archived tasks", "Archive tasks you want out of the way."};
                default -> new String[]{q.isEmpty() ? "No tasks here" : "No matches", q.isEmpty() ? "Add a task to get started." : "Try a different search or filter."};
            };
            list.add(new EmptyState(Icons.Type.TASKS, msg[0], msg[1], 260), 20);
            list.revalidate();
            list.repaint();
        } else {
            list.setTasks(out);
        }
    }

    private Comparator<Task> comparator(int sort) {
        Comparator<Task> byDone = Comparator.comparing(Task::isCompleted);
        return switch (sort) {
            case 1 -> byDone.thenComparingLong(t -> t.deadline == null ? Long.MAX_VALUE : t.deadline);
            case 2 -> byDone.thenComparingLong(t -> t.start == null ? Long.MAX_VALUE : t.start);
            case 3 -> byDone.thenComparing((Task t) -> -t.priority.ordinal()).thenComparingLong(t -> t.deadline == null ? Long.MAX_VALUE : t.deadline);
            case 4 -> Comparator.comparingLong((Task t) -> -t.createdAt);
            case 5 -> Comparator.comparing((Task t) -> t.title.toLowerCase());
            default -> byDone.thenComparing((Task t) -> -AppContext.urgency.evaluate(t).score())
                    .thenComparingLong(t -> t.deadline == null ? Long.MAX_VALUE : t.deadline);
        };
    }
}
