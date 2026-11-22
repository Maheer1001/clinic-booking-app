package dao;

import app.DB;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import model.AccessibilitySettings;
import model.AppointmentReminderDefaults;
import model.BookingCommunicationPreferences;
import model.StaffAccess;
import model.StaffRole;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO implements UserRepository, AccountRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM \"User\" WHERE email = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public int getDefaultClinic(int userId) throws SQLException {
        String sql = "SELECT  default_clinic_id FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1;
                int val = rs.getInt("default_clinic_id");
                return rs.wasNull() ? -1 : val;
            }
        }
    }

    @Override
    public boolean updateDefaultClinic(int userId, int clinicId) throws SQLException {
        String sql = "UPDATE \"User\" SET default_clinic_id = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }
    @Override
    public boolean defaultClinicExists(int userID) throws SQLException {
        return getDefaultClinic(userID) >= 0;
    }
    @Override
    public boolean insertUser(String email, String passwordHash) throws SQLException {
        email = email.toLowerCase();

        String sql = """
        INSERT INTO "User"(user_id, email, password_hash, email_verified)
        VALUES(?, ?, ?, false)
        """;

        // In tests (and occasionally under concurrent inserts), MAX(user_id)+1 can race.
        // Retry on duplicate primary key to keep inserts robust.
        for (int attempt = 0; attempt < 5; attempt++) {
            int newId = nextUserId();
            try (Connection c = DB.connect();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, newId);
                ps.setString(2, email);
                ps.setString(3, passwordHash);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                // Postgres unique violation
                if ("23505".equals(e.getSQLState())) {
                    continue;
                }
                throw e;
            }
        }
        return false;
    }

    @Override
    public String getPasswordHashByEmail(String email) throws SQLException {
        String sql = "SELECT password_hash FROM \"User\" WHERE email = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("password_hash");
            }
        }
    }

    @Override
    public boolean updatePasswordHashByEmail(String email, String passwordHash) throws SQLException {
        String sql = "UPDATE \"User\" SET password_hash = ? WHERE email = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setString(2, email);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public Integer getUserIdByEmail(String email) throws SQLException {
        String sql = "SELECT user_id FROM \"User\" WHERE email = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt("user_id");
            }
        }
    }

    @Override
    public String getEmailByUserId(int userId) throws SQLException {
        String sql = "SELECT email FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("email");
            }
        }
    }

    @Override
    public String getDisplayNameByUserId(int userId) throws SQLException {
        String sql = "SELECT first_name, last_name FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String first = rs.getString("first_name");
                String last = rs.getString("last_name");
                if (first != null && !first.isBlank() && last != null && !last.isBlank())
                    return first.trim() + " " + last.trim();
                if (first != null && !first.isBlank()) return first.trim();
                if (last != null && !last.isBlank()) return last.trim();
                return null;
            }
        }
    }

    @Override
    public Optional<User> findById(int userId) throws SQLException {
        String sql = "SELECT user_id, email, first_name, last_name FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new User(
                        rs.getInt("user_id"),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("last_name")));
            }
        }
    }


    /**
     * Role and clinic memberships for login routing (and for every staff action, which re-reads it).
     * Empty if the user does not exist. A patient comes back with role PATIENT and no clinics.
     */
    public Optional<StaffAccess> getStaffAccess(int userId) throws SQLException {
        try (Connection c = DB.connect()) {
            return getStaffAccess(c, userId);
        }
    }

    public static Optional<StaffAccess> getStaffAccess(Connection c, int userId) throws SQLException {
        StaffRole role;
        boolean mustReset;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT role, must_reset_password FROM \"User\" WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                role = StaffRole.fromDb(rs.getString("role"));
                mustReset = rs.getBoolean("must_reset_password");
            }
        }
        List<Integer> clinics = new ArrayList<>();
        if (role.isStaff()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT clinic_id FROM \"StaffClinic\" WHERE user_id = ? ORDER BY clinic_id")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) clinics.add(rs.getInt(1));
                }
            }
        }
        return Optional.of(new StaffAccess(userId, role, clinics, mustReset));
    }

    /** When the patient last saw their results (null: never). See {@link TestDAO#findResultsSinceLastSeen}. */
    public LocalDateTime getLastResultsSeenAt(int userId) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_results_seen_at FROM \"User\" WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp t = rs.getTimestamp(1);
                return t == null ? null : t.toLocalDateTime();
            }
        }
    }

    /**
     * Moves the "seen up to" watermark forward to {@code seenUpTo} (never back). Pass the watermark
     * returned with the results that were shown, not "now", so a result released in between is not
     * lost and client/database clock differences do not matter.
     */
    public boolean setLastResultsSeenAt(int userId, LocalDateTime seenUpTo) throws SQLException {
        if (seenUpTo == null) return false;
        String sql = """
            UPDATE "User" SET last_results_seen_at = ?
            WHERE user_id = ? AND (last_results_seen_at IS NULL OR last_results_seen_at < ?)
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(seenUpTo));
            ps.setInt(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(seenUpTo));
            return ps.executeUpdate() == 1;
        }
    }

    private int nextUserId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(user_id), 0) + 1 AS next_id FROM \"User\"";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("next_id");
        }
    }

    /**
     * Deletes the user and everything that references them, in one transaction.
     * Upcoming confirmed bookings give their timeslot capacity back first.
     *
     * Previously this only ran {@code DELETE FROM "User"}, which the foreign keys from
     * Appointment, Test, Prescription, reminders and Session reject for any user with data,
     * and the error was swallowed, so the account silently survived.
     *
     * @throws SQLException if anything fails; nothing is deleted in that case.
     */
    @Override
    public void deleteUserById(int userId) throws SQLException {
        String[] statements = {
                // free the slots held by bookings that are still going ahead
                """
                UPDATE "Timeslot" t
                SET booked_count = GREATEST(COALESCE(t.booked_count, 0) - (
                        SELECT COUNT(*) FROM "Appointment" a
                        WHERE a.timeslot_id = t.timeslot_id AND a.user_id = ? AND a.status = 'CONFIRMED'), 0)
                WHERE t.date >= CURRENT_DATE AND t.timeslot_id IN (
                        SELECT a.timeslot_id FROM "Appointment" a
                        WHERE a.user_id = ? AND a.status = 'CONFIRMED')
                """,
                """
                DELETE FROM "AppointmentReminder"
                WHERE user_id = ? OR app_id IN (SELECT app_id FROM "Appointment" WHERE user_id = ?)
                """,
                """
                DELETE FROM "PrescriptionReminder"
                WHERE user_id = ? OR prescription_id IN (
                        SELECT prescription_id FROM "Prescription"
                        WHERE user_id = ? OR test_id IN (SELECT test_id FROM "Test" WHERE user_id = ?))
                """,
                "DELETE FROM \"BookingReminder\" WHERE user_id = ?",
                "DELETE FROM \"BookingDetails\" WHERE app_id IN (SELECT app_id FROM \"Appointment\" WHERE user_id = ?)",
                "DELETE FROM \"Appointment\" WHERE user_id = ?",
                """
                DELETE FROM "Prescription"
                WHERE user_id = ? OR test_id IN (SELECT test_id FROM "Test" WHERE user_id = ?)
                """,
                "DELETE FROM \"TestResult\" WHERE test_id IN (SELECT test_id FROM \"Test\" WHERE user_id = ?)",
                "DELETE FROM \"Test\" WHERE user_id = ?",
                "DELETE FROM \"Session\" WHERE user_id = ?",
                // staff accounts: clinic membership (AuditLog rows are kept: append-only, ids only)
                "DELETE FROM \"StaffClinic\" WHERE user_id = ?",
                "DELETE FROM \"User\" WHERE user_id = ?"
        };

        try (Connection c = DB.connect()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (String sql : statements) {
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        // every placeholder in these statements is the user id
                        long params = sql.chars().filter(ch -> ch == '?').count();
                        for (int i = 1; i <= params; i++) ps.setInt(i, userId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public String getPasswordHashByUserId(int userId) throws SQLException {
        String sql = "SELECT password_hash FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("password_hash") : null;
            }
        }
    }

    @Override
    public boolean updatePasswordHashByUserId(int userId, String passwordHash) throws SQLException {
        String sql = "UPDATE \"User\" SET password_hash = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean emailTakenByOtherUser(int userId, String email) throws SQLException {
        String sql = "SELECT 1 FROM \"User\" WHERE LOWER(email) = LOWER(?) AND user_id <> ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email == null ? null : email.trim());
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean updateName(int userId, String firstName, String lastName) throws SQLException {
        String sql = "UPDATE \"User\" SET first_name = ?, last_name = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setInt(3, userId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean updateEmail(int userId, String newEmail) throws SQLException {
        String sql = "UPDATE \"User\" SET email = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newEmail);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }


    @Override
    public service.AccountService.AccountInfo getAccountInfo(int userId) throws SQLException {
        String sql = """
        SELECT first_name, last_name, phone_number, dob, email
        FROM "User"
        WHERE user_id = ?
        LIMIT 1
        """;

        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                java.sql.Date dobSql = rs.getDate("dob");
                java.time.LocalDate dob = (dobSql == null) ? null : dobSql.toLocalDate();

                return new service.AccountService.AccountInfo(
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("phone_number"),
                        dob,
                        rs.getString("email")
                );
            }
        }
    }


    public boolean emailUsedByOtherUser(int userId, String email) throws SQLException {
        String sql = "SELECT user_id FROM \"User\" WHERE email = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                int found = rs.getInt("user_id");
                return found != userId;
            }
        }
    }

    public boolean updateAccountInfo(int userId,
                                     String firstName,
                                     String lastName,
                                     String phone,
                                     java.time.LocalDate dob,
                                     String email) throws SQLException {

        String sql = """
        UPDATE "User"
        SET first_name = ?,
            last_name = ?,
            phone_number = ?,
            dob = ?,
            email = ?
        WHERE user_id = ?
        """;

        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, phone);

            if (dob == null) ps.setNull(4, java.sql.Types.DATE);
            else ps.setDate(4, java.sql.Date.valueOf(dob));

            ps.setString(5, email);
            ps.setInt(6, userId);

            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<AppointmentReminderDefaults> getDefaultAppointmentReminderTemplate(int userId) throws SQLException {
        String sql = "SELECT default_reminder_config FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String raw = rs.getString("default_reminder_config");
                if (raw == null || raw.isBlank()) return Optional.empty();
                try {
                    return Optional.of(JSON.readValue(raw, AppointmentReminderDefaults.class));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return Optional.empty();
                }
            }
        }
    }

    @Override
    public void saveDefaultAppointmentReminderTemplate(int userId, AppointmentReminderDefaults defaults) throws SQLException {
        String json;
        try {
            json = JSON.writeValueAsString(defaults);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize default reminder template", e);
        }
        String sql = "UPDATE \"User\" SET default_reminder_config = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, json);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    @Override
    public BookingCommunicationPreferences getBookingCommunicationPreferences(int userId) throws SQLException {
        String sql = "SELECT booking_comm_prefs FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return BookingCommunicationPreferences.defaults();
                }
                return parseBookingCommunicationPreferences(rs.getString("booking_comm_prefs"));
            }
        }
    }

    private static BookingCommunicationPreferences parseBookingCommunicationPreferences(String raw) {
        if (raw == null || raw.isBlank()) {
            return BookingCommunicationPreferences.defaults();
        }
        try {
            JsonNode n = JSON.readTree(raw);
            BookingCommunicationPreferences d = BookingCommunicationPreferences.defaults();
            boolean confirmation = n.hasNonNull("confirmation_discreet")
                    ? n.get("confirmation_discreet").asBoolean()
                    : d.confirmationDiscreet();
            boolean reschedule = n.hasNonNull("reschedule_discreet")
                    ? n.get("reschedule_discreet").asBoolean()
                    : d.rescheduleDiscreet();
            boolean cancellation = n.hasNonNull("cancellation_discreet")
                    ? n.get("cancellation_discreet").asBoolean()
                    : d.cancellationDiscreet();
            String confirmationOv = extractOverridesJson(n, "confirmation_overrides");
            String rescheduleOv = extractOverridesJson(n, "reschedule_overrides");
            String cancellationOv = extractOverridesJson(n, "cancellation_overrides");
            return new BookingCommunicationPreferences(confirmation, reschedule, cancellation,
                    confirmationOv, rescheduleOv, cancellationOv);
        } catch (Exception e) {
            return BookingCommunicationPreferences.defaults();
        }
    }

    private static String extractOverridesJson(JsonNode parent, String key) {
        if (!parent.has(key) || parent.get(key).isNull()) {
            return null;
        }
        JsonNode v = parent.get(key);
        if (v.isTextual()) {
            String t = v.asText();
            return t != null && t.isBlank() ? null : t;
        }
        return v.toString();
    }

    @Override
    public void saveBookingCommunicationPreferences(int userId, BookingCommunicationPreferences prefs) throws SQLException {
        String json;
        try {
            json = JSON.writeValueAsString(prefs);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize booking communication preferences", e);
        }
        String sql = "UPDATE \"User\" SET booking_comm_prefs = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, json);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public static AccessibilitySettings getAccessibilitySettingsByUserId(int userId) throws SQLException {
        String sql = "SELECT accessibility_settings FROM \"User\" WHERE user_id = ? LIMIT 1";
        try (Connection c = DB.connect();
        PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new AccessibilitySettings();
                } else {
                    String json = rs.getString("accessibility_settings");
                    if (json == null || json.isBlank()) {
                        return new AccessibilitySettings();
                    } else {
                        return JSON.readValue(json, AccessibilitySettings.class);
                    }
                }
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to load accessibility settings, resetting to default", e);
            }
        }
    }

    public static void setAccessibilitySettingsByUserId(int userId, AccessibilitySettings settings) throws SQLException {
        String sql = "UPDATE \"User\" SET accessibility_settings = ? WHERE user_id = ?";
        try (Connection c = DB.connect();
        PreparedStatement ps = c.prepareStatement(sql)) {
            String settingsJSON = JSON.writeValueAsString(settings);
            ps.setString(1, settingsJSON);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to set accessibility settings, previous retained", e);
        }
    }
}


