package dao;

import app.DB;
import model.AppointmentDetails;
import model.PrescriptionSummary;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The queries behind the Tracking screens (appointment history, test order, current prescriptions),
 * against an in-memory H2 copy of the tables.
 */
class TrackingDAOTest {

    private static final int USER = 5;
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yy");

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_tracking_dao;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE \"Clinic\" (clinic_id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("CREATE TABLE \"Service\" (service_id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("""
                CREATE TABLE "Timeslot" (timeslot_id INT AUTO_INCREMENT PRIMARY KEY, date DATE, start_time TIME,
                    end_time TIME, service_id INT, clinic_id INT, capacity INT, booked_count INT)
            """);
            s.execute("""
                CREATE TABLE "Appointment" (app_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, timeslot_id INT,
                    notes VARCHAR(200), status VARCHAR(20), canceled_at TIMESTAMP)
            """);
            s.execute("""
                CREATE TABLE "Test" (test_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, clinic_id INT,
                    test_type VARCHAR(50), test_date DATE, clinician_notes VARCHAR(200), treatment_instructions VARCHAR(200),
                    released_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """);
            s.execute("CREATE TABLE \"TestResult\" (result_id INT AUTO_INCREMENT PRIMARY KEY, test_id INT, condition VARCHAR(50), result VARCHAR(20))");
            s.execute("CREATE TABLE \"Medicine\" (medicine_id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("""
                CREATE TABLE "Prescription" (prescription_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, clinic_id INT,
                    medicine_id INT, test_id INT, dosage VARCHAR(50), doses_per_day INT, days_between_doses INT,
                    start_date DATE, end_date DATE, instructions VARCHAR(200), actively_taking BOOLEAN)
            """);
            s.execute("INSERT INTO \"Clinic\" VALUES (1, 'Clinic 1')");
            s.execute("INSERT INTO \"Service\" VALUES (1, 'STI Test')");
            s.execute("INSERT INTO \"Medicine\" VALUES (1, 'Doxycycline')");
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @BeforeEach
    void clearRows() throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            for (String t : List.of("Appointment", "Timeslot", "TestResult", "Test", "Prescription")) {
                s.execute("DELETE FROM \"" + t + "\"");
            }
        }
    }

    private static void appointment(LocalDateTime start, String status, String notes) throws Exception {
        try (Connection c = DB.connect()) {
            int slot;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO \"Timeslot\" (date, start_time, end_time, service_id, clinic_id, capacity, booked_count) VALUES (?, ?, ?, 1, 1, 1, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setObject(1, start.toLocalDate());
                ps.setObject(2, start.toLocalTime().withNano(0));
                ps.setObject(3, start.toLocalTime().withNano(0).plusMinutes(15));
                ps.executeUpdate();
                var keys = ps.getGeneratedKeys();
                keys.next();
                slot = keys.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO \"Appointment\" (user_id, timeslot_id, notes, status) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, USER);
                ps.setInt(2, slot);
                ps.setString(3, notes);
                ps.setString(4, status);
                ps.executeUpdate();
            }
        }
    }

    @Test
    void pastAppointments_includeStartedBookingsAndEarlierToday_notCancelledOrUpcoming() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        appointment(now.minusDays(30), "COMPLETED", "completed");
        // a booking whose time has passed: nothing in the app marks it COMPLETED, it used to be left out
        appointment(now.minusDays(3), "CONFIRMED", "attended booking");
        appointment(now.minusDays(5), "CANCELLED", "cancelled");
        appointment(now.plusDays(2), "CONFIRMED", "upcoming");
        // started a minute ago, today: "date before today" used to hide it until tomorrow
        // (skipped just after midnight, when a minute ago is yesterday)
        boolean earlierToday = now.toLocalTime().isAfter(LocalTime.of(0, 2));
        if (earlierToday) {
            appointment(now.minusMinutes(1), "COMPLETED", "earlier today");
        }

        List<AppointmentDetails> past = new AppointmentDAO().getPastAppointmentDetailsByUserId(USER);
        List<String> notes = past.stream().map(AppointmentDetails::notes).toList();

        List<String> expected = earlierToday
                ? List.of("earlier today", "attended booking", "completed")
                : List.of("attended booking", "completed");
        assertEquals(expected, notes);
    }

    @Test
    void pastAppointments_listNoShowAsMissed() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        appointment(now.minusDays(2), "NO_SHOW", "missed visit");
        appointment(now.minusDays(4), "COMPLETED", "attended");
        appointment(now.minusDays(6), "CANCELLED", "cancelled");

        List<AppointmentDetails> past = new AppointmentDAO().getPastAppointmentDetailsByUserId(USER);

        assertEquals(List.of("missed visit", "attended"), past.stream().map(AppointmentDetails::notes).toList());
        assertTrue(past.get(0).missed());
        assertEquals(model.AppointmentStatus.NO_SHOW, past.get(0).status());
        assertFalse(past.get(1).missed());
    }

    @Test
    void upcomingAppointments_neverNoShowOrCancelled() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        appointment(now.plusDays(2), "CONFIRMED", "booked");
        appointment(now.plusDays(3), "NO_SHOW", "marked no-show early");
        appointment(now.plusDays(4), "CANCELLED", "cancelled");

        AppointmentDAO dao = new AppointmentDAO();
        assertEquals(List.of("booked"), dao.getUpcomingAppointmentDetailsByUserId(USER).stream()
                .map(model.UpcomingAppointmentDetails::notes).toList());
        assertEquals(1, dao.getUpcomingAppointmentsForUser(USER).size());
    }

    @Test
    void tests_comeMostRecentlyAddedFirst_soTheNewResultIsTheOneAnnounced() throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Test\" (user_id, test_type, test_date) VALUES (5, 'Older', DATE '2026-10-01')");
            s.execute("INSERT INTO \"Test\" (user_id, test_type, test_date) VALUES (5, 'No date', NULL)");
            // added last but taken earlier (back-dated): ordering by date used to put it last
            s.execute("INSERT INTO \"Test\" (user_id, test_type, test_date) VALUES (5, 'Just added', DATE '2026-09-01')");
        }
        List<String> types = new TestDAO().getTestsByUserId(USER).stream().map(model.Test::testType).toList();
        assertEquals(List.of("Just added", "No date", "Older"), types);
    }

    @Test
    void prescriptions_lastDayStillCurrent_stoppedCourseIsFinished() throws Exception {
        LocalDate today = LocalDate.now();
        try (Connection c = DB.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO \"Prescription\" (user_id, medicine_id, dosage, doses_per_day, days_between_doses, start_date, end_date, instructions, actively_taking) "
                        + "VALUES (5, 1, ?, 1, 0, ?, ?, NULL, ?)")) {
            Object[][] rows = {
                    {"ends today", today.minusDays(6), today, true},
                    {"ended yesterday", today.minusDays(6), today.minusDays(1), true},
                    {"stopped early", today.minusDays(1), today.plusDays(30), false},
                    {"ongoing", today.minusDays(20), null, null},
            };
            for (Object[] r : rows) {
                ps.setString(1, (String) r[0]);
                ps.setObject(2, r[1]);
                ps.setObject(3, r[2]);
                ps.setObject(4, r[3]);
                ps.executeUpdate();
            }
        }
        List<PrescriptionSummary> list = new PrescriptionDAO().getPrescriptionsByUserId(USER);
        java.util.Map<String, Boolean> active = new java.util.HashMap<>();
        for (PrescriptionSummary p : list) {
            active.put(p.dosage(), p.active());
        }
        assertEquals(Boolean.TRUE, active.get("ends today"));
        assertEquals(Boolean.FALSE, active.get("ended yesterday"));
        assertEquals(Boolean.FALSE, active.get("stopped early"));
        assertEquals(Boolean.TRUE, active.get("ongoing"));
        // matches what the dose reminders list offers
        List<String> remindable = new PrescriptionDAO().getPrescriptionsForRemindersByUserId(USER).stream()
                .map(model.Prescription::getDosage).sorted().toList();
        List<String> current = list.stream().filter(PrescriptionSummary::active).map(PrescriptionSummary::dosage).sorted().toList();
        assertEquals(current, remindable);
    }

    @Test
    void isActive_rules() {
        LocalDate today = LocalDate.of(2026, 11, 22);
        assertTrue(PrescriptionDAO.isActive(null, null, today));
        assertTrue(PrescriptionDAO.isActive(today, true, today));
        assertFalse(PrescriptionDAO.isActive(today.minusDays(1), true, today));
        assertFalse(PrescriptionDAO.isActive(today.plusDays(10), false, today));
    }
}
