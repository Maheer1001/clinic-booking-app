package app;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DB {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
    private static String url = dotenv.get("DB_URL");
    private static String username = dotenv.get("DB_USERNAME");
    private static String password = dotenv.get("DB_PASSWORD");

    static {
        // Validate database configuration on startup
        if (url == null || url.isEmpty()) {
            System.err.println("[DB] WARNING: DB_URL not configured in .env file. Database connections will fail.");
        }
        if (username == null || username.isEmpty()) {
            System.err.println("[DB] WARNING: DB_USERNAME not configured in .env file. Database connections will fail.");
        }
        if (password == null || password.isEmpty()) {
            System.err.println("[DB] WARNING: DB_PASSWORD not configured in .env file. Database connections will fail.");
        }
    }

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

    public static Connection connect() throws SQLException {
        if (url == null || url.isEmpty()) {
            throw new SQLException("Database URL is not configured. Please set DB_URL in your .env file.");
        }
        if (username == null || username.isEmpty()) {
            throw new SQLException("Database username is not configured. Please set DB_USERNAME in your .env file.");
        }
        // Allow null/empty password for in-memory H2 (tests); production .env should set DB_PASSWORD
        if (password == null) {
            throw new SQLException("Database password is not configured. Please set DB_PASSWORD in your .env file.");
        }
        
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            // Provide more helpful error message
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.toLowerCase().contains("connection")) {
                throw new SQLException(
                    "Failed to connect to database. Check:\n" +
                    "  - DB_URL, DB_USERNAME, and DB_PASSWORD in .env file\n" +
                    "  - Database server is accessible from your network\n" +
                    "  - Firewall allows connections to the database port\n" +
                    "Original error: " + errorMsg,
                    e
                );
            }
            throw e;
        }
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
        }
    }
}
