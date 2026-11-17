package database;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData ;
import java.sql.ResultSet;
import java.sql.SQLException;


import static org.junit.jupiter.api.Assertions.*;

@Tag("db")
public class Table_Init_Test {

    @Test
    void testTablesCreated() throws Exception {
        try (Connection c = DB.connect()) {

            DB.initTables(c);

            DatabaseMetaData meta = c.getMetaData();

            assertTrue(tableExists(meta, "User"));
            assertTrue(tableExists(meta, "Session"));
            assertTrue(tableExists(meta, "Clinic"));
            assertTrue(tableExists(meta, "Service"));
            assertTrue(tableExists(meta, "Appointment"));
        }
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }
}