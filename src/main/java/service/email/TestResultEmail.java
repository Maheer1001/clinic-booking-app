package service.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to notify a patient that their test results are available.
 * Does NOT include actual results for confidentiality — directs them to log in.
 * In discreet mode the test type is also left out.
 */
public class TestResultEmail extends BaseEmail {

    private final String testType;
    private final LocalDate testDate;
    private final String resultStatus; // e.g. "Available", "Requires Follow-Up"
    private final boolean discreet;

    public TestResultEmail(String recipientEmail, String recipientName,
                           String testType, LocalDate testDate, String resultStatus) {
        this(recipientEmail, recipientName, testType, testDate, resultStatus, false);
    }

    public TestResultEmail(String recipientEmail, String recipientName,
                           String testType, LocalDate testDate, String resultStatus, boolean discreet) {
        super(recipientEmail, recipientName);
        this.testType = testType;
        this.testDate = testDate;
        this.resultStatus = resultStatus;
        this.discreet = discreet;
    }

    @Override
    public String getSubject() {
        return "Your test results are ready";
    }

    @Override
    public String getTemplateName() {
        return "test-result";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        if (discreet) {
            context.put("discreet", true);
        } else {
            context.put("testType", testType);
        }
        context.put("testDate", testDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        context.put("resultStatus", resultStatus);
        return context;
    }

    @Override
    public boolean isDiscreet() {
        return discreet;
    }
}
