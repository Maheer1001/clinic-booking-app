package view.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TreatmentReminderNotificationTest {

    private TreatmentReminderNotification notification;
    private final String patientName = "Jane Doe";
    private final String treatmentName = "Doxycycline Course";
    private final String treatmentInstructions = "Take 100mg twice daily with food";
    private final LocalDate nextDueDate = LocalDate.of(2026, 2, 17);

    @BeforeEach
    void setUp() {
        notification = new TreatmentReminderNotification(
                patientName, treatmentName, treatmentInstructions, nextDueDate
        );
    }

    @Test
    void getTypeShouldReturnTreatmentReminder() {
        assertEquals("TREATMENT_REMINDER", notification.getType());
    }

    @Test
    void getTitleShouldReturnTreatmentReminder() {
        assertEquals("Treatment Reminder", notification.getTitle());
    }

    // Message content is now structured UI; we only assert the type and title remain stable.
}
