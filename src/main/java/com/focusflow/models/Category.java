package com.focusflow.models;

import java.awt.Color;

public class Category {
    public long id;
    public String name = "";
    public String color = "#8E8E93";
    public int sortOrder;

    public Category() {}
    public Category(long id, String name, String color) { this.id = id; this.name = name; this.color = color; }

    public Color awt() {
        try { return Color.decode(color); } catch (Exception e) { return new Color(0x8E8E93); }
    }

    @Override public String toString() { return name; }
}
