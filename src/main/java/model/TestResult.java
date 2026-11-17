package model;

/** A single result line within a test (e.g. condition name and positive/negative/pending). */
public record TestResult(String condition, TestResultStatus result) {

    public Boolean isPositive() {
        return result.equals(TestResultStatus.POSITIVE);
    }

    public Boolean isNegative() {
        return result.equals(TestResultStatus.NEGATIVE);
    }

    public Boolean isPending() {
        return result.equals(TestResultStatus.PENDING);
    }
}
