package setup;

import app.DB;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Shared by the tests tagged "db": makes sure the schema exists before a test touches it, so each
 * of them also works on a fresh, empty database whatever order the classes run in.
 */
public final class SetupCheck {

    private static boolean schemaReady;

    private SetupCheck() {}

    /** Runs {@link DB#initTables} once per test run (it is idempotent, this just saves round trips). */
    public static synchronized void ensureSchema() throws SQLException {
        if (schemaReady) return;
        try (Connection c = DB.connect()) {
            DB.initTables(c);
        }
        schemaReady = true;
    }
}
