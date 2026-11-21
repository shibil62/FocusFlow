package com.focusflow.app;

import com.focusflow.animations.Animator;
import com.focusflow.config.AppPaths;
import com.focusflow.database.Database;
import com.focusflow.notifications.NotificationService;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.StartupRegistry;
import com.focusflow.widget.WidgetController;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;

public final class Main {
    private static FileChannel lockChannel;
    private static FileLock lock;
    private static boolean background;

    private Main() {}

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log(e));
        // "--background" is used when Windows starts FocusFlow at sign-in: only the widget and tray icon appear.
        background = Arrays.asList(args).contains("--background");
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

    // ------------------------------------------------------ single instance

    /** Listens on localhost so a second launch can ask this running copy to open its window. */
    private static void listenForOpenRequests() {
        try {
            ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            Files.writeString(AppPaths.dataDir().resolve("focusflow.port"), String.valueOf(server.getLocalPort()));
            Thread t = new Thread(() -> {
                while (true) {
                    try (Socket s = server.accept();
                         BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                        s.setSoTimeout(2000);
                        if ("SHOW".equals(r.readLine())) SwingUtilities.invokeLater(AppContext::showMain);
                    } catch (IOException e) {
                        if (server.isClosed()) return;
                    }
                }
            }, "focusflow-open-listener");
            t.setDaemon(true);
            t.start();
        } catch (IOException ignored) { }
    }

    private static boolean askRunningCopyToOpen() {
        try {
            int port = Integer.parseInt(Files.readString(AppPaths.dataDir().resolve("focusflow.port")).trim());
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
                s.getOutputStream().write("SHOW\n".getBytes());
                s.getOutputStream().flush();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------- start

    private static void start() {
        try {
            if (!acquireLock()) {
                if (!background && !askRunningCopyToOpen()) {
                    JOptionPane.showMessageDialog(null, "FocusFlow is already running.\nLook for it in the taskbar or the system tray.",
                            "FocusFlow", JOptionPane.INFORMATION_MESSAGE);
                }
                System.exit(0);
                return;
            }
            listenForOpenRequests();
            Database.open(AppPaths.dbFile());
            AppContext.init();
            SettingsService st = AppContext.settings;
            Theme.accentName = st.get(SettingsService.ACCENT, "Blue");
            Theme.paletteName = st.get(SettingsService.APP_PALETTE, "");
            Theme.init("dark".equals(st.get(SettingsService.THEME, "light")));
            Animator.reduceMotion = st.getBool(SettingsService.REDUCE_MOTION, false);
            AppContext.backup.autoBackupIfNeeded();

            enableStartupWithWidgetOnce();

            // Background services: they keep running when the window is closed.
            AppContext.tray.install();
            AppContext.notifications.start();
            WidgetController.start();

            if (background && AppContext.tray.isAvailable()) {
                // Started by Windows at sign-in: stay out of the way, just the widget and the tray icon.
            } else {
                AppContext.showMain();
                if (!background) AppContext.frame.playIntroOnce();
            }

            // While only the widget is running, hand unused memory back to Windows now and then.
            javax.swing.Timer trim = new javax.swing.Timer(120_000, e -> { if (AppContext.frame == null) System.gc(); });
            trim.start();
        } catch (Throwable t) {
            log(t);
            JOptionPane.showMessageDialog(null, "FocusFlow could not start:\n" + t.getMessage() + "\n\nDetails were saved to " + AppPaths.logFile(),
                    "FocusFlow", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /** The widget is meant to be there whenever Windows starts, so turn "launch at startup" on the first time. */
    private static void enableStartupWithWidgetOnce() {
        SettingsService st = AppContext.settings;
        if (st.getBool("system.widgetStartupSet", false)) return;
        st.setQuiet("system.widgetStartupSet", "true");
        if (!StartupRegistry.supported() || !st.getBool(SettingsService.WIDGET_ENABLED, true)) return;
        try {
            StartupRegistry.set(true);
            st.setQuiet(SettingsService.LAUNCH_ON_STARTUP, "true");
        } catch (Exception ignored) { }
    }

    private static void log(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            Files.writeString(AppPaths.logFile(), LocalDateTime.now() + "\n" + sw + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
}
