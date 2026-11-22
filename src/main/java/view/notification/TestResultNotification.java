package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Notification popup to inform a patient their test results are available.
 *
 * Usage:
 *   new TestResultNotification(
 *       "John Doe", "Chlamydia Test",
 *       LocalDate.of(2026, 2, 5), "Available"
 *   ).show();
 */
public class TestResultNotification extends BaseNotification {

    private final String patientName;
    private final String testType;
    private final LocalDate testDate;
    private final String resultStatus;
    /** True when a result released earlier has changed (e.g. PENDING to a final result). */
    private final boolean updated;

    public TestResultNotification(String patientName, String testType,
                                  LocalDate testDate, String resultStatus) {
        this(patientName, testType, testDate, resultStatus, false);
    }

    /**
     * The alert for a newly released result, or (updated) for one whose result has changed since.
     * Says only that something is ready, never what the result is.
     */
    public TestResultNotification(String patientName, String testType, LocalDate testDate, boolean updated) {
        this(patientName, testType, testDate, updated ? "Updated" : "Available", updated);
    }

    private TestResultNotification(String patientName, String testType, LocalDate testDate,
                                   String resultStatus, boolean updated) {
        this.patientName = patientName;
        this.testType = testType;
        this.testDate = testDate;
        this.resultStatus = resultStatus;
        this.updated = updated;
    }

    @Override
    public String getTitle() {
        return updated ? "Test Result Updated" : "Test Results Ready";
    }

    private String headline() {
        return updated ? "You have an updated test result." : "Your test results are now available.";
    }

    @Override
    public String getMessage() {
        return "Hi " + patientName + ",\n\n"
                + (updated ? "You have an updated test result:" : "Your test results are now available:") + "\n\n"
                + NotificationLayout.detailsText(rows()) + "\n\n"
                + FOOTNOTE;
    }

    @Override
    public String getType() {
        return "TEST_RESULT";
    }

    @Override
    protected Node buildPreviewContent() {
        return NotificationLayout.page(getTitle(), patientName, headline(),
                NotificationLayout.detailsCard(rows()), FOOTNOTE);
    }

    /** Shown while logged in, so it points to where the results are rather than asking to log in. */
    static final String FOOTNOTE = "Open Tracking > Test Results to see them.";

    private String[] rows() {
        return new String[]{"Test", testType, "Date taken", Dates.medium(testDate), "Status", resultStatus};
    }
}