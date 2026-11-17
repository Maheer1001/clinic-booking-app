package service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestResultEmailTest {

    private TestResultEmail email;
    private final String recipientEmail = "patient@example.com";
    private final String recipientName = "Jane Doe";
    private final String testType = "Chlamydia Test";
    private final LocalDate testDate = LocalDate.of(2026, 2, 5);
    private final String resultStatus = "Available";

    @BeforeEach
    void setUp() {
        email = new TestResultEmail(
                recipientEmail, recipientName,
                testType, testDate, resultStatus
        );
    }

    @Test
    void getRecipientEmailShouldReturnCorrectEmail() {
        assertEquals(recipientEmail, email.getRecipientEmail());
    }

    @Test
    void getRecipientNameShouldReturnCorrectName() {
        assertEquals(recipientName, email.getRecipientName());
    }

    @Test
    void getSubjectShouldNotContainTestType() {
        assertFalse(email.getSubject().contains(testType));
    }

    @Test
    void getSubjectShouldIndicateResultsReady() {
        assertTrue(email.getSubject().contains("results are ready"));
    }

    @Test
    void getTemplateNameShouldReturnTestResult() {
        assertEquals("test-result", email.getTemplateName());
    }

    @Test
    void getTemplateContextShouldContainAllFields() {
        Map<String, Object> context = email.getTemplateContext();

        assertEquals(testType, context.get("testType"));
        assertEquals("05 February 2026", context.get("testDate"));
        assertEquals(resultStatus, context.get("resultStatus"));
    }

    @Test
    void getTemplateContextShouldHaveThreeEntries() {
        assertEquals(3, email.getTemplateContext().size());
    }

    @Test
    void getTemplateContextShouldNotContainActualResults() {
        Map<String, Object> context = email.getTemplateContext();

        // Ensure no sensitive result data leaks into the email context
        assertFalse(context.containsKey("result"));
        assertFalse(context.containsKey("diagnosis"));
    }
}
