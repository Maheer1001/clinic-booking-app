package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database connection and table initialisation.
 * NOTE: This class duplicates app.DB; DAOs use app.DB as the primary connection. Consider consolidating.
 */
public class DB {

    private static final String URL = System.getenv("DB_URL");
    private static final String Username = System.getenv("DB_USERNAME");
    private static final String Password = System.getenv("DB_PASSWORD");

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, Username, Password);
    }

    public static void initTables(Connection c) throws SQLException {
        String User = """
            CREATE TABLE IF NOT EXISTS "User" (
                user_id INTEGER PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR (50),
                email VARCHAR (255),
                password_hash VARCHAR(255),
                phone_number VARCHAR(20),
                dob DATE,
                gender VARCHAR (50),
                sexuality VARCHAR (50),
                disabilities VARCHAR (255),
                email_verified BOOLEAN,
                verification_token VARCHAR (255),
                v_token_expiry TIMESTAMP,
                default_reminder_config TEXT,
                default_clinic_id INTEGER REFERENCES "Clinic"(clinic_id),
                booking_comm_prefs TEXT,
                accessibility_settings TEXT,
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
                custom_days INTEGER,
                custom_message TEXT,
                notification_method VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;



        try (Statement s = c.createStatement()) {
            s.execute(Clinic);
            System.out.println("Clinic table initialised.");

            s.execute(Service);
            System.out.println("Service table initialised.");

            s.execute(Medicine);
            System.out.println("Medicine table initialised.");

            s.execute(User);
            System.out.println("User table initialised.");

            s.execute(Session);
            System.out.println("Session table initialised.");

            s.execute(ClinicService);
            System.out.println("ClinicService table initialised.");

            s.execute(Timeslot);
            System.out.println("Timeslot table initialised.");

            s.execute(Appointment);
            System.out.println("Appointment table initialised.");

            s.execute(AppointmentReminder);
            System.out.println("AppointmentReminder table initialised.");

            s.execute(Test);
            System.out.println("Test table initialised.");

            s.execute(TestResult);
            System.out.println("TestResult table initialised.");

            s.execute(Prescription);
            System.out.println("Prescription table initialised.");

            s.execute(PrescriptionReminder);
            System.out.println("PrescriptionReminder table initialised.");

            s.execute(BookingReminder);
            System.out.println("BookingReminder table initialised.");

            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS default_reminder_config TEXT");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS booking_comm_prefs TEXT");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS accessibility_settings TEXT");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS default_clinic_id INTEGER");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS created TIMESTAMP");
            s.execute("ALTER TABLE \"User\" ADD COLUMN IF NOT EXISTS updated TIMESTAMP");
        }
    }

}

