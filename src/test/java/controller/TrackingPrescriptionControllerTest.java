package controller;

import model.PrescriptionSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingPrescriptionControllerTest {

    TrackingPrescriptionController TPController;
    TrackingAppointmentControllerTest test = new TrackingAppointmentControllerTest();
    DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yy");

    @BeforeEach
    void setUp() {TPController = new TrackingPrescriptionController(null, null, null);}

    @AfterEach
    void tearDown() {
        TPController = null;
    }

    @Test
    void testGetSearchableTextCurrent() {
        assertEquals("Onceadayactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true))));
        assertEquals("Onceadayactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "", 1, 1, "01/01/01", "", "", true))));
    }

    @Test
    void testGetSearchableTextHistory() {
        assertEquals("Onceadayinactivepasthistory", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "", 1, 1, "01/01/01", LocalDate.now().minusDays(1).format(FORMATTER), "", false))));
    }

    @Test
    void testGetSearchableTextCourse() {
        assertEquals("Twiceevery2daysactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "", 2, 2, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true))));
        assertEquals("3timesevery7daysactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "", 3, 7, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true))));
    }

    @Test
    void testGetSearchableTextDetails() {
        assertEquals("PrescriptionOnceadayactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("Prescription", "", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true))));
        assertEquals("100mgOnceadayactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true))));
        assertEquals("OnceadayTakeorallyactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("", "", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true))));
        assertEquals("Prescription100mgOnceadayTakeorallyactivecurrent", test.removeWhitespace(TPController.getSearchableText(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true))));
    }

    @Test
    void testGetPredicateSearchEmpty() {
        assertTrue(TPController.getPredicate("", null, null).test(new PrescriptionSummary("Prescription", "", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true)));
        assertTrue(TPController.getPredicate("", null, null).test(new PrescriptionSummary("", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "", true)));
        assertTrue(TPController.getPredicate("", null, null).test(new PrescriptionSummary("", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
    }

    @Test
    void testGetPredicateSearchSingleValue() {
        assertTrue(TPController.getPredicate("prescription", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("100mg", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("once", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("once a day", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("current", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("active", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
        assertTrue(TPController.getPredicate("orally", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
    }

    @Test
    void testGetPredicateFilterEmpty() {
        assertTrue(TPController.getPredicate("", null, null).test(new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/01/01", LocalDate.now().plusDays(1).format(FORMATTER), "Take orally", true)));
    }

    @Test
    void testGetPredicateFilterDateFrom() {
        PrescriptionSummary pres = new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/09/25", "30/09/25", "Take orally", true);
        assertTrue(TPController.getPredicate("", LocalDate.of(2025, 8, 15), null).test(pres));
        assertTrue(TPController.getPredicate("", LocalDate.of(2025, 8, 31), null).test(pres));
        assertTrue(TPController.getPredicate("", LocalDate.of(2025, 9, 1), null).test(pres));
        assertTrue(TPController.getPredicate("", LocalDate.of(2025, 9, 15), null).test(pres));
        assertTrue(TPController.getPredicate("", LocalDate.of(2025, 9, 30), null).test(pres));
        assertFalse(TPController.getPredicate("", LocalDate.of(2025, 10, 1), null).test(pres));
        assertFalse(TPController.getPredicate("", LocalDate.of(2025, 10, 15), null).test(pres));
    }

    @Test
    void testGetPredicateFilterDateTo() {
        PrescriptionSummary pres = new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/09/25", "30/09/25", "Take orally", true);
        assertFalse(TPController.getPredicate("", null, LocalDate.of(2025, 8, 15)).test(pres));
        assertFalse(TPController.getPredicate("", null, LocalDate.of(2025, 8, 31)).test(pres));
        assertTrue(TPController.getPredicate("", null, LocalDate.of(2025, 9, 1)).test(pres));
        assertTrue(TPController.getPredicate("", null, LocalDate.of(2025, 9, 15)).test(pres));
        assertTrue(TPController.getPredicate("", null, LocalDate.of(2025, 9, 30)).test(pres));
        assertTrue(TPController.getPredicate("", null, LocalDate.of(2025, 10, 1)).test(pres));
        assertTrue(TPController.getPredicate("", null, LocalDate.of(2025, 10, 15)).test(pres));
    }

    @Test
    void testGetPredicateAll() {
        PrescriptionSummary pres = new PrescriptionSummary("Prescription", "100mg", 1, 1, "01/09/25", "30/09/25", "Take orally", true);
        assertTrue(TPController.getPredicate("prescription", LocalDate.of(2025, 8, 15), LocalDate.of(2025, 10, 15)).test(pres));
        assertFalse(TPController.getPredicate("prescriptions", LocalDate.of(2025, 8, 15), LocalDate.of(2025, 10, 15)).test(pres));
        assertFalse(TPController.getPredicate("prescription", LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15)).test(pres));
        assertFalse(TPController.getPredicate("prescription", LocalDate.of(2025, 8, 15), LocalDate.of(2025, 8, 31)).test(pres));
    }

    @Test
    void testGetComparatorDateNewest() {
        PrescriptionSummary pre1 = new PrescriptionSummary("Antibiotics", "100mg", 2, 1, "14/04/25", "28/04/25", "Take orally", false);
        PrescriptionSummary pre2 = new PrescriptionSummary("PrEP", "500mg", 1, 2, "06/02/25", "", "Remember to refill when low", true);
        PrescriptionSummary pre3 = new PrescriptionSummary("Cream", "200mg", 1, 1, "26/10/25", "02/11/25", "Apply with care", false);
        List<PrescriptionSummary> pres = Arrays.asList(pre1, pre2, pre3);
        pres.sort(TPController.getComparator("Date (Newest)"));
        assertEquals("Cream", pres.getFirst().name());
        assertEquals("Antibiotics", pres.get(1).name());
        assertEquals("PrEP", pres.get(2).name());
    }

    @Test
    void testGetComparatorDateOldest() {
        PrescriptionSummary pre1 = new PrescriptionSummary("Antibiotics", "100mg", 2, 1, "14/04/25", "28/04/25", "Take orally", false);
        PrescriptionSummary pre2 = new PrescriptionSummary("PrEP", "500mg", 1, 2, "06/02/25", "", "Remember to refill when low", true);
        PrescriptionSummary pre3 = new PrescriptionSummary("Cream", "200mg", 1, 1, "26/10/25", "02/11/25", "Apply with care", false);
        List<PrescriptionSummary> pres = Arrays.asList(pre1, pre2, pre3);
        pres.sort(TPController.getComparator("Date (Oldest)"));
        assertEquals("PrEP", pres.getFirst().name());
        assertEquals("Antibiotics", pres.get(1).name());
        assertEquals("Cream", pres.get(2).name());
    }

    @Test
    void testGetComparatorNameAZ() {
        PrescriptionSummary pre1 = new PrescriptionSummary("Antibiotics", "100mg", 2, 1, "14/04/25", "28/04/25", "Take orally", false);
        PrescriptionSummary pre2 = new PrescriptionSummary("PrEP", "500mg", 1, 2, "06/02/25", "", "Remember to refill when low", true);
        PrescriptionSummary pre3 = new PrescriptionSummary("Cream", "200mg", 1, 1, "26/10/25", "02/11/25", "Apply with care", false);
        List<PrescriptionSummary> pres = Arrays.asList(pre1, pre2, pre3);
        pres.sort(TPController.getComparator("Name (A-Z)"));
        assertEquals("Antibiotics", pres.getFirst().name());
        assertEquals("Cream", pres.get(1).name());
        assertEquals("PrEP", pres.get(2).name());
    }
}
