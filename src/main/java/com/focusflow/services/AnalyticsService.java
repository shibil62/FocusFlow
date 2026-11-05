package com.focusflow.services;

import com.focusflow.models.Category;
import com.focusflow.models.FocusSession;
import com.focusflow.models.Task;
import com.focusflow.models.Urgency;
import com.focusflow.utilities.Time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** Every number here is computed from stored tasks and focus sessions. */
public class AnalyticsService {
    public record DayStat(LocalDate date, int planned, int completed) {}
    public record Totals(int completed, int pending, int overdue, int onTime, int late, double rate) {}
    public record CatCount(Category category, int count) {}
    public record CatTime(Category category, long seconds) {}
    public record Today(int total, int completed, int remaining, int overdue, int urgent, long focusSeconds, double percent) {}

    private final TaskService tasks;
    private final CategoryService categories;
    private final FocusService focus;
    private final UrgencyService urgency;

    public AnalyticsService(TaskService tasks, CategoryService categories, FocusService focus, UrgencyService urgency) {
        this.tasks = tasks;
        this.categories = categories;
        this.focus = focus;
        this.urgency = urgency;
    }

    static LocalDate planDate(Task t) {
        if (t.start != null) return t.scheduledDate();
        if (t.deadline != null) return t.deadlineDate();
        return Time.dt(t.createdAt).toLocalDate();
    }

    private static boolean in(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private static LocalDate completedDate(Task t) {
        return t.completedAt == null ? null : Time.dt(t.completedAt).toLocalDate();
    }

    public Today today() {
        LocalDate today = Time.today();
        int total = 0, done = 0, overdue = 0, urgent = 0;
        for (Task t : tasks.all()) {
            boolean mine = (t.start != null ? t.coversDay(today) : today.equals(planDate(t))) || today.equals(completedDate(t));
            if (mine) { total++; if (t.isCompleted()) done++; }
            if (TaskService.isOverdue(t)) overdue++;
            else if (!t.isCompleted()) {
                Urgency.Level l = urgency.evaluate(t).level();
                if (l == Urgency.Level.URGENT || l == Urgency.Level.CRITICAL) urgent++;
            }
        }
        long focusSec = focus.secondsBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return new Today(total, done, total - done, overdue, urgent, focusSec, total == 0 ? 0 : done * 100.0 / total);
    }

    public List<DayStat> daily(LocalDate from, LocalDate to) {
        Map<LocalDate, int[]> m = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) m.put(d, new int[2]);
        for (Task t : tasks.allIncludingArchived()) {
            LocalDate p = planDate(t);
            if (in(p, from, to)) m.get(p)[0]++;
            LocalDate c = completedDate(t);
            if (in(c, from, to)) m.get(c)[1]++;
        }
        List<DayStat> out = new ArrayList<>();
        for (Map.Entry<LocalDate, int[]> e : m.entrySet()) out.add(new DayStat(e.getKey(), e.getValue()[0], e.getValue()[1]));
        return out;
    }

    public Totals totals(LocalDate from, LocalDate to) {
        int done = 0, pending = 0, overdue = 0, onTime = 0, late = 0;
        for (Task t : tasks.allIncludingArchived()) {
            if (in(planDate(t), from, to)) {
                if (t.isCompleted()) done++; else pending++;
                if (TaskService.isOverdue(t)) overdue++;
            }
            if (t.isCompleted() && t.deadline != null && in(completedDate(t), from, to)) {
                if (t.completedAt <= t.deadline) onTime++; else late++;
            }
        }
        int all = done + pending;
        return new Totals(done, pending, overdue, onTime, late, all == 0 ? 0 : done * 100.0 / all);
    }

