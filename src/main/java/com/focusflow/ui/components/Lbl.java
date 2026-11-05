package com.focusflow.ui.components;

import com.focusflow.ui.Theme;

import javax.swing.*;
import java.awt.*;

/** A label whose colour follows the active theme automatically. */
public class Lbl extends JLabel {
    public enum Role { TEXT, TEXT2, TEXT3, ACCENT, DANGER, SUCCESS }

    private Role role;

    public Lbl(String text, Font font, Role role) {
        super(text);
        this.role = role;
        setFont(font);
        setOpaque(false);
    }

    public void setRole(Role r) { role = r; repaint(); }

    @Override
    public Color getForeground() {
        if (role == null) return super.getForeground();
        return switch (role) {
            case TEXT -> Theme.text;
            case TEXT2 -> Theme.text2;
            case TEXT3 -> Theme.text3;
            case ACCENT -> Theme.accent;
            case DANGER -> Theme.danger;
            case SUCCESS -> Theme.success;
        };
    }

    public static Lbl title(String s) { return new Lbl(s, Theme.bold(28f), Role.TEXT); }
    public static Lbl h2(String s) { return new Lbl(s, Theme.semi(16f), Role.TEXT); }
    public static Lbl body(String s) { return new Lbl(s, Theme.font(13f), Role.TEXT); }
    public static Lbl sub(String s) { return new Lbl(s, Theme.font(13f), Role.TEXT2); }
    public static Lbl caption(String s) { return new Lbl(s, Theme.font(12f), Role.TEXT2); }
}
