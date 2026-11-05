package com.focusflow.services;

import com.focusflow.app.AppContext;
import com.focusflow.models.FocusSession;
import com.focusflow.repositories.FocusRepository;
import com.focusflow.utilities.Time;

import javax.swing.Timer;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Power Mode timer engine. The countdown is derived from wall-clock timestamps (never from counting UI ticks),
 * and the live state is persisted so a session survives minimising, sleeping or closing the app.
 */
public class FocusService extends Notifier {
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    private final FocusRepository repo;
    private final SettingsService settings;
    private final TaskService tasks;

    private State state = State.IDLE;
    private long plannedMs, elapsedBefore, resumedAt, startedAt;
    private Long taskId;
    private FocusSession lastSession;

    public FocusService(FocusRepository repo, SettingsService settings, TaskService tasks) {
        this.repo = repo;
        this.settings = settings;
        this.tasks = tasks;
        restore();
        Timer watchdog = new Timer(500, e -> check());
        watchdog.start();
    }

    public State state() { return state; }
    public Long taskId() { return taskId; }
    public long plannedMs() { return plannedMs; }
    public FocusSession lastSession() { return lastSession; }
    public boolean isActive() { return state == State.RUNNING || state == State.PAUSED; }

    public long elapsedMs() {
        long e = elapsedBefore;
        if (state == State.RUNNING) e += Time.now() - resumedAt;
        return Math.max(0, Math.min(e, plannedMs));
    }

    public long remainingMs() { return Math.max(0, plannedMs - elapsedMs()); }

    public void start(int minutes, Long task) {
        plannedMs = minutes * 60_000L;
        elapsedBefore = 0;
        startedAt = resumedAt = Time.now();
        taskId = task;
        state = State.RUNNING;
        settings.setQuiet(SettingsService.FOCUS_LAST_MINUTES, String.valueOf(minutes));
        if (task != null) tasks.markInProgress(task);
        persist();
        fire();
    }

    public void pause() {
        if (state != State.RUNNING) return;
        elapsedBefore = elapsedMs();
        state = State.PAUSED;
        persist();
        fire();
    }

    public void resume() {
        if (state != State.PAUSED) return;
        resumedAt = Time.now();
        state = State.RUNNING;
        persist();
        fire();
    }

    /** Restarts the countdown from the full duration and waits (paused) for the user to resume. */
    public void reset() {
        if (!isActive()) return;
        elapsedBefore = 0;
        startedAt = Time.now();
        state = State.PAUSED;
        persist();
        fire();
    }

    /** Ends the session early. Anything focused for at least 30 seconds is saved. */
    public void stop() {
        if (!isActive()) return;
        long el = elapsedMs();
        if (el >= 30_000) record(el, false, Time.now());
        state = State.IDLE;
        clearPersist();
        fire();
    }

    public void dismiss() {
        if (state == State.FINISHED) {
            state = State.IDLE;
            fire();
        }
    }

    public void reload() {
        state = State.IDLE;
        clearPersist();
        fire();
    }

    private void check() {
        if (state == State.RUNNING && remainingMs() <= 0) finish(Time.now());
    }

    private void finish(long endedAt) {
        elapsedBefore = plannedMs;
        record(plannedMs, true, endedAt);
        state = State.FINISHED;
        clearPersist();
        fire();
        if (AppContext.notifications != null) AppContext.notifications.focusFinished(lastSession);
    }

    private void record(long actualMs, boolean completed, long endedAt) {
        FocusSession s = new FocusSession();
        s.taskId = taskId;
        s.startedAt = startedAt;
        s.endedAt = endedAt;
        s.plannedSeconds = (int) (plannedMs / 1000);
        s.actualSeconds = (int) (actualMs / 1000);
        s.completed = completed;
        repo.insert(s);
        lastSession = s;
    }

    // ------------------------------------------------------------ persistence

    private void persist() {
        settings.setQuiet(SettingsService.FOCUS_ACTIVE, String.join("|", state.name(), String.valueOf(plannedMs),
                String.valueOf(elapsedBefore), String.valueOf(resumedAt), String.valueOf(startedAt),
                taskId == null ? "" : String.valueOf(taskId)));
    }

    private void clearPersist() { settings.removeQuiet(SettingsService.FOCUS_ACTIVE); }

    private void restore() {
        String v = settings.get(SettingsService.FOCUS_ACTIVE, null);
        if (v == null || v.isBlank()) return;
        try {
            String[] p = v.split("\\|", -1);
            State st = State.valueOf(p[0]);
            plannedMs = Long.parseLong(p[1]);
            elapsedBefore = Long.parseLong(p[2]);
            resumedAt = Long.parseLong(p[3]);
            startedAt = Long.parseLong(p[4]);
            taskId = p[5].isEmpty() ? null : Long.parseLong(p[5]);
            if (st == State.RUNNING) {
                long total = elapsedBefore + (Time.now() - resumedAt);
                if (total >= plannedMs) {
                    // It ran out while the app was closed: record it and show the completion screen.
                    elapsedBefore = plannedMs;
                    record(plannedMs, true, resumedAt + (plannedMs - Long.parseLong(p[2])));
                    state = State.FINISHED;
                    clearPersist();
                } else {
                    state = State.RUNNING;
                }
            } else if (st == State.PAUSED) {
                state = State.PAUSED;
            } else {
                clearPersist();
            }
        } catch (Exception e) {
            state = State.IDLE;
            clearPersist();
        }
    }

    // ---------------------------------------------------------------- queries

    public List<FocusSession> sessions() { return repo.findAll(); }

    public long secondsBetween(LocalDateTime from, LocalDateTime to) {
        return repo.secondsBetween(Time.ms(from), Time.ms(to));
    }
}
