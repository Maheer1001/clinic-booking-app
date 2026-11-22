package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the model.Test record (named TestRecordTest to avoid
 * collision with org.junit.jupiter.api.Test).
 */
class TestRecordTest {

    @Test
    void isTreatment_returnsTrue_whenTreatmentIsNotNull() {
        model.Test test = new model.Test("STI", "13:00 21/04/25",
                "Antibiotics", "Notes", new ArrayList<>());
        assertTrue(test.isTreatment());
    }

    @Test
    void isTreatment_returnsFalse_whenTreatmentIsNull() {
        model.Test test = new model.Test("STI", "13:00 21/04/25",
                null, "Notes", new ArrayList<>());
        assertFalse(test.isTreatment());
    }

    @Test
    void isClinicianNotes_returnsTrue_whenNotesIsNotNull() {
        model.Test test = new model.Test("STI", "13:00 21/04/25",
                null, "Some notes", new ArrayList<>());
        assertTrue(test.isClinicianNotes());
    }

    @Test
    void isClinicianNotes_returnsFalse_whenNotesIsNull() {
        model.Test test = new model.Test("STI", "13:00 21/04/25",
                null, null, new ArrayList<>());
        assertFalse(test.isClinicianNotes());
    }

    @Test
    void blankTreatmentAndNotes_countAsNotRecorded() {
        // Blank values used to show empty TREATMENT / CLINICIAN NOTES sections on the result page.
        model.Test test = new model.Test("Swab", "10/11/26", "   ", "", new ArrayList<>());
        assertFalse(test.isTreatment());
        assertFalse(test.isClinicianNotes());
    }

    @Test
    void recordAccessors_returnCorrectValues() {
        List<TestResult> results = List.of(
                new TestResult("Chlamydia", TestResultStatus.POSITIVE));
        model.Test test = new model.Test("Multi STI", "09:30 14/05/25",
                "Doxy", "All clear", results);

        assertEquals("Multi STI", test.testType());
        assertEquals("09:30 14/05/25", test.testDate());
        assertEquals("Doxy", test.treatment());
        assertEquals("All clear", test.clinicianNotes());
        assertEquals(1, test.testResults().size());
    }
}
