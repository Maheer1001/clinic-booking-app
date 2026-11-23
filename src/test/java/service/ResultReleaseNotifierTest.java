package service;

import model.BookingCommunicationPreferences;
import model.ReminderTemplateOverrides;
import model.User;
import org.junit.jupiter.api.Test;
import service.email.Email;
import service.email.EmailSender;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The result-release email, with an in-memory user repository and a capturing sender (no DB, no SMTP). */
class ResultReleaseNotifierTest {

    private static final int PATIENT = 7;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private static final class CapturingSender extends EmailSender {
        final List<Email> sent = new ArrayList<>();
        @Override public boolean send(Email email) {
            sent.add(email);
            return true;
        }
    }

    private final FakeUserRepository users = new FakeUserRepository()
            .add(new User(PATIENT, "pat@example.com", "Pat", "Lee"));
    private final CapturingSender sender = new CapturingSender();
    private final ResultReleaseNotifier notifier = ResultReleaseNotifier.email(users, sender);

    private static BookingCommunicationPreferences withFromName(String fromName) {
        String overrides = new ReminderTemplateOverrides(null, null, fromName, null, null, null, null).toJson();
        return new BookingCommunicationPreferences(true, true, true, overrides, null, null);
    }

    @Test
    void usesThePatientsChosenSenderName() {
        users.saveBookingCommunicationPreferences(PATIENT, withFromName("My Appointments"));

        assertTrue(notifier.resultAvailable(PATIENT, DAY));

        assertEquals(1, sender.sent.size());
        Email mail = sender.sent.get(0);
        assertEquals("pat@example.com", mail.getRecipientEmail());
        assertEquals("My Appointments", mail.getFromName());
    }

    @Test
    void withNoSavedPreferencesTheDefaultDiscreetSenderIsUsed() {
        assertTrue(notifier.resultAvailable(PATIENT, DAY));

        Email mail = sender.sent.get(0);
        assertNull(mail.getFromName());
        assertTrue(mail.isDiscreet());
    }

    @Test
    void staysDiscreetEvenWhenThePatientChoseStandardBookingEmails() {
        users.saveBookingCommunicationPreferences(PATIENT,
                new BookingCommunicationPreferences(false, false, false, null, null, null));

        assertTrue(notifier.resultAvailable(PATIENT, DAY));

        Email mail = sender.sent.get(0);
        assertTrue(mail.isDiscreet());
        assertEquals(Boolean.TRUE, mail.getTemplateContext().get("discreet"));
        assertFalse(mail.getTemplateContext().containsKey("testType"));
    }

    @Test
    void patientWithoutEmailIsNotSent() {
        users.add(new User(8, "", "No", "Email"));

        assertFalse(notifier.resultAvailable(8, DAY));
        assertFalse(notifier.resultAvailable(99, DAY));
        assertTrue(sender.sent.isEmpty());
    }
}
