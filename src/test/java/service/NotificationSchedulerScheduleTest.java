package service;

import model.Appointment;
import model.Prescription;
import model.ReminderSchedule;
import model.ReminderTemplateOverrides;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import service.email.AppointmentReminderEmail;
import service.email.Email;
import service.email.EmailSender;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotificationScheduler: schedule-based reminders, de-duplication, cancelled / past
 * appointments, exception handling and daylight saving handling.
 *
 * These use a fake EmailSender and a very small default delay so the tests complete quickly.
 */
class NotificationSchedulerScheduleTest {

    private static class CapturingEmailSender extends EmailSender {
        final List<Email> sent = new CopyOnWriteArrayList<>();
        volatile boolean throwOnSend;

        @Override
        public boolean send(Email email) {
            sent.add(email);
            if (throwOnSend) {
                throw new IllegalStateException("boom");
            }
            return true;
        }
    }

    private final CapturingEmailSender sender = new CapturingEmailSender();
    private NotificationScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private static Appointment appointmentIn(long seconds) {
        return new Appointment(1, 1, "STI Screening", "Clinic 1", "Dr. Smith",
                LocalDateTime.now().plusSeconds(seconds));
    }

    private static String singleRule(int amount, String unit) {
        return new ReminderSchedule(List.of(new ReminderSchedule.ReminderRule(amount, unit, null, null)), null).toJson();
    }

    private static String discreetOverrides() {
        return new ReminderTemplateOverrides(null, null, null, null, null, null, true).toJson();
    }

    private void waitForSends(int count, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (sender.sent.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void scheduleAppointmentReminderWithSchedule_sendsEmailForSingleRule() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);

        // Appointment in 61s, reminder 1 minute before → sends in about 1s.
        scheduler.scheduleAppointmentReminderWithSchedule(appointmentIn(61), "patient@example.com", "Jane Doe",
                singleRule(1, "minute"), discreetOverrides());

        waitForSends(1, 3000);
        assertEquals(1, sender.sent.size());
        assertTrue(sender.sent.getFirst().isDiscreet(), "Saved discreet choice must reach the email");
    }

