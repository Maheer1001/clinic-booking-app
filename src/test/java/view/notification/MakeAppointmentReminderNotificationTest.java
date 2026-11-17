package view.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MakeAppointmentReminderNotificationTest {

    private MakeAppointmentReminderNotification notification;
    private final String patientName = "Jane Doe";
    private final String reminderReason = "It has been 12 months since your last screening";
    private final String suggestedAppointmentType = "Annual STI Screening";

    @BeforeEach
    void setUp() {
        notification = new MakeAppointmentReminderNotification(
                patientName, reminderReason, suggestedAppointmentType
        );
    }

    @Test
    void getTypeShouldReturnMakeAppointmentReminder() {
        assertEquals("MAKE_APPOINTMENT_REMINDER", notification.getType());
    }

    @Test
    void getTitleShouldReturnBookAnAppointment() {
        assertEquals("Book an Appointment", notification.getTitle());
    }
}
