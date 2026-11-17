package service;

import model.Appointment;
import model.ReminderSchedule;
import model.ReminderTemplateOverrides;
import org.junit.jupiter.api.Test;
import service.email.EmailSender;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the schedule-based appointment reminder helper on NotificationScheduler.
 *
 * These use a fake EmailSender and a very small default delay so the tests complete quickly.
 */
class NotificationSchedulerScheduleTest {

    private static class CapturingEmailSender extends EmailSender {
        private final AtomicInteger sendCount = new AtomicInteger();

        @Override
        public boolean send(service.email.Email email) {
            sendCount.incrementAndGet();
            return true;
        }
    }

    private static class TestableScheduler extends NotificationScheduler {
        TestableScheduler(EmailSender sender, long defaultDelaySeconds) {
            super(sender, defaultDelaySeconds);
        }
    }

    @Test
    void scheduleAppointmentReminderWithSchedule_sendsEmailForSingleRule() throws Exception {
        CapturingEmailSender sender = new CapturingEmailSender();
        NotificationScheduler scheduler = new TestableScheduler(sender, 0);

        Appointment appt = new Appointment(
                1, 1, "STI Screening", "Clinic 1", "Dr. Smith",
                LocalDateTime.now().minusMinutes(1)
        );

        // Single reminder 1 minute before, no repeats, no quiet hours.
        ReminderSchedule.ReminderRule rule =
                new ReminderSchedule.ReminderRule(1, "minute", null, null);
        ReminderSchedule schedule = new ReminderSchedule(
                java.util.List.of(rule),
                null
        );
        String scheduleJson = schedule.toJson();

        ReminderTemplateOverrides overrides = new ReminderTemplateOverrides(
                null, null, null, null, null, null, true
        );

        scheduler.scheduleAppointmentReminderWithSchedule(
                appt,
                "patient@example.com",
                "Jane Doe",
                scheduleJson,
                overrides.toJson()
        );

        // Wait (bounded) for the scheduled task to run.
        long deadline = System.currentTimeMillis() + 2000;
        while (sender.sendCount.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        scheduler.shutdown();

        assertTrue(sender.sendCount.get() >= 1);
    }
}

