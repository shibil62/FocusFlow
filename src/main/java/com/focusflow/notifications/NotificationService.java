package com.focusflow.notifications;

import com.focusflow.app.AppContext;
import com.focusflow.models.FocusSession;
import com.focusflow.models.Task;
import com.focusflow.models.Urgency;
import com.focusflow.repositories.NotificationRepository;
import com.focusflow.services.AnalyticsService;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.components.Toast;
import com.focusflow.utilities.Time;

import javax.swing.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Local reminders. Every notification has a unique key stored in SQLite, so nothing fires twice, and
 * bursts are bundled into one summary so you are never flooded.
 */
public class NotificationService {
    private final NotificationRepository repo;
    private final Timer timer;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
        timer = new Timer(30_000, e -> check());
        timer.setInitialDelay(4000);
    }

    public void start() {
        repo.pruneOlderThan(Time.now() - 90L * 24 * 3600 * 1000);
        timer.start();
    }

    private boolean on(String key, boolean def) { return AppContext.settings.getBool(key, def); }

    private boolean deliver(String kind, String key, Long taskId, String title, String msg) {
        if (!repo.tryInsert(key, taskId, kind, title + ": " + msg)) return false;
        Toast.show(title + "  ·  " + msg);
        if (AppContext.frame == null || !AppContext.frame.isActive()) AppContext.tray.message(title, msg);
        return true;
    }

    public void focusFinished(FocusSession s) {
        if (s == null || !on(SettingsService.NOTIFY_ENABLED, true) || !on(SettingsService.NOTIFY_FOCUS, true)) return;
        deliver("focus", "focus:" + s.startedAt, s.taskId, "Focus session complete", "You focused for " + Time.seconds(s.actualSeconds) + ".");
    }

    private void check() {
        if (!on(SettingsService.NOTIFY_ENABLED, true)) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        int critH = AppContext.settings.getInt(SettingsService.CRITICAL_HOURS, 24);
        int lead = AppContext.settings.getInt(SettingsService.NOTIFY_LEAD, 10);
        int budget = 3;
        List<Task> critical = new ArrayList<>(), justOver = new ArrayList<>();
        int overdueTotal = 0;

        for (Task t : AppContext.tasks.all()) {
            if (t.isCompleted()) continue;
            if (on(SettingsService.NOTIFY_START, true) && t.isTimed() && budget > 0) {
                long mins = Duration.between(now, t.startDT()).toMinutes();
                if (mins >= 0 && mins <= lead
                        && deliver("start", "start:" + t.id + ":" + t.start, t.id, "Starting soon", t.title + " at " + Time.time(t.startDT()))) budget--;
            }
            if (t.deadline != null) {
                Urgency u = AppContext.urgency.evaluate(t, now);
                if (u.level() == Urgency.Level.OVERDUE) {
                    overdueTotal++;
                    if (Duration.between(t.deadlineDT(), now).toMinutes() <= 60) justOver.add(t);
                } else if (u.level() == Urgency.Level.CRITICAL && Duration.between(now, t.deadlineDT()).toHours() < critH) {
                    critical.add(t);
                }
            }
        }

        if (on(SettingsService.NOTIFY_DEADLINES, true) && !critical.isEmpty() && budget > 0) {
            if (critical.size() > 2) {
                if (deliver("critical", "crit-sum:" + today + ":" + critical.size(), null, "Deadlines close",
                        critical.size() + " tasks are due within " + critH + " hours.")) budget--;
            } else {
                for (Task t : critical) {
                    if (budget > 0 && deliver("critical", "crit:" + t.id + ":" + t.deadline, t.id, "Due soon",
                            t.title + " is due " + Time.relativeDay(t.deadlineDate()).toLowerCase() + " at " + Time.time(t.deadlineDT()))) budget--;
                }
            }
        }
        if (on(SettingsService.NOTIFY_OVERDUE, true) && budget > 0) {
            if (justOver.size() > 2) {
                if (deliver("overdue", "over-sum:" + today + ":" + justOver.size(), null, "Overdue", justOver.size() + " tasks just passed their deadline.")) budget--;
            } else {
                for (Task t : justOver) {
                    if (budget > 0 && deliver("overdue", "over:" + t.id + ":" + t.deadline, t.id, "Overdue", t.title + " has passed its deadline.")) budget--;
                }
            }
            if (overdueTotal > 0 && now.getHour() >= 9 && budget > 0
                    && deliver("overdue", "over-day:" + today, null, "Overdue tasks", "You have " + overdueTotal + " overdue " + (overdueTotal == 1 ? "task" : "tasks") + ".")) budget--;
        }
        if (now.getHour() >= 20) {
            if (on(SettingsService.NOTIFY_DAILY, false)) {
                AnalyticsService.Today td = AppContext.analytics.today();
                deliver("summary", "daily:" + today, null, "Today's summary",
                        td.completed() + " of " + td.total() + " tasks done, " + Time.seconds(td.focusSeconds()) + " focused.");
            }
            if (on(SettingsService.NOTIFY_WEEKLY, false) && now.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate ws = today.minusDays(6);
                int done = 0;
                for (AnalyticsService.DayStat d : AppContext.analytics.daily(ws, today)) done += d.completed();
                deliver("summary", "weekly:" + today, null, "Weekly summary", "You completed " + done + " tasks this week.");
            }
        }
    }
}
