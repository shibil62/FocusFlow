package com.focusflow.ui.components;

import java.util.ArrayList;
import java.util.List;

/** Collects clean-up actions (stop a timer, remove a listener) so a closed window can be fully released. */
public final class Janitor {
    private Janitor() {}

    private static final List<Runnable> TASKS = new ArrayList<>();

    public static void add(Runnable r) { TASKS.add(r); }

    public static void runAll() {
        List<Runnable> copy = new ArrayList<>(TASKS);
        TASKS.clear();
        for (Runnable r : copy) {
            try { r.run(); } catch (Throwable ignored) { }
        }
    }
}
