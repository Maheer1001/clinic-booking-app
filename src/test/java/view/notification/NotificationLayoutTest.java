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
    void listCard_putsEachHeadingAboveItsDetail() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // fine
        }
        javafx.scene.layout.VBox card = NotificationLayout.listCard(
                "Extended sexual health screen incl. hepatitis B/C", "20 Nov 2026  ·  Available",
                " ", "skipped",
                "HIV test", "19 Nov 2026  ·  Updated");

        assertEquals(2, card.getChildren().size(), "a blank heading is left out");
        javafx.scene.layout.VBox first = (javafx.scene.layout.VBox) card.getChildren().get(0);
        assertEquals("Extended sexual health screen incl. hepatitis B/C",
                ((javafx.scene.control.Label) first.getChildren().get(0)).getText());
        assertEquals("20 Nov 2026  ·  Available", ((javafx.scene.control.Label) first.getChildren().get(1)).getText(),
                "the date goes on its own line under a long test type, not squeezed beside it");
        assertTrue(((javafx.scene.control.Label) first.getChildren().get(1)).isWrapText());
    }

    @Test
    void realClinicianIsShown() {
        assertTrue(NotificationLayout.hasValue("Clinician", "Dr Patel"));
        assertFalse(NotificationLayout.hasValue("Clinician", "clinician"));
        assertFalse(NotificationLayout.hasValue("Dose", null));
    }
}
