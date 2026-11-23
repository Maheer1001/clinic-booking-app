package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A test as staff see it, draft or released. Patients only ever see released tests.
 * @param releasedAt null while it is a draft (UTC once released)
 */
public record StaffTest(int testId, int patientUserId, int clinicId, Integer appId, String testType,
                        LocalDate testDate, String clinicianNotes, String treatmentInstructions,
                        Integer clinicianId, LocalDateTime releasedAt, List<Result> results) {

    /** One result line; {@code updatedAt} moves when the value changes (UTC). */
    public record Result(int resultId, String condition, TestResultStatus result, LocalDateTime updatedAt) {}

    public StaffTest {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public boolean released() {
        return releasedAt != null;
    }

    public boolean anyPositive() {
        return results.stream().anyMatch(r -> r.result() == TestResultStatus.POSITIVE);
    }

    @Override
    public String toString() {
        return "StaffTest[testId=" + testId + ", released=" + released() + ", <results hidden>]";
    }
}
