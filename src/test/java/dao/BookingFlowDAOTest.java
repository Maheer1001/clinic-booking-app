package dao;

import app.DB;
import model.AvailableTimeslot;
import model.Book;
import model.UpcomingAppointmentDetails;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Booking, cancelling and rescheduling against an in-memory H2 copy of the slot tables,
 * checking that each slot's booked count stays right.
 */
class BookingFlowDAOTest {

    private static final int CLINIC = 1;
    private static final int SERVICE = 1;
    private static final int USER = 7;

    private static String savedUrl, savedUser, savedPass;

    private final TimeslotDAO slots = new TimeslotDAO();
    private final AppointmentDAO appointments = new AppointmentDAO(slots);

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_booking_flow;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE "Clinic" (clinic_id INT PRIMARY KEY, name VARCHAR(50), opening_hours VARCHAR(50))
            """);
            s.execute("""
                CREATE TABLE "Service" (service_id INT PRIMARY KEY, name VARCHAR(50), duration_minutes INT)
            """);
            s.execute("""
                CREATE TABLE "Timeslot" (
                    timeslot_id INT AUTO_INCREMENT PRIMARY KEY,
                    date DATE, start_time TIME, end_time TIME,
                    service_id INT, clinic_id INT, capacity INT, booked_count INT
                )
            """);
            s.execute("""
                CREATE TABLE "Appointment" (
                    app_id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT, timeslot_id INT, notes VARCHAR(200), status VARCHAR(20),
                    canceled_at TIMESTAMP
                )
            """);
            // Open every day, all day, so slots exist whatever day the tests run on.
            s.execute("INSERT INTO \"Clinic\" VALUES (1, 'Clinic', 'Monday-Sunday: 0:00-23:59')");
            s.execute("INSERT INTO \"Service\" VALUES (1, 'STI test', 30)");
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
            s.execute("DELETE FROM \"Appointment\"");
            s.execute("DELETE FROM \"Timeslot\"");
        }
    }

    private static final LocalDate DAY = LocalDate.now().plusDays(2);

    /** Books a 30-minute slot on {@link #DAY} at the given time, returning the appointment id. */
    private int book(LocalTime start) {
        int slotId = slots.reserve(0, CLINIC, SERVICE, DAY, start, start.plusMinutes(30));
        assertTrue(slotId > 0, "slot should be reserved");
        return appointments.save(new Book(), USER, slotId).orElseThrow();
    }

    private static int bookedCount(int timeslotId) throws Exception {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement("SELECT booked_count FROM \"Timeslot\" WHERE timeslot_id = ?")) {
            ps.setInt(1, timeslotId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static boolean isOffered(List<AvailableTimeslot> list, LocalTime start) {
        return list.stream().anyMatch(s -> s.startTime().equals(start));
    }

    @Test
    void bookedSlot_isNoLongerOffered() {
        book(LocalTime.of(10, 0));

        List<AvailableTimeslot> free = slots.findAvailable(CLINIC, SERVICE, DAY);
        assertFalse(isOffered(free, LocalTime.of(10, 0)));
        assertTrue(isOffered(free, LocalTime.of(10, 30)));
    }

    @Test
    void cancel_freesThePlaceOnce() throws Exception {
        int appId = book(LocalTime.of(10, 0));
        int slotId = appointments.getTimeslotIdByAppointmentId(appId).orElseThrow();

        assertTrue(appointments.cancelAppointment(appId));
        assertEquals(0, bookedCount(slotId));
        assertTrue(isOffered(slots.findAvailable(CLINIC, SERVICE, DAY), LocalTime.of(10, 0)));

        // Someone else books the freed slot; cancelling the first appointment again must not free it.
        int other = slots.reserve(slotId, CLINIC, SERVICE, DAY, LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertEquals(slotId, other);
        assertFalse(appointments.cancelAppointment(appId));
        assertEquals(1, bookedCount(slotId));
    }

    @Test
    void reschedule_movesAppointmentAndFreesOldSlot() throws Exception {
        int appId = book(LocalTime.of(10, 0));
        int oldSlot = appointments.getTimeslotIdByAppointmentId(appId).orElseThrow();

        AvailableTimeslot target = new AvailableTimeslot(0, DAY, LocalTime.of(11, 0), LocalTime.of(11, 30));
        assertTrue(appointments.rescheduleToSlot(appId, target));

        int newSlot = appointments.getTimeslotIdByAppointmentId(appId).orElseThrow();
        assertNotEquals(oldSlot, newSlot);
        assertEquals(0, bookedCount(oldSlot));
        assertEquals(1, bookedCount(newSlot));
    }

    @Test
    void reschedule_toCurrentSlot_isRefusedAndCountUnchanged() throws Exception {
        int appId = book(LocalTime.of(10, 0));
        int slotId = appointments.getTimeslotIdByAppointmentId(appId).orElseThrow();
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("UPDATE \"Timeslot\" SET capacity = 3"); // room left, so the slot is still offered
        }

        AvailableTimeslot same = new AvailableTimeslot(slotId, DAY, LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertFalse(appointments.rescheduleToSlot(appId, same));
        assertEquals(1, bookedCount(slotId));
    }

    @Test
    void reschedule_ofCancelledAppointment_releasesTheReservedPlace() throws Exception {
        int appId = book(LocalTime.of(10, 0));
        appointments.cancelAppointment(appId);

        AvailableTimeslot target = new AvailableTimeslot(0, DAY, LocalTime.of(12, 0), LocalTime.of(12, 30));
        assertFalse(appointments.rescheduleToSlot(appId, target));
        assertTrue(isOffered(slots.findAvailable(CLINIC, SERVICE, DAY), LocalTime.of(12, 0)),
                "the place reserved for the failed move should be given back");
    }

    @Test
    void reserve_sameFreeSlotTwice_usesOneRowAndRespectsCapacity() throws Exception {
        LocalTime start = LocalTime.of(14, 0);
        int first = slots.reserve(0, CLINIC, SERVICE, DAY, start, start.plusMinutes(30));
        int second = slots.reserve(0, CLINIC, SERVICE, DAY, start, start.plusMinutes(30));

        assertTrue(first > 0);
        assertEquals(-1, second, "capacity is 1, so the second booking must fail");
        try (Connection c = DB.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM \"Timeslot\"")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void findAvailableDates_coversTheWindowAndSkipsFullDays() throws Exception {
        List<LocalDate> dates = slots.findAvailableDates(CLINIC, SERVICE);
        assertFalse(dates.isEmpty());
        assertTrue(dates.contains(DAY));
        assertTrue(dates.stream().noneMatch(d -> d.isBefore(LocalDate.now())));

        // Fill every slot on DAY: it should drop out of the list.
        for (AvailableTimeslot slot : slots.findAvailable(CLINIC, SERVICE, DAY)) {
            slots.reserve(slot.timeslotId(), CLINIC, SERVICE, DAY, slot.startTime(), slot.endTime());
        }
        assertFalse(slots.findAvailableDates(CLINIC, SERVICE).contains(DAY));
    }

    @Test
    void upcoming_excludesAppointmentsThatAlreadyStartedToday() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Assumptions.assumeTrue(now.getHour() >= 1 && now.getHour() <= 22, "needs room either side of now");
        LocalDate today = now.toLocalDate();
        LocalTime earlier = now.toLocalTime().minusMinutes(30).withSecond(0).withNano(0);
        LocalTime later = now.toLocalTime().plusMinutes(30).withSecond(0).withNano(0);

        int pastSlot = slots.reserve(0, CLINIC, SERVICE, today, earlier, earlier.plusMinutes(30));
        int futureSlot = slots.reserve(0, CLINIC, SERVICE, today, later, later.plusMinutes(30));
        int pastApp = appointments.save(new Book(), USER, pastSlot).orElseThrow();
        int futureApp = appointments.save(new Book(), USER, futureSlot).orElseThrow();

        List<UpcomingAppointmentDetails> upcoming = appointments.getUpcomingAppointmentDetailsByUserId(USER);
        assertEquals(List.of(futureApp), upcoming.stream().map(UpcomingAppointmentDetails::appId).toList());
        assertFalse(appointments.isUpcomingAppointmentForUser(pastApp, USER));
        assertTrue(appointments.isUpcomingAppointmentForUser(futureApp, USER));
        assertFalse(appointments.isUpcomingAppointmentForUser(futureApp, USER + 1));
    }

    @Test
    void weekdayOnlyClinic_offersNothingAtWeekends() throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Clinic\" VALUES (2, 'Weekdays', 'Monday-Friday: 9:00-5:00')");
        }
        LocalDate saturday = LocalDate.now().plusDays(1);
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) saturday = saturday.plusDays(1);

        assertTrue(slots.findAvailable(2, SERVICE, saturday).isEmpty());
        assertFalse(slots.findAvailableDates(2, SERVICE).contains(saturday));
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM \"Clinic\" WHERE clinic_id = 2");
        }
    }
}
