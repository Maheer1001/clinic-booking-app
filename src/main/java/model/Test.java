package model;

import java.util.List;

/**
 * A single STI test record with type, date, optional treatment/notes, and list of results.
 */
public record Test(String testType, String testDate, String treatment, String clinicianNotes,
                   List<TestResult> testResults) implements TrackingData {

    /** True if treatment was recorded (non-null). */
    public Boolean isTreatment() { return treatment != null; }

    /** True if clinician notes were recorded (non-null). */
    public Boolean isClinicianNotes() { return clinicianNotes != null; }
}
