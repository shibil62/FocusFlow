package com.focusflow.ui.components;

import com.focusflow.models.Task;

import java.util.List;

/** A vertical list of TaskRows with an optional empty state. */
public class TaskList extends VStack {
    private final EmptyState empty;

    public TaskList(EmptyState empty) { this.empty = empty; }

    public void setTasks(List<Task> tasks) {
        clear();
        if (tasks.isEmpty()) {
            if (empty != null) add(empty, 0);
        } else {
            boolean first = true;
            for (Task t : tasks) {
                add(new TaskRow(t), first ? 0 : 8);
                first = false;
            }
        }
        revalidate();
        repaint();
    }
}
