package controller;

import model.PrescriptionSummary;
import model.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackingDatesTest {

    @Test
    void parse_returnsNullInsteadOfThrowing() {
        assertNull(TrackingDates.parse(""));
        assertNull(TrackingDates.parse(null));
        assertNull(TrackingDates.parse("not a date"));
        assertEquals(LocalDate.of(2025, 4, 21), TrackingDates.parse("21/04/25"));
    }

    @Test
    void display_usesFourDigitYearAndMonthName() {
        assertEquals("21 Apr 2025", TrackingDates.display("21/04/25"));
        assertEquals("Date not recorded", TrackingDates.display(""));
    }

    @Test
    void testWithoutDate_doesNotBreakFilteringOrSorting() {
        TrackingTestController c = new TrackingTestController(null, null, null);
        model.Test undated = new model.Test("Undated", "", null, null, List.of());
        model.Test dated = new model.Test("Dated", "01/01/25", null, null, List.of());

        assertTrue(c.getPredicate("", null, null).test(undated));
        assertFalse(c.getPredicate("", LocalDate.of(2024, 1, 1), null).test(undated));

        List<model.Test> tests = new ArrayList<>(List.of(undated, dated));
        tests.sort(c.getComparator(null));
        assertEquals("Dated", tests.getFirst().testType(), "Undated records sort last");
    }

    @Test
    void prescriptionDefaultSort_isNewestFirst() {
        TrackingPrescriptionController c = new TrackingPrescriptionController(null, null, null);
        PrescriptionSummary older = new PrescriptionSummary("A", "", 1, 1, "01/01/24", "", "", true);
        PrescriptionSummary newer = new PrescriptionSummary("B", "", 1, 1, "01/01/25", "", "", true);
        List<PrescriptionSummary> list = new ArrayList<>(List.of(older, newer));
        list.sort(c.getComparator(null));
        assertEquals("B", list.getFirst().name());
    }

    @Test
    void statusLabels_areHumanReadable() {
        assertEquals("Positive", TestResultStatus.POSITIVE.label());
        assertEquals("Negative", TestResultStatus.NEGATIVE.label());
        assertEquals("Pending", TestResultStatus.PENDING.label());
        assertEquals("POSITIVE", TestResultStatus.POSITIVE.toString(), "toString unchanged for search text");
    }
}
