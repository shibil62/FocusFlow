package com.focusflow.animations;

/**
 * A spring-driven number, described the way Apple does it: "response" (seconds, how quick) and
 * "damping" (1.0 = no overshoot, below 1.0 bounces). Retargeting mid-flight keeps the current
 * value and velocity, so motion is always interruptible.
 */
public final class SpringValue implements Animator.Tickable {
    public double value, target, velocity;
    private final double omega, zeta;
    private final Runnable onChange;
    private boolean running;
    private Runnable onSettled;

    public SpringValue(double initial, double response, double damping, Runnable onChange) {
        value = target = initial;
        omega = 2 * Math.PI / response;
        zeta = damping;
        this.onChange = onChange;
    }

    public SpringValue onSettled(Runnable r) { onSettled = r; return this; }

    public void to(double t) {
        target = t;
        if (Animator.reduceMotion) {
            value = t;
            velocity = 0;
            onChange.run();
            if (onSettled != null) onSettled.run();
            return;
        }
        if (!running) {
            running = true;
            Animator.run(this);
        }
    }

    public void snap(double v) {
        value = target = v;
        velocity = 0;
    }

    public boolean isAnimating() { return running; }

    @Override
    public boolean tick(double dt) {
        double k = omega * omega, c = 2 * zeta * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            double a = -k * (value - target) - c * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        boolean settled = Math.abs(value - target) < 0.0008 && Math.abs(velocity) < 0.0008;
        if (settled) {
            value = target;
            velocity = 0;
            running = false;
        }
        onChange.run();
        if (settled && onSettled != null) onSettled.run();
        return !settled;
    }
}
