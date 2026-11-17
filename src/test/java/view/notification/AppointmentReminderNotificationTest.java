package view.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentReminderNotificationTest {

    private AppointmentReminderNotification notification;
    private final String patientName = "Jane Doe";
    private final String appointmentType = "STI Screening";
    private final LocalDateTime appointmentDateTime = LocalDateTime.of(2026, 3, 15, 10, 30);
    private final String clinicLocation = "Southampton Sexual Health Clinic";
    private final String doctorName = "Dr. Smith";

    @BeforeEach
    void setUp() {
        notification = new AppointmentReminderNotification(
                patientName, appointmentType, appointmentDateTime, clinicLocation, doctorName,
                null, null, null, null
        );
    }

    @Test
    void getTypeShouldReturnAppointmentReminder() {
        assertEquals("APPOINTMENT_REMINDER", notification.getType());
    }

    @Test
    void getTitleShouldReturnAppointmentReminder() {
        assertEquals("Appointment Reminder", notification.getTitle());
    }

}
