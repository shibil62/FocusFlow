package com.focusflow.animations;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * One shared ~120Hz Swing timer drives every animation. It only runs while something is animating,
 * so an idle app uses no CPU. Nothing here blocks: interactions never wait for an animation to finish.
 */
public final class Animator {
    private Animator() {}

    public interface Tickable { boolean tick(double dt); }

    public static volatile boolean reduceMotion = false;

    private static final List<Tickable> active = new ArrayList<>();
    private static final List<Tickable> pending = new ArrayList<>();
    private static Timer timer;
    private static long last;

    public static void run(Tickable t) {
        pending.add(t);
        if (timer == null) timer = new Timer(8, e -> tick());
        if (!timer.isRunning()) {
            last = System.nanoTime();
            timer.start();
        }
    }

    private static void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9);
        last = now;
        active.addAll(pending);
        pending.clear();
        Iterator<Tickable> it = active.iterator();
        while (it.hasNext()) {
            if (!it.next().tick(dt)) it.remove();
        }
        if (active.isEmpty() && pending.isEmpty()) timer.stop();
    }

    /** Time-based tween with ease-out. progress goes 0..1. */
    public static void tween(int ms, DoubleConsumer onProgress, Runnable done) {
        if (reduceMotion || ms <= 0) {
            onProgress.accept(1);
            if (done != null) done.run();
            return;
        }
        double[] t = {0};
        run(dt -> {
            t[0] += dt * 1000.0 / ms;
            boolean end = t[0] >= 1;
            onProgress.accept(easeOut(Math.min(1, t[0])));
            if (end && done != null) done.run();
            return !end;
        });
    }

    public static double easeOut(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }
}
