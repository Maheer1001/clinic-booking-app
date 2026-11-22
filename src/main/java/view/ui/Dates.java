package view.ui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * How dates and times are written on screen, so every page uses the same readable format
 * ("Fri 20 Nov 2026", "10:00 – 10:30") instead of a mix of "20/11/26" and "2026-11-20".
 * One family: {@link #full} for appointment dates, {@link #medium} for lists, history and dates of birth,
 * {@link #timeRange} for times. English month names keep "Sep" (Locale.UK writes "Sept").
 * The DAOs hand dates around as "dd/MM/yy" strings; {@link #parseStored} reads those.
 */
public final class Dates {

    /** Format the DAOs use for dates passed as strings. */
    public static final DateTimeFormatter STORED = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Dates() {}

    /** "20/11/26" → 2026-11-20, or null if blank or not in that format. */
    public static LocalDate parseStored(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            return LocalDate.parse(stored.trim(), STORED);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "Fri 20 Nov 2026". */
    public static String full(LocalDate date) {
        return date == null ? "" : date.format(FULL);
    }

    /** "20 Nov 2026" (lists and history, where the weekday adds nothing). */
    public static String medium(LocalDate date) {
        return date == null ? "" : date.format(LONG);
    }

    /** A stored "dd/MM/yy" date as "20 Nov 2026"; unreadable values are returned unchanged. */
    public static String medium(String stored) {
        LocalDate d = parseStored(stored);
        return d != null ? medium(d) : (stored == null ? "" : stored);
    }

    /** Upcoming dates: "Today", "Tomorrow", otherwise "Fri 20 Nov" (with the year if it is not this year). */
    public static String upcoming(LocalDate date, LocalDate today) {
        if (date == null) return "";
        long days = ChronoUnit.DAYS.between(today, date);
        if (days == 0) return "Today";
        if (days == 1) return "Tomorrow";
        return date.getYear() == today.getYear() ? date.format(DAY_MONTH) : date.format(FULL);
    }

    /** A stored date for an upcoming appointment, as {@link #upcoming}; unreadable values unchanged. */
    public static String upcoming(String stored, LocalDate today) {
        LocalDate d = parseStored(stored);
        return d != null ? upcoming(d, today) : (stored == null ? "" : stored);
    }

    /** "10:00 – 10:30", or just the start time if there is no end. */
    public static String timeRange(String start, String end) {
        String s = start == null ? "" : start.trim();
        String e = end == null ? "" : end.trim();
        if (s.isEmpty()) return e;
        if (e.isEmpty()) return s;
        return s + " – " + e;
    }

    /** "09:00". */
    public static String time(LocalTime time) {
        return time == null ? "" : time.format(TIME);
    }

    /** "09:00 – 09:45", or just the start time if there is no end. */
    public static String timeRange(LocalTime start, LocalTime end) {
        return timeRange(time(start), time(end));
    }

    /** "Mon 23 Nov 2026, 09:00 – 09:45" (or "Mon 23 Nov 2026, 09:00" without an end). */
    public static String dateTime(LocalDate date, LocalTime start, LocalTime end) {
        String d = full(date);
        String t = timeRange(start, end);
        if (d.isEmpty()) return t;
        return t.isEmpty() ? d : d + ", " + t;
    }

    /** A stored "dd/MM/yy" date with "HH:mm" times, as {@link #dateTime}; unreadable dates are kept as they are. */
    public static String dateTime(String stored, String start, String end) {
        LocalDate d = parseStored(stored);
        String t = timeRange(start, end);
        String ds = d != null ? full(d) : (stored == null ? "" : stored.trim());
        if (ds.isEmpty()) return t;
        return t.isEmpty() ? ds : ds + ", " + t;
    }

    /** "in 2 days", "tomorrow", "today" for a date that is coming up; empty for past dates. */
    public static String countdown(LocalDate date, LocalDate today) {
        if (date == null) return "";
        long days = ChronoUnit.DAYS.between(today, date);
        if (days < 0) return "";
        if (days == 0) return "today";
        if (days == 1) return "tomorrow";
        if (days < 14) return "in " + days + " days";
        return "in " + (days / 7) + " weeks";
    }
}
