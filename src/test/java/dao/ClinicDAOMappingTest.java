package dao;

import app.DB;
import model.Clinic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Row mapping details of {@link ClinicDAO}: missing coordinates, accessibility parsing, ordering. */
class ClinicDAOMappingTest {

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_clinic_mapping;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect();
             Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS "Clinic" (
                    clinic_id INT PRIMARY KEY,
                    name VARCHAR(100),
                    address VARCHAR(200),
                    postcode VARCHAR(20),
                    phone VARCHAR(20),
                    latitude DOUBLE PRECISION,
                    longitude DOUBLE PRECISION,
                    disability_accessibility VARCHAR(500)
                )
            """);
            s.execute("""
                INSERT INTO "Clinic" VALUES
                  (1, 'Zeta Clinic', 'A', 'SO17 1BJ', '1', 50.93, -1.39, '{"Step-free access",Hearing loop}'),
                  (2, 'Alpha Clinic', 'B', 'SO14 0YG', '2', NULL, NULL, NULL),
                  (3, 'Mid Clinic', 'C', 'SO15 2AA', '3', 0, 0, 'Lift, ')
            """);
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @Test
    void findAllIsOrderedByName() {
        List<Clinic> clinics = new ClinicDAO().findAll();
        assertEquals(List.of("Alpha Clinic", "Mid Clinic", "Zeta Clinic"),
                clinics.stream().map(Clinic::getName).toList());
    }

    @Test
    void missingCoordinatesMapToNullNotZero() {
        Clinic alpha = new ClinicDAO().findById(2);
        assertNotNull(alpha);
        assertNull(alpha.getCoords());

        Clinic mid = new ClinicDAO().findById(3);
        assertNotNull(mid.getCoords(), "a stored 0,0 is still a real value");
        assertEquals(0.0, mid.getCoords().lat());
    }

    @Test
    void findByIdParsesAccessibilityLikeFindAll() {
        Clinic zeta = new ClinicDAO().findById(1);
        assertEquals(List.of("Step-free access", "Hearing loop"), zeta.getDissAcc());
        assertEquals(50.93, zeta.getCoords().lat(), 1e-9);
    }

    @Test
    void findByIdReturnsNullForUnknownId() {
        assertNull(new ClinicDAO().findById(999));
    }

    @Test
    void parseAccessibilityHandlesBlankAndTrailingCommas() {
        assertTrue(ClinicDAO.parseAccessibility(null).isEmpty());
        assertTrue(ClinicDAO.parseAccessibility("{}").isEmpty());
        assertEquals(List.of("Lift"), ClinicDAO.parseAccessibility("Lift, "));
    }
}
