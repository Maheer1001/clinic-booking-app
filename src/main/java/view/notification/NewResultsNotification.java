package view.notification;

import javafx.scene.Node;
import model.NewResults;
import view.ui.Dates;

import java.util.ArrayList;
import java.util.List;

/**
 * One popup for several new test results ("You have 3 new results"), listing each test's type, date
 * and whether it is newly available or updated. Never says what a result is. A single new result
 * keeps its own {@link TestResultNotification}; {@link #forResults} picks between the two.
 */
public class NewResultsNotification extends BaseNotification {

    private final String patientName;
    private final List<NewResults.Entry> results;

    public NewResultsNotification(String patientName, List<NewResults.Entry> results) {
        this.patientName = patientName;
        this.results = List.copyOf(results);
    }

    /** The popup for these new results: the single-result one for one, this combined one for more; null for none. */
    public static Notification forResults(String patientName, List<NewResults.Entry> results) {
        if (results == null || results.isEmpty()) return null;
        if (results.size() == 1) {
            NewResults.Entry e = results.getFirst();
            return new TestResultNotification(patientName, e.testType(), e.testDate(), e.resultUpdated());
        }
        return new NewResultsNotification(patientName, results);
    }

    @Override
    public String getTitle() {
        return "You have " + results.size() + " new results";
    }

    private static final String LEAD = "These test results are ready or have been updated:";

    @Override
    public String getMessage() {
        return "Hi " + patientName + ",\n\n" + LEAD + "\n\n"
                + NotificationLayout.detailsText(rows()) + "\n\n"
                + TestResultNotification.FOOTNOTE;
    }

    @Override
    public String getType() {
        return "TEST_RESULT";
    }

    @Override
    protected Node buildPreviewContent() {
        // each result as its own two-line entry: a long test type next to its date squeezed the date
        // into a narrow column ("20 Nov / 2026 · / Available")
        return NotificationLayout.page(getTitle(), patientName, LEAD,
                NotificationLayout.listCard(rows()), TestResultNotification.FOOTNOTE);
    }

    /** One row per result: the test type, then e.g. "5 Feb 2026 · Available". */
    private String[] rows() {
        List<String> rows = new ArrayList<>();
        for (NewResults.Entry e : results) {
            String type = e.testType() == null || e.testType().isBlank() ? "Test" : e.testType();
            String date = e.testDate() == null ? "" : Dates.medium(e.testDate()) + "  ·  ";
            rows.add(type);
            rows.add(date + (e.resultUpdated() ? "Updated" : "Available"));
        }
        return rows.toArray(String[]::new);
    }
}
