package service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotScheduleTest {

    private static LocalDate next(DayOfWeek day) {
        return LocalDate.now().with(TemporalAdjusters.next(day));
    }

    @Test
    void parsesWeekdayHoursWithTwelveHourClosingTime() {
        SlotSchedule.Hours h = SlotSchedule.parseHours("Monday-Friday: 9:00-5:00");
        assertEquals(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), h.days());
        assertEquals(LocalTime.of(9, 0), h.open());
        assertEquals(LocalTime.of(17, 0), h.close());
    }

    @Test
    void parsesSingleDayAnd24HourTimes() {
        SlotSchedule.Hours h = SlotSchedule.parseHours("Saturday: 10:00-14:30");
        assertEquals(EnumSet.of(DayOfWeek.SATURDAY), h.days());
        assertEquals(LocalTime.of(14, 30), h.close());
    }

    @Test
    void dayRangesWrapAroundTheWeekend() {
        SlotSchedule.Hours h = SlotSchedule.parseHours("Friday-Monday: 10:00-14:00");
        assertEquals(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY), h.days());
    }

    @Test
    void unreadableOrMissingHoursFallBackToDefault() {
        assertEquals(SlotSchedule.DEFAULT_HOURS, SlotSchedule.parseHours(null));
        assertEquals(SlotSchedule.DEFAULT_HOURS, SlotSchedule.parseHours("by appointment"));
    }

    @Test
    void fillsTheDayWithBackToBackSlotsThatFitBeforeClosing() {
        SlotSchedule.Hours h = SlotSchedule.parseHours("Monday-Friday: 9:00-5:00");

        List<SlotSchedule.Slot> thirty = SlotSchedule.slotsOn(h, next(DayOfWeek.TUESDAY), 30);
        assertEquals(16, thirty.size());
        assertEquals(new SlotSchedule.Slot(LocalTime.of(9, 0), LocalTime.of(9, 30)), thirty.get(0));
        assertEquals(new SlotSchedule.Slot(LocalTime.of(16, 30), LocalTime.of(17, 0)), thirty.get(15));

        // 45-minute slots: the 11th would end at 17:15, after closing.
        assertEquals(10, SlotSchedule.slotsOn(h, next(DayOfWeek.TUESDAY), 45).size());
    }

    @Test
    void noSlotsOnClosedDaysOrForInvalidDurations() {
        SlotSchedule.Hours h = SlotSchedule.parseHours("Monday-Friday: 9:00-5:00");
        assertTrue(SlotSchedule.slotsOn(h, next(DayOfWeek.SUNDAY), 30).isEmpty());
        assertTrue(SlotSchedule.slotsOn(h, next(DayOfWeek.TUESDAY), 0).isEmpty());
    }
}
