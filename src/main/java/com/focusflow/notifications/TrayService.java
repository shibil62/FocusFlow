package com.focusflow.notifications;

import com.focusflow.ui.MainFrame;
import com.focusflow.ui.components.AppIcon;

import java.awt.*;

/** System tray icon: open, quick add, quit, and balloon messages when the window is in the background. */
public class TrayService {
    private TrayIcon icon;

    public boolean isAvailable() { return icon != null; }

    public void install(MainFrame f) {
        if (!SystemTray.isSupported() || icon != null) return;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open FocusFlow");
            open.addActionListener(e -> f.showFromTray());
            MenuItem add = new MenuItem("New task");
            add.addActionListener(e -> { f.showFromTray(); com.focusflow.ui.dialogs.TaskDialog.create(); });
            MenuItem quit = new MenuItem("Quit FocusFlow");
            quit.addActionListener(e -> f.quit());
            menu.add(open);
            menu.add(add);
            menu.addSeparator();
            menu.add(quit);
            icon = new TrayIcon(AppIcon.create(64), "FocusFlow", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> f.showFromTray());
            SystemTray.getSystemTray().add(icon);
        } catch (Exception e) {
            icon = null;
        }
    }

    public void message(String title, String text) {
        if (icon != null) icon.displayMessage(title, text, TrayIcon.MessageType.INFO);
    }

    public void remove() {
        if (icon != null) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }
}
