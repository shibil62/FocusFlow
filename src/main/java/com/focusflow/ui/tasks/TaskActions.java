package com.focusflow.ui.tasks;

import com.focusflow.app.AppContext;
import com.focusflow.models.Task;
import com.focusflow.ui.components.Sheet;
import com.focusflow.ui.components.Toast;
import com.focusflow.ui.dialogs.TaskDialog;
import com.focusflow.utilities.Time;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Shared task operations used by every list, card and calendar surface. */
public final class TaskActions {
    private TaskActions() {}

    public static void open(Task t) {
        Task cur = AppContext.tasks.get(t.id);
        TaskDialog.open(cur == null ? t : cur, null, null, null);
    }

    public static void toggle(Task t) {
        Task cur = AppContext.tasks.get(t.id);
        if (cur == null) return;
        if (cur.isCompleted()) {
            AppContext.tasks.reopen(cur);
            Toast.show("Reopened “" + shorten(cur.title) + "”", "Undo", AppContext.tasks::undoLast);
        } else {
            AppContext.tasks.complete(cur);
            Toast.show("Completed “" + shorten(cur.title) + "”", "Undo", AppContext.tasks::undoLast);
        }
    }

    public static void confirmDelete(Task t) {
        Sheet.confirm("Delete this task?", "“" + shorten(t.title) + "” and its subtasks will be removed. You can undo right after.",
                "Delete", true, () -> {
                    AppContext.tasks.delete(t);
                    Toast.show("Task deleted", "Undo", AppContext.tasks::undoLast);
                });
    }

    public static void rescheduleTo(Task t, LocalDate target) {
        Task c = t.copy();
        if (t.start == null) {
            c.allDay = true;
            c.setStart(target.atStartOfDay());
            c.end = null;
        } else if (t.allDay) {
            long shift = java.time.temporal.ChronoUnit.DAYS.between(t.scheduledDate(), target);
            c.setStart(target.atStartOfDay());
            if (t.end != null) c.setEnd(t.endDT().plusDays(shift));
        } else {
            long dur = t.end - t.start;
            c.start = Time.ms(target.atTime(t.startDT().toLocalTime()));
            c.end = c.start + dur;
        }
        AppContext.tasks.saveUndoable(c, "Rescheduled");
        Toast.show("Rescheduled to " + Time.relativeDay(target), "Undo", AppContext.tasks::undoLast);
    }

    public static void extendDeadline(Task t, int days) {
        LocalDateTime base = t.deadlineDT();
        LocalDateTime now = LocalDateTime.now();
        if (base.isBefore(now)) base = now;
        Task c = t.copy();
        c.setDeadline(base.plusDays(days));
        AppContext.tasks.saveUndoable(c, "Deadline changed");
        Toast.show("Deadline moved to " + Time.dateShort(c.deadlineDate()), "Undo", AppContext.tasks::undoLast);
    }

    public static void menu(Task t, Component c, int x, int y) {
        Task cur = AppContext.tasks.get(t.id);
        if (cur == null) cur = t;
        final Task task = cur;
        JPopupMenu m = new JPopupMenu();
        item(m, "Edit…", e -> open(task));
        item(m, task.isCompleted() ? "Reopen" : "Mark complete", e -> toggle(task));
        if (!task.isCompleted()) item(m, "Start focus session", e -> AppContext.frame.startFocusFor(task.id));
        m.addSeparator();
        JMenu res = new JMenu("Reschedule");
        LocalDate today = Time.today();
        item(res, "Today", e -> rescheduleTo(task, today));
        item(res, "Tomorrow", e -> rescheduleTo(task, today.plusDays(1)));
        item(res, "Next week", e -> rescheduleTo(task, today.plusDays(7)));
        m.add(res);
        if (task.deadline != null) {
            JMenu dl = new JMenu("Extend deadline");
            item(dl, "By 1 day", e -> extendDeadline(task, 1));
            item(dl, "By 3 days", e -> extendDeadline(task, 3));
            item(dl, "By 1 week", e -> extendDeadline(task, 7));
            m.add(dl);
        }
        m.addSeparator();
        item(m, task.archived ? "Unarchive" : "Archive", e -> {
            AppContext.tasks.archive(task, !task.archived);
            Toast.show(task.archived ? "Task restored" : "Task archived", "Undo", AppContext.tasks::undoLast);
        });
        item(m, "Delete…", e -> confirmDelete(task));
        m.show(c, x, y);
    }

    private static void item(JComponent menu, String text, ActionListener l) {
        JMenuItem it = new JMenuItem(text);
        it.addActionListener(l);
        menu.add(it);
    }

    public static String shorten(String s) { return s.length() > 40 ? s.substring(0, 38) + "…" : s; }
}
