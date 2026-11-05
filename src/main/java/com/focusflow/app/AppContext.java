package com.focusflow.app;

import com.focusflow.notifications.NotificationService;
import com.focusflow.notifications.TrayService;
import com.focusflow.repositories.*;
import com.focusflow.services.*;
import com.focusflow.ui.MainFrame;

/** Application-wide service registry. Built once at startup. */
public final class AppContext {
    private AppContext() {}

    public static SettingsService settings;
    public static CategoryService categories;
    public static TaskService tasks;
    public static UrgencyService urgency;
    public static FocusService focus;
    public static AnalyticsService analytics;
    public static BackupService backup;
    public static NotificationService notifications;
    public static TrayService tray;
    public static MainFrame frame;

    public static void init() {
        settings = new SettingsService(new SettingsRepository());
        categories = new CategoryService(new CategoryRepository());
        tasks = new TaskService(new TaskRepository());
        urgency = new UrgencyService(settings);
        focus = new FocusService(new FocusRepository(), settings, tasks);
        analytics = new AnalyticsService(tasks, categories, focus, urgency);
        backup = new BackupService();
        tray = new TrayService();
        notifications = new NotificationService(new NotificationRepository());
    }

    /** Re-reads everything from the database (after a restore). */
    public static void reloadAll() {
        settings.reload();
        categories.reload();
        tasks.reload();
        focus.reload();
    }
}
