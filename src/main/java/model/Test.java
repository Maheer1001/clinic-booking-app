package model;

import java.util.List;

/**
 * A single STI test record with type, date, optional treatment/notes, and list of results.
 */
public record Test(String testType, String testDate, String treatment, String clinicianNotes,
                   List<TestResult> testResults) implements TrackingData {

    /** True if treatment was recorded (not null or blank; a blank value used to show an empty section). */
    public Boolean isTreatment() { return treatment != null && !treatment.isBlank(); }

    /** True if clinician notes were recorded (not null or blank). */
    public Boolean isClinicianNotes() { return clinicianNotes != null && !clinicianNotes.isBlank(); }
}
