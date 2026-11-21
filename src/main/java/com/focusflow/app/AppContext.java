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

    /** Opens the main window, building it first if it was released while running in the background. */
    public static void showMain() {
        if (frame == null) {
            MainFrame f = new MainFrame();
            frame = f;
            f.start(false);
        } else {
            frame.showFromTray();
        }
    }

    /** Runs {@code r} once the main window is open and laid out. */
    public static void inMain(Runnable r) {
        showMain();
        javax.swing.SwingUtilities.invokeLater(r);
    }

    public static void quit() {
        try {
            if (frame != null) frame.saveBoundsNow();
            tray.remove();
            com.focusflow.database.Database.close();
        } finally {
            System.exit(0);
        }
    }

    /** Re-reads everything from the database (after a restore). */
    public static void reloadAll() {
        settings.reload();
        categories.reload();
        tasks.reload();
        focus.reload();
    }
}
