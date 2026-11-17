package service.email;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Discreet mode must keep sensitive wording (service, clinic, treatment, "sexual health")
 * out of reminder email subjects, bodies and sender names. Also checks user text is HTML-escaped.
 */
class DiscreetReminderEmailTest {

    private static final LocalDateTime APPT = LocalDateTime.of(2026, 2, 15, 10, 30);
    private final EmailSender sender = new EmailSender();

    private static void assertNoSensitiveWording(String text) {
        String lower = text.toLowerCase();
        assertFalse(lower.contains("sexual health"), "Leaked 'sexual health': " + text);
        assertFalse(lower.contains("hiv"), "Leaked service name");
        assertFalse(lower.contains("prep"), "Leaked medicine name");
        assertFalse(lower.contains("harbour clinic"), "Leaked clinic name");
    }

    @Test
    void appointmentReminder_discreet_hidesServiceClinicAndClinician() {
        AppointmentReminderEmail email = new AppointmentReminderEmail(
                "p@example.com", "Jane", "HIV Test", APPT, "Harbour Clinic", "Dr Who",
                null, null, null, true);

        Map<String, Object> ctx = email.getTemplateContext();
        assertFalse(ctx.containsKey("appointmentType"));
        assertFalse(ctx.containsKey("clinicLocation"));
        assertFalse(ctx.containsKey("doctorName"));
        assertEquals("10:30", ctx.get("appointmentTime"));

        assertNoSensitiveWording(email.getSubject());
        String html = sender.renderHtml(email);
        assertNoSensitiveWording(html);
        assertFalse(html.contains("Dr Who"));
        assertTrue(html.contains("10:30"));
    }

    @Test
    void appointmentReminder_fullDetails_stillIncludesDetails() {
        AppointmentReminderEmail email = new AppointmentReminderEmail(
                "p@example.com", "Jane", "HIV Test", APPT, "Harbour Clinic", "Dr Who",
                null, null, null, false);
        String html = sender.renderHtml(email);
        assertTrue(html.contains("HIV Test"));
        assertTrue(html.contains("Harbour Clinic"));
    }

    @Test
    void treatmentReminder_discreet_hidesTreatmentAndUsesNeutralSubject() {
        TreatmentReminderEmail email = new TreatmentReminderEmail(
                "p@example.com", "Jane", "PrEP", "One tablet daily", LocalDate.of(2026, 2, 17),
                null, null, null, true);

        assertFalse(email.getSubject().toLowerCase().contains("treatment"));
        assertFalse(email.getTemplateContext().containsKey("treatmentName"));
        String html = sender.renderHtml(email);
        assertNoSensitiveWording(html);
        assertFalse(html.contains("One tablet daily"));
    }

    @Test
    void treatmentReminder_nullDueDate_doesNotThrow() {
        TreatmentReminderEmail email = new TreatmentReminderEmail(
                "p@example.com", "Jane", "Doxycycline", "Twice daily", null);
        assertFalse(email.getTemplateContext().containsKey("nextDueDate"));
        assertDoesNotThrow(() -> sender.renderHtml(email));
    }

    @Test
    void bookingReminder_discreet_hidesSuggestedServiceAndClinicType() {
        MakeAppointmentReminderEmail email = new MakeAppointmentReminderEmail(
                "p@example.com", "Jane", "It has been 90 days since your last visit",
                "Routine Sexual Health Check-up", null, null, null, true);
        assertFalse(email.getTemplateContext().containsKey("suggestedAppointmentType"));
        assertNoSensitiveWording(sender.renderHtml(email));
    }

    @Test
    void testResult_discreet_hidesTestType() {
        TestResultEmail email = new TestResultEmail(
                "p@example.com", "Jane", "HIV Test", LocalDate.of(2026, 2, 5), "Available", true);
        assertFalse(email.getTemplateContext().containsKey("testType"));
        assertNoSensitiveWording(sender.renderHtml(email));
    }

    @Test
    void discreetEmail_usesNeutralSenderNameUnlessOverridden() {
        AppointmentReminderEmail discreet = new AppointmentReminderEmail(
                "p@example.com", "Jane", "HIV Test", APPT, "Harbour Clinic", "Dr Who",
                null, null, null, true);
        assertEquals(EmailSender.DISCREET_FROM_NAME, EmailSender.resolveFromName(discreet));
        assertNoSensitiveWording(EmailSender.resolveFromName(discreet));

        AppointmentReminderEmail custom = new AppointmentReminderEmail(
                "p@example.com", "Jane", "HIV Test", APPT, "Harbour Clinic", "Dr Who",
                null, null, "  My Reminders ", true);
        assertEquals("My Reminders", EmailSender.resolveFromName(custom));
    }

    @Test
    void userProvidedTextIsHtmlEscaped() {
        AppointmentReminderEmail email = new AppointmentReminderEmail(
                "p@example.com", "<script>alert(1)</script>", "Check-up", APPT, "Clinic <b>A</b>", "Dr",
                null, "Hi & <i>welcome</i>", null, false);
        String html = sender.renderHtml(email);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<b>A</b>"));
        assertFalse(html.contains("<i>welcome</i>"));
    }
}
