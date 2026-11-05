package com.focusflow.services;

import com.focusflow.models.*;
import com.focusflow.repositories.TaskRepository;
import com.focusflow.utilities.Time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** In-memory task cache backed by SQLite. All mutations go through here so undo and history stay consistent. */
public class TaskService extends Notifier {
    private final TaskRepository repo;
    private final List<Task> cache = new ArrayList<>();
    private final Deque<Undo> undo = new ArrayDeque<>();

    private record Undo(String label, Runnable action) {}

    public TaskService(TaskRepository repo) {
        this.repo = repo;
        cache.addAll(repo.findAll());
    }

    public void reload() {
        cache.clear();
        cache.addAll(repo.findAll());
        undo.clear();
        fire();
    }

    // ---------------------------------------------------------------- queries

    /** Active (non-archived) tasks. The returned objects are shared: never mutate them, copy() first. */
    public List<Task> all() {
        List<Task> r = new ArrayList<>();
        for (Task t : cache) if (!t.archived) r.add(t);
        return r;
    }

    public List<Task> allIncludingArchived() { return new ArrayList<>(cache); }

    public Task get(long id) {
        for (Task t : cache) if (t.id == id) return t;
        return null;
    }

    public static boolean isOverdue(Task t) {
        return !t.isCompleted() && t.deadline != null && t.deadline < Time.now();
    }

    public List<Task> forDay(LocalDate d) {
        List<Task> r = new ArrayList<>();
        for (Task t : all()) {
            LocalDate sd = t.scheduledDate();
            if (sd != null ? t.coversDay(d) : (t.deadlineDate() != null && t.deadlineDate().equals(d))) r.add(t);
        }
        r.sort(Comparator.comparing((Task t) -> !t.allDay && t.start != null)
                .thenComparing(t -> t.start == null ? Long.MAX_VALUE : t.start)
                .thenComparing(t -> t.deadline == null ? Long.MAX_VALUE : t.deadline));
        return r;
    }

    /** Unfinished tasks scheduled on an earlier day: candidates for the "move to today" suggestion. */
    public List<Task> rolloverCandidates() {
        LocalDate today = Time.today();
        List<Task> r = new ArrayList<>();
        for (Task t : all()) {
            if (t.isCompleted() || t.start == null) continue;
            if (t.lastDay().isBefore(today)) r.add(t);
        }
        r.sort(Comparator.comparingLong(t -> t.start));
        return r;
    }

    // -------------------------------------------------------------- mutations

    public Task save(Task edited) {
        long now = Time.now();
        Task old = edited.id == 0 ? null : get(edited.id);
        edited.updatedAt = now;
        if (edited.createdAt == 0) edited.createdAt = now;

        boolean becameDone = false;
        if (old != null) {
            if (!old.isCompleted() && edited.isCompleted()) {
                becameDone = true;
                if (edited.completedAt == null) edited.completedAt = now;
            } else if (old.isCompleted() && !edited.isCompleted()) {
                edited.completedAt = null;
            }
        } else if (edited.isCompleted() && edited.completedAt == null) {
            edited.completedAt = now;
        }

        if (old == null) {
            repo.insert(edited);
            cache.add(edited);
            repo.history(edited.id, "created", edited.title);
        } else {
            repo.update(edited);
            cache.set(cache.indexOf(old), edited);
            repo.history(edited.id, becameDone ? "completed" : "updated", edited.title);
        }
        if (becameDone) spawnNext(edited);
        fire();
        return edited;
    }

    /** Saves and records an undo step that restores the previous version. */
    public void saveUndoable(Task after, String label) {
        Task cur = get(after.id);
        Task before = cur == null ? null : cur.copy();
        save(after);
        if (before != null) pushUndo(label, () -> save(before.copy()));
    }

    public void complete(Task t) {
        Task before = t.copy();
        Task c = t.copy();
        c.status = Status.COMPLETED;
        save(c);
        pushUndo("Completed", () -> {
            removeSpawned(before.id);
            Task cur = get(before.id);
            if (cur != null) {
                Task x = cur.copy();
                x.status = before.status;
                save(x);
            }
        });
    }

