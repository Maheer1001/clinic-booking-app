package service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The save message says plainly when a chosen reminder time won't be sent (there is no catch-up). */
class AppointmentReminderPlanTest {

    private static final Optional<LocalDateTime> MON_9AM = Optional.of(LocalDateTime.of(2026, 11, 30, 9, 0));

    @Test
    void allFit_saysWhenTheNextOneGoesOut() {
        assertEquals("Saved. Your next reminder will be emailed on Mon 30 Nov at 09:00.",
                new AppointmentReminderPlan(MON_9AM, 0, 0).message());
    }

    @Test
    void someSkipped_warnsAboutThoseOnly() {
        assertEquals("Saved. Your next reminder will be emailed on Mon 30 Nov at 09:00."
                        + " One reminder time has already passed, so it won't be sent.",
                new AppointmentReminderPlan(MON_9AM, 1, 0).message());
        assertEquals("Saved. Your next reminder will be emailed on Mon 30 Nov at 09:00."
                        + " 2 reminder times have already passed or fall after the appointment, so they won't be sent.",
                new AppointmentReminderPlan(MON_9AM, 1, 1).message());
    }

    @Test
    void singleRuleThatCannotFit_saysItWontBeSent() {
        assertEquals("Saved, but this reminder time has already passed, so it won't be sent.",
                new AppointmentReminderPlan(Optional.empty(), 1, 0).message());
        assertEquals("Saved, but this reminder time falls after the appointment, so it won't be sent.",
                new AppointmentReminderPlan(Optional.empty(), 0, 1).message());
    }

    @Test
    void noneFit_saysNoneWillBeSent() {
        assertEquals("Saved, but these reminder times fall after the appointment, so none will be sent.",
                new AppointmentReminderPlan(Optional.empty(), 0, 2).message());
    }

    @Test
    void pastAppointment() {
        AppointmentReminderPlan plan = AppointmentReminderPlan.pastAppointment();
        assertTrue(plan.firstSend().isEmpty());
        assertEquals(0, plan.skipped());
        assertTrue(plan.message().contains("appointment has already passed"));
    }
}
