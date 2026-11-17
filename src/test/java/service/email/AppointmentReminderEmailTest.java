package service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentReminderEmailTest {

    private AppointmentReminderEmail email;
    private final String recipientEmail = "patient@example.com";
    private final String recipientName = "Jane Doe";
    private final String appointmentType = "STI Screening";
    private final LocalDateTime appointmentDateTime = LocalDateTime.of(2026, 3, 15, 10, 30);
    private final String clinicLocation = "Southampton Sexual Health Clinic";
    private final String doctorName = "Dr. Smith";

    @BeforeEach
    void setUp() {
        email = new AppointmentReminderEmail(
                recipientEmail, recipientName,
                appointmentType, appointmentDateTime,
                clinicLocation, doctorName,
                null, null, null
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
    void getSubjectShouldContainFormattedDate() {
        assertTrue(email.getSubject().contains("15 Mar 2026"));
    }

    @Test
    void getSubjectShouldStartWithReminder() {
        assertTrue(email.getSubject().startsWith("Reminder"));
    }

    @Test
    void getTemplateNameShouldReturnAppointmentReminder() {
        assertEquals("appointment-reminder", email.getTemplateName());
    }

    @Test
    void getTemplateContextShouldContainAllFields() {
        Map<String, Object> context = email.getTemplateContext();

        assertEquals(appointmentType, context.get("appointmentType"));
        assertEquals("Sunday, 15 March 2026", context.get("appointmentDate"));
        assertEquals("10:30", context.get("appointmentTime"));
        assertEquals(clinicLocation, context.get("clinicLocation"));
        assertEquals(doctorName, context.get("doctorName"));
    }

    @Test
    void getTemplateContextShouldHaveFiveEntries() {
        assertEquals(5, email.getTemplateContext().size());
    }
}
