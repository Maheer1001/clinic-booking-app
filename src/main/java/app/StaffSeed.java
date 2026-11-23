package app;

import dao.StaffAccountDAO;
import dao.UserDAO;
import model.StaffRole;
import service.AuthService;
import service.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds staff access. Codes are hashed with {@link PasswordHasher}, which is why this is Java and
 * not SQL (docs/migrations/2026-11-22-staff-login.sql).
 *
 * <p>Dev main, run against the database in {@code .env}:
 * <pre>
 *   STAFF_SEED_PASSWORD=... java app.StaffSeed admin &lt;clinicId&gt; &lt;email&gt;
 * </pre>
 * creates the first CLINIC_ADMIN of a clinic (admin at that clinic only: the role is stored on the
 * "StaffClinic" membership) as a new account with must_reset_password set (an
 * existing email is refused). The password comes from the environment, never the command line or SQL.
 * {@code java app.StaffSeed codes} seeds missing demo staff codes only (demo databases).
 */
public final class StaffSeed {

    private StaffSeed() {}

    /** The demo staff code of a clinic: clinic1, clinic2, ... */
    public static String demoCode(int clinicId) {
        return "clinic" + clinicId;
    }

    /**
     * Gives every clinic without a staff code its demo code, hashed. Clinics that already have one
     * (including rotated codes) are left alone, so this is safe to run on every start.
     * @return how many codes were added
     */
    public static int seedMissingStaffCodes(Connection c) throws SQLException {
        List<Integer> missing = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT clinic_id FROM "Clinic"
                WHERE clinic_id NOT IN (SELECT clinic_id FROM "ClinicStaffCode")
                ORDER BY clinic_id
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) missing.add(rs.getInt(1));
        }
        int added = 0;
        for (int clinicId : missing) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO "ClinicStaffCode" (clinic_id, code_hash, code_is_demo)
                    SELECT ?, ?, TRUE WHERE NOT EXISTS (SELECT 1 FROM "ClinicStaffCode" WHERE clinic_id = ?)
                    """)) {
                ps.setInt(1, clinicId);
                ps.setString(2, PasswordHasher.hash(demoCode(clinicId)));
                ps.setInt(3, clinicId);
                added += ps.executeUpdate();
            } catch (SQLException e) {
                // Another app instance seeded it first.
                if (!"23505".equals(e.getSQLState())) throw e;
            }
        }
        return added;
    }

    /**
     * Creates the clinic's first admin as a NEW account with must_reset_password set. An email that
     * already has an account is refused (never promoted): staff and patient accounts stay separate.
     * @return the admin's user id
     * @throws IllegalStateException if the email already has an account
     */
    public static int seedClinicAdmin(int clinicId, String email, String password) throws SQLException {
        String normalized = AuthService.normalize(email);
        if (!AuthService.isValidEmail(normalized)) throw new IllegalArgumentException("Invalid email");
        if (new UserDAO().emailExists(normalized)) {
            throw new IllegalStateException("An account with this email already exists; existing accounts are "
                    + "never made staff. Use a new email for the clinic admin.");
        }
        if (!AuthService.isStrongEnoughPassword(password)) {
            throw new IllegalArgumentException("Password too weak: " + AuthService.PASSWORD_RULES);
        }
        return new StaffAccountDAO().createStaffUser(normalized, PasswordHasher.hash(password), StaffRole.CLINIC_ADMIN,
                clinicId, true);
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && args[0].equals("codes")) {
            try (Connection c = DB.connect()) {
                System.out.println("Staff codes added: " + seedMissingStaffCodes(c));
            }
            return;
        }
        if (args.length == 3 && args[0].equals("admin")) {
            String password = System.getenv("STAFF_SEED_PASSWORD");
            try {
                int id = seedClinicAdmin(Integer.parseInt(args[1]), args[2], password);
                System.out.println("Clinic admin created, user id " + id + " (must reset password at first login)");
            } catch (IllegalStateException | IllegalArgumentException e) {
                System.err.println("Not seeded: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        System.out.println("Usage: java app.StaffSeed codes | STAFF_SEED_PASSWORD=... java app.StaffSeed admin <clinicId> <email>");
    }
}
