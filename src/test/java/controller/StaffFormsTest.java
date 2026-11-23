package controller;

import controller.StaffForms.ResultRow;
import model.AppointmentStatus;
import model.TestDraft;
import model.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaffFormsTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 10);

    @Test
    void appointmentPills_sayWhatHappened() {
        assertEquals("Booked", StaffForms.appointmentPill(AppointmentStatus.CONFIRMED).text());
        assertEquals("Attended", StaffForms.appointmentPill(AppointmentStatus.COMPLETED).text());
        assertEquals("Did not attend", StaffForms.appointmentPill(AppointmentStatus.NO_SHOW).text());
        assertEquals("Cancelled", StaffForms.appointmentPill(AppointmentStatus.CANCELLED).text());
        assertEquals("Unknown", StaffForms.appointmentPill(null).text());
        assertEquals("Draft", StaffForms.testPill(false).text());
        assertEquals("Released", StaffForms.testPill(true).text());
        assertEquals("bad", StaffForms.resultPill(TestResultStatus.POSITIVE).kind());
    }

    @Test
    void outcomeRules_matchTheService() {
        assertTrue(StaffForms.canRecordOutcome(AppointmentStatus.CONFIRMED, TODAY, TODAY));
        assertTrue(StaffForms.canRecordOutcome(AppointmentStatus.CONFIRMED, TODAY.minusDays(3), TODAY));
        assertFalse(StaffForms.canRecordOutcome(AppointmentStatus.CONFIRMED, TODAY.plusDays(1), TODAY));
        assertFalse(StaffForms.canRecordOutcome(AppointmentStatus.CANCELLED, TODAY, TODAY));
        assertTrue(StaffForms.canClearOutcome(AppointmentStatus.NO_SHOW));
        assertFalse(StaffForms.canClearOutcome(AppointmentStatus.CONFIRMED));
    }

    @Test
    void testDraft_dropsEmptyRows_trimsAndDefaultsToPending() {
        TestDraft d = StaffForms.testDraft(5, "STI screen", TODAY, List.of(
                new ResultRow("  Chlamydia ", TestResultStatus.NEGATIVE),
                new ResultRow("   ", TestResultStatus.POSITIVE),
                new ResultRow("HIV", null)), "n", "t");
        assertEquals(2, d.results().size());
        assertEquals("Chlamydia", d.results().get(0).condition());
        assertEquals(TestResultStatus.PENDING, d.results().get(1).result());
        assertEquals(5, d.appId());
        assertFalse(d.anyPositive(), "the blank positive row is dropped");
    }

    @Test
    void parseTime_acceptsCommonForms() {
        assertEquals(LocalTime.of(9, 30), StaffForms.parseTime("9:30"));
        assertEquals(LocalTime.of(9, 30), StaffForms.parseTime("09.30"));
        assertEquals(LocalTime.of(9, 30), StaffForms.parseTime("0930"));
        assertEquals(LocalTime.of(14, 15), StaffForms.parseTime("2:15pm"));
        assertEquals(LocalTime.of(0, 0), StaffForms.parseTime("12am"));
        assertEquals(LocalTime.of(9, 0), StaffForms.parseTime(" 9 "));
        assertNull(StaffForms.parseTime("25:00"));
        assertNull(StaffForms.parseTime("9:75"));
        assertNull(StaffForms.parseTime("13pm"));
        assertNull(StaffForms.parseTime("soon"));
        assertNull(StaffForms.parseTime(null));
    }

    @Test
    void savedText_alwaysStartsWithSaved() {
        assertEquals("Saved.", StaffForms.savedText(null));
        assertEquals("Saved.", StaffForms.savedText("Saved."));
        assertEquals("Saved. Marked as attended.", StaffForms.savedText("Marked as attended."));
        assertEquals("Saved as a draft. The patient can't see it until you release it.",
                StaffForms.savedText("Saved as a draft. The patient can't see it until you release it."));
        assertEquals("1 of 1 place booked", StaffForms.capacityText(1, 1));
        assertEquals("2 of 5 places booked", StaffForms.capacityText(2, 5));
    }
}
