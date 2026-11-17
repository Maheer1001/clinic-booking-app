package service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TreatmentReminderEmailTest {

    private TreatmentReminderEmail email;
    private final String recipientEmail = "patient@example.com";
    private final String recipientName = "Jane Doe";
    private final String treatmentName = "Doxycycline Course";
    private final String treatmentInstructions = "Take 100mg twice daily with food";
    private final LocalDate nextDueDate = LocalDate.of(2026, 2, 17);

    @BeforeEach
    void setUp() {
        email = new TreatmentReminderEmail(
                recipientEmail, recipientName,
                treatmentName, treatmentInstructions, nextDueDate
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
    void getSubjectShouldBeGenericTreatmentReminder() {
        String subject = email.getSubject();
        assertTrue(subject.toLowerCase().startsWith("treatment reminder"));
        // Should not expose specific treatment name in the subject for privacy
        assertFalse(subject.contains(treatmentName));
    }

    @Test
    void getTemplateNameShouldReturnTreatmentReminder() {
        assertEquals("treatment-reminder", email.getTemplateName());
    }

    @Test
    void getTemplateContextShouldContainAllFields() {
        Map<String, Object> context = email.getTemplateContext();

        assertEquals(treatmentName, context.get("treatmentName"));
        assertEquals(treatmentInstructions, context.get("treatmentInstructions"));
        assertEquals("Tuesday, 17 February 2026", context.get("nextDueDate"));
    }

    @Test
    void getTemplateContextShouldHaveThreeEntries() {
        assertEquals(3, email.getTemplateContext().size());
    }
}
