package com.focusflow.ui.components;

import com.focusflow.utilities.Time;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

/** Date and time spinners plus conversions to java.time. */
public final class Pickers {
    private Pickers() {}

    private static Date toDate(LocalDateTime t) { return Date.from(t.atZone(Time.ZONE).toInstant()); }

    public static JSpinner date(LocalDate d) {
        JSpinner s = new JSpinner(new SpinnerDateModel(toDate(d.atStartOfDay()), null, null, Calendar.DAY_OF_MONTH));
        s.setEditor(new JSpinner.DateEditor(s, "EEE, d MMM yyyy"));
        return s;
    }

    public static JSpinner time(LocalTime t) {
        JSpinner s = new JSpinner(new SpinnerDateModel(toDate(LocalDate.now().atTime(t)), null, null, Calendar.MINUTE));
        s.setEditor(new JSpinner.DateEditor(s, "h:mm a"));
        return s;
    }

    public static LocalDate getDate(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(Time.ZONE).toLocalDate();
    }

    public static LocalTime getTime(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(Time.ZONE).toLocalTime().withSecond(0).withNano(0);
    }

    public static void setDate(JSpinner s, LocalDate d) { s.setValue(toDate(d.atStartOfDay())); }
    public static void setTime(JSpinner s, LocalTime t) { s.setValue(toDate(LocalDate.now().atTime(t))); }
}
