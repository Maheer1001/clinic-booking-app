package view.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18); // a Wednesday

    @Test
    void parseStored_readsDaoFormat_andRejectsJunk() {
        assertEquals(LocalDate.of(2026, 11, 20), Dates.parseStored("20/11/26"));
        assertNull(Dates.parseStored("2026-11-20"));
        assertNull(Dates.parseStored(" "));
        assertNull(Dates.parseStored(null));
    }

    @Test
    void full_and_medium() {
        assertEquals("Fri 20 Nov 2026", Dates.full(LocalDate.of(2026, 11, 20)));
        assertEquals("20 Nov 2026", Dates.medium("20/11/26"));
        assertEquals("not a date", Dates.medium("not a date"));
        assertEquals("", Dates.full(null));
    }

    @Test
    void upcoming_usesTodayTomorrowThenWeekday() {
        assertEquals("Today", Dates.upcoming(TODAY, TODAY));
        assertEquals("Tomorrow", Dates.upcoming(TODAY.plusDays(1), TODAY));
        assertEquals("Fri 20 Nov", Dates.upcoming(TODAY.plusDays(2), TODAY));
        assertEquals("Fri 1 Jan 2027", Dates.upcoming(LocalDate.of(2027, 1, 1), TODAY), "other years show the year");
        assertEquals("Fri 20 Nov", Dates.upcoming("20/11/26", TODAY));
    }

    @Test
    void timeRange_handlesMissingParts() {
        assertEquals("10:00 – 10:30", Dates.timeRange("10:00", "10:30"));
        assertEquals("10:00", Dates.timeRange("10:00", null));
        assertEquals("10:30", Dates.timeRange(" ", "10:30"));
    }

    @Test
    void dateTime_isOneFormatEverywhere() {
        java.time.LocalTime nine = java.time.LocalTime.of(9, 0);
        assertEquals("Mon 23 Nov 2026, 09:00 – 09:45",
                Dates.dateTime(LocalDate.of(2026, 11, 23), nine, nine.plusMinutes(45)));
        assertEquals("Mon 23 Nov 2026, 09:00", Dates.dateTime(LocalDate.of(2026, 11, 23), nine, null));
        assertEquals("Wed 25 Nov 2026, 15:00 – 15:30", Dates.dateTime("25/11/26", "15:00", "15:30"),
                "stored strings (emails, cancel/reschedule) read the same as the screens");
        assertEquals("09:00 – 09:45", Dates.timeRange(nine, nine.plusMinutes(45)));
    }

    @Test
    void monthsAreThreeLetters() {
        // Locale.UK writes "Sept"; every other month is three letters.
        assertEquals("Wed 23 Sep 2026", Dates.full(LocalDate.of(2026, 9, 23)));
        assertEquals("1 Jan 1990", Dates.medium(LocalDate.of(1990, 1, 1)));
    }

    @Test
    void countdown() {
        assertEquals("today", Dates.countdown(TODAY, TODAY));
        assertEquals("tomorrow", Dates.countdown(TODAY.plusDays(1), TODAY));
        assertEquals("in 5 days", Dates.countdown(TODAY.plusDays(5), TODAY));
        assertEquals("in 3 weeks", Dates.countdown(TODAY.plusDays(21), TODAY));
        assertEquals("", Dates.countdown(TODAY.minusDays(1), TODAY), "past dates have no countdown");
        assertEquals("", Dates.countdown(null, TODAY));
    }
}
