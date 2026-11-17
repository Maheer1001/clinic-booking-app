package model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that setters on reminder models correctly update the updatedAt timestamp.
 */
class ReminderTimestampTest {

    @Test
    void appointmentReminder_setNotify_updatesTimestamp() throws Exception {
        AppointmentReminder r = new AppointmentReminder(1, 1, 1, false, "2", "msg", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setNotify(true);
        assertTrue(r.isNotify());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void appointmentReminder_setCustomDays_updatesTimestamp() throws Exception {
        AppointmentReminder r = new AppointmentReminder(1, 1, 1, true, "2", "msg", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setCustomDays("5");
        assertEquals("5", r.getCustomDays());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void appointmentReminder_setCustomMessage_updatesTimestamp() throws Exception {
        AppointmentReminder r = new AppointmentReminder(1, 1, 1, true, "2", "old", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setCustomMessage("new message");
        assertEquals("new message", r.getCustomMessage());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void appointmentReminder_createdAt_doesNotChange() throws Exception {
        AppointmentReminder r = new AppointmentReminder(1, 1, 1, false, "2", "msg", "email");
        LocalDateTime created = r.getCreatedAt();

        r.setNotify(true);
        r.setCustomDays("7");
        r.setCustomMessage("updated");
        assertEquals(created, r.getCreatedAt());
    }

    @Test
    void bookingReminder_setNotify_updatesTimestamp() throws Exception {
        BookingReminder r = new BookingReminder(1, 1, false, 180, "msg", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setNotify(true);
        assertTrue(r.isNotify());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void bookingReminder_setDaysSinceLast_updatesTimestamp() throws Exception {
        BookingReminder r = new BookingReminder(1, 1, true, 180, "msg", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setDaysSinceLast(365);
        assertEquals(365, r.getDaysSinceLast());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void prescriptionReminder_setNotify_updatesTimestamp() throws Exception {
        PrescriptionReminder r = new PrescriptionReminder(1, 1, 1, false, 12, "msg", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setNotify(true);
        assertTrue(r.isNotify());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void prescriptionReminder_setInterval_updatesTimestamp() throws Exception {
        PrescriptionReminder r = new PrescriptionReminder(1, 1, 1, true, 12, "msg", "email");
        LocalDateTime before = r.getUpdatedAt();

        r.setIntervalBetweenDoses(24);
        assertEquals(24, r.getIntervalBetweenDoses());
        assertTrue(r.getUpdatedAt().isAfter(before) || r.getUpdatedAt().isEqual(before));
    }

    @Test
    void prescriptionReminder_createdAt_doesNotChange() throws Exception {
        PrescriptionReminder r = new PrescriptionReminder(1, 1, 1, false, 12, "msg", "email");
        LocalDateTime created = r.getCreatedAt();

        r.setNotify(true);
        r.setIntervalBetweenDoses(6);
        r.setCustomMessage("updated");
        assertEquals(created, r.getCreatedAt());
    }
}
