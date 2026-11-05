package com.focusflow.models;

import java.time.LocalDateTime;

public enum Recurrence {
    NONE("Does not repeat"), DAILY("Every day"), WEEKLY("Every week"), MONTHLY("Every month");

    public final String label;
    Recurrence(String label) { this.label = label; }

    public LocalDateTime shift(LocalDateTime t, int times) {
        LocalDateTime r = t;
        for (int i = 0; i < times; i++) {
            r = switch (this) {
                case DAILY -> r.plusDays(1);
                case WEEKLY -> r.plusWeeks(1);
                case MONTHLY -> r.plusMonths(1);
                default -> r;
            };
        }
        return r;
    }

    public static Recurrence parse(String s) {
        try { return valueOf(s); } catch (Exception e) { return NONE; }
    }
}
