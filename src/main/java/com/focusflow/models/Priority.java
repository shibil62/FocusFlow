package com.focusflow.models;

public enum Priority {
    LOW("Low"), NORMAL("Normal"), HIGH("High"), CRITICAL("Critical");

    public final String label;
    Priority(String label) { this.label = label; }

    public static Priority of(int i) {
        Priority[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
