package model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrescriptionSummaryTest {

    @Test
    void getCourse_onceEveryday() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "100mg", 1, 1,
                "01/01/25", "07/01/25", "Take with food", true);
        assertEquals("Once a day", rx.getCourse());
    }

    @Test
    void getCourse_twiceEveryday() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "50mg", 2, 1,
                "01/01/25", "14/01/25", "Take morning and night", true);
        assertEquals("Twice a day", rx.getCourse());
    }

    @Test
    void getCourse_zeroDaysBetweenDosesMeansDaily() {
        // Daily courses are recorded with 0 days between doses; this used to read "Once every 0 days".
        PrescriptionSummary rx = new PrescriptionSummary("Med", "75mcg", 1, 0,
                "01/01/25", "01/04/25", "Same time each day", true);
        assertEquals("Once a day", rx.getCourse());
    }

    @Test
    void getInstructionLines_nullIsEmpty() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "75mcg", 1, 1,
                "01/01/25", "01/04/25", null, true);
        assertEquals(List.of(), rx.getInstructionLines());
    }

    @Test
    void getCourse_threeTimesEveryTwoDays() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "25mg", 3, 2,
                "01/01/25", "21/01/25", "Space evenly", false);
        assertEquals("3 times every 2 days", rx.getCourse());
    }

    @Test
    void getCourse_fiveTimesEveryThreeDays() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "10mg", 5, 3,
                "01/01/25", "30/01/25", "With meals", true);
        assertEquals("5 times every 3 days", rx.getCourse());
    }

    @Test
    void getInstructionLines_multiLine() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "100mg", 1, 1,
                "01/01/25", "07/01/25", "Line one\nLine two\nLine three", true);
        List<String> lines = rx.getInstructionLines();
        assertEquals(3, lines.size());
        assertEquals("Line one", lines.get(0));
        assertEquals("Line two", lines.get(1));
        assertEquals("Line three", lines.get(2));
    }

    @Test
    void getInstructionLines_singleLine() {
        PrescriptionSummary rx = new PrescriptionSummary("Med", "100mg", 1, 1,
                "01/01/25", "07/01/25", "Just one line", true);
        List<String> lines = rx.getInstructionLines();
        assertEquals(1, lines.size());
        assertEquals("Just one line", lines.get(0));
    }
}
