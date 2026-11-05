package com.focusflow.services;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal observable base class. Listeners are always invoked on the Swing event thread. */
public class Notifier {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    protected void fire() {
        if (SwingUtilities.isEventDispatchThread()) {
            for (Runnable r : listeners) r.run();
        } else {
            SwingUtilities.invokeLater(() -> { for (Runnable r : listeners) r.run(); });
        }
    }
}
