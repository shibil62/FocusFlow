package com.focusflow.utilities;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Date and time helpers. All persisted times are epoch milliseconds. */
public final class Time {
    private Time() {}

    public static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter T12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter D_SHORT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter D_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter D_MED = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static long now() { return System.currentTimeMillis(); }
    public static long ms(LocalDateTime t) { return t.atZone(ZONE).toInstant().toEpochMilli(); }
    public static Long msOrNull(LocalDateTime t) { return t == null ? null : ms(t); }
    public static LocalDateTime dt(long ms) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE); }
    public static LocalDateTime dtOrNull(Long ms) { return ms == null ? null : dt(ms); }
    public static LocalDate today() { return LocalDate.now(ZONE); }

    public static String time(LocalDateTime t) { return t.format(T12); }
    public static String time(LocalTime t) { return t.format(T12); }
    public static String range(LocalDateTime a, LocalDateTime b) { return time(a) + " – " + time(b); }
    public static String dateShort(LocalDate d) { return d.format(D_SHORT); }
    public static String dateLong(LocalDate d) { return d.format(D_LONG); }
    public static String dateMed(LocalDate d) { return d.format(D_MED); }

    public static String relativeDay(LocalDate d) {
        LocalDate t = today();
        if (d.equals(t)) return "Today";
        if (d.equals(t.plusDays(1))) return "Tomorrow";
        if (d.equals(t.minusDays(1))) return "Yesterday";
        return dateShort(d);
    }

    /** 90 -> "1h 30m" */
    public static String minutes(long m) {
        if (m < 60) return m + "m";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + "h" : h + "h " + r + "m";
    }

    public static String seconds(long s) {
        if (s < 60) return s + "s";
        return minutes(Math.round(s / 60.0));
    }

    public static LocalDate weekStart(LocalDate d, DayOfWeek first) {
        int diff = (d.getDayOfWeek().getValue() - first.getValue() + 7) % 7;
        return d.minusDays(diff);
    }
}
