package app;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Single source of JDBC connections for the app. Settings come from {@code .env}
 * (DB_URL, DB_USERNAME, DB_PASSWORD) or, failing that, from process environment variables.
 * Each call to {@link #connect()} opens a new connection that the caller must close
 * (all DAOs use try-with-resources).
 */
public class DB {

    /** Seconds to wait for the server before giving up, so an unreachable DB does not hang the UI. */
    static final int CONNECT_TIMEOUT_SECONDS = 10;

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
    private static String url = dotenv.get("DB_URL");
    private static String username = dotenv.get("DB_USERNAME");
    private static String password = dotenv.get("DB_PASSWORD");

    static {
        String problem = configurationProblem();
        if (problem != null) {
            System.err.println("[DB] WARNING: " + problem);
        }
    }

    private DB() {}

    /**
     * Override the default connection URL (used by tests with H2).
     */
    public static void setUrl(String newUrl) {
        url = newUrl;
    }

    /** Override for tests (e.g. H2). */
    public static void setUsername(String u) { username = u; }
    /** Override for tests (e.g. H2). */
    public static void setPassword(String p) { password = p; }

    public static String getUrl() { return url; }
    public static String getUsername() { return username; }
    public static String getPassword() { return password; }

    /** True when DB_URL, DB_USERNAME and DB_PASSWORD are all set. */
    public static boolean isConfigured() {
        return configurationProblem() == null;
    }

    /**
     * Describes what is missing from the database configuration, or returns null when it is complete.
     * The message is suitable for showing to the user.
     */
    public static String configurationProblem() {
        StringBuilder missing = new StringBuilder();
        if (isBlank(url)) missing.append("DB_URL");
        if (isBlank(username)) missing.append(missing.isEmpty() ? "" : ", ").append("DB_USERNAME");
        // An empty password is valid (e.g. in-memory H2 in tests); only a missing one is an error.
        if (password == null) missing.append(missing.isEmpty() ? "" : ", ").append("DB_PASSWORD");
        if (missing.isEmpty()) return null;

        String where = Files.exists(Path.of(".env"))
                ? "Add them to the .env file in " + Path.of("").toAbsolutePath() + "."
                : "No .env file was found in " + Path.of("").toAbsolutePath()
                  + ". Copy .env.example to .env and fill in your database details (see docs/SETUP.md).";
        return "Database is not configured (missing " + missing + "). " + where;
    }

    public static Connection connect() throws SQLException {
        String problem = configurationProblem();
        if (problem != null) {
            throw new SQLException(problem, "08001");
        }

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        if (url.startsWith("jdbc:postgresql:")) {
            // Fail fast instead of blocking the JavaFX thread for the OS default TCP timeout.
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
            props.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        }

        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            throw new SQLException(describeFailure(e), e.getSQLState(), e.getErrorCode(), e);
        }
    }

    /** Turns a driver exception into a message that says what to check. */
    static String describeFailure(SQLException e) {
        String state = e.getSQLState() != null ? e.getSQLState() : "";
        String original = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String hint;
        if (state.startsWith("28")) {
            hint = "The database rejected the login. Check DB_USERNAME and DB_PASSWORD in .env.";
        } else if (state.equals("3D000")) {
            hint = "The database named in DB_URL does not exist. Check DB_URL in .env.";
        } else if (state.startsWith("08") || original.toLowerCase().contains("connection")) {
            hint = "Could not reach the database server. Check DB_URL in .env, your network connection, "
                    + "and that the server accepts connections on its port.";
        } else if (original.startsWith("No suitable driver")) {
            hint = "DB_URL is not a valid JDBC URL (expected jdbc:postgresql://host/database).";
        } else {
            hint = "Could not connect to the database.";
        }
        return hint + " (" + original + ")";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static void initTables(Connection c) throws SQLException {
        // Live DB schema (introspected) uses dob/created/updated and includes default_clinic_id + accessibility_settings.
        // Also, some clinic_id columns are not enforced as FKs in the live DB, so we keep them as plain INTEGER.
        String User = """
            CREATE TABLE IF NOT EXISTS "User" (
                user_id INTEGER PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR(50),
                email VARCHAR(255),
                password_hash VARCHAR(255),
                phone_number VARCHAR(20),
                dob DATE,
                gender VARCHAR(50),
                sexuality VARCHAR(50),
                disabilities VARCHAR(255),
                email_verified BOOLEAN,
                verification_token VARCHAR(255),
                v_token_expiry TIMESTAMP,
                default_reminder_config TEXT,
                booking_comm_prefs TEXT,
                accessibility_settings TEXT,
                default_clinic_id INTEGER REFERENCES "Clinic"(clinic_id),
                created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String Session = """
            CREATE TABLE IF NOT EXISTS "Session" (
                session_id VARCHAR(255) PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP
            )
            """;

        String Medicine = """
            CREATE TABLE IF NOT EXISTS "Medicine" (
                medicine_id SERIAL PRIMARY KEY,
                name VARCHAR(50)
            )
            """;

        String Clinic = """
            CREATE TABLE IF NOT EXISTS "Clinic" (
                clinic_id SERIAL PRIMARY KEY,
                name VARCHAR(100),
                address VARCHAR(255),
                postcode VARCHAR(20),
                phone VARCHAR(20),
                rating DECIMAL(2,1),
                photo_url VARCHAR(255),
                opening_hours VARCHAR(255),
                updated_at TIMESTAMP,
                latitude DOUBLE PRECISION,
                longitude DOUBLE PRECISION
            )
            """;

        String Service = """
            CREATE TABLE IF NOT EXISTS "Service" (
                service_id SERIAL PRIMARY KEY,
                name VARCHAR(50),
                description TEXT,
                duration_minutes INTEGER
            )
            """;

        String ClinicService = """
            CREATE TABLE IF NOT EXISTS "ClinicService" (
                clinic_id INTEGER,
                service_id INTEGER REFERENCES "Service"(service_id),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (clinic_id, service_id)
            )
            """;

        String Timeslot = """
            CREATE TABLE IF NOT EXISTS "Timeslot" (
                timeslot_id SERIAL PRIMARY KEY,
                date DATE,
                start_time TIME,
                end_time TIME,
                service_id INTEGER REFERENCES "Service"(service_id),
                clinic_id INTEGER,
                capacity INTEGER,
                booked_count INTEGER
            )
            """;

        String Appointment = """
            CREATE TABLE IF NOT EXISTS "Appointment" (
                app_id SERIAL PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                timeslot_id INTEGER REFERENCES "Timeslot"(timeslot_id),
                notes VARCHAR(255),
                status VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                canceled_at TIMESTAMP
            )
            """;

        String AppointmentReminder = """
            CREATE TABLE IF NOT EXISTS "AppointmentReminder" (
                ar_rem_id SERIAL PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                app_id INTEGER REFERENCES "Appointment"(app_id),
                notify BOOLEAN,
                custom_days TEXT,
                custom_message TEXT,
                notification_method VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String Test = """
            CREATE TABLE IF NOT EXISTS "Test" (
                test_id SERIAL PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                clinic_id INTEGER,
                test_type VARCHAR(50),
                test_date DATE,
                clinician_notes TEXT,
                treatment_instructions TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String TestResult = """
            CREATE TABLE IF NOT EXISTS "TestResult" (
                result_id SERIAL PRIMARY KEY,
                test_id INTEGER REFERENCES "Test"(test_id),
                condition VARCHAR(50),
                result VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String Prescription = """
            CREATE TABLE IF NOT EXISTS "Prescription" (
                prescription_id SERIAL PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                clinic_id INTEGER,
                medicine_id INTEGER REFERENCES "Medicine"(medicine_id),
                test_id INTEGER REFERENCES "Test"(test_id),
                dosage VARCHAR(50),
                doses_per_day INTEGER,
                days_between_doses INTEGER,
                start_date DATE,
                end_date DATE,
                instructions TEXT,
                actively_taking BOOLEAN,
                prescribing_clinician_id INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String PrescriptionReminder = """
            CREATE TABLE IF NOT EXISTS "PrescriptionReminder" (
                pr_rem_id SERIAL PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                prescription_id INTEGER REFERENCES "Prescription"(prescription_id),
                notify BOOLEAN,
                interval_between_doses INTEGER,
                custom_message TEXT,
                notification_method VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String BookingReminder = """
            CREATE TABLE IF NOT EXISTS "BookingReminder" (
                br_rem_id SERIAL PRIMARY KEY,
                user_id INTEGER REFERENCES "User"(user_id),
                notify BOOLEAN,
                days_since_last INTEGER,
                custom_message TEXT,
                notification_method VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Statement s = c.createStatement()) {
            s.execute(Clinic);
            s.execute(Service);
            s.execute(Medicine);
            s.execute(User);
            s.execute(Session);
            s.execute(ClinicService);
            s.execute(Timeslot);
            s.execute(Appointment);
            s.execute(AppointmentReminder);
            s.execute(Test);
            s.execute(TestResult);
            s.execute(Prescription);
            s.execute(PrescriptionReminder);
            s.execute(BookingReminder);

            // Safety migrations for older DBs (idempotent).
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS default_reminder_config TEXT");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS booking_comm_prefs TEXT");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS accessibility_settings TEXT");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS default_clinic_id INTEGER");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS created TIMESTAMP");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS updated TIMESTAMP");
            // ClinicDAO reads this column; older/fresh schemas created above do not have it.
            s.execute("ALTER TABLE \"Clinic\" ADD COLUMN IF NOT EXISTS disability_accessibility TEXT");
        }
    }
}
