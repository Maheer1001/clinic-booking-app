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

    public TestResultNotification(String patientName, String testType,
                                  LocalDate testDate, String resultStatus) {
        this.patientName = patientName;
        this.testType = testType;
        this.testDate = testDate;
        this.resultStatus = resultStatus;
    }

    @Override
    public String getTitle() {
        return "Test Results Ready";
    }

    @Override
    public String getMessage() {
        return "Hi " + patientName + ",\n\n"
                + "Your test results are now available:\n\n"
                + NotificationLayout.detailsText(rows()) + "\n\n"
                + FOOTNOTE;
    }

    @Override
    public String getType() {
        return "TEST_RESULT";
    }

    @Override
    protected Node buildPreviewContent() {
        return NotificationLayout.page(getTitle(), patientName, "Your test results are now available.",
                NotificationLayout.detailsCard(rows()), FOOTNOTE);
    }

    /** Shown while logged in, so it points to where the results are rather than asking to log in. */
    static final String FOOTNOTE = "Open Tracking > Test Results to see them.";

    private String[] rows() {
        return new String[]{"Test", testType, "Date taken", Dates.medium(testDate), "Status", resultStatus};
    }
}