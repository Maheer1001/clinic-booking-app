package database;

import org.junit.jupiter.api.Test;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class DBConnectionTest {

    @Test
    void testDatabaseConnection() {
        try (Connection conn = DB.connect()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        } catch (Exception e) {
            fail("Connection failed: " + e.getMessage());
        }
    }
}
