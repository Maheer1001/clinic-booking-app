package service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MakeAppointmentReminderEmailTest {

    private MakeAppointmentReminderEmail email;
    private final String recipientEmail = "patient@example.com";
    private final String recipientName = "Jane Doe";
    private final String reminderReason = "It has been 12 months since your last screening";
    private final String suggestedAppointmentType = "Annual STI Screening";

    @BeforeEach
    void setUp() {
        email = new MakeAppointmentReminderEmail(
                recipientEmail, recipientName,
                reminderReason, suggestedAppointmentType
        );
    }

    @Test
    void getRecipientEmailShouldReturnCorrectEmail() {
        assertEquals(recipientEmail, email.getRecipientEmail());
    }

    @Test
    void getRecipientNameShouldReturnCorrectName() {
        assertEquals(recipientName, email.getRecipientName());
    }

    @Test
    void getSubjectShouldBeGenericReminder() {
        String subject = email.getSubject();
        assertTrue(subject.startsWith("Reminder:"));
        assertTrue(subject.contains("time to book a check-up"));
        // Should not expose specific appointment type in the subject for privacy
        assertFalse(subject.contains(suggestedAppointmentType));
    }

    @Test
    void getTemplateNameShouldReturnMakeAppointmentReminder() {
        assertEquals("make-appointment-reminder", email.getTemplateName());
    }

    @Test
    void getTemplateContextShouldContainAllFields() {
        Map<String, Object> context = email.getTemplateContext();

        assertEquals(reminderReason, context.get("reminderReason"));
        assertEquals(suggestedAppointmentType, context.get("suggestedAppointmentType"));
    }

    @Test
    void getTemplateContextShouldHaveTwoEntries() {
        assertEquals(2, email.getTemplateContext().size());
    }
}
