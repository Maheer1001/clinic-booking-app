package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
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
                + "Test: " + testType + "\n"
                + "Date Taken: " + testDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")) + "\n"
                + "Status: " + resultStatus + "\n\n"
                + "Please log in to view your results securely.";
    }

    @Override
    public String getType() {
        return "TEST_RESULT";
    }

    @Override
    protected Node buildPreviewContent() {
        String dateStr = testDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        Label heading = UI.titleSm(getTitle());
        Label hi = UI.body("Hi " + patientName + ",");
        Label lead = UI.body("Your test results are now available.");
        VBox details = UI.card(8,
                NotificationLayout.detailRow("Test", testType),
                NotificationLayout.detailRow("Date taken", dateStr),
                NotificationLayout.detailRow("Status", resultStatus)
        );
        Label foot = UI.muted("Please log in to view your results securely.");
        return new VBox(12, heading, hi, lead, details, foot);
    }
}