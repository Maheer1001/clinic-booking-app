package dao;

import app.DB;
import model.BookingDetails;
import model.ClinicBookingDetails;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Table "BookingDetails": the personal details given when booking, keyed by app_id.
 * Never log the values read or written here.
 */
public class BookingDetailsDAO implements BookingDetailsRepository {

    static final String COLUMNS =
            "d.app_id, d.first_name, d.last_name, d.dob, d.phone_number, d.contact_email, d.gender, d.sexuality, "
            + "d.accessibility_needs";

    /**
     * Inserts the details for an appointment on the caller's connection, so it can share the
     * appointment's transaction.
     */
    public static void insert(Connection c, BookingDetails d) throws SQLException {
        String sql = """
            INSERT INTO "BookingDetails" (app_id, first_name, last_name, dob, phone_number, contact_email,
                gender, sexuality, accessibility_needs)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, d.appId());
            ps.setString(2, d.firstName());
            ps.setString(3, d.lastName());
            if (d.dob() == null) ps.setNull(4, Types.DATE);
            else ps.setDate(4, Date.valueOf(d.dob()));
            ps.setString(5, d.phone());
            ps.setString(6, d.email());
            ps.setString(7, d.gender());
            ps.setString(8, d.sexuality());
            ps.setString(9, d.accessibilityNeeds());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<BookingDetails> findByAppointmentId(int appId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM \"BookingDetails\" d WHERE d.app_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<ClinicBookingDetails> findByClinic(int clinicId, LocalDate from, LocalDate toInclusive)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + """
            , a.user_id, a.status, a.notes, t.date, t.start_time, t.end_time, s.name AS service_name
            FROM "BookingDetails" d
            JOIN "Appointment" a ON a.app_id = d.app_id
            JOIN "Timeslot" t ON t.timeslot_id = a.timeslot_id
            LEFT JOIN "Service" s ON s.service_id = t.service_id
            WHERE t.clinic_id = ?
              AND (CAST(? AS DATE) IS NULL OR t.date >= ?)
              AND (CAST(? AS DATE) IS NULL OR t.date <= ?)
            ORDER BY t.date, t.start_time, d.app_id
            """;
        List<ClinicBookingDetails> out = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            setDate(ps, 2, from);
            setDate(ps, 3, from);
            setDate(ps, 4, toInclusive);
            setDate(ps, 5, toInclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date date = rs.getDate("date");
                    Time start = rs.getTime("start_time");
                    Time end = rs.getTime("end_time");
                    out.add(new ClinicBookingDetails(
                            rs.getInt("app_id"),
                            rs.getInt("user_id"),
                            date == null ? null : date.toLocalDate(),
                            start == null ? null : start.toLocalTime(),
                            end == null ? null : end.toLocalTime(),
                            rs.getString("service_name"),
                            rs.getString("status"),
                            rs.getString("notes"),
                            map(rs)));
                }
            }
        }
        return out;
    }

    private static void setDate(PreparedStatement ps, int i, LocalDate d) throws SQLException {
        if (d == null) ps.setNull(i, Types.DATE);
        else ps.setDate(i, Date.valueOf(d));
    }

    /** Maps the {@link #COLUMNS} of a row (also used by {@link StaffDAO}). */
    static BookingDetails map(ResultSet rs) throws SQLException {
        Date dob = rs.getDate("dob");
        return new BookingDetails(
                rs.getInt("app_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                dob == null ? null : dob.toLocalDate(),
                rs.getString("phone_number"),
                rs.getString("contact_email"),
                rs.getString("gender"),
                rs.getString("sexuality"),
                rs.getString("accessibility_needs"));
    }
}
