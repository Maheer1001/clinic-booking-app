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
                        int appId = keys.getInt(1);
                        timeslotDAO.incrementBookedCount(timeslotId);
                        return Optional.of(appId);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public void cancelAppointment(int appointmentId) {
        Integer timeslotId = getTimeslotIdByAppointmentId(appointmentId);
        try (Connection c = DB.connect()) {
            String update = """
                UPDATE "Appointment"
                SET status = 'CANCELLED', canceled_at = CURRENT_TIMESTAMP
                WHERE app_id = ?
                """;
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setInt(1, appointmentId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (timeslotId != null) {
            timeslotDAO.decrementBookedCount(timeslotId);
        }
    }

    @Override
    public void rescheduleAppointment(int appointmentId, LocalDate newDate) {
        Integer timeslotId = getTimeslotIdByAppointmentId(appointmentId);
        if (timeslotId != null) {
            timeslotDAO.updateDate(timeslotId, newDate);
        }
    }
    @Override
    public List<LocalDate> getAvailableDates(int clinicId, int serviceId) {

        List<LocalDate> dates = new ArrayList<>();

        LocalDate today = LocalDate.now();

        // scan a reasonable range (e.g. next 30 days)
        for (int i = 0; i < 30; i++) {
            LocalDate date = today.plusDays(i);

            List<AvailableTimeslot> slots =
                    timeslotDAO.findAvailable(clinicId, serviceId, date);

            if (!slots.isEmpty()) {
                dates.add(date);
            }
        }

        return dates;
    }

    @Override
    public void rescheduleToNewTimeslot(int appointmentId, int newTimeslotId) {
        Integer oldTimeslotId = getTimeslotIdByAppointmentId(appointmentId);
        try (Connection c = DB.connect()) {
            String update = "UPDATE \"Appointment\" SET timeslot_id = ? WHERE app_id = ?";
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setInt(1, newTimeslotId);
                ps.setInt(2, appointmentId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (oldTimeslotId != null) {
            timeslotDAO.decrementBookedCount(oldTimeslotId);
        }
        timeslotDAO.incrementBookedCount(newTimeslotId);
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

    private Integer getTimeslotIdByAppointmentId(int appointmentId) {
        String sql = "SELECT timeslot_id FROM \"Appointment\" WHERE app_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("timeslot_id") : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean isUpcomingAppointmentForUser(int appointmentId, int userId) {
        String sql = """
            SELECT 1 FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            WHERE a.app_id = ? AND a.user_id = ? AND a.status = 'CONFIRMED' AND t.date >= CURRENT_DATE
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<AppointmentDetails> getPastAppointmentDetailsByUserId(int userId) {
        String sql = """
            SELECT t.date, t.start_time, t.end_time, s.name AS service_name, c.name AS clinic_name, a.notes, a.app_id
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            JOIN "Service" s ON t.service_id = s.service_id
            JOIN "Clinic" c ON t.clinic_id = c.clinic_id
            WHERE a.user_id = ? AND a.status = 'COMPLETED' AND t.date < CURRENT_DATE
            ORDER BY t.date DESC, t.start_time DESC
            """;
        return getAppointmentDetailsFromQuery(sql, userId);
    }

    @Override
    public List<UpcomingAppointmentDetails> getUpcomingAppointmentDetailsByUserId(int userId) {
        String sql = """
            SELECT a.app_id, t.date, t.start_time, t.end_time, s.name AS service_name, c.name AS clinic_name, a.notes
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            JOIN "Service" s ON t.service_id = s.service_id
            JOIN "Clinic" c ON t.clinic_id = c.clinic_id
            WHERE a.user_id = ? AND a.status = 'CONFIRMED' AND t.date >= CURRENT_DATE
            ORDER BY t.date ASC, t.start_time ASC
            """;
        List<UpcomingAppointmentDetails> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
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
            WHERE a.user_id = ? AND a.status = 'CONFIRMED' AND t.date >= CURRENT_DATE
            ORDER BY t.date ASC, t.start_time ASC
            """;
        List<Appointment> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
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

    private List<AppointmentDetails> getAppointmentDetailsFromQuery(String sql, int userId) {
        List<AppointmentDetails> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getObject("date", LocalDate.class);
                    LocalTime start = rs.getObject("start_time", LocalTime.class);
                    LocalTime end = rs.getObject("end_time", LocalTime.class);
                    String dateStr = d != null ? d.format(DATE_FMT) : "";
                    String startStr = start != null ? start.format(TIME_FMT) : "";
                    String endStr = end != null ? end.format(TIME_FMT) : "";
                    list.add(new AppointmentDetails(
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
    public Optional<LocalDate> getLastAppointmentDateByUserId(int userId) {
        String sql = """
            SELECT MAX(t.date) AS last_date
            FROM "Appointment" a
            JOIN "Timeslot" t ON a.timeslot_id = t.timeslot_id
            WHERE a.user_id = ? AND t.date < CURRENT_DATE
            """;
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
