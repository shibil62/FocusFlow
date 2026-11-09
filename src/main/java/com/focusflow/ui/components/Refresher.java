package com.focusflow.ui.components;

import com.focusflow.services.Notifier;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.HierarchyEvent;

/**
 * Coalesces change events into one refresh, only while the view is visible; also refreshes every 30s for clocks,
 * and once whenever the view becomes visible (so a view that was built while hidden never stays empty).
 */
public final class Refresher {
    private Refresher() {}

    public static void install(JComponent view, Runnable refresh, Notifier... sources) {
        Timer debounce = new Timer(40, e -> refresh.run());
        debounce.setRepeats(false);
        Runnable trigger = () -> { if (view.isShowing()) debounce.restart(); };
        for (Notifier n : sources) n.addListener(trigger);
        view.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && view.isShowing()) debounce.restart();
        });
        new Timer(30_000, e -> { if (view.isShowing()) refresh.run(); }).start();
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        return sp;
    }
}