    public void reopen(Task t) {
        Task before = t.copy();
        Task c = t.copy();
        c.status = c.subtasksDone() > 0 ? Status.IN_PROGRESS : Status.NOT_STARTED;
        save(c);
        pushUndo("Reopened", () -> {
            Task cur = get(before.id);
            if (cur != null) {
                Task x = cur.copy();
                x.status = Status.COMPLETED;
                x.completedAt = before.completedAt;
                save(x);
            }
        });
    }

    public void markInProgress(long id) {
        Task t = get(id);
        if (t != null && t.status == Status.NOT_STARTED) {
            Task c = t.copy();
            c.status = Status.IN_PROGRESS;
            save(c);
        }
    }

    /** Toggles one subtask. Returns true when every subtask is now done and the parent is still open. */
    public boolean toggleSubtask(Task t, int index) {
        Task c = t.copy();
        Subtask s = c.subtasks.get(index);
        s.done = !s.done;
        s.doneAt = s.done ? Time.now() : null;
        if (s.done && c.status == Status.NOT_STARTED) c.status = Status.IN_PROGRESS;
        save(c);
        return c.subtasksDone() == c.subtasks.size() && !c.isCompleted();
    }

    public void reschedule(Task t, LocalDateTime start, LocalDateTime end) {
        Task c = t.copy();
        c.allDay = false;
        c.setStart(start);
        c.setEnd(end);
        saveUndoable(c, "Rescheduled");
    }

    public void archive(Task t, boolean archived) {
        Task c = t.copy();
        c.archived = archived;
        saveUndoable(c, archived ? "Archived" : "Unarchived");
    }

    public void delete(Task t) {
        Task snapshot = t.copy();
        repo.delete(t.id);
        cache.removeIf(x -> x.id == t.id);
        fire();
        pushUndo("Deleted", () -> {
            Task restored = snapshot.copy();
            restored.id = 0;
            save(restored);
        });
    }

    private void removeSpawned(long parentId) {
        List<Task> kids = new ArrayList<>();
        for (Task t : cache) {
            if (t.recurrenceParent != null && t.recurrenceParent == parentId && !t.isCompleted()) kids.add(t);
        }
        for (Task k : kids) {
            repo.delete(k.id);
            cache.remove(k);
        }
    }

    private void spawnNext(Task done) {
        if (done.recurrence == Recurrence.NONE) return;
        for (Task t : cache) {
            if (t.recurrenceParent != null && t.recurrenceParent == done.id) return;
        }
        Task n = done.copy();
        n.id = 0;
        n.status = Status.NOT_STARTED;
        n.completedAt = null;
        n.createdAt = 0;
        n.archived = false;
        n.recurrenceParent = done.id;
        for (Subtask s : n.subtasks) { s.done = false; s.doneAt = null; }

        LocalDate today = Time.today();
        LocalDate ref = done.scheduledDate() != null ? done.scheduledDate() : done.deadlineDate();
        int steps = 1;
        if (ref != null) {
            while (steps < 400 && done.recurrence.shift(ref.atStartOfDay(), steps).toLocalDate().isBefore(today)) steps++;
        }
        if (done.start != null) n.setStart(done.recurrence.shift(done.startDT(), steps));
        if (done.end != null) n.setEnd(done.recurrence.shift(done.endDT(), steps));
        if (done.deadline != null) n.setDeadline(done.recurrence.shift(done.deadlineDT(), steps));
        save(n);
    }

    // ------------------------------------------------------------------- undo

    private void pushUndo(String label, Runnable action) {
        undo.push(new Undo(label, action));
        while (undo.size() > 30) undo.removeLast();
    }

    public boolean canUndo() { return !undo.isEmpty(); }

    /** Runs the most recent undo step and returns its label (or null when there is nothing to undo). */
    public String undoLast() {
        Undo u = undo.poll();
        if (u == null) return null;
        u.action().run();
        return u.label();
    }
}
