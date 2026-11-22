package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Released tests the patient has not seen yet: newly released, or released earlier with a result
 * that has since gone from PENDING (or any value) to a final POSITIVE/NEGATIVE.
 *
 * @param watermark pass this to {@code UserDAO.setLastResultsSeenAt} once the patient has been shown
 *                  them; null when there is nothing new
 */
public record NewResults(List<Entry> tests, LocalDateTime watermark) {

    /**
     * One test with something new. Holds no result values, so it is safe to use in a discreet alert.
     * @param resultUpdated true when the test was released before and a result has changed since
     */
    public record Entry(int testId, int clinicId, String testType, LocalDate testDate, LocalDateTime changedAt,
                        boolean resultUpdated) {}

    public NewResults {
        tests = tests == null ? List.of() : List.copyOf(tests);
    }

    public int count() {
        return tests.size();
    }

    public boolean isEmpty() {
        return tests.isEmpty();
    }
}
