package model;

import java.time.LocalDate;
import java.util.List;

/**
 * What a clinician enters in the test editor. Results are keyed by condition (case-insensitive):
 * saving an existing test updates a condition's result in place, adds new conditions and removes
 * ones no longer listed.
 * @param appId optional: the appointment at this clinic the test was taken at
 */
public record TestDraft(Integer appId, String testType, LocalDate testDate, String clinicianNotes,
                        String treatmentInstructions, List<TestResult> results) {

    public TestDraft {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public boolean anyPositive() {
        return results.stream().anyMatch(r -> r.result() == TestResultStatus.POSITIVE);
    }

    @Override
    public String toString() {
        return "TestDraft[<contents hidden>]";
    }
}
