package view.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingConfirmationNotificationTest {

    private BookingConfirmationNotification notification;
    private final String patientName = "Jane Doe";
    private final String bookingReference = "BK-20260210-001";
    private final String appointmentType = "STI Screening";
    private final LocalDateTime appointmentDateTime = LocalDateTime.of(2026, 2, 20, 14, 0);
    private final String clinicLocation = "Southampton Sexual Health Clinic";
    private final String doctorName = "Dr. Smith";

    @BeforeEach
    void setUp() {
        notification = new BookingConfirmationNotification(
                patientName, bookingReference, appointmentType,
                appointmentDateTime, clinicLocation, doctorName,
                false,
                null
        );
    }

    @Test
    void getTypeShouldReturnBookingConfirmation() {
        assertEquals("BOOKING_CONFIRMATION", notification.getType());
    }

    @Test
    void getTitleShouldReturnBookingConfirmed() {
        assertEquals("Booking Confirmed", notification.getTitle());
    }

    @Test
    void getMessageShouldContainPatientName() {
        assertTrue(notification.getMessage().contains(patientName));
    }

    @Test
    void getMessageShouldContainBookingReference() {
        assertTrue(notification.getMessage().contains(bookingReference));
    }

    @Test
    void getMessageShouldContainAppointmentType() {
        assertTrue(notification.getMessage().contains(appointmentType));
    }

    @Test
    void getMessageShouldContainFormattedDate() {
        assertTrue(notification.getMessage().contains("Fri 20 Feb 2026"));
    }

    @Test
    void getMessageShouldContainFormattedTime() {
        assertTrue(notification.getMessage().contains("14:00"));
    }

    @Test
    void getMessageShouldContainClinicLocation() {
        assertTrue(notification.getMessage().contains(clinicLocation));
    }

    @Test
    void getMessageShouldContainDoctorName() {
        assertTrue(notification.getMessage().contains(doctorName));
    }

    @Test
    void discreetMessageShouldNotRevealClinicalDetails() {
        BookingConfirmationNotification disc = new BookingConfirmationNotification(
                patientName, bookingReference, appointmentType,
                appointmentDateTime, clinicLocation, doctorName,
                true,
                null);
        String msg = disc.getMessage();
        assertFalse(msg.contains(bookingReference));
        assertFalse(msg.contains(appointmentType));
        assertFalse(msg.contains(clinicLocation));
        assertFalse(msg.contains(doctorName));
    }
}
