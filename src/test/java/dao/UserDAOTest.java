package dao;

import app.DB;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

public class UserDAOTest {

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:test_user;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect();
             var s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS "User" (
                    user_id INTEGER PRIMARY KEY,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50),
                    email VARCHAR(255),
                    password_hash VARCHAR(255),
                    phone_number VARCHAR(20),
                    DOB DATE,
                    gender VARCHAR(50),
                    sexuality VARCHAR(50),
                    disabilities VARCHAR(255),
                    email_verified BOOLEAN,
                    verification_token VARCHAR(255),
                    v_token_expiry TIMESTAMP,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    default_reminder_config TEXT,
                    booking_comm_prefs TEXT
                )
                """);
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    private void deleteByEmail(String email) throws Exception {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM \"User\" WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
    }

    @Test
    void insert_then_exists_shouldWork() throws Exception {
        UserDAO dao = new UserDAO();
        String email = "alex.test@example.com";

        deleteByEmail(email);

        assertFalse(dao.emailExists(email));
        assertTrue(dao.insertUser(email, "hash123"));
        assertTrue(dao.emailExists(email));
        assertEquals("hash123", dao.getPasswordHashByEmail(email));
    }
}