    public List<CatCount> distribution(LocalDate from, LocalDate to) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Task t : tasks.allIncludingArchived()) {
            if (in(planDate(t), from, to)) counts.merge(t.categoryId == null ? -1L : t.categoryId, 1, Integer::sum);
        }
        List<CatCount> out = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : counts.entrySet()) out.add(new CatCount(e.getKey() < 0 ? null : categories.byId(e.getKey()), e.getValue()));
        out.sort((a, b) -> b.count() - a.count());
        return out;
    }

    public List<CatTime> focusByCategory(LocalDate from, LocalDate to) {
        long a = Time.ms(from.atStartOfDay()), b = Time.ms(to.plusDays(1).atStartOfDay());
        Map<Long, Long> secs = new LinkedHashMap<>();
        for (FocusSession s : focus.sessions()) {
            if (s.startedAt < a || s.startedAt >= b) continue;
            Task t = s.taskId == null ? null : tasks.get(s.taskId);
            long key = t == null || t.categoryId == null ? -1L : t.categoryId;
            secs.merge(key, (long) s.actualSeconds, Long::sum);
        }
        List<CatTime> out = new ArrayList<>();
        for (Map.Entry<Long, Long> e : secs.entrySet()) out.add(new CatTime(e.getKey() < 0 ? null : categories.byId(e.getKey()), e.getValue()));
        out.sort((x, y) -> Long.compare(y.seconds(), x.seconds()));
        return out;
    }

    public long focusSeconds(LocalDate from, LocalDate to) {
        return focus.secondsBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    /** Completed tasks per weekday, Monday = index 0. */
    public int[] weekdayCounts(LocalDate from, LocalDate to) {
        int[] r = new int[7];
        for (Task t : tasks.allIncludingArchived()) {
            LocalDate c = completedDate(t);
            if (t.isCompleted() && in(c, from, to)) r[c.getDayOfWeek().getValue() - 1]++;
        }
        return r;
    }

    public double avgDailyCompletion(LocalDate from, LocalDate to) {
        int n = 0;
        for (Task t : tasks.allIncludingArchived()) if (t.isCompleted() && in(completedDate(t), from, to)) n++;
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        return n / (double) days;
    }

    public int activeDays(LocalDate from, LocalDate to) {
        Set<LocalDate> days = new HashSet<>();
        for (Task t : tasks.allIncludingArchived()) {
            LocalDate c = completedDate(t);
            if (t.isCompleted() && in(c, from, to)) days.add(c);
        }
        return days.size();
    }

    /** Completed task counts for the last n weeks, oldest first. */
    public int[] weeklyTotals(int n, DayOfWeek first) {
        int[] r = new int[n];
        LocalDate thisWeek = Time.weekStart(Time.today(), first);
        for (Task t : tasks.allIncludingArchived()) {
            LocalDate c = completedDate(t);
            if (!t.isCompleted() || c == null) continue;
            long idx = java.time.temporal.ChronoUnit.WEEKS.between(Time.weekStart(c, first), thisWeek);
            if (idx >= 0 && idx < n) r[(int) (n - 1 - idx)]++;
        }
        return r;
    }

    // ---- subtasks

    public record Progress(Task task, int done, int total) {}

    private static LocalDate subDoneDate(com.focusflow.models.Subtask s) {
        return s.done && s.doneAt != null ? Time.dt(s.doneAt).toLocalDate() : null;
    }

    /** Subtasks ticked off between the two dates. */
    public int subtasksDone(LocalDate from, LocalDate to) {
        int n = 0;
        for (Task t : tasks.allIncludingArchived()) for (com.focusflow.models.Subtask s : t.subtasks) if (in(subDoneDate(s), from, to)) n++;
        return n;
    }

    public int[] subtasksDaily(LocalDate from, LocalDate to) {
        int days = (int) (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        int[] r = new int[days];
        for (Task t : tasks.allIncludingArchived()) {
            for (com.focusflow.models.Subtask s : t.subtasks) {
                LocalDate d = subDoneDate(s);
                if (in(d, from, to)) r[(int) java.time.temporal.ChronoUnit.DAYS.between(from, d)]++;
            }
        }
        return r;
    }

    /** Open tasks that have subtasks, nearest deadline first. */
    public List<Progress> subtaskProgress() {
        List<Progress> out = new ArrayList<>();
        for (Task t : tasks.all()) if (!t.isCompleted() && !t.subtasks.isEmpty()) out.add(new Progress(t, t.subtasksDone(), t.subtasks.size()));
        out.sort(Comparator.comparingLong((Progress p) -> p.task().deadline == null ? Long.MAX_VALUE : p.task().deadline));
        return out;
    }

    public int totalCompleted() {
        int n = 0;
        for (Task t : tasks.allIncludingArchived()) if (t.isCompleted()) n++;
        return n;
    }

    public int totalOverdue() {
        int n = 0;
        for (Task t : tasks.all()) if (TaskService.isOverdue(t)) n++;
        return n;
    }
}
