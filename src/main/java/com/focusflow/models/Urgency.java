package com.focusflow.models;

/** Result of the deadline intelligence calculation for one task. */
public record Urgency(Level level, int score, String message, String countdown, boolean overloaded) {

    /** Order matters: the four middle levels are compared by ordinal when escalating. */
    public enum Level {
        NONE("No deadline"), DONE("Completed"), COMFORTABLE("Comfortable"), APPROACHING("Approaching"),
        URGENT("Urgent"), CRITICAL("Critical"), OVERDUE("Overdue");

        public final String label;
        Level(String label) { this.label = label; }
    }

    public boolean isHot() { return level == Level.URGENT || level == Level.CRITICAL || level == Level.OVERDUE; }
}
