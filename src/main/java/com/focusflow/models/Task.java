package com.focusflow.models;

import com.focusflow.utilities.Time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A task. The scheduled work session (start/end) and the final deadline are separate concepts.
 * Times are stored as epoch milliseconds. All-day tasks use start = midnight of the day and no end.
 */
public class Task {
    /** Colours offered for tasks (same family as the category colours). */
    public static final String[] PALETTE = {
            "#0A84FF", "#FF375F", "#30D158", "#FF9F0A", "#BF5AF2", "#40C8E0",
            "#FFD60A", "#FF453A", "#5E5CE6", "#64D2FF", "#AC8E68", "#66D4CF"};

    public long id;
    public String title = "";
    public String description = "";
    public String notes = "";
    public Long categoryId;
    public Priority priority = Priority.NORMAL;
    public Status status = Status.NOT_STARTED;
    public Long start;
    public Long end;
    public boolean allDay;
    public Long deadline;
    public int estimatedMinutes;
    public Long completedAt;
    public long createdAt;
    public long updatedAt;
    public boolean archived;
    public Recurrence recurrence = Recurrence.NONE;
    public Long recurrenceParent;
    /** Own colour as "#RRGGBB", or null (then the category colour, or a colour derived from the id, is used). */
    public String color;
    public List<Subtask> subtasks = new ArrayList<>();

    public boolean isCompleted() { return status == Status.COMPLETED; }
    public boolean isTimed() { return start != null && end != null && !allDay; }
    public boolean isScheduled() { return start != null; }

    public LocalDateTime startDT() { return Time.dtOrNull(start); }
    public LocalDateTime endDT() { return Time.dtOrNull(end); }
    public LocalDateTime deadlineDT() { return Time.dtOrNull(deadline); }
    public LocalDate scheduledDate() { return start == null ? null : Time.dt(start).toLocalDate(); }
    public LocalDate deadlineDate() { return deadline == null ? null : Time.dt(deadline).toLocalDate(); }

    public void setStart(LocalDateTime t) { start = Time.msOrNull(t); }
    public void setEnd(LocalDateTime t) { end = Time.msOrNull(t); }
    public void setDeadline(LocalDateTime t) { deadline = Time.msOrNull(t); }

    /**
     * Last day this task is scheduled on. All-day tasks can span days (end = last day); timed tasks can run
     * past midnight (e.g. 9 PM to 3 AM). A timed task that ends exactly at 12:00 AM ends on the previous day.
     */
    public LocalDate lastDay() {
        LocalDate s = scheduledDate();
        if (s == null) return null;
        if (end == null) return s;
        LocalDateTime e = Time.dt(end);
        if (allDay) {
            LocalDate d = e.toLocalDate();
            return d.isAfter(s) ? d : s;
        }
        LocalDate d = e.toLocalDate();
        if (e.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) d = d.minusDays(1);
        return d.isAfter(s) ? d : s;
    }

    public boolean coversDay(LocalDate d) {
        LocalDate s = scheduledDate();
        return s != null && !d.isBefore(s) && !d.isAfter(lastDay());
    }

    public int scheduledMinutes() { return isTimed() ? (int) ((end - start) / 60000) : 0; }

    public int subtasksDone() {
        int n = 0;
        for (Subtask s : subtasks) if (s.done) n++;
        return n;
    }

    /** 0..1, or -1 when the task has no subtasks. */
    public double progress() {
        if (subtasks.isEmpty()) return -1;
        return subtasksDone() / (double) subtasks.size();
    }

    public Task copy() {
        Task t = new Task();
        t.id = id; t.title = title; t.description = description; t.notes = notes;
        t.categoryId = categoryId; t.priority = priority; t.status = status;
        t.start = start; t.end = end; t.allDay = allDay; t.deadline = deadline;
        t.estimatedMinutes = estimatedMinutes; t.completedAt = completedAt;
        t.createdAt = createdAt; t.updatedAt = updatedAt; t.archived = archived;
        t.recurrence = recurrence; t.recurrenceParent = recurrenceParent; t.color = color;
        for (Subtask s : subtasks) t.subtasks.add(s.copy());
        return t;
    }
}
