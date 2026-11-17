package controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Date helpers for the tracking lists. Tracking records carry dates as "dd/MM/yy" strings; a
 * missing or malformed date must not crash searching or sorting, so parsing here returns null
 * instead of throwing, and records without a date sort last.
 */
final class TrackingDates {

    static final DateTimeFormatter STORED = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private TrackingDates() {}

    /** @return the parsed date, or null if blank or not in the stored format */
    static LocalDate parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(stored.trim(), STORED);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "21/04/25" → "21 Apr 2025" (four-digit year, month name); unparseable values are returned as-is. */
    static String display(String stored) {
        LocalDate d = parse(stored);
        if (d != null) {
            return d.format(DISPLAY);
        }
        return stored == null || stored.isBlank() ? "Date not recorded" : stored;
    }

    /** Within [from, to] (either may be null = open). A record with no date only matches when no range is set. */
    static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return from == null && to == null;
        }
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    static <T> Comparator<T> newestFirst(Function<T, String> dateOf) {
        return Comparator.comparing((T t) -> parse(dateOf.apply(t)), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    static <T> Comparator<T> oldestFirst(Function<T, String> dateOf) {
        return Comparator.comparing((T t) -> parse(dateOf.apply(t)), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** Case-insensitive name order, null-safe. */
    static <T> Comparator<T> byName(Function<T, String> nameOf) {
        return Comparator.comparing((T t) -> nameOf.apply(t) == null ? "" : nameOf.apply(t), String.CASE_INSENSITIVE_ORDER);
    }
}
