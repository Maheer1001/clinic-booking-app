package dao;

import app.DB;
import model.Service;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceDAOTest {

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_service;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect();
             Statement s = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS "Service" (
                    service_id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50),
                    description VARCHAR(200),
                    duration_minutes INT
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS "ClinicService" (
                    clinic_id INT,
                    service_id INT
                )
            """);

            s.execute("""
                INSERT INTO "Service" (name, description, duration_minutes)
                VALUES ('Checkup', 'General health check', 30)
            """);

            s.execute("""
                INSERT INTO "ClinicService" (clinic_id, service_id)
                VALUES (1, 1)
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
    void testFindByClinicId() {
        ServiceDAO dao = new ServiceDAO();
        List<Service> services = dao.findByClinicId(1);
        assertEquals(1, services.size());

        Service s = services.get(0);
        assertEquals("Checkup", s.getName());
        assertEquals(30, s.getDurationMinutes());
    }
}
