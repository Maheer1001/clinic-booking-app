package setup;

import app.DB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Temporary databases for the tests tagged "db", so that nothing they write ever lands in the
 * database in .env. Each one is named {@code setup_check_<epoch seconds>_<8 hex>}, created on the same
 * server, built with {@link DB#initTables}, and dropped afterwards. Only names matching that pattern
 * are ever dropped. A database rather than a schema, because connection poolers in transaction mode
 * (Neon, PgBouncer) do not keep a per-connection search_path.
 */
public final class SetupCheck {

    public static final String PREFIX = "setup_check_";
    static final Pattern NAME = Pattern.compile("^setup_check_([0-9]+)_[0-9a-f]{8}$");
    /** A temporary database this old is left over from an interrupted run. */
    public static final long STALE_AFTER_SECONDS = 3600;

    private SetupCheck() {}

    /** A fresh name for a temporary database. */
    public static String newName() {
        return PREFIX + (System.currentTimeMillis() / 1000) + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** DB_URL with its database name replaced, keeping host, port and parameters. */
    public static String withDatabase(String url, String database) {
        String prefix = "jdbc:postgresql://";
        int slash = url.indexOf('/', prefix.length());
        if (slash < 0) throw new IllegalArgumentException("DB_URL has no database name");
        int query = url.indexOf('?', slash);
        return url.substring(0, slash + 1) + database + (query < 0 ? "" : url.substring(query));
    }

    /** Drops setup_check_* databases older than an hour, reporting each one to {@code note}. */
    public static void dropStale(Consumer<String> note) throws SQLException {
        long cutoff = System.currentTimeMillis() / 1000 - STALE_AFTER_SECONDS;
        try (Connection c = DB.connect()) {
            List<String> stale = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT datname FROM pg_database")) {
                while (rs.next()) {
                    Matcher m = NAME.matcher(rs.getString(1));
                    if (m.matches() && Long.parseLong(m.group(1)) < cutoff) stale.add(m.group());
                }
            }
            for (String name : stale) {
                try (Statement s = c.createStatement()) {
                    s.execute("DROP DATABASE " + name + " WITH (FORCE)");
                    note.accept("dropped " + name + ", left by an earlier run that was interrupted");
                } catch (SQLException e) {
                    note.accept("could not drop the old temporary database " + name + ": " + e.getMessage());
                }
            }
        }
    }

    /** Drops one temporary database; refuses any name that is not a setup_check_* name. */
    public static void drop(String name) throws SQLException {
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("not a temporary database: " + name);
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }

    /**
     * Creates a temporary database with the app's schema and points {@link DB} at it until
     * {@link TempDatabase#close()}, which points DB back and drops it. Use with try-with-resources,
     * or open in {@code @BeforeAll} and close in {@code @AfterAll}.
     */
    public static TempDatabase tempDatabase() throws SQLException {
        String realUrl = DB.getUrl();
        String name = newName();
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new SQLException("Could not create a temporary database for the db tests (the login needs "
                    + "CREATEDB); they never write to the database in .env. " + e.getMessage(), e.getSQLState(), e);
        }
        TempDatabase db = new TempDatabase(name, realUrl);
        DB.setUrl(withDatabase(realUrl, name));
        try (Connection c = DB.connect()) {
            DB.initTables(c);
        } catch (SQLException | RuntimeException e) {
            db.close();
            throw e;
        }
        return db;
    }

    /** See {@link #tempDatabase()}. */
    public static final class TempDatabase implements AutoCloseable {
        private final String name;
        private final String realUrl;

        private TempDatabase(String name, String realUrl) {
            this.name = name;
            this.realUrl = realUrl;
        }

        public String name() { return name; }

        @Override
        public void close() throws SQLException {
            DB.setUrl(realUrl);
            drop(name);
        }
    }
}
