package view.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TestResultNotificationTest {

    private TestResultNotification notification;
    private final String patientName = "Jane Doe";
    private final String testType = "Chlamydia Test";
    private final LocalDate testDate = LocalDate.of(2026, 2, 5);
    private final String resultStatus = "Available";

    @BeforeEach
    void setUp() {
        notification = new TestResultNotification(
                patientName, testType, testDate, resultStatus
        );
    }

    @Test
    void getTypeShouldReturnTestResult() {
        assertEquals("TEST_RESULT", notification.getType());
    }

    @Test
    void getTitleShouldReturnTestResultsReady() {
        assertEquals("Test Results Ready", notification.getTitle());
    }

    @Test
    void getMessageShouldContainPatientName() {
        assertTrue(notification.getMessage().contains(patientName));
    }

    @Test
    void getMessageShouldContainTestType() {
        assertTrue(notification.getMessage().contains(testType));
    }

    @Test
    void getMessageShouldContainFormattedDate() {
        assertTrue(notification.getMessage().contains("5 Feb 2026"));
    }

    @Test
    void getMessageShouldContainResultStatus() {
        assertTrue(notification.getMessage().contains(resultStatus));
    }

    @Test
    void getMessageShouldSayWhereToSeeResults() {
        assertTrue(notification.getMessage().contains("Tracking"));
    }

    @Test
    void updatedResultSaysUpdatedButNotTheResult() {
        TestResultNotification updated = new TestResultNotification(patientName, testType, testDate, true);
        assertEquals("Test Result Updated", updated.getTitle());
        String text = updated.getMessage();
        assertTrue(text.contains("updated test result"), text);
        assertTrue(text.contains(testType) && text.contains("5 Feb 2026"), text);
        String lower = text.toLowerCase();
        assertFalse(lower.contains("positive") || lower.contains("negative") || lower.contains("pending"), text);
    }

    @Test
    void newlyReleasedResultSaysAvailable() {
        TestResultNotification released = new TestResultNotification(patientName, testType, testDate, false);
        assertEquals("Test Results Ready", released.getTitle());
        assertTrue(released.getMessage().contains("Available"));
    }

    @Test
    void getMessageShouldNotContainActualResults() {
        // Ensure no sensitive data leaks into the notification text
        assertFalse(notification.getMessage().contains("positive"));
        assertFalse(notification.getMessage().contains("negative"));
    }
}
