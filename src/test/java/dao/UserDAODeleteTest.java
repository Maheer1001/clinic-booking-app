package dao;

import app.DB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Account deletion must remove the user's dependent rows, not fail on foreign keys. */
class UserDAODeleteTest {

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:user_delete_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect()) {
            DB.initTables(c);
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    private static int count(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void deleteUser_removesDependentRows_andFreesUpcomingSlots() throws Exception {
        UserDAO dao = new UserDAO();
        dao.insertUser("delete.me@example.com", "h");
        dao.insertUser("keep.me@example.com", "h");
        int victim = dao.getUserIdByEmail("delete.me@example.com");
        int other = dao.getUserIdByEmail("keep.me@example.com");
        String future = LocalDate.now().plusDays(7).toString();

        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Medicine\" (medicine_id, name) VALUES (900, 'Med')");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, capacity, booked_count) VALUES (900, '" + future + "', 2, 2)");
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (900, " + victim + ", 900, 'CONFIRMED')");
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (901, " + other + ", 900, 'CONFIRMED')");
            s.execute("INSERT INTO \"AppointmentReminder\" (user_id, app_id, notify) VALUES (" + victim + ", 900, true)");
            s.execute("INSERT INTO \"Test\" (test_id, user_id, test_type) VALUES (900, " + victim + ", 'STI')");
            s.execute("INSERT INTO \"TestResult\" (test_id, condition, result) VALUES (900, 'X', 'NEGATIVE')");
            s.execute("INSERT INTO \"Prescription\" (prescription_id, user_id, medicine_id, test_id) VALUES (900, " + victim + ", 900, 900)");
            s.execute("INSERT INTO \"PrescriptionReminder\" (user_id, prescription_id, notify) VALUES (" + victim + ", 900, true)");
            s.execute("INSERT INTO \"BookingReminder\" (user_id, notify) VALUES (" + victim + ", true)");
            s.execute("INSERT INTO \"Session\" (session_id, user_id) VALUES ('s-900', " + victim + ")");
        }

        dao.deleteUserById(victim);

        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"User\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"Appointment\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"AppointmentReminder\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"Test\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"TestResult\" WHERE test_id = 900"));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"Prescription\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"PrescriptionReminder\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"BookingReminder\" WHERE user_id = " + victim));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM \"Session\" WHERE user_id = " + victim));

            // the other user's data is untouched and only the deleted user's seat is freed
            assertEquals(1, count(s, "SELECT COUNT(*) FROM \"User\" WHERE user_id = " + other));
            assertEquals(1, count(s, "SELECT COUNT(*) FROM \"Appointment\" WHERE user_id = " + other));
            assertEquals(1, count(s, "SELECT booked_count FROM \"Timeslot\" WHERE timeslot_id = 900"));
        }
    }

    @Test
    void deleteUser_withNoData_removesUser() throws Exception {
        UserDAO dao = new UserDAO();
        dao.insertUser("lonely@example.com", "h");
        int id = dao.getUserIdByEmail("lonely@example.com");

        dao.deleteUserById(id);

        assertFalse(dao.emailExists("lonely@example.com"));
    }
}
