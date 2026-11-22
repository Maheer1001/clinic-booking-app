package view.notification;

import model.Appointment;
import model.Prescription;
import model.ReminderTemplateOverrides;
import org.junit.jupiter.api.Test;
import service.ReminderAlert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Scheduled reminders pop up with their saved wording, and only for the signed-in user. */
class ReminderPopupsTest {

    private static final String EMAIL = "pat@example.com";
    private static final ReminderTemplateOverrides SAVED =
            new ReminderTemplateOverrides(null, null, null, "Clinic visit soon", "Tap to see details", "See you tomorrow", true);

    private static ReminderAlert appointmentAlert() {
        Appointment appt = new Appointment(3, 7, "STI Test", "Clinic 5", "Dr Who", LocalDateTime.of(2026, 12, 1, 10, 0));
        return new ReminderAlert(ReminderAlert.Kind.APPOINTMENT, EMAIL, "Pat", SAVED, appt, null, null, null);
    }

    @Test
    void appointmentPopup_usesTheSavedTitle() {
        Notification n = ReminderPopups.notificationFor(appointmentAlert());
        assertInstanceOf(AppointmentReminderNotification.class, n);
        assertEquals("Clinic visit soon", n.getTitle());
    }

    @Test
    void treatmentAndBookingPopups_useTheSavedTitle() {
        Prescription rx = new Prescription(5, 7, "Doxycycline", "100mg", LocalDate.now(), null);
        Notification dose = ReminderPopups.notificationFor(new ReminderAlert(ReminderAlert.Kind.TREATMENT, EMAIL, "Pat",
                SAVED, null, rx, LocalDate.now(), null));
        assertInstanceOf(TreatmentReminderNotification.class, dose);
        assertEquals("Clinic visit soon", dose.getTitle());

        Notification book = ReminderPopups.notificationFor(new ReminderAlert(ReminderAlert.Kind.BOOKING, EMAIL, "Pat",
                SAVED, null, null, null, "It has been at least 90 days since your last visit"));
        assertInstanceOf(MakeAppointmentReminderNotification.class, book);
        assertEquals("Clinic visit soon", book.getTitle());
    }

    @Test
    void noSavedWording_fallsBackToTheDefaultTitle() {
        Appointment appt = new Appointment(3, 7, "STI Test", "Clinic 5", "Dr Who", LocalDateTime.of(2026, 12, 1, 10, 0));
        Notification n = ReminderPopups.notificationFor(
                new ReminderAlert(ReminderAlert.Kind.APPOINTMENT, EMAIL, "Pat", null, appt, null, null, null));
        assertEquals("Appointment Reminder", n.getTitle());
    }

    @Test
    void notShown_whenLoggedOutOrADifferentUserIsSignedIn() {
        List<Runnable> queued = new ArrayList<>();
        ReminderPopups.showIfSignedIn(appointmentAlert(), () -> null, queued::add);
        ReminderPopups.showIfSignedIn(appointmentAlert(), () -> "someone.else@example.com", queued::add);
        assertTrue(queued.isEmpty());
    }

    @Test
    void shownOnTheFxThread_forTheSignedInUser_butNotIfTheyLogOutFirst() {
        List<Runnable> queued = new ArrayList<>();
        AtomicReference<String> signedIn = new AtomicReference<>("PAT@example.com ");

        ReminderPopups.showIfSignedIn(appointmentAlert(), signedIn::get, queued::add);
        assertEquals(1, queued.size(), "dispatched to the FX thread rather than shown on the scheduler thread");

        signedIn.set(null); // logged out before the FX thread ran it
        assertDoesNotThrow(() -> queued.getFirst().run(), "nothing is shown, so no FX toolkit is needed");
    }

    @Test
    void alertIsFor_matchesEmailIgnoringCaseAndSpaces() {
        ReminderAlert alert = appointmentAlert();
        assertTrue(alert.isFor(" Pat@Example.com"));
        assertFalse(alert.isFor(null));
        assertFalse(alert.isFor("other@example.com"));
    }
}
