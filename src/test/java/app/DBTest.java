package app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DBTest {

    private String savedUrl, savedUser, savedPass;

    @BeforeEach
    void save() {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
    }

    @AfterEach
    void restore() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @Test
    void connectWithoutUrlNamesTheMissingSetting() {
        DB.setUrl(null);
        DB.setUsername("sa");
        DB.setPassword("");
        assertFalse(DB.isConfigured());
        SQLException e = assertThrows(SQLException.class, DB::connect);
        assertTrue(e.getMessage().contains("DB_URL"), e.getMessage());
        assertFalse(e.getMessage().contains("DB_USERNAME"), e.getMessage());
    }

    @Test
    void configurationProblemListsEveryMissingSetting() {
        DB.setUrl("");
        DB.setUsername(null);
        DB.setPassword(null);
        String problem = DB.configurationProblem();
        assertNotNull(problem);
        assertTrue(problem.contains("DB_URL, DB_USERNAME, DB_PASSWORD"), problem);
        assertTrue(problem.contains(".env"), problem);
    }

    @Test
    void emptyPasswordIsAllowed() {
        DB.setUrl("jdbc:h2:mem:db_test_empty_pw");
        DB.setUsername("sa");
        DB.setPassword("");
        assertTrue(DB.isConfigured());
        assertNull(DB.configurationProblem());
    }

    @Test
    void connectsToH2AndInitialisesSchema() throws Exception {
        DB.setUrl("jdbc:h2:mem:db_test_init;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect()) {
            assertFalse(c.isClosed());
            DB.initTables(c);
            // ClinicDAO selects this column, so a freshly initialised schema must have it.
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT disability_accessibility FROM \"Clinic\"")) {
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void invalidUrlGivesActionableMessage() {
        DB.setUrl("not-a-jdbc-url");
        DB.setUsername("sa");
        DB.setPassword("");
        SQLException e = assertThrows(SQLException.class, DB::connect);
        assertTrue(e.getMessage().contains("DB_URL"), e.getMessage());
    }

    @Test
    void describeFailureClassifiesBySqlState() {
        assertTrue(DB.describeFailure(new SQLException("password authentication failed", "28P01"))
                .contains("DB_USERNAME and DB_PASSWORD"));
        assertTrue(DB.describeFailure(new SQLException("The connection attempt failed.", "08001"))
                .contains("Could not reach the database server"));
        assertTrue(DB.describeFailure(new SQLException("database \"x\" does not exist", "3D000"))
                .contains("does not exist"));
        String generic = DB.describeFailure(new SQLException("boom", "XX000"));
        assertTrue(generic.contains("boom"), generic);
    }
}
