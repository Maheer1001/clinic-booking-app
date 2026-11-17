package controller;

import model.AppointmentDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingAppointmentControllerTest {

    TrackingAppointmentController TAController;

    @BeforeEach
    void setUp() {TAController = new TrackingAppointmentController(null, null, null);}

    @AfterEach
    void tearDown() {
        TAController = null;
    }

    @Test
    void testGetSearchableTextEmpty() {
        assertEquals("", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "", "", ""))));
        assertEquals("", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("12/02/26", "09:30", "10:30", "", "", ""))));
    }

    @Test
    void testGetSearchableTextSingleValue() {
        assertEquals("Checkup", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "Checkup", "", ""))));
        assertEquals("Clinic", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "", "Clinic", ""))));
        assertEquals("Notes", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "", "", "Notes"))));
    }

    @Test
    void testGetSearchableTextMultipleValues() {
        assertEquals("CheckupClinicNotes", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "Checkup", "Clinic", "Notes"))));
        assertEquals("ClinicNotes", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "", "Clinic", "Notes"))));
        assertEquals("CheckupNotes", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "Checkup", "", "Notes"))));
        assertEquals("CheckupClinic", removeWhitespace(TAController.getSearchableText(new AppointmentDetails("", "", "", "Checkup", "Clinic", ""))));
    }

    @Test
    void testGetPredicateSearchEmpty() {
        assertTrue(TAController.getPredicate("", null, null).test(new AppointmentDetails("21/09/25", "", "", "", "", "")));
        assertTrue(TAController.getPredicate("", null, null).test(new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes")));
    }

    @Test
    void testGetPredicateSearchSingleValue() {
        assertTrue(TAController.getPredicate("checkup", null, null).test(new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes")));
        assertTrue(TAController.getPredicate("check", null, null).test(new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes")));
        assertFalse(TAController.getPredicate("test", null, null).test(new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes")));
        assertFalse(TAController.getPredicate("checks", null, null).test(new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes")));
    }

    @Test
    void testGetPredicateFilterEmpty() {
        assertTrue(TAController.getPredicate("", null, null).test(new AppointmentDetails("21/09/25", "", "", "", "", "")));
        assertTrue(TAController.getPredicate("", null, null).test(new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes")));
    }

    @Test
    void testGetPredicateFilterDateFrom() {
        AppointmentDetails app = new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes");
        assertTrue(TAController.getPredicate("", LocalDate.of(2025, 8, 21), null).test(app));
        assertTrue(TAController.getPredicate("", LocalDate.of(2025, 9, 20), null).test(app));
        assertTrue(TAController.getPredicate("", LocalDate.of(2025, 9, 21), null).test(app));
        assertFalse(TAController.getPredicate("", LocalDate.of(2025, 9, 22), null).test(app));
        assertFalse(TAController.getPredicate("", LocalDate.of(2025, 10, 21), null).test(app));
    }

    @Test
    void testGetPredicateFilterDateTo() {
        AppointmentDetails app = new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes");
        assertFalse(TAController.getPredicate("", null, LocalDate.of(2025, 8, 21)).test(app));
        assertFalse(TAController.getPredicate("", null, LocalDate.of(2025, 9, 20)).test(app));
        assertTrue(TAController.getPredicate("", null, LocalDate.of(2025, 9, 21)).test(app));
        assertTrue(TAController.getPredicate("", null, LocalDate.of(2025, 9, 22)).test(app));
        assertTrue(TAController.getPredicate("", null, LocalDate.of(2025, 10, 21)).test(app));
    }

    @Test
    void testGetPredicateAll() {
        AppointmentDetails app = new AppointmentDetails("21/09/25", "", "", "Checkup", "Clinic", "Notes");
        assertTrue(TAController.getPredicate("clinic", LocalDate.of(2025, 8, 21), LocalDate.of(2025, 10, 21)).test(app));
        assertFalse(TAController.getPredicate("clinics", LocalDate.of(2025, 8, 21), LocalDate.of(2025, 10, 21)).test(app));
        assertFalse(TAController.getPredicate("clinic", LocalDate.of(2025, 10, 21), LocalDate.of(2025, 10, 21)).test(app));
        assertFalse(TAController.getPredicate("clinic", LocalDate.of(2025, 8, 21), LocalDate.of(2025, 8, 21)).test(app));
    }

    @Test
    void testGetComparatorDateNewest() {
        AppointmentDetails app1 = new AppointmentDetails("21/09/25", "", "", "Checkup", "", "");
        AppointmentDetails app2 = new AppointmentDetails("17/04/25", "", "", "Vaccine", "", "");
        AppointmentDetails app3 = new AppointmentDetails("06/01/26", "", "", "Test", "", "");
        List<AppointmentDetails> apps = Arrays.asList(app1, app2, app3);
        apps.sort(TAController.getComparator("Date (Newest)"));
        assertEquals("Test", apps.getFirst().serviceName());
        assertEquals("Checkup", apps.get(1).serviceName());
        assertEquals("Vaccine", apps.get(2).serviceName());
    }

    @Test
    void testGetComparatorDateOldest() {
        AppointmentDetails app1 = new AppointmentDetails("21/09/25", "", "", "Checkup", "", "");
        AppointmentDetails app2 = new AppointmentDetails("17/04/25", "", "", "Vaccine", "", "");
        AppointmentDetails app3 = new AppointmentDetails("06/01/26", "", "", "Test", "", "");
        List<AppointmentDetails> apps = Arrays.asList(app1, app2, app3);
        apps.sort(TAController.getComparator("Date (Oldest)"));
        assertEquals("Vaccine", apps.getFirst().serviceName());
        assertEquals("Checkup", apps.get(1).serviceName());
        assertEquals("Test", apps.get(2).serviceName());
    }

    @Test
    void testGetComparatorNameAZ() {
        AppointmentDetails app1 = new AppointmentDetails("21/09/25", "", "", "Checkup", "", "");
        AppointmentDetails app2 = new AppointmentDetails("17/04/25", "", "", "Vaccine", "", "");
        AppointmentDetails app3 = new AppointmentDetails("06/01/26", "", "", "Test", "", "");
        List<AppointmentDetails> apps = Arrays.asList(app1, app2, app3);
        apps.sort(TAController.getComparator("Name (A-Z)"));
        assertEquals("Checkup", apps.getFirst().serviceName());
        assertEquals("Test", apps.get(1).serviceName());
        assertEquals("Vaccine", apps.get(2).serviceName());
    }

    public String removeWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }
}
