package model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReminderScheduleTest {

    @Test
    void fromString_plainIntegerYieldsSingleDayRule() {
        ReminderSchedule schedule = ReminderSchedule.fromString("3", 2);

        assertEquals(1, schedule.getReminders().size());
        ReminderSchedule.ReminderRule rule = schedule.getReminders().get(0);
        assertEquals(3, rule.amount());
        assertEquals("day", rule.unit());
        assertNull(rule.timeOfDay());
        assertNull(rule.repeatEvery());
    }

    @Test
    void fromString_nullUsesDefaultDaysBefore() {
        ReminderSchedule schedule = ReminderSchedule.fromString(null, 2);

        assertEquals(1, schedule.getReminders().size());
        ReminderSchedule.ReminderRule rule = schedule.getReminders().get(0);
        assertEquals(2, rule.amount());
        assertEquals("day", rule.unit());
    }

    @Test
    void toJson_and_fromString_roundTripMultipleRemindersAndQuietHours() {
        ReminderSchedule.ReminderRule r1 =
                new ReminderSchedule.ReminderRule(1, "day", "09:00",
                        new ReminderSchedule.RepeatEvery(1, "hour"));
        ReminderSchedule.ReminderRule r2 =
                new ReminderSchedule.ReminderRule(2, "week", null, null);
        ReminderSchedule.QuietHours qh = new ReminderSchedule.QuietHours("22:00", "07:00");

        ReminderSchedule original = new ReminderSchedule(
                java.util.List.of(r1, r2),
                qh
        );

        String json = original.toJson();
        ReminderSchedule parsed = ReminderSchedule.fromString(json, 2);

        assertEquals(2, parsed.getReminders().size());

        ReminderSchedule.ReminderRule pr1 = parsed.getReminders().get(0);
        assertEquals(1, pr1.amount());
        assertEquals("day", pr1.unit());
        assertEquals("09:00", pr1.timeOfDay());
        assertNotNull(pr1.repeatEvery());
        assertEquals(1, pr1.repeatEvery().amount());
        assertEquals("hour", pr1.repeatEvery().unit());

        ReminderSchedule.ReminderRule pr2 = parsed.getReminders().get(1);
        assertEquals(2, pr2.amount());
        assertEquals("week", pr2.unit());
        assertNull(pr2.timeOfDay());
        assertNull(pr2.repeatEvery());

        assertNotNull(parsed.getQuietHours());
        assertEquals("22:00", parsed.getQuietHours().start());
        assertEquals("07:00", parsed.getQuietHours().end());
    }

    @Test
    void applyQuietHours_movesInsideWindowToEnd() {
        ReminderSchedule.QuietHours qh = new ReminderSchedule.QuietHours("22:00", "07:00");
        ReminderSchedule schedule = new ReminderSchedule(java.util.List.of(), qh);

        LocalDateTime original = LocalDateTime.of(2026, 3, 20, 23, 0);
        LocalDateTime adjusted = schedule.applyQuietHours(original);

        assertEquals(7, adjusted.getHour());
        assertEquals(original.toLocalDate().plusDays(1), adjusted.toLocalDate());
    }
}

