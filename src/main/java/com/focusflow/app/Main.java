package com.focusflow.app;

import com.focusflow.animations.Animator;
import com.focusflow.config.AppPaths;
import com.focusflow.database.Database;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.MainFrame;
import com.focusflow.ui.Theme;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Locale;

public final class Main {
    private static FileChannel lockChannel;
    private static FileLock lock;

    private Main() {}

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log(e));
        SwingUtilities.invokeLater(Main::start);
    }

    private static boolean acquireLock() {
        try {
            Path f = AppPaths.dataDir().resolve("focusflow.lock");
            lockChannel = FileChannel.open(f, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            return lock != null;
        } catch (IOException | OverlappingFileLockException e) {
            return false;
        }
    }

    private static void start() {
        try {
            if (!acquireLock()) {
                JOptionPane.showMessageDialog(null, "FocusFlow is already running.\nLook for it in the taskbar or the system tray.",
                        "FocusFlow", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }
            Database.open(AppPaths.dbFile());
            AppContext.init();
            Theme.init("dark".equals(AppContext.settings.get(SettingsService.THEME, "light")));
            Animator.reduceMotion = AppContext.settings.getBool(SettingsService.REDUCE_MOTION, false);
            AppContext.backup.autoBackupIfNeeded();
            MainFrame frame = new MainFrame();
            AppContext.frame = frame;
            frame.start();
        } catch (Throwable t) {
            log(t);
            JOptionPane.showMessageDialog(null, "FocusFlow could not start:\n" + t.getMessage() + "\n\nDetails were saved to " + AppPaths.logFile(),
                    "FocusFlow", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void log(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            Files.writeString(AppPaths.logFile(), LocalDateTime.now() + "\n" + sw + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
}
