package dao;

import app.DB;
import model.Book;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentDAOTest {

    private static String savedUrl, savedUser, savedPass;
    private AppointmentDAO dao;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_appt;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect();
             Statement s = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS "Timeslot" (
                    timeslot_id INT AUTO_INCREMENT PRIMARY KEY,
                    date DATE,
                    booked_count INT DEFAULT 0
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS "Appointment" (
                    app_id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT,
                    timeslot_id INT,
                    notes VARCHAR(200),
                    status VARCHAR(20),
                    canceled_at TIMESTAMP
                )
            """);

            s.execute("INSERT INTO \"Timeslot\" (date, booked_count) VALUES ('2026-02-11', 0)");
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @BeforeEach
    void setUp() {
        dao = new AppointmentDAO();
    }

    @Test
    void testSaveCreatesAppointment() throws Exception {
        Book b = new Book();
        b.setReason("Routine check");

        Optional<Integer> appId = dao.save(b, 1, 1);
        assertTrue(appId.isPresent());

        try (Connection c = DB.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT status, notes FROM \"Appointment\" WHERE user_id = 1 AND notes = 'Routine check'")) {
            assertTrue(rs.next());
            assertEquals("CONFIRMED", rs.getString("status"));
            assertEquals("Routine check", rs.getString("notes"));
        }
    }

    @Test
    void testCancelUpdatesStatus() throws Exception {
        try (Connection c = DB.connect();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Appointment\" (user_id, timeslot_id, notes, status) VALUES (1, 1, 'note', 'CONFIRMED')");
            s.execute("UPDATE \"Timeslot\" SET booked_count = 1 WHERE timeslot_id = 1");
        }
        dao.cancelAppointment(1);

        try (Connection c = DB.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT status FROM \"Appointment\" WHERE app_id = 1")) {
            assertTrue(rs.next());
            assertEquals("CANCELLED", rs.getString("status"));
        }
    }

    @Test
    void testRescheduleUpdatesDate() throws Exception {
        try (Connection c = DB.connect();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Appointment\" (user_id, timeslot_id, notes, status) VALUES (1, 1, 'note', 'CONFIRMED')");
        }
        LocalDate newDate = LocalDate.of(2026, 3, 15);
        dao.rescheduleAppointment(1, newDate);

        try (Connection c = DB.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT date FROM \"Timeslot\" WHERE timeslot_id = 1")) {
            assertTrue(rs.next());
            assertEquals(newDate, rs.getDate("date").toLocalDate());
        }
    }
}
