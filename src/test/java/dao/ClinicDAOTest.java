package dao;

import app.DB;
import model.Clinic;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClinicDAOTest {

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_clinic;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect();
             Statement s = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS "Clinic" (
                    clinic_id INT AUTO_INCREMENT PRIMARY KEY,
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
                INSERT INTO "Clinic" (name, address, postcode, phone, latitude, longitude)
                VALUES ('Test Clinic', '123 Test St', 'AB12CD', '0123456789', 0, 0)
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
    void testFindAll() {
        ClinicDAO dao = new ClinicDAO();
        List<Clinic> clinics = dao.findAll();
        assertEquals(1, clinics.size());

        Clinic c = clinics.get(0);
        assertEquals("Test Clinic", c.getName());
        assertEquals("123 Test St", c.getAddress());
    }
}