    @Test
    void pastAppointment_getsNoReminder() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);

        scheduler.scheduleAppointmentReminderWithSchedule(appointmentIn(-60), "patient@example.com", "Jane Doe",
                singleRule(1, "minute"), null);
        scheduler.scheduleAppointmentReminder(appointmentIn(-60), "patient@example.com", "Jane Doe", 0L, null);

        Thread.sleep(300);
        assertEquals(0, sender.sent.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void reschedulingSameAppointment_replacesInsteadOfDuplicating() throws Exception {
        scheduler = new NotificationScheduler(sender, 3600);
        Appointment appt = appointmentIn(10 * 86400);

        scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "Jane", singleRule(2, "day"), null);
        scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "Jane", singleRule(2, "day"), null);
        scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "Jane", singleRule(1, "day"), null);

        assertEquals(1, scheduler.pendingCount("appointment:1"));

        scheduler.cancelAppointmentReminders(1);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void missedReminderTimes_areSkippedWithNoCatchUp() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);
        // Appointment in 30 minutes; 1-day and 2-day reminders have both passed.
        String schedule = new ReminderSchedule(List.of(
                new ReminderSchedule.ReminderRule(1, "day", null, null),
                new ReminderSchedule.ReminderRule(2, "day", null, null)), null).toJson();

        AppointmentReminderPlan plan = scheduler.scheduleAppointmentReminderWithSchedule(
                appointmentIn(1800), "p@example.com", "Jane", schedule, null);

        Thread.sleep(400);
        assertEquals(0, sender.sent.size(), "a passed reminder time must not be sent early as a catch-up");
        assertEquals(0, scheduler.pendingCount());
        assertTrue(plan.firstSend().isEmpty());
        assertEquals(2, plan.passed());
        assertEquals(0, plan.afterAppointment());
    }

    @Test
    void onlyTheRulesThatFitAreScheduled_andTheSkippedOnesAreReported() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);
        Appointment appt = new Appointment(9, 1, "STI Screening", "Clinic 1", "Dr. Smith",
                LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0));
        String schedule = new ReminderSchedule(List.of(
                new ReminderSchedule.ReminderRule(1, "day", null, null),     // fits
                new ReminderSchedule.ReminderRule(5, "day", null, null),     // already passed
                new ReminderSchedule.ReminderRule(1, "hour", "18:00", null)  // 18:00 on the day: after a 14:00 appointment
        ), null).toJson();

        AppointmentReminderPlan plan = scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "P", schedule, null);

        assertEquals(java.util.Optional.of(appt.getAppointmentDateTime().minusDays(1)), plan.firstSend());
        assertEquals(1, plan.passed());
        assertEquals(1, plan.afterAppointment());
        assertEquals(1, scheduler.pendingCount("appointment:9"), "only the rule that fits is scheduled; no catch-up");
        Thread.sleep(300);
        assertEquals(0, sender.sent.size());
    }

    @Test
    void cancelledAppointment_isNotRemindedAtSendTime() throws Exception {
        AtomicBoolean stillBooked = new AtomicBoolean(true);
        scheduler = new NotificationScheduler(sender, 0, Clock.systemDefaultZone(), (appId, userId) -> stillBooked.get());

        scheduler.scheduleAppointmentReminder(appointmentIn(3600), "p@example.com", "Jane", 1L, null);
        stillBooked.set(false); // appointment cancelled before the reminder fires

        Thread.sleep(1500);
        assertEquals(0, sender.sent.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void repeatingReminder_keepsRunningAfterSendThrows() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);
        sender.throwOnSend = true;
        // Every second, starting 1s from now; appointment in an hour.
        String schedule = new ReminderSchedule(List.of(new ReminderSchedule.ReminderRule(3599, "second", null,
                new ReminderSchedule.RepeatEvery(1, "second"))), null).toJson();

        scheduler.scheduleAppointmentReminderWithSchedule(appointmentIn(3600), "p@example.com", "Jane", schedule, null);

        waitForSends(2, 4000);
        assertTrue(sender.sent.size() >= 2, "An exception in one send must not stop later repeats");
    }

    @Test
    void treatmentReminder_withNoEndDateAndDiscreet_sends() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);
        Prescription rx = new Prescription(5, 1, "PrEP", "1 tablet", LocalDate.now().minusDays(3), null);

        scheduler.scheduleTreatmentReminder(rx, "p@example.com", "Jane", 0L, null);

        waitForSends(1, 2000);
        assertEquals(1, sender.sent.size());
        assertTrue(sender.sent.getFirst().isDiscreet(), "No saved choice defaults to discreet");
        assertFalse(sender.sent.getFirst().getTemplateContext().containsKey("treatmentName"));
    }

    @Test
    void treatmentReminder_afterCourseEnded_isNotScheduled() {
        scheduler = new NotificationScheduler(sender, 0);
        Prescription rx = new Prescription(6, 1, "Doxycycline", "100mg", LocalDate.now().minusDays(7), LocalDate.now().minusDays(1));

        scheduler.scheduleTreatmentReminder(rx, "p@example.com", "Jane", 12, null);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void treatmentReminder_forCourseStartingLater_waitsForTheStartDate() {
        scheduler = new NotificationScheduler(sender, 0);
        LocalDate start = LocalDate.now().plusDays(5);
        Prescription rx = new Prescription(8, 1, "Doxycycline", "100mg", start, start.plusDays(7));

        var first = scheduler.scheduleTreatmentReminder(rx, "p@example.com", "Jane", 12, null);

        assertTrue(first.isPresent());
        assertFalse(first.get().toLocalDate().isBefore(start), "No dose reminder before the course starts: " + first.get());
        assertTrue(first.get().isBefore(start.atStartOfDay().plusHours(12)), "First one on the start day: " + first.get());
    }

    @Test
    void bookingReminder_rescheduleReplacesAndCancelWorks() {
        scheduler = new NotificationScheduler(sender, 3600);
        scheduler.scheduleBookingReminder("P@example.com", "Jane", 90, LocalDate.now(), null);
        scheduler.scheduleBookingReminder("p@example.com", "Jane", 120, LocalDate.now(), null);
        assertEquals(1, scheduler.pendingCount());

        scheduler.cancelBookingReminders("p@example.com");
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void dailyRepeat_keepsWallClockTimeAcrossDaylightSaving() {
        ZoneId london = ZoneId.of("Europe/London");
        // Clocks go forward on 29 March 2026.
        ZonedDateTime before = LocalDateTime.of(2026, 3, 28, 9, 0).atZone(london);
        UnaryOperator<ZonedDateTime> daily = NotificationScheduler.repeatStep(new ReminderSchedule.RepeatEvery(1, "day"));

        ZonedDateTime after = daily.apply(before);
        assertEquals(9, after.getHour());
        assertEquals(23, java.time.Duration.between(before, after).toHours());
    }

    @Test
    void invalidRepeat_isTreatedAsOneOff() {
        assertNull(NotificationScheduler.repeatStep(new ReminderSchedule.RepeatEvery(0, "day")));
        assertNull(NotificationScheduler.repeatStep(new ReminderSchedule.RepeatEvery(1, "fortnight")));
        assertNull(NotificationScheduler.repeatStep(null));
    }

    @Test
    void workerThreadsAreDaemon() throws Exception {
        scheduler = new NotificationScheduler(new EmailSender() {
            @Override
            public boolean send(Email email) {
                sender.sent.add(email);
                assertTrue(Thread.currentThread().isDaemon(), "Scheduler threads must not keep the JVM alive");
                return true;
            }
        }, 0);
        scheduler.scheduleBookingReminder("p@example.com", "Jane", 30, 0L, null);
        waitForSends(1, 2000);
        assertEquals(1, sender.sent.size());
    }

    @Test
    void fullDetailsChoice_isPassedToEmail() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);
        String fullDetails = new ReminderTemplateOverrides(null, null, null, null, null, null, false).toJson();
        scheduler.scheduleAppointmentReminder(appointmentIn(3600), "p@example.com", "Jane", 0L, fullDetails);
        waitForSends(1, 2000);
        AppointmentReminderEmail email = (AppointmentReminderEmail) sender.sent.getFirst();
        assertFalse(email.isDiscreet());
        assertEquals("STI Screening", email.getTemplateContext().get("appointmentType"));
    }

    @Test
    void schedule_reportsWhenTheFirstReminderGoesOut() {
        scheduler = new NotificationScheduler(sender, 30);
        Appointment appt = new Appointment(5, 1, "STI Screening", "Clinic 1", "Dr. Smith",
                LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0));
        String rules = new ReminderSchedule(List.of(
                new ReminderSchedule.ReminderRule(1, "day", "9:00", null),
                new ReminderSchedule.ReminderRule(2, "day", null, null)), null).toJson();

        var first = scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "P", rules, discreetOverrides());

        assertEquals(java.util.Optional.of(appt.getAppointmentDateTime().minusDays(2)), first.firstSend());
        assertEquals(0, first.skipped());
        assertEquals(2, scheduler.pendingCount("appointment:5"));
    }

    @Test
    void reminderPushedPastAppointmentByQuietHours_isNotReportedForAfterTheAppointment() {
        scheduler = new NotificationScheduler(sender, 30);
        Appointment appt = new Appointment(6, 1, "STI Screening", "Clinic 1", "Dr. Smith",
                LocalDateTime.now().plusDays(3).withHour(7).withMinute(0).withSecond(0).withNano(0));
        String rules = new ReminderSchedule(List.of(new ReminderSchedule.ReminderRule(1, "hour", null, null)),
                new ReminderSchedule.QuietHours("22:00", "08:00")).toJson();

        var plan = scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "P", rules, discreetOverrides());

        assertTrue(plan.firstSend().isEmpty(),
                "06:00 moved to 08:00 by quiet hours is after the 07:00 appointment, so it is not scheduled");
        assertEquals(1, plan.afterAppointment());
        assertEquals(0, scheduler.pendingCount("appointment:6"), "and no catch-up reminder is sent instead");
    }

    @Test
    void timeOfDayAfterTheAppointment_isNotReportedAsScheduled() {
        scheduler = new NotificationScheduler(sender, 30);
        Appointment appt = new Appointment(7, 1, "STI Screening", "Clinic 1", "Dr. Smith",
                LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0));
        String rules = new ReminderSchedule(List.of(new ReminderSchedule.ReminderRule(1, "hour", "18:00", null)), null).toJson();

        var plan = scheduler.scheduleAppointmentReminderWithSchedule(appt, "p@example.com", "P", rules, discreetOverrides());

        assertTrue(plan.firstSend().isEmpty(), "18:00 on the day of a 10:00 appointment can never be sent");
        assertEquals(1, plan.afterAppointment());
        assertEquals(0, scheduler.pendingCount("appointment:7"));
    }

    @Test
    void schedule_forPastAppointment_reportsNothing() {
        scheduler = new NotificationScheduler(sender, 30);
        assertTrue(scheduler.scheduleAppointmentReminderWithSchedule(appointmentIn(-60), "p@example.com", "P",
                singleRule(1, "day"), discreetOverrides()).firstSend().isEmpty());
    }

    @Test
    void bookingReminder_goesOutAtExactlyNine() {
        scheduler = new NotificationScheduler(sender, 30);
        LocalDate last = LocalDate.now().minusDays(10);

        var when = scheduler.scheduleBookingReminder("p@example.com", "P", 30, last, null, () -> true);

        assertEquals(java.util.Optional.of(last.plusDays(30).atTime(9, 0)), when);
    }

    @Test
    void overdueBookingReminder_doesNotUnderstateTheTimeSinceTheLastVisit() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);

        scheduler.scheduleBookingReminder("p@example.com", "P", 30, LocalDate.now().minusDays(82), null, () -> true);

        waitForSends(1, 2000);
        assertEquals("It has been at least 30 days since your last visit",
                sender.sent.getFirst().getTemplateContext().get("reminderReason"));
    }

    @Test
    void bookingReminder_notSentOnceNoLongerWanted() throws Exception {
        scheduler = new NotificationScheduler(sender, 0);

        scheduler.scheduleBookingReminder("p@example.com", "P", 30, null, null, () -> false);
        Thread.sleep(300);

        assertTrue(sender.sent.isEmpty());
        assertEquals(0, scheduler.pendingCount());
    }
}
