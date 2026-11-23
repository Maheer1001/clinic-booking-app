package view.notification;

import model.NewResults;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Several new results make one popup listing them; one keeps the single-result popup. */
class NewResultsNotificationTest {

    private static NewResults.Entry entry(int id, String type, LocalDate date, boolean updated) {
        return new NewResults.Entry(id, 3, type, date, LocalDateTime.of(2026, 3, 2, 9, 0), updated);
    }

    @Test
    void oneResult_usesTheSingleResultPopup() {
        Notification n = NewResultsNotification.forResults("Sam",
                List.of(entry(1, "Blood test", LocalDate.of(2026, 3, 1), false)));
        assertInstanceOf(TestResultNotification.class, n);
        assertEquals("Test Results Ready", n.getTitle());
    }

    @Test
    void noResults_noPopup() {
        assertNull(NewResultsNotification.forResults("Sam", List.of()));
        assertNull(NewResultsNotification.forResults("Sam", null));
    }

    @Test
    void severalResults_onePopupSayingHowManyAndListingEach() {
        Notification n = NewResultsNotification.forResults("Sam", List.of(
                entry(1, "Blood test", LocalDate.of(2026, 3, 1), false),
                entry(2, "Urine test", LocalDate.of(2026, 2, 5), true),
                entry(3, "Swab", LocalDate.of(2026, 2, 6), false)));

        assertInstanceOf(NewResultsNotification.class, n);
        assertEquals("You have 3 new results", n.getTitle());
        assertEquals("TEST_RESULT", n.getType());
        String text = n.getMessage();
        assertTrue(text.contains("Hi Sam,"), text);
        assertTrue(text.contains("Blood test: 1 Mar 2026  ·  Available"), text);
        assertTrue(text.contains("Urine test: 5 Feb 2026  ·  Updated"), text);
        assertTrue(text.contains("Swab: 6 Feb 2026  ·  Available"), text);
        assertTrue(text.contains("Tracking"), "says where to see them: " + text);
    }

    @Test
    void severalResults_neverSayWhatTheResultIs() {
        String text = NewResultsNotification.forResults("Sam", List.of(
                entry(1, "Blood test", LocalDate.of(2026, 3, 1), true),
                entry(2, "Urine test", LocalDate.of(2026, 2, 5), true))).getMessage().toLowerCase();
        assertFalse(text.contains("positive") || text.contains("negative") || text.contains("pending"), text);
    }
}
