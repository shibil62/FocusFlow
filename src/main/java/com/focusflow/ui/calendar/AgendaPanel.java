package com.focusflow.ui.calendar;

import com.focusflow.app.AppContext;
import com.focusflow.models.Task;
import com.focusflow.services.TaskService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chronological list of the next 30 days, with an overdue group on top. */
public class AgendaPanel extends JPanel {
    private final VStack stack = new VStack();

    public AgendaPanel() {
        super(new BorderLayout());
        setOpaque(false);
        stack.setBorder(new EmptyBorder(14, 14, 20, 14));
        add(Refresher.scroll(stack), BorderLayout.CENTER);
    }

    public void setData(LocalDate from, List<Task> tasks) {
        stack.clear();
        List<Task> overdue = new ArrayList<>();
        for (Task t : tasks) if (TaskService.isOverdue(t)) overdue.add(t);
        overdue.sort(Comparator.comparingLong(t -> t.deadline));
        boolean any = false;
        if (!overdue.isEmpty()) {
            group("Overdue", overdue, Theme.danger);
            any = true;
        }
        for (int i = 0; i < 30; i++) {
            LocalDate d = from.plusDays(i);
            List<Task> day = new ArrayList<>();
            for (Task t : tasks) {
                if (overdue.contains(t)) continue;
                boolean on = t.start != null ? t.coversDay(d) : t.deadline != null && d.equals(t.deadlineDate());
                if (on) day.add(t);
            }
            if (day.isEmpty()) continue;
            day.sort(Comparator.comparing((Task t) -> t.allDay || t.start == null ? 0 : 1)
                    .thenComparingLong(t -> t.start == null ? 0 : t.start));
            group(Time.relativeDay(d) + (d.equals(Time.today()) || d.equals(Time.today().plusDays(1)) ? " · " + Time.dateShort(d) : ""), day, d.equals(Time.today()) ? Theme.accent : null);
            any = true;
        }
        if (!any) stack.add(new EmptyState(Icons.Type.CALENDAR, "Nothing on the agenda", "The next 30 days are clear.", 220), 20);
        stack.revalidate();
        stack.repaint();
    }

    private void group(String title, List<Task> list, Color accent) {
        Lbl l = new Lbl(title, Theme.semi(14f), accent == Theme.danger ? Lbl.Role.DANGER : accent != null ? Lbl.Role.ACCENT : Lbl.Role.TEXT2);
        l.setBorder(new EmptyBorder(0, 4, 0, 0));
        stack.add(l, 16);
        for (Task t : list) stack.add(new TaskRow(t), 6);
    }
}
