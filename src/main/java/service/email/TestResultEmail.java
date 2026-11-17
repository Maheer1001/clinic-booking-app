package service.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to notify a patient that their test results are available.
 * Does NOT include actual results for confidentiality — directs them to log in.
 */
public class TestResultEmail extends BaseEmail {

    private final String testType;
    private final LocalDate testDate;
    private final String resultStatus; // e.g. "Available", "Requires Follow-Up"

    public TestResultEmail(String recipientEmail, String recipientName,
                           String testType, LocalDate testDate, String resultStatus) {
        super(recipientEmail, recipientName);
        this.testType = testType;
        this.testDate = testDate;
        this.resultStatus = resultStatus;
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
        context.put("testType", testType);
        context.put("testDate", testDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        context.put("resultStatus", resultStatus);
        return context;
    }
}