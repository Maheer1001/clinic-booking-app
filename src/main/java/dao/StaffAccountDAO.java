package dao;

import app.DB;
import model.AuditEntry;
import model.StaffRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Tables "ClinicStaffCode", "StaffCodeAttempt" and the staff side of "User" / "StaffClinic". */
public class StaffAccountDAO implements StaffAccountRepository {

    @Override
    public List<ClinicCodeHash> allStaffCodeHashes() throws SQLException {
        String sql = "SELECT clinic_id, code_hash FROM \"ClinicStaffCode\" ORDER BY clinic_id";
        List<ClinicCodeHash> out = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new ClinicCodeHash(rs.getInt(1), rs.getString(2)));
        }
        return out;
    }

    @Override
    public int failedCodeAttempts(String emailHash, LocalDateTime since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM \"StaffCodeAttempt\" WHERE email_hash = ? AND attempted_at >= ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, emailHash);
            ps.setTimestamp(2, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Override
    public int failedCodeAttemptsAll(LocalDateTime since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM \"StaffCodeAttempt\" WHERE attempted_at >= ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Override
    public void recordFailedCodeAttempt(String emailHash, LocalDateTime at) throws SQLException {
        try (Connection c = DB.connect()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO \"StaffCodeAttempt\" (email_hash, attempted_at) VALUES (?, ?)")) {
                ps.setString(1, emailHash);
                ps.setTimestamp(2, Timestamp.valueOf(at));
                ps.executeUpdate();
            }
            // Old attempts no longer count towards any limit.
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM \"StaffCodeAttempt\" WHERE attempted_at < ?")) {
                ps.setTimestamp(1, Timestamp.valueOf(at.minusDays(1)));
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void clearFailedCodeAttempts(String emailHash) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM \"StaffCodeAttempt\" WHERE email_hash = ?")) {
            ps.setString(1, emailHash);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean emailExists(String email) throws SQLException {
        return new UserDAO().emailExists(email);
    }

    @Override
    public int createStaffUser(String email, String passwordHash, StaffRole role, int clinicId,
                               boolean mustResetPassword, boolean pending) throws SQLException {
        // Checked before the transaction: the role goes on the clinic membership (and, as "a staff
        // account", on "User".role), so PATIENT here would make an account with no rights anywhere.
        if (role == null || !role.isStaff()) throw new IllegalArgumentException("A staff account needs a staff role");
        String insertUser = """
            INSERT INTO "User" (user_id, email, password_hash, email_verified, role, must_reset_password)
            VALUES (?, ?, ?, false, ?, ?)
            """;
        for (int attempt = 0; attempt < 5; attempt++) {
            try (Connection c = DB.connect()) {
                boolean autoCommit = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    int userId = nextUserId(c);
                    try (PreparedStatement ps = c.prepareStatement(insertUser)) {
                        ps.setInt(1, userId);
                        ps.setString(2, email.toLowerCase());
                        ps.setString(3, passwordHash);
                        ps.setString(4, role.name());
                        ps.setBoolean(5, mustResetPassword);
                        ps.executeUpdate();
                    }
                    addMembership(c, userId, clinicId, role, pending);
                    AuditLogDAO.insert(c, AuditEntry.of(userId, clinicId,
                            mustResetPassword ? AuditEntry.Action.STAFF_SEEDED : AuditEntry.Action.STAFF_SIGN_UP,
                            AuditEntry.STAFF, userId, null));
                    c.commit();
                    return userId;
                } catch (SQLException e) {
                    c.rollback();
                    // MAX(user_id)+1 raced with another insert: try the next id.
                    if ("23505".equals(e.getSQLState()) && !DB.isDuplicateEmail(e)) continue;
                    throw e;
                } finally {
                    c.setAutoCommit(autoCommit);
                }
            }
        }
        throw new SQLException("Could not allocate a user id");
    }


    /** "StaffClinic".status: only ACTIVE memberships give access to the clinic. */
    public static final String ACTIVE = "ACTIVE";
    public static final String PENDING = "PENDING";

    /** The membership's role is what the person may do at this clinic only. */
    static void addMembership(Connection c, int userId, int clinicId, StaffRole role, boolean pending)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO \"StaffClinic\" (user_id, clinic_id, status, role) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, clinicId);
            ps.setString(3, pending ? PENDING : ACTIVE);
            ps.setString(4, role.name());
            ps.executeUpdate();
        }
    }

    private static int nextUserId(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(user_id), 0) + 1 FROM \"User\"");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
