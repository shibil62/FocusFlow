package com.focusflow.services;

import com.focusflow.models.Task;
import com.focusflow.models.Urgency;
import com.focusflow.models.Urgency.Level;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Deadline intelligence. Level comes from the time remaining (configurable thresholds) and is escalated when
 * the score says the workload is heavy. The 0-100 score blends three things:
 *   55%  time pressure   (0 when the deadline is beyond the "approaching" horizon, 100 at the deadline)
 *   45%  workload pressure (remaining work that is not yet covered by a scheduled session, versus the
 *                           productive hours left; productive hours are assumed to be 8 of every 24)
 *   +/-  priority nudge  (low -5, normal 0, high +6, critical +12)
 * It is an estimate to help you plan, not a prediction.
 */
public class UrgencyService {
    private final SettingsService settings;

    public UrgencyService(SettingsService settings) { this.settings = settings; }

    public Urgency evaluate(Task t) { return evaluate(t, LocalDateTime.now()); }

    public Urgency evaluate(Task t, LocalDateTime now) {
        if (t.isCompleted()) return new Urgency(Level.DONE, 0, "Completed", "", false);
        if (t.deadline == null) return new Urgency(Level.NONE, 0, "No deadline set.", "", false);

        LocalDateTime dl = t.deadlineDT();
        double h = Duration.between(now, dl).toMinutes() / 60.0;
        if (h <= 0) return new Urgency(Level.OVERDUE, 100, "This task is overdue.", "Overdue by " + span(-h), false);

        int approachDays = settings.getInt(SettingsService.APPROACHING_DAYS, 7);
        int urgentH = settings.getInt(SettingsService.URGENT_HOURS, 72);
        int criticalH = settings.getInt(SettingsService.CRITICAL_HOURS, 24);
        double horizon = approachDays * 24.0;

        double timeComponent = 100 * Math.pow(Math.max(0, 1 - h / horizon), 1.5);

        double estHours = effectiveMinutes(t) / 60.0;
        double progress = t.progress();
        double work = progress >= 0 ? estHours * (1 - progress) : estHours;

        double committed = 0;
        if (t.isTimed()) {
            LocalDateTime s = t.startDT(), e = t.endDT();
            if (e.isAfter(now) && !e.isAfter(dl)) {
                if (s.isBefore(now)) s = now;
                committed = Duration.between(s, e).toMinutes() / 60.0;
            }
        }
        double need = Math.max(0, work - committed);
        double available = Math.max(0.25, h * 8.0 / 24.0);
        double ratio = need / available;
        double workComponent = 100 * Math.min(1, ratio);

        double boost = switch (t.priority) {
            case LOW -> -5;
            case NORMAL -> 0;
            case HIGH -> 6;
            case CRITICAL -> 12;
        };
        int score = (int) Math.round(Math.max(0, Math.min(100, 0.55 * timeComponent + 0.45 * workComponent + boost)));

        Level level;
        if (h > horizon) level = Level.COMFORTABLE;
        else if (h > urgentH) level = Level.APPROACHING;
        else if (h > criticalH) level = Level.URGENT;
        else level = Level.CRITICAL;

        Level byScore = score >= 85 ? Level.CRITICAL : score >= 65 ? Level.URGENT : score >= 40 ? Level.APPROACHING : Level.COMFORTABLE;
        if (byScore.ordinal() > level.ordinal()) level = byScore;

        boolean overloaded = ratio > 1;
        String msg;
        if (overloaded) msg = "Estimated work may exceed the time available.";
        else if (score >= 80) msg = "This task needs immediate attention.";
        else if (score >= 55) msg = "You should start this task soon.";
        else if (level.ordinal() >= Level.APPROACHING.ordinal()) msg = "Deadline approaching.";
        else msg = "Plenty of time remaining.";

        return new Urgency(level, score, msg, span(h) + " left", overloaded);
    }

    private static int effectiveMinutes(Task t) {
        if (t.estimatedMinutes > 0) return t.estimatedMinutes;
        if (t.isTimed()) return t.scheduledMinutes();
        return 30;
    }

    public static String span(double hours) {
        long mins = Math.round(hours * 60);
        if (mins < 60) return Math.max(1, mins) + "m";
        long h = mins / 60, m = mins % 60;
        if (h < 24) return m == 0 ? h + "h" : h + "h " + m + "m";
        long d = h / 24, rh = h % 24;
        return rh == 0 ? d + "d" : d + "d " + rh + "h";
    }
}
