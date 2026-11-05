package com.focusflow.models;

/** Stored statuses. "Overdue" is never stored: it is calculated from the deadline. */
public enum Status {
    NOT_STARTED("Not Started"), IN_PROGRESS("In Progress"), COMPLETED("Completed");

    public final String label;
    Status(String label) { this.label = label; }

    public static Status of(int i) {
        Status[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
