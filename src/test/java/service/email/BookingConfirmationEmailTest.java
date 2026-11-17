package service.email;

import model.ReminderTemplateOverrides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BookingConfirmationEmailTest {

    private BookingConfirmationEmail email;
    private final String recipientEmail = "patient@example.com";
    private final String recipientName = "Jane Doe";
    private final String bookingReference = "BK-20260210-001";
    private final String appointmentType = "STI Screening";
    private final LocalDateTime appointmentDateTime = LocalDateTime.of(2026, 2, 20, 14, 0);
    private final String clinicLocation = "Southampton Sexual Health Clinic";
    private final String doctorName = "Dr. Smith";

    @BeforeEach
    void setUp() {
        email = new BookingConfirmationEmail(
                recipientEmail, recipientName,
                bookingReference, appointmentType,
                appointmentDateTime, clinicLocation, doctorName,
                false,
                null
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
    void getSubjectShouldIndicateAppointmentConfirmed() {
        assertTrue(email.getSubject().contains("Your appointment is confirmed"));
    }

    @Test
    void getSubjectShouldNotContainAppointmentType() {
        assertFalse(email.getSubject().contains(appointmentType));
    }

    @Test
    void getSubjectShouldContainBookingReference() {
        assertTrue(email.getSubject().contains(bookingReference));
    }

    @Test
    void getTemplateNameShouldReturnBookingConfirmation() {
        assertEquals("booking-confirmation", email.getTemplateName());
    }

    @Test
    void getTemplateContextShouldContainAllFields() {
        Map<String, Object> context = email.getTemplateContext();

        assertEquals(bookingReference, context.get("bookingReference"));
        assertEquals(appointmentType, context.get("appointmentType"));
        assertEquals("Friday, 20 February 2026", context.get("appointmentDate"));
        assertEquals("14:00", context.get("appointmentTime"));
        assertEquals(clinicLocation, context.get("clinicLocation"));
        assertEquals(doctorName, context.get("doctorName"));
    }

    @Test
    void getTemplateContextShouldHaveSevenEntries() {
        assertEquals(7, email.getTemplateContext().size());
    }

    @Test
    void discreetSubjectShouldNotIncludeReference() {
        BookingConfirmationEmail discreetEmail = new BookingConfirmationEmail(
                recipientEmail, recipientName,
                bookingReference, appointmentType,
                appointmentDateTime, clinicLocation, doctorName,
                true,
                null);
        assertFalse(discreetEmail.getSubject().contains(bookingReference));
        assertEquals(Boolean.TRUE, discreetEmail.getTemplateContext().get("discreet"));
    }

    @Test
    void getSubjectShouldRespectTemplateOverride() {
        ReminderTemplateOverrides ov = new ReminderTemplateOverrides(
                "My custom subject", null, null, null, null, null, null);
        BookingConfirmationEmail custom = new BookingConfirmationEmail(
                recipientEmail, recipientName,
                bookingReference, appointmentType,
                appointmentDateTime, clinicLocation, doctorName,
                false, ov);
        assertEquals("My custom subject", custom.getSubject());
    }
}
