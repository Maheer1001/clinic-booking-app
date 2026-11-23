package app;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DB#applyPerClinicRolesMigration} (same statements as
 * docs/migrations/2026-11-23-per-clinic-roles.sql) on a database in the old shape, where the role was
 * on "User" only. In-memory H2, never the real database.
 */
class PerClinicRolesMigrationTest {

    private static Connection oldShapeDatabase(String name) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE \"User\" (user_id INTEGER PRIMARY KEY, email VARCHAR(255), role VARCHAR(20))");
            s.execute("""
                CREATE TABLE "StaffClinic" (
                    user_id INTEGER NOT NULL, clinic_id INTEGER NOT NULL, status VARCHAR(20) DEFAULT 'PENDING',
                    PRIMARY KEY (user_id, clinic_id))
                """);
            s.execute("INSERT INTO \"User\" VALUES (1, 'admin@x.test', 'CLINIC_ADMIN'), (2, 'clin@x.test', 'CLINICIAN'),"
                    + " (3, 'odd@x.test', 'PATIENT'), (4, 'null@x.test', NULL)");
            // admin at two clinics, clinician at two, one pending, two odd rows
            s.execute("INSERT INTO \"StaffClinic\" (user_id, clinic_id, status) VALUES"
                    + " (1, 1, 'ACTIVE'), (1, 2, 'ACTIVE'), (2, 1, 'ACTIVE'), (2, 2, 'PENDING'),"
                    + " (3, 1, 'ACTIVE'), (4, 2, 'ACTIVE')");
        }
        return c;
    }

    private static Map<String, String> roles(Connection c) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT user_id, clinic_id, role FROM \"StaffClinic\"")) {
            while (rs.next()) out.put(rs.getInt(1) + "@" + rs.getInt(2), rs.getString(3));
        }
        return out;
    }

    @Test
    void copiesEachUsersRoleOntoEachMembership_leastPrivilegeOtherwise() throws Exception {
        try (Connection c = oldShapeDatabase("per_clinic_roles_copy")) {
            DB.applyPerClinicRolesMigration(c);
            Map<String, String> r = roles(c);
            assertEquals("CLINIC_ADMIN", r.get("1@1"));
            assertEquals("CLINIC_ADMIN", r.get("1@2"));
            assertEquals("CLINICIAN", r.get("2@1"));
            assertEquals("CLINICIAN", r.get("2@2"), "pending memberships get a role too");
            assertEquals("CLINICIAN", r.get("3@1"), "no staff role on the account: the lesser role");
            assertEquals("CLINICIAN", r.get("4@2"));
        }
    }

    @Test
    void isIdempotent_andNeverUndoesALaterRoleChange() throws Exception {
        try (Connection c = oldShapeDatabase("per_clinic_roles_rerun")) {
            DB.applyPerClinicRolesMigration(c);
            try (Statement s = c.createStatement()) {
                // after the upgrade: demoted at clinic 2 only
                s.executeUpdate("UPDATE \"StaffClinic\" SET role = 'CLINICIAN' WHERE user_id = 1 AND clinic_id = 2");
            }
            DB.applyPerClinicRolesMigration(c);
            DB.applyPerClinicRolesMigration(c);
            Map<String, String> r = roles(c);
            assertEquals("CLINIC_ADMIN", r.get("1@1"));
            assertEquals("CLINICIAN", r.get("1@2"), "a re-run must not copy the account role back");

            try (Statement s = c.createStatement()) {
                assertThrows(SQLException.class, () ->
                        s.executeUpdate("UPDATE \"StaffClinic\" SET role = 'PATIENT' WHERE user_id = 1"));
                assertThrows(SQLException.class, () ->
                        s.executeUpdate("UPDATE \"StaffClinic\" SET role = NULL WHERE user_id = 1"));
                s.execute("INSERT INTO \"StaffClinic\" (user_id, clinic_id, status) VALUES (2, 3, 'ACTIVE')");
            }
            assertEquals("CLINICIAN", roles(c).get("2@3"), "the default is the lesser role");
        }
    }
}
