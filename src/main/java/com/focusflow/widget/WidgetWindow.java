package com.focusflow.widget;

import com.focusflow.app.AppContext;
import com.focusflow.services.SettingsService;

import javax.swing.*;
import java.awt.*;

/** The borderless, see-through window that holds the widget. It does not appear in the taskbar. */
final class WidgetWindow extends JWindow {
    static final int DEF_W = 588, DEF_H = 328, MIN_W = 150, MIN_H = 120, MAX_W = 1800, MAX_H = 1200;

    private final WidgetPanel panel;
    private DesktopPin pin;

    WidgetWindow() {
        super((Frame) null);
        setFocusableWindowState(false);
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) { }
        panel = new WidgetPanel(this);
        setContentPane(panel);
        getRootPane().setOpaque(false);
        getLayeredPane().setOpaque(false);
        setSize(storedSize());
        applySettings();
        place();
    }

    private static Dimension storedSize() {
        SettingsService s = AppContext.settings;
        int w = Math.max(MIN_W, Math.min(MAX_W, s.getInt(SettingsService.WIDGET_W, DEF_W)));
        int h = Math.max(MIN_H, Math.min(MAX_H, s.getInt(SettingsService.WIDGET_H, DEF_H)));
        return new Dimension(w, h);
    }

    void applySettings() {
        panel.applySettings();
        Dimension d = storedSize();
        if (!d.equals(getSize())) {
            Point p = getLocation();
            setSize(d);
            if (isShowing()) setLocation(clamp(p, d));
        }
        panel.revalidate();
        panel.repaint();
    }

    private void place() {
        Dimension d = getSize();
        SettingsService s = AppContext.settings;
        int x = s.getInt(SettingsService.WIDGET_X, Integer.MIN_VALUE);
        int y = s.getInt(SettingsService.WIDGET_Y, Integer.MIN_VALUE);
        Point p;
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || !onScreen(new Rectangle(x, y, d.width, d.height))) {
            Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            p = new Point(r.x + r.width - d.width - 24, r.y + 40);
        } else {
            p = new Point(x, y);
        }
        setLocation(p);
    }

    private static boolean onScreen(Rectangle r) {
        for (GraphicsDevice dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = dev.getDefaultConfiguration().getBounds();
            Rectangle i = b.intersection(r);
            if (i.width > 80 && i.height > 80) return true;
        }
        return false;
    }

    private static Point clamp(Point p, Dimension d) {
        return onScreen(new Rectangle(p.x, p.y, d.width, d.height)) ? p : new Point(40, 40);
    }

    /** Remembers where the widget is and how big the user made it. */
    void saveBounds() {
        Rectangle r = getBounds();
        SettingsService s = AppContext.settings;
        s.setQuiet(SettingsService.WIDGET_X, String.valueOf(r.x));
        s.setQuiet(SettingsService.WIDGET_Y, String.valueOf(r.y));
        s.setQuiet(SettingsService.WIDGET_W, String.valueOf(r.width));
        s.setQuiet(SettingsService.WIDGET_H, String.valueOf(r.height));
    }

    /** Called once the window is visible: moves it onto the desktop layer, below all apps. */
    void pinToDesktop() {
        if (pin == null) {
            try { pin = DesktopPin.attach(this); } catch (Throwable ignored) { }
        }
    }

    @Override public void dispose() {
        if (pin != null) pin.stop();
        panel.shutdown();
        super.dispose();
    }
}
