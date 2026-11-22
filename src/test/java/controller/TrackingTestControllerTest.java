package controller;

import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingTestControllerTest {

    TrackingTestController TTController;
    TrackingAppointmentControllerTest test = new TrackingAppointmentControllerTest();

    @BeforeEach
    void setUp() {TTController = new TrackingTestController(null, null, null);}

    @AfterEach
    void tearDown() {
        TTController = null;
    }

    @Test
    void testGetSearchableTextEmpty() {
        assertEquals("", test.removeWhitespace(TTController.getSearchableText(new model.Test("", "", null, null, List.of()))));
        assertEquals("", test.removeWhitespace(TTController.getSearchableText(new model.Test("", "21/04/25", null, null, List.of()))));
    }

    @Test
    void testGetSearchableTextSingleValue() {
        assertEquals("Test", test.removeWhitespace(TTController.getSearchableText(new model.Test("Test", "", null, null, List.of()))));
        assertEquals("treatmentTreatment", test.removeWhitespace(TTController.getSearchableText(new model.Test("", "", "Treatment", null, List.of()))));
        assertEquals("notesNotes", test.removeWhitespace(TTController.getSearchableText(new model.Test("", "", null, "Notes", List.of()))));
        assertEquals("STIPositive", test.removeWhitespace(TTController.getSearchableText(new model.Test("", "", null, null, List.of(new TestResult("STI", TestResultStatus.POSITIVE))))));
    }

    @Test
    void testGetSearchableTextMultipleValues() {
        assertEquals("TesttreatmentTreatmentnotesNotesSTIPositive", test.removeWhitespace(TTController.getSearchableText(new model.Test("Test", "21/04/26", "Treatment", "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE))))));
        assertEquals("TesttreatmentTreatmentnotesNotes", test.removeWhitespace(TTController.getSearchableText(new model.Test("Test", "21/04/26", "Treatment", "Notes", List.of()))));
        assertEquals("TesttreatmentTreatmentSTIPositive", test.removeWhitespace(TTController.getSearchableText(new model.Test("Test", "21/04/26", "Treatment", null, List.of(new TestResult("STI", TestResultStatus.POSITIVE))))));
        assertEquals("TestnotesNotesSTIPositive", test.removeWhitespace(TTController.getSearchableText(new model.Test("Test", "21/04/26", null, "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE))))));
        assertEquals("treatmentTreatmentnotesNotesSTIPositive", test.removeWhitespace(TTController.getSearchableText(new model.Test("", "21/04/26", "Treatment", "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE))))));
    }

    @Test
    void testGetPredicateSearchEmpty() {
        assertTrue(TTController.getPredicate("", null, null).test(new model.Test("", "21/04/25", "", "", List.of())));
    }

    @Test
    void testGetPredicateSearchSingleValue() {
        model.Test test = new model.Test("Test", "21/04/26", "Treatment", "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE)));
        assertTrue(TTController.getPredicate("test", null, null).test(test));
        assertTrue(TTController.getPredicate("tes", null, null).test(test));
        assertFalse(TTController.getPredicate("tests", null, null).test(test));
        assertTrue(TTController.getPredicate("positive", null, null).test(test));
        assertFalse(TTController.getPredicate("negative", null, null).test(test));
    }

    @Test
    void testGetPredicateFilterEmpty() {
        assertTrue(TTController.getPredicate("", null, null).test(new model.Test("", "21/04/25", "", "", List.of())));
    }

    @Test
    void testGetPredicateFilterDateFrom() {
        model.Test test = new model.Test("Test", "21/04/25", "Treatment", "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE)));
        assertTrue(TTController.getPredicate("", LocalDate.of(2025, 3, 21), null).test(test));
        assertTrue(TTController.getPredicate("", LocalDate.of(2025, 4, 20), null).test(test));
        assertTrue(TTController.getPredicate("", LocalDate.of(2025, 4, 21), null).test(test));
        assertFalse(TTController.getPredicate("", LocalDate.of(2025, 4, 22), null).test(test));
        assertFalse(TTController.getPredicate("", LocalDate.of(2025, 5, 21), null).test(test));
    }

    @Test
    void testGetPredicateFilterDateTo() {
        model.Test test = new model.Test("Test", "21/04/25", "Treatment", "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE)));
        assertFalse(TTController.getPredicate("", null, LocalDate.of(2025, 3, 21)).test(test));
        assertFalse(TTController.getPredicate("", null, LocalDate.of(2025, 4, 20)).test(test));
        assertTrue(TTController.getPredicate("", null, LocalDate.of(2025, 4, 21)).test(test));
        assertTrue(TTController.getPredicate("", null, LocalDate.of(2025, 4, 22)).test(test));
        assertTrue(TTController.getPredicate("", null, LocalDate.of(2025, 5, 21)).test(test));
    }

    @Test
    void testGetPredicateAll() {
        model.Test test = new model.Test("Test", "21/04/25", "Treatment", "Notes", List.of(new TestResult("STI", TestResultStatus.POSITIVE)));
        assertTrue(TTController.getPredicate("test", LocalDate.of(2025, 3, 21), LocalDate.of(2025, 5, 21)).test(test));
        assertFalse(TTController.getPredicate("tests", LocalDate.of(2025, 3, 21), LocalDate.of(2025, 5, 21)).test(test));
        assertFalse(TTController.getPredicate("test", LocalDate.of(2025, 4, 22), LocalDate.of(2025, 5, 21)).test(test));
        assertFalse(TTController.getPredicate("test", LocalDate.of(2025, 3, 21), LocalDate.of(2025, 4, 20)).test(test));

    }

    @Test
    void testGetComparatorDateNewest() {
        model.Test test1 = new model.Test("Chlamydia Test", "03/11/25", "", "", List.of(new TestResult("Chlamydia", TestResultStatus.NEGATIVE)));
        model.Test test2 = new model.Test("Multi STI Test", "24/10/25", "Antibiotics", "Take daily for a week", List.of(new TestResult("Chlamydia", TestResultStatus.POSITIVE), new TestResult("Syphilis", TestResultStatus.POSITIVE)));
        model.Test test3 = new model.Test("Syphilis Test", "02/11/25", "Syphilis Antibiotics", "Take daily until symptoms disappear", List.of(new TestResult("Syphilis", TestResultStatus.POSITIVE)));
        List<model.Test> tests = Arrays.asList(test1, test2, test3);
        tests.sort(TTController.getComparator("Date (Newest)"));
        assertEquals("Chlamydia Test", tests.getFirst().testType());
        assertEquals("Syphilis Test", tests.get(1).testType());
        assertEquals("Multi STI Test", tests.get(2).testType());
    }

    @Test
    void testGetComparatorDateOldest() {
        model.Test test1 = new model.Test("Chlamydia Test", "03/11/25", "", "", List.of(new TestResult("Chlamydia", TestResultStatus.NEGATIVE)));
        model.Test test2 = new model.Test("Multi STI Test", "24/10/25", "Antibiotics", "Take daily for a week", List.of(new TestResult("Chlamydia", TestResultStatus.POSITIVE), new TestResult("Syphilis", TestResultStatus.POSITIVE)));
        model.Test test3 = new model.Test("Syphilis Test", "02/11/25", "Syphilis Antibiotics", "Take daily until symptoms disappear", List.of(new TestResult("Syphilis", TestResultStatus.POSITIVE)));
        List<model.Test> tests = Arrays.asList(test1, test2, test3);
        tests.sort(TTController.getComparator("Date (Oldest)"));
        assertEquals("Multi STI Test", tests.getFirst().testType());
        assertEquals("Syphilis Test", tests.get(1).testType());
        assertEquals("Chlamydia Test", tests.get(2).testType());
    }

    @Test
    void testGetComparatorNameAZ() {
        model.Test test1 = new model.Test("Chlamydia Test", "03/11/25", "", "", List.of(new TestResult("Chlamydia", TestResultStatus.NEGATIVE)));
        model.Test test2 = new model.Test("Multi STI Test", "24/10/25", "Antibiotics", "Take daily for a week", List.of(new TestResult("Chlamydia", TestResultStatus.POSITIVE), new TestResult("Syphilis", TestResultStatus.POSITIVE)));
        model.Test test3 = new model.Test("Syphilis Test", "02/11/25", "Syphilis Antibiotics", "Take daily until symptoms disappear", List.of(new TestResult("Syphilis", TestResultStatus.POSITIVE)));
        List<model.Test> tests = Arrays.asList(test1, test2, test3);
        tests.sort(TTController.getComparator("Name (A-Z)"));
        assertEquals("Chlamydia Test", tests.getFirst().testType());
        assertEquals("Multi STI Test", tests.get(1).testType());
        assertEquals("Syphilis Test", tests.get(2).testType());
    }
}
