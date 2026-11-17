package model;

public enum TestResultStatus {
    NEGATIVE("Negative"), POSITIVE("Positive"), PENDING("Pending");

    private final String label;

    TestResultStatus(String label) {
        this.label = label;
    }

    /** Human-readable label for the UI ("Negative" rather than "NEGATIVE"). */
    public String label() {
        return label;
    }
}
