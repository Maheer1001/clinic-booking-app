package service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which appointment slots a clinic offers, from its opening hours and the service's length.
 *
 * Slots are not stored up front: a {@code Timeslot} row is only created when somebody books one,
 * and it records how many bookings it has against its capacity. A slot with no row is free.
 */
public final class SlotSchedule {

    /** Bookings allowed per slot when a slot row is first created. */
    public static final int DEFAULT_CAPACITY = 1;

    /** How far ahead (in days, including today) patients can book. */
    public static final int BOOKING_WINDOW_DAYS = 28;

    /** Used when a clinic has no opening hours recorded, or they can't be read. */
    public static final Hours DEFAULT_HOURS =
            new Hours(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(17, 0));

    private static final Pattern HOURS = Pattern.compile(
            "\\s*([A-Za-z]+)\\s*(?:-\\s*([A-Za-z]+))?\\s*:\\s*(\\d{1,2})(?::(\\d{2}))?\\s*-\\s*(\\d{1,2})(?::(\\d{2}))?\\s*");

    private SlotSchedule() {}

    /** Opening days and times for a clinic. */
    public record Hours(Set<DayOfWeek> days, LocalTime open, LocalTime close) {}

    /** A bookable time range on one day. */
    public record Slot(LocalTime start, LocalTime end) {}

    /**
     * Parses opening hours such as {@code "Monday-Friday: 9:00-5:00"} or {@code "Saturday: 10:00-14:00"}.
     * Times are read on a 12-hour clock when the closing time would otherwise be before the opening time
     * (so {@code 9:00-5:00} means 09:00 to 17:00). Falls back to {@link #DEFAULT_HOURS}.
     */
    public static Hours parseHours(String text) {
        if (text == null) return DEFAULT_HOURS;
        Matcher m = HOURS.matcher(text);
        if (!m.matches()) return DEFAULT_HOURS;
        DayOfWeek from = day(m.group(1));
        DayOfWeek to = m.group(2) == null ? from : day(m.group(2));
        if (from == null || to == null) return DEFAULT_HOURS;

        LocalTime open = time(m.group(3), m.group(4));
        LocalTime close = time(m.group(5), m.group(6));
        if (open == null || close == null) return DEFAULT_HOURS;
        if (!close.isAfter(open) && close.getHour() < 12) {
            close = close.plusHours(12);
        }
        if (!close.isAfter(open)) return DEFAULT_HOURS;

        Set<DayOfWeek> days = EnumSet.of(from);
        for (DayOfWeek d = from; d != to; d = d.plus(1)) {
            days.add(d.plus(1)); // walks forward, so "Friday-Monday" wraps over the weekend
        }
        return new Hours(days, open, close);
    }

    /** Every slot of the given length that fits inside opening hours on {@code date}. */
    public static List<Slot> slotsOn(Hours hours, LocalDate date, int durationMinutes) {
        List<Slot> slots = new ArrayList<>();
        if (durationMinutes <= 0 || !hours.days().contains(date.getDayOfWeek())) return slots;
        LocalTime start = hours.open();
        while (!start.plusMinutes(durationMinutes).isAfter(hours.close())
                && start.plusMinutes(durationMinutes).isAfter(start)) {
            slots.add(new Slot(start, start.plusMinutes(durationMinutes)));
            start = start.plusMinutes(durationMinutes);
        }
        return slots;
    }

    private static DayOfWeek day(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().equals(n) || d.name().startsWith(n) && n.length() >= 3) return d;
        }
        return null;
    }

    private static LocalTime time(String h, String m) {
        int hour = Integer.parseInt(h);
        int minute = m == null ? 0 : Integer.parseInt(m);
        if (hour > 23 || minute > 59) return null;
        return LocalTime.of(hour, minute);
    }
}
