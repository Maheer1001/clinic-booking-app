package dao;

import app.DB;
import model.Book;
import model.BookingDetails;
import model.ClinicBookingDetails;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Booking details are saved with the appointment in one transaction and can be read back per
 * appointment and per clinic, against an in-memory H2 copy of the real schema.
 */
class BookingDetailsDAOTest {

    private static String savedUrl, savedUser, savedPass;
    private static final String URL = "jdbc:h2:mem:booking_details_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private final AppointmentDAO appointments = new AppointmentDAO();
    private final BookingDetailsDAO details = new BookingDetailsDAO();
    private static int userId;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        useDb(URL);
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            DB.initTables(c);
            // second run is a no-op (idempotent migration)
            DB.initTables(c);
            s.execute("INSERT INTO \"Service\" (service_id, name, duration_minutes) VALUES (1, 'STI test', 30)");
            s.execute("INSERT INTO \"Service\" (service_id, name, duration_minutes) VALUES (2, 'PrEP', 15)");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, start_time, end_time, service_id, clinic_id, capacity, booked_count) "
                    + "VALUES (10, DATE '2030-01-02', TIME '10:00:00', TIME '10:30:00', 1, 5, 3, 0)");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, start_time, end_time, service_id, clinic_id, capacity, booked_count) "
                    + "VALUES (11, DATE '2030-01-01', TIME '09:00:00', TIME '09:15:00', 2, 5, 3, 0)");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, start_time, end_time, service_id, clinic_id, capacity, booked_count) "
                    + "VALUES (12, DATE '2030-01-01', TIME '09:00:00', TIME '09:15:00', 2, 6, 3, 0)");
        }
        UserDAO users = new UserDAO();
        users.insertUser("details@example.com", "h");
        userId = users.getUserIdByEmail("details@example.com");
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @BeforeEach
    void clearRows() throws Exception {
        useDb(URL);
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM \"BookingDetails\"");
            s.execute("DELETE FROM \"Appointment\"");
        }
    }

    private static void useDb(String url) {
        DB.setUrl(url);
        DB.setUsername("sa");
        DB.setPassword("");
    }

    private static Book bookingWithDetails() {
        Book b = new Book();
        b.setFirstName(" Alex ");
        b.setLastName("Taylor");
        b.setDob(LocalDate.of(1999, 3, 4));
        b.setPhone("07123 456789");
        b.setEmail("Alex@Example.com");
        b.setGender("Non-binary");
        b.setSexuality("Pan");
        b.setDisabilities("Hearing loop");
        b.setReason("Nervous about needles");
        return b;
    }

    @Test
    void save_storesDetailsWithTheAppointment() throws Exception {
        int appId = appointments.save(bookingWithDetails(), userId, 10).orElseThrow();

        BookingDetails d = details.findByAppointmentId(appId).orElseThrow();
        assertEquals(appId, d.appId());
        assertEquals("Alex", d.firstName());
        assertEquals("Taylor", d.lastName());
        assertEquals(LocalDate.of(1999, 3, 4), d.dob());
        assertEquals("07123456789", d.phone());
        assertEquals("alex@example.com", d.email());
        assertEquals("Non-binary", d.gender());
        assertEquals("Pan", d.sexuality());
        assertEquals("Hearing loop", d.accessibilityNeeds());
    }

    @Test
    void save_withoutDetails_storesNoDetailsRow() throws Exception {
        int appId = appointments.save(new Book(), userId, 10).orElseThrow();
        assertEquals(Optional.empty(), details.findByAppointmentId(appId));
    }

    @Test
    void multiServiceBooking_getsTheSameDetailsOnEachAppointment() throws Exception {
        Book b = bookingWithDetails();
        int first = appointments.save(b, userId, 10).orElseThrow();
        int second = appointments.save(b, userId, 11).orElseThrow();

        BookingDetails a = details.findByAppointmentId(first).orElseThrow();
        BookingDetails c = details.findByAppointmentId(second).orElseThrow();
        assertEquals(a.withAppId(0), c.withAppId(0));
    }

    @Test
    void findByClinic_returnsOnlyThatClinic_soonestFirst_withAppointmentInfo() throws Exception {
        Book b = bookingWithDetails();
        int later = appointments.save(b, userId, 10).orElseThrow();   // clinic 5, 2 Jan
        int sooner = appointments.save(b, userId, 11).orElseThrow();  // clinic 5, 1 Jan
        appointments.save(b, userId, 12).orElseThrow();               // clinic 6

        List<ClinicBookingDetails> rows = details.findByClinic(5, null, null);
        assertEquals(List.of(sooner, later), rows.stream().map(ClinicBookingDetails::appId).toList());
        ClinicBookingDetails r = rows.get(1);
        assertEquals(userId, r.userId());
        assertEquals(LocalDate.of(2030, 1, 2), r.date());
        assertEquals("STI test", r.serviceName());
        assertEquals("CONFIRMED", r.status());
        assertEquals("Nervous about needles", r.notes());
        assertEquals("Alex Taylor", r.details().fullName());

        assertEquals(List.of(later), details.findByClinic(5, LocalDate.of(2030, 1, 2), null)
                .stream().map(ClinicBookingDetails::appId).toList());
        assertEquals(List.of(sooner), details.findByClinic(5, null, LocalDate.of(2030, 1, 1))
                .stream().map(ClinicBookingDetails::appId).toList());
        assertTrue(details.findByClinic(99, null, null).isEmpty());
    }

    @Test
    void detailsThatCannotBeSaved_rollBackTheAppointment() throws Exception {
        // A database without the BookingDetails table: the details insert fails.
        useDb("jdbc:h2:mem:booking_details_missing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE "Appointment" (app_id SERIAL PRIMARY KEY, user_id INT, timeslot_id INT,
                    notes VARCHAR(255), status VARCHAR(20))
                """);
        }
        assertEquals(Optional.empty(), appointments.save(bookingWithDetails(), userId, 10));
        try (Connection c = DB.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM \"Appointment\"")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "appointment must not be kept without its details");
        }
        // Without details the same database still takes the booking.
        assertTrue(appointments.save(new Book(), userId, 10).isPresent());
    }

    @Test
    void deletingTheUser_removesTheirBookingDetails() throws Exception {
        UserDAO users = new UserDAO();
        users.insertUser("details-delete@example.com", "h");
        int victim = users.getUserIdByEmail("details-delete@example.com");
        int appId = appointments.save(bookingWithDetails(), victim, 10).orElseThrow();

        users.deleteUserById(victim);

        assertEquals(Optional.empty(), details.findByAppointmentId(appId));
    }
}
