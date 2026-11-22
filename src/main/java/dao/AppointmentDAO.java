package dao;

import app.DB;
import model.Appointment;
import model.AppointmentDetails;
import model.AvailableTimeslot;
import model.Book;
import model.UpcomingAppointmentDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AppointmentDAO implements AppointmentRepository {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TimeslotRepository timeslotDAO;

    public AppointmentDAO() {
        this(new TimeslotDAO());
    }

    public AppointmentDAO(TimeslotRepository timeslotDAO) {
        this.timeslotDAO = timeslotDAO;
    }

    @Override
    public Optional<Integer> save(Book b, int userId, int timeslotId) {
        try (Connection c = DB.connect()) {
            String insert = """
                INSERT INTO "Appointment" (user_id, timeslot_id, notes, status)
                VALUES (?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setInt(2, timeslotId);
                ps.setString(3, b.getReason());
                ps.setString(4, "CONFIRMED");
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return Optional.of(keys.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public boolean cancelAppointment(int appointmentId) {
        Integer timeslotId = getTimeslotIdByAppointmentId(appointmentId).orElse(null);
        int updated;
        try (Connection c = DB.connect()) {
            // Only a confirmed appointment can be cancelled, so cancelling twice never frees its place twice.
            String update = """
                UPDATE "Appointment"
                SET status = 'CANCELLED', canceled_at = CURRENT_TIMESTAMP
                WHERE app_id = ? AND status = 'CONFIRMED'
                """;
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setInt(1, appointmentId);
                updated = ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        if (updated != 1) return false;
        if (timeslotId != null) {
            timeslotDAO.decrementBookedCount(timeslotId);
        }
        return true;
    }

    @Override
    public void rescheduleAppointment(int appointmentId, LocalDate newDate) {
        getTimeslotIdByAppointmentId(appointmentId)
                .ifPresent(timeslotId -> timeslotDAO.updateDate(timeslotId, newDate));
    }
    @Override
    public List<LocalDate> getAvailableDates(int clinicId, int serviceId) {
        return timeslotDAO.findAvailableDates(clinicId, serviceId);
    }

    @Override
    public boolean rescheduleToNewTimeslot(int appointmentId, int newTimeslotId) {
        Integer oldTimeslotId = getTimeslotIdByAppointmentId(appointmentId).orElse(null);
        if (oldTimeslotId != null && oldTimeslotId == newTimeslotId) return false;
        int updated;
        try (Connection c = DB.connect()) {
            String update = """
                UPDATE "Appointment" SET timeslot_id = ?
                WHERE app_id = ? AND status = 'CONFIRMED'
                """;
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setInt(1, newTimeslotId);
                ps.setInt(2, appointmentId);
                updated = ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        if (updated != 1) return false;
        // Only free the old place once the appointment has really moved.
        if (oldTimeslotId != null) {
            timeslotDAO.decrementBookedCount(oldTimeslotId);
        }
        return true;
    }

    @Override
    public boolean rescheduleToSlot(int appointmentId, AvailableTimeslot slot) {
        Optional<int[]> ids = getClinicAndServiceIdsForAppointment(appointmentId);
        if (ids.isEmpty() || slot == null) return false;
        Optional<Integer> currentId = getTimeslotIdByAppointmentId(appointmentId);
        if (slot.timeslotId() > 0 && currentId.isPresent() && currentId.get() == slot.timeslotId()) {
            return false; // already booked into this slot
        }
        int newId = timeslotDAO.reserve(slot.timeslotId(), ids.get()[0], ids.get()[1],
                slot.date(), slot.startTime(), slot.endTime());
        if (newId < 0) return false; // slot filled up in the meantime
        if (currentId.isPresent() && currentId.get() == newId) {
            timeslotDAO.decrementBookedCount(newId); // same slot: undo the extra reservation
            return false;
        }
        if (!rescheduleToNewTimeslot(appointmentId, newId)) {
            timeslotDAO.decrementBookedCount(newId); // give the reserved place back
            return false;
        }
        return true;
    }

    @Override
    public Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId) {
        String sql = """
            SELECT t.clinic_id, t.service_id FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            WHERE a.app_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new int[]{rs.getInt("clinic_id"), rs.getInt("service_id")});
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getTimeslotIdByAppointmentId(int appointmentId) {
        String sql = "SELECT timeslot_id FROM \"Appointment\" WHERE app_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("timeslot_id");
                    return rs.wasNull() ? Optional.empty() : Optional.of(id);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * SQL condition for appointments that have not started yet. "Now" comes from this machine, the same
     * clock used for bookable slots, rather than the database server's, which may be in another time zone.
     */
    private static final String NOT_STARTED = "(t.date > ? OR (t.date = ? AND t.start_time > ?))";

    /** Binds the three {@link #NOT_STARTED} parameters starting at {@code index}. */
    private static void bindNow(PreparedStatement ps, int index) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        ps.setObject(index, now.toLocalDate());
        ps.setObject(index + 1, now.toLocalDate());
        ps.setObject(index + 2, now.toLocalTime().withNano(0));
    }

    @Override
    public boolean isUpcomingAppointmentForUser(int appointmentId, int userId) {
        String sql = """
            SELECT 1 FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            WHERE a.app_id = ? AND a.user_id = ? AND a.status = 'CONFIRMED' AND
            """ + NOT_STARTED;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, userId);
            bindNow(ps, 3);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Appointments that have started, newest first: ones the clinic marked COMPLETED, and booked
     * (CONFIRMED) ones whose time has passed. Nothing in the app moves a booking to COMPLETED, so
     * counting only those left the patient's own past bookings out of their history; and the old
     * "date before today" check (on the database server's clock) hid visits from earlier today.
     */
    @Override
    public List<AppointmentDetails> getPastAppointmentDetailsByUserId(int userId) {
        String sql = """
            SELECT t.date, t.start_time, t.end_time, s.name AS service_name, c.name AS clinic_name, a.notes, a.app_id
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            JOIN "Service" s ON t.service_id = s.service_id
            JOIN "Clinic" c ON t.clinic_id = c.clinic_id
            WHERE a.user_id = ? AND a.status IN ('COMPLETED', 'CONFIRMED') AND NOT
            """ + NOT_STARTED + """

            ORDER BY t.date DESC, t.start_time DESC
            """;
        List<AppointmentDetails> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            bindNow(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(toAppointmentDetails(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<UpcomingAppointmentDetails> getUpcomingAppointmentDetailsByUserId(int userId) {
        String sql = """
            SELECT a.app_id, t.date, t.start_time, t.end_time, s.name AS service_name, c.name AS clinic_name, a.notes
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            JOIN "Service" s ON t.service_id = s.service_id
            JOIN "Clinic" c ON t.clinic_id = c.clinic_id
            WHERE a.user_id = ? AND a.status = 'CONFIRMED' AND
            """ + NOT_STARTED + """

            ORDER BY t.date ASC, t.start_time ASC
            """;
        List<UpcomingAppointmentDetails> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            bindNow(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getObject("date", LocalDate.class);
                    LocalTime start = rs.getObject("start_time", LocalTime.class);
                    LocalTime end = rs.getObject("end_time", LocalTime.class);
                    String dateStr = d != null ? d.format(DATE_FMT) : "";
                    String startStr = start != null ? start.format(TIME_FMT) : "";
                    String endStr = end != null ? end.format(TIME_FMT) : "";
                    list.add(new UpcomingAppointmentDetails(
                            rs.getInt("app_id"),
                            dateStr, startStr, endStr,
                            rs.getString("service_name"),
                            rs.getString("clinic_name"),
                            rs.getString("notes")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Appointment> getUpcomingAppointmentsForUser(int userId) {
        String sql = """
            SELECT a.app_id, a.user_id, s.name AS service_name, c.name AS clinic_name,
                   t.date, t.start_time
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            JOIN "Service" s ON t.service_id = s.service_id
            JOIN "Clinic" c ON t.clinic_id = c.clinic_id
            WHERE a.user_id = ? AND a.status = 'CONFIRMED' AND
            """ + NOT_STARTED + """

            ORDER BY t.date ASC, t.start_time ASC
            """;
        List<Appointment> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            bindNow(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime dateTime = LocalDateTime.of(
                            rs.getObject("date", LocalDate.class),
                            rs.getObject("start_time", LocalTime.class));
                    list.add(new Appointment(
                            rs.getInt("app_id"),
                            rs.getInt("user_id"),
                            rs.getString("service_name"),
                            rs.getString("clinic_name"),
                            "Clinician",
                            dateTime));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date) {
        return timeslotDAO.findAvailable(clinicId, serviceId, date);
    }

    private static AppointmentDetails toAppointmentDetails(ResultSet rs) throws SQLException {
        LocalDate d = rs.getObject("date", LocalDate.class);
        LocalTime start = rs.getObject("start_time", LocalTime.class);
        LocalTime end = rs.getObject("end_time", LocalTime.class);
        String dateStr = d != null ? d.format(DATE_FMT) : "";
        String startStr = start != null ? start.format(TIME_FMT) : "";
        String endStr = end != null ? end.format(TIME_FMT) : "";
        return new AppointmentDetails(
                dateStr, startStr, endStr,
                rs.getString("service_name"),
                rs.getString("clinic_name"),
                rs.getString("notes"));
    }

    @Override
    public Optional<LocalDate> getLastAppointmentDateByUserId(int userId) {
        String sql = """
            SELECT MAX(t.date) AS last_date
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            WHERE a.user_id = ? AND t.date < CURRENT_DATE AND a.status <> 'CANCELLED'
            """;
        // A cancelled appointment is not a visit, so it must not reset the "time to book" reminder.
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDate d = rs.getObject("last_date", LocalDate.class);
                    return Optional.ofNullable(d);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
}
