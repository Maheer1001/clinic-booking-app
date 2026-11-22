package view.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationLayoutTest {

    @Test
    void placeholderClinicianAndBlankValuesAreLeftOut() {
        assertEquals("Date: Mon 23 Nov 2026\nLocation: Clinic 1",
                NotificationLayout.detailsText("Date", "Mon 23 Nov 2026", "Time", " ",
                        "Location", "Clinic 1", "Clinician", "Clinician"));
    }

    @Test
    void realClinicianIsShown() {
        assertTrue(NotificationLayout.hasValue("Clinician", "Dr Patel"));
        assertFalse(NotificationLayout.hasValue("Clinician", "clinician"));
        assertFalse(NotificationLayout.hasValue("Dose", null));
    }
}
