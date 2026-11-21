package com.focusflow.widget;

import com.focusflow.app.AppContext;
import com.focusflow.services.SettingsService;

import javax.swing.*;

/** Creates, shows and removes the desktop calendar widget, following the settings. */
public final class WidgetController {
    private WidgetController() {}

    private static WidgetWindow window;
    private static boolean hooked;

    /** Called once after the main window is up. */
    public static void start() {
        if (!hooked) {
            hooked = true;
            AppContext.settings.addListener(WidgetController::sync);
        }
        sync();
    }

    /** Shows, hides or restyles the widget to match the stored settings. */
    public static void sync() {
        boolean on = AppContext.settings.getBool(SettingsService.WIDGET_ENABLED, true);
        if (on && window == null) {
            try {
                window = new WidgetWindow();
                window.setVisible(true);
                window.pinToDesktop();
            } catch (Throwable t) {
                window = null;
            }
        } else if (!on && window != null) {
            window.dispose();
            window = null;
        } else if (window != null) {
            window.applySettings();
        }
    }

    /** The theme changed: a widget that follows the app colours needs to repaint. */
    public static void themeChanged() {
        if (window != null) SwingUtilities.invokeLater(window::applySettings);
    }

    public static void setEnabled(boolean on) { AppContext.settings.set(SettingsService.WIDGET_ENABLED, on); }
}
