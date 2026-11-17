package model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppointmentDetailsTest {

    private AppointmentDetails details;

    @Test
    void testGetNotesLinesMultiLine() {
        details = new AppointmentDetails("01/01/01", "12:00", "13:00", "STI Test", "Clinic 1",
                "Discussed symptoms\nTest results will be available in 3 days\nFollow up recommended");
        List<String> notesLines = details.getNotesLines();
        assertEquals(3, notesLines.size());
        assertEquals("Discussed symptoms", notesLines.getFirst());
        assertEquals("Test results will be available in 3 days", notesLines.get(1));
        assertEquals("Follow up recommended", notesLines.get(2));
    }

    @Test
    void testGetNotesLinesSingleLine() {
        details = new AppointmentDetails("01/01/01", "12:00", "13:00", "STI Test", "Clinic 1", "Discussed symptoms");
        List<String> notesLines = details.getNotesLines();
        assertEquals(1, notesLines.size());
        assertEquals("Discussed symptoms", notesLines.getFirst());
    }

    @Test
    void testGetNotesLinesSingleLineTrailingNewLine() {
        details = new AppointmentDetails("01/01/01", "12:00", "13:00", "STI Test", "Clinic 1", "Discussed symptoms\n");
        List<String> notesLines = details.getNotesLines();
        assertEquals(1, notesLines.size());
        assertEquals("Discussed symptoms", notesLines.getFirst());
    }

    @Test
    void testGetNotesLinesNull() {
        details = new AppointmentDetails("01/01/01", "12:00", "13:00", "STI Test", "Clinic 1", null);
        List<String> notesLines = details.getNotesLines();
        assertEquals(0, notesLines.size());
    }

    @Test
    void testGetNotesLinesEmpty() {
        details = new AppointmentDetails("01/01/01", "12:00", "13:00", "STI Test", "Clinic 1", "");
        List<String> notesLines = details.getNotesLines();
        assertEquals(0, notesLines.size());
    }
}
