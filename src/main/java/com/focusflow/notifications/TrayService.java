package com.focusflow.notifications;

import com.focusflow.app.AppContext;
import com.focusflow.ui.components.AppIcon;

import java.awt.*;

/** System tray icon: open, quick add, quit, and balloon messages when the window is in the background. */
public class TrayService {
    private TrayIcon icon;

    public boolean isAvailable() { return icon != null; }

    public void install() {
        if (!SystemTray.isSupported() || icon != null) return;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open FocusFlow");
            open.addActionListener(e -> AppContext.showMain());
            MenuItem add = new MenuItem("New task");
            add.addActionListener(e -> com.focusflow.widget.QuickTaskDialog.create(null));
            MenuItem quit = new MenuItem("Quit FocusFlow");
            quit.addActionListener(e -> AppContext.quit());
            CheckboxMenuItem widget = new CheckboxMenuItem("Desktop widget", com.focusflow.app.AppContext.settings.getBool(com.focusflow.services.SettingsService.WIDGET_ENABLED, true));
            widget.addItemListener(e -> com.focusflow.widget.WidgetController.setEnabled(widget.getState()));
            com.focusflow.app.AppContext.settings.addListener(() ->
                    widget.setState(com.focusflow.app.AppContext.settings.getBool(com.focusflow.services.SettingsService.WIDGET_ENABLED, true)));
            menu.add(open);
            menu.add(add);
            menu.add(widget);
            menu.addSeparator();
            menu.add(quit);
            icon = new TrayIcon(AppIcon.create(64), "FocusFlow", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> AppContext.showMain());
            SystemTray.getSystemTray().add(icon);
        } catch (Exception e) {
            icon = null;
        }
    }

    /** The tray icon follows the theme colour. */
    public void refreshIcon() {
        if (icon != null) icon.setImage(AppIcon.create(64));
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
