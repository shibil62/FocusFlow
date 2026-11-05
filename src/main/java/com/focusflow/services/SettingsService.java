package com.focusflow.services;

import com.focusflow.repositories.SettingsRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsService extends Notifier {
    public static final String THEME = "theme";
    public static final String REDUCE_MOTION = "reduceMotion";
    public static final String SIDEBAR_COLLAPSED = "sidebarCollapsed";
    public static final String APPROACHING_DAYS = "thr.approachingDays";
    public static final String URGENT_HOURS = "thr.urgentHours";
    public static final String CRITICAL_HOURS = "thr.criticalHours";
    public static final String WEEK_START = "calendar.weekStart";
    public static final String DEFAULT_DURATION = "calendar.defaultDuration";
    public static final String CAL_MODE = "calendar.mode";
    public static final String NOTIFY_ENABLED = "notify.enabled";
    public static final String NOTIFY_DEADLINES = "notify.deadlines";
    public static final String NOTIFY_START = "notify.start";
    public static final String NOTIFY_OVERDUE = "notify.overdue";
    public static final String NOTIFY_FOCUS = "notify.focus";
    public static final String NOTIFY_DAILY = "notify.daily";
    public static final String NOTIFY_WEEKLY = "notify.weekly";
    public static final String NOTIFY_LEAD = "notify.leadMinutes";
    public static final String CLOSE_TO_TRAY = "window.closeToTray";
    public static final String START_MAXIMIZED = "window.startMaximized";
    public static final String LAUNCH_ON_STARTUP = "system.launchOnStartup";
    public static final String WINDOW_BOUNDS = "window.bounds";
    public static final String ROLLOVER_DISMISSED = "rollover.dismissedOn";
    public static final String FOCUS_LAST_MINUTES = "focus.lastMinutes";
    public static final String FOCUS_ACTIVE = "focus.active";

    private final SettingsRepository repo;
    private final Map<String, String> map = new ConcurrentHashMap<>();

    public SettingsService(SettingsRepository repo) {
        this.repo = repo;
        map.putAll(repo.loadAll());
    }

    public void reload() {
        map.clear();
        map.putAll(repo.loadAll());
        fire();
    }

    public String get(String key, String def) { return map.getOrDefault(key, def); }
    public boolean getBool(String key, boolean def) {
        String v = map.get(key);
        return v == null ? def : v.equals("true");
    }
    public int getInt(String key, int def) {
        try { return Integer.parseInt(map.get(key)); } catch (Exception e) { return def; }
    }

    public void set(String key, String value) { setQuiet(key, value); fire(); }
    public void set(String key, boolean value) { set(key, String.valueOf(value)); }
    public void set(String key, int value) { set(key, String.valueOf(value)); }

    /** Persists without notifying listeners (used for window geometry and timer state). */
    public void setQuiet(String key, String value) {
        map.put(key, value);
        repo.put(key, value);
    }

    public void removeQuiet(String key) {
        map.remove(key);
        repo.remove(key);
    }
}
