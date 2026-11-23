package setup;

import app.DB;
import app.StaffSeed;
import dao.AppointmentDAO;
import dao.BookingDetailsDAO;
import dao.ClinicDAO;
import dao.ReminderDAO;
import dao.ServiceDAO;
import dao.StaffDAO;
import dao.UserDAO;
import io.github.cdimascio.dotenv.Dotenv;
import model.AppointmentReminder;
import model.AuditEntry;
import model.AvailableTimeslot;
import model.Book;
import model.BookingDetails;
import model.BookingReminder;
import model.Clinic;
import model.Item;
import model.SecurityQuestion;
import model.Service;
import model.StaffAccess;
import model.StaffMember;
import model.StaffRole;
import model.UpcomingAppointmentDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import service.AuthService;
import service.BookingService;
import service.PasswordHasher;
import service.SecurityQuestionService;
import service.StaffAuthService;
import service.email.BookingConfirmationEmail;
import service.email.EmailSender;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Is my setup hooked up?" Run with {@code mvn test -Psetup-check} after filling in {@code .env}.
 *
 * <p>The steps run in order and each one says what to fix when it fails; a step whose prerequisite
 * failed is skipped rather than failing with a confusing error. Everything the check creates (users,
 * a clinic, a service, slots, bookings, reminders, staff memberships, audit rows) is named
 * {@code setup-check...} and removed again in the last step, so it is safe to run on a fresh database
 * and on one that is already in use, as often as you like.
 *
 * <ol>
 *   <li>.env is found and has the database keys</li>
 *   <li>the database is reachable with those details</li>
 *   <li>DB.initTables creates the schema on an empty database and upgrades this one</li>
 *   <li>sign-up, booking, reminders and staff approval round-trip through the real DAOs</li>
 *   <li>SMTP: login check (no email sent); a real send only if SETUP_CHECK_EMAIL_TO is set</li>
 *   <li>cleanup: nothing the check created is left behind</li>
 * </ol>
 */
@Tag("db")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(1) // first of the db test classes (ClassOrderer.OrderAnnotation is set in the Maven profiles)
@DisplayName("Setup check")
class SetupCheckTest {

    /** Keys the app cannot start without. */
    static final List<String> REQUIRED_KEYS = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD");

    /** Every table DB.initTables is expected to create. */
    static final List<String> EXPECTED_TABLES = List.of(
            "User", "Session", "Medicine", "Clinic", "Service", "ClinicService", "Timeslot", "Appointment",
            "AppointmentReminder", "Test", "TestResult", "Prescription", "PrescriptionReminder",
            "BookingReminder", "BookingDetails", "StaffClinic", "ClinicStaffCode", "StaffCodeAttempt",
            "AuditLog", "SchemaMigration");

    /** Everything the check creates is recognisable by these, so a crashed run is cleaned up next time. */
    static final String EMAIL_PREFIX = "setup-check.";
    static final String EMAIL_DOMAIN = "@example.com";
    static final String CLINIC_PREFIX = "Setup check clinic ";
    static final String SERVICE_PREFIX = "Setup check service ";

    static final String PASSWORD = "SetupCheck-Passw0rd!";
    static final String SECURITY_ANSWER = "setup check answer";

    private final String nonce = UUID.randomUUID().toString().substring(0, 8);

    private boolean envOk, dbOk, schemaOk;
    private int clinicId, serviceId;
    private Integer patientId;
    private Integer bookedAppId;

    private static Dotenv dotenv() {
        // Same lookup as app.DB: process environment first, then .env in the working directory.
        return Dotenv.configure().ignoreIfMissing().load();
    }

    private static void ok(String message) {
        System.out.println("[setup-check] OK   " + message);
    }

    private static void note(String message) {
        System.out.println("[setup-check] NOTE " + message);
    }

    /** Skips the step with a reason (printed too: Maven's console output hides skip reasons). */
    private static void skip(String reason) {
        System.out.println("[setup-check] SKIP " + reason);
        Assumptions.abort(reason);
    }

    /** Skips the step when an earlier step it depends on did not pass. */
    private static void requireStep(boolean passed, String reason) {
        if (!passed) skip(reason);
    }

    // ------------------------------------------------------------------ 1. .env

    @Test
    @Order(1)
    @DisplayName("1. .env is found and has DB_URL, DB_USERNAME and DB_PASSWORD")
    void step1_envFileHasRequiredKeys() throws IOException {
        Path env = Path.of(".env").toAbsolutePath();
        Dotenv dotenv = dotenv();
        Set<String> inFile = keysIn(env);

        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            // DB_PASSWORD may be empty (e.g. a local trust login); it just has to be present.
            String value = dotenv.get(key);
            boolean present = key.equals("DB_PASSWORD") ? value != null : value != null && !value.isBlank();
            if (!present) missing.add(key);
        }
        if (!Files.exists(env) && !missing.isEmpty()) {
            fail("No .env file in " + env.getParent() + " and " + String.join(", ", missing)
                    + " are not set in the environment either. Copy .env.example to .env and fill in "
                    + "your database details (README -> Getting started, docs/SETUP.md).");
        }
        assertTrue(missing.isEmpty(), "Missing or empty in .env: " + String.join(", ", missing)
                + ". Every key is listed with an example in .env.example.");

        String url = dotenv.get("DB_URL").strip();
        assertTrue(url.startsWith("jdbc:postgresql://"), "DB_URL must be a JDBC URL that starts with "
                + "jdbc:postgresql:// (e.g. jdbc:postgresql://localhost:5432/clinic_booking). "
                + "A Neon/Heroku style postgres://user:pass@host/db URL has to be rewritten into that form, "
                + "with the user and password moved to DB_USERNAME and DB_PASSWORD.");

        if (Files.exists(env)) {
            ok(".env found at " + env);
            Set<String> optional = new LinkedHashSet<>(keysIn(Path.of(".env.example")));
            optional.removeAll(inFile);
            optional.removeAll(REQUIRED_KEYS);
            optional.removeIf(k -> System.getenv(k) != null);
            if (!optional.isEmpty()) {
                note("not set (optional, see .env.example): " + String.join(", ", optional));
            }
        } else {
            ok("no .env file, using DB_URL, DB_USERNAME and DB_PASSWORD from the environment");
        }
        List<String> overridden = REQUIRED_KEYS.stream()
                .filter(k -> System.getenv(k) != null && inFile.contains(k)).toList();
        if (!overridden.isEmpty()) {
            note(String.join(", ", overridden) + " set in the environment too; the environment wins over .env");
        }
        envOk = true;
    }

    /** Key names (never values) defined in an env file. */
    private static Set<String> keysIn(Path file) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        if (!Files.exists(file)) return keys;
        for (String line : Files.readAllLines(file)) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
            keys.add(l.substring(0, l.indexOf('=')).strip());
        }
        return keys;
    }

    // ------------------------------------------------------------------ 2. reachable

    @Test
    @Order(2)
    @DisplayName("2. the database in DB_URL is reachable and accepts the login")
    void step2_databaseIsReachable() {
        requireStep(envOk, "step 1 (.env) failed");
        try (Connection c = DB.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database(), version()")) {
            assertTrue(rs.next());
            DatabaseMetaData meta = c.getMetaData();
            assertEquals("PostgreSQL", meta.getDatabaseProductName(),
                    "The app is built for PostgreSQL; DB_URL points at " + meta.getDatabaseProductName());
            ok("connected to database '" + rs.getString(1) + "' (PostgreSQL "
                    + meta.getDatabaseProductVersion() + ")");
        } catch (SQLException e) {
            // DB.connect already turns driver errors into "check X in .env" messages.
            fail("Could not connect: " + e.getMessage());
        }
        dbOk = true;
    }

    // ------------------------------------------------------------------ 3. schema

    @Test
    @Order(3)
    @DisplayName("3a. DB.initTables builds the full schema on an empty database (scratch schema, dropped after)")
    void step3a_initTablesOnEmptyDatabase() throws SQLException {
        requireStep(dbOk, "step 2 (database connection) failed");
        String scratch = "setup_check_" + nonce;
        try (Connection c = DB.connect()) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE SCHEMA " + scratch);
            } catch (SQLException e) {
                skip("this login may not create schemas (" + e.getMessage()
                        + "), so the empty-database check cannot run here. Step 3b still checks your schema.");
            }
            try {
                try (Statement s = c.createStatement()) {
                    s.execute("SET search_path TO " + scratch);
                }
                DB.initTables(c);
                DB.initTables(c); // a second start must not fail
                assertTablesExist(c, scratch);
                assertStaffClinicRoleColumn(c, scratch);
                assertTrue(uniqueEmailIndexExists(c, scratch),
                        "On an empty database DB.initTables should create the unique index "
                                + DB.USER_EMAIL_UNIQUE_INDEX + " on \"User\"(LOWER(email))");
                ok("an empty database gets all " + EXPECTED_TABLES.size()
                        + " tables, the per-clinic role column and the unique email index");
            } finally {
                try (Statement s = c.createStatement()) {
                    s.execute("SET search_path TO DEFAULT");
                    s.execute("DROP SCHEMA IF EXISTS " + scratch + " CASCADE");
                }
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("3b. DB.initTables creates or upgrades the schema of the database in DB_URL")
    void step3b_initTablesOnThisDatabase() throws SQLException {
        requireStep(dbOk, "step 2 (database connection) failed");
        try (Connection c = DB.connect()) {
            try {
                DB.initTables(c);
                DB.initTables(c); // idempotent: the app runs it on every start
            } catch (SQLException e) {
                fail("DB.initTables failed (SQLState " + e.getSQLState() + "): " + e.getMessage()
                        + ". The login needs permission to create and alter tables in this database.");
            }
            String schema = c.getSchema();
            assertTablesExist(c, schema);
            assertStaffClinicRoleColumn(c, schema);
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM information_schema.table_constraints"
                         + " WHERE LOWER(constraint_name) = 'staff_clinic_role_check'")) {
                rs.next();
                assertTrue(rs.getInt(1) > 0, "\"StaffClinic\".role should have the CHECK constraint "
                        + "staff_clinic_role_check (docs/migrations/2026-11-23-per-clinic-roles.sql)");
            }
            assertTrue(uniqueEmailIndexExists(c, schema), "The unique email index " + DB.USER_EMAIL_UNIQUE_INDEX
                    + " is missing. DB.initTables only adds it while \"User\" is empty, because an older database "
                    + "may hold duplicate emails: check for duplicates and apply "
                    + "docs/migrations/2026-11-23-unique-email.sql.");
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM information_schema.triggers"
                         + " WHERE trigger_name = 'audit_log_append_only'")) {
                rs.next();
                assertTrue(rs.getInt(1) > 0, "the append-only trigger on \"AuditLog\" is missing");
            }
            ok("schema '" + schema + "' is up to date (tables, per-clinic roles, unique email, audit trigger)");
        }
        schemaOk = true;
    }

    private static void assertTablesExist(Connection c, String schema) throws SQLException {
        Set<String> found = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) found.add(rs.getString(1));
            }
        }
        List<String> missing = EXPECTED_TABLES.stream().filter(t -> !found.contains(t)).toList();
        assertTrue(missing.isEmpty(), "Tables missing in schema " + schema + " after DB.initTables: " + missing);
    }

    private static void assertStaffClinicRoleColumn(Connection c, String schema) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT is_nullable, column_default FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'StaffClinic' AND column_name = 'role'
                """)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "\"StaffClinic\".role (per-clinic staff role) is missing in " + schema);
                assertEquals("NO", rs.getString(1), "\"StaffClinic\".role should be NOT NULL");
                assertTrue(String.valueOf(rs.getString(2)).contains("CLINICIAN"),
                        "\"StaffClinic\".role should default to CLINICIAN");
            }
        }
    }

    private static boolean uniqueEmailIndexExists(Connection c, String schema) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = ? AND tablename = 'User' AND indexname = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, DB.USER_EMAIL_UNIQUE_INDEX);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1).toUpperCase(Locale.ROOT).contains("UNIQUE");
            }
        }
    }

    // ------------------------------------------------------------------ 4. DAO round trips

    @Test
    @Order(5)
    @DisplayName("4a. clinics and services can be read (docs/sample-data.sql loaded?)")
    void step4a_sampleDataIsReadable() throws SQLException {
        requireStep(schemaOk, "step 3 (schema) failed");
        removeLeftovers(); // from an earlier run that was killed before its cleanup
        List<Clinic> clinics = new ClinicDAO().findAll().stream()
                .filter(c -> c.getName() == null || !c.getName().startsWith(CLINIC_PREFIX)).toList();
        if (clinics.isEmpty()) {
            skip("there are no clinics yet, so the booking screens will be empty. Load "
                    + "docs/sample-data.sql (psql ... -f docs/sample-data.sql); the rest of the check uses its "
                    + "own temporary clinic.");
        }
        ServiceDAO services = new ServiceDAO();
        long withServices = clinics.stream().filter(c -> !services.findByClinicId(c.getId()).isEmpty()).count();
        assertTrue(withServices > 0, clinics.size() + " clinic(s) found but none offers a service: add rows to "
                + "\"ClinicService\" (see docs/sample-data.sql), otherwise nothing can be booked");
        ok(clinics.size() + " clinic(s), " + withServices + " with services to book");
    }

    @Test
    @Order(6)
    @DisplayName("4b. a patient can sign up, sign in, and the email is unique ignoring case")
    void step4b_patientSignUp() throws SQLException {
        requireStep(schemaOk, "step 3 (schema) failed");
        String email = email("patient");
        AuthService auth = new AuthService();

        assertEquals(AuthService.SignUpResult.SUCCESS, auth.signUp(email, PASSWORD, true,
                SecurityQuestion.FIRST_JOB.key(), SECURITY_ANSWER), "sign-up through AuthService/UserDAO");
        patientId = new UserDAO().getUserIdByEmail(email);
        assertNotNull(patientId, "the new user should be found by email");
        assertTrue(new SecurityQuestionService().hasSecurityQuestion(patientId), "security question saved");

        assertEquals(AuthService.SignInResult.SUCCESS, auth.signIn(email, PASSWORD), "sign-in with the password");
        assertEquals(AuthService.SignInResult.WRONG_PASSWORD, auth.signIn(email, PASSWORD + "x"));
        assertEquals(AuthService.SignUpResult.EMAIL_ALREADY_USED,
                auth.signUp(email.toUpperCase(Locale.ROOT), PASSWORD, true));

        // Straight past the service's emailExists check: the database index itself must refuse it.
        SQLException duplicate = assertThrows(SQLException.class,
                () -> insertRawUser(email.toUpperCase(Locale.ROOT)),
                "a second account with the same email (different case) must be refused by "
                        + DB.USER_EMAIL_UNIQUE_INDEX);
        assertTrue(DB.isDuplicateEmail(duplicate), "refused, but not by the unique email index: "
                + duplicate.getMessage());
        ok("patient sign-up, sign-in and the unique email index work");
    }

    private static void insertRawUser(String email) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO "User" (user_id, email, password_hash, email_verified)
                     SELECT COALESCE(MAX(user_id), 0) + 1, ?, 'x', false FROM "User"
                     """)) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
    }

    @Test
    @Order(7)
    @DisplayName("4c. the patient can book a slot at a clinic (temporary clinic and service)")
    void step4c_booking() throws SQLException {
        requireStep(patientId != null, "step 4b (sign-up) failed");
        createClinicAndService();
        BookingService booking = new BookingService();

        Clinic clinic = booking.findAllClinics().stream().filter(c -> c.getId() == clinicId).findFirst()
                .orElseThrow(() -> new AssertionError("ClinicDAO.findAll does not return the new clinic"));
        Service service = booking.findServicesByClinicId(clinicId).stream()
                .filter(s -> s.getId() == serviceId).findFirst()
                .orElseThrow(() -> new AssertionError("ServiceDAO does not link the service to the clinic"));

        LocalDate day = LocalDate.now().plusDays(1);
        List<AvailableTimeslot> free = booking.getAvailableTimeslots(clinicId, serviceId, day);
        assertFalse(free.isEmpty(), "a clinic open every day should have free slots tomorrow");
        AvailableTimeslot slot = free.get(0);

        Book book = new Book();
        book.setClinic(clinic);
        book.setFirstName("Setup");
        book.setLastName("Check");
        book.setEmail(email("patient"));
        book.setReason("setup check booking");
        Item item = new Item(service);
        item.setDate(slot.date());
        item.setTime(slot.startTime());
        book.setItems(List.of(item));

        BookingService.BookingResult result = booking.placeBooking(book, patientId);
        assertEquals(1, result.booked().size(), "one appointment should be booked, unavailable: "
                + result.unavailable());
        bookedAppId = result.booked().get(0).appId();

        BookingDetails details = new BookingDetailsDAO().findByAppointmentId(bookedAppId)
                .orElseThrow(() -> new AssertionError("no \"BookingDetails\" row saved with the appointment"));
        assertEquals("Setup", details.firstName());

        List<UpcomingAppointmentDetails> upcoming = new AppointmentDAO().getUpcomingAppointmentDetailsByUserId(patientId);
        assertTrue(upcoming.stream().anyMatch(a -> a.appId() == bookedAppId && clinic.getName().equals(a.clinicName())),
                "the booking should be listed in the patient's upcoming appointments");
        assertTrue(booking.getAvailableTimeslots(clinicId, serviceId, day).stream()
                        .noneMatch(s -> s.startTime().equals(slot.startTime())),
                "a slot with capacity 1 should no longer be offered once booked");
        ok("booked " + service.getName() + " on " + slot.date() + " " + slot.startTime()
                + " (Timeslot, Appointment, BookingDetails)");
    }

    @Test
    @Order(8)
    @DisplayName("4d. reminders are saved for the booking, and cancelling frees the slot")
    void step4d_remindersAndCancel() {
        requireStep(bookedAppId != null, "step 4c (booking) failed");
        ReminderDAO reminders = new ReminderDAO();

        assertTrue(reminders.saveAppointmentReminder(patientId, bookedAppId, true, "1,3", "setup check"),
                "saving an appointment reminder");
        assertTrue(reminders.saveAppointmentReminder(patientId, bookedAppId, true, "1", "setup check"),
                "saving it again replaces the row");
        List<AppointmentReminder> appReminders = reminders.getAppointmentRemindersByUserId(patientId);
        assertEquals(1, appReminders.size(), "one \"AppointmentReminder\" row per appointment");
        assertEquals("1", appReminders.get(0).getCustomDays());

        assertTrue(reminders.saveBookingReminder(patientId, true, 90, "setup check"), "saving a booking reminder");
        List<BookingReminder> bookingReminders = reminders.getBookingRemindersByUserId(patientId);
        assertEquals(1, bookingReminders.size());
        assertEquals(90, bookingReminders.get(0).getDaysSinceLast());

        AppointmentDAO appointments = new AppointmentDAO();
        assertTrue(appointments.cancelAppointment(bookedAppId), "cancelling the appointment");
        assertTrue(appointments.getUpcomingAppointmentDetailsByUserId(patientId).stream()
                .noneMatch(a -> a.appId() == bookedAppId), "a cancelled appointment is no longer upcoming");
        ok("appointment and booking reminders saved; cancel works");
    }

    @Test
    @Order(9)
    @DisplayName("4e. staff sign up with a clinic code, wait for approval, and get their per-clinic role")
    void step4e_staffMembershipAndApproval() throws SQLException {
        requireStep(schemaOk, "step 3 (schema) failed");
        if (clinicId == 0) createClinicAndService();
        String code = "setup-check-" + nonce;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO \"ClinicStaffCode\" (clinic_id, code_hash, code_is_demo) VALUES (?, ?, FALSE)")) {
            ps.setInt(1, clinicId);
            ps.setString(2, PasswordHasher.hash(code));
            ps.executeUpdate();
        }

        int adminId = StaffSeed.seedClinicAdmin(clinicId, email("admin"), PASSWORD);
        StaffAccess admin = new UserDAO().getStaffAccess(adminId).orElseThrow();
        assertTrue(admin.isAdminAt(clinicId), "the seeded admin should be CLINIC_ADMIN at the clinic");
        assertTrue(admin.mustResetPassword(), "a seeded admin must choose a new password first");

        StaffAuthService.StaffSignUpResult signUp = new StaffAuthService().signUpStaff(email("clinician"), PASSWORD,
                true, code.toUpperCase(Locale.ROOT), SecurityQuestion.FIRST_JOB.key(), SECURITY_ANSWER);
        assertTrue(signUp.success(), "staff sign-up with the clinic code: " + signUp.message());
        assertEquals(clinicId, signUp.clinicId(), "the code should match the clinic it belongs to");
        int clinicianId = signUp.userId();

        StaffDAO staff = new StaffDAO();
        assertTrue(staff.pendingStaff(clinicId).stream().anyMatch(m -> m.userId() == clinicianId),
                "a new staff sign-up waits for approval (PENDING)");
        assertFalse(new UserDAO().getStaffAccess(clinicianId).orElseThrow().worksAt(clinicId),
                "a PENDING member must not have access yet");

        assertTrue(staff.approveStaff(clinicId, clinicianId, adminId, LocalDateTime.now(),
                AuditEntry.of(adminId, clinicId, AuditEntry.Action.APPROVE_STAFF, AuditEntry.STAFF, clinicianId, null)),
                "the clinic admin approves the sign-up");
        StaffMember member = staff.staff(clinicId).stream().filter(m -> m.userId() == clinicianId).findFirst()
                .orElseThrow(() -> new AssertionError("an approved member should be listed as ACTIVE staff"));
        assertEquals(StaffRole.CLINICIAN, member.role());
        assertEquals(StaffRole.CLINICIAN, new UserDAO().getStaffAccess(clinicianId).orElseThrow().roleAt(clinicId),
                "the role comes from the clinic membership");

        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            SQLException refused = assertThrows(SQLException.class,
                    () -> s.executeUpdate("DELETE FROM \"AuditLog\" WHERE clinic_id = " + clinicId),
                    "\"AuditLog\" should be append-only");
            assertTrue(refused.getMessage().contains("append-only"), refused.getMessage());
        }
        ok("staff code sign-up, PENDING -> ACTIVE approval, per-clinic role and append-only audit log work");
    }

    /** A clinic open every day, all day (so slots exist whenever this runs), offering one 30-minute service. */
    private void createClinicAndService() throws SQLException {
        try (Connection c = DB.connect()) {
            // Explicit ids (MAX + 1) so the clinic/service SERIAL sequences are left untouched.
            clinicId = nextId(c, "Clinic", "clinic_id");
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO "Clinic" (clinic_id, name, address, postcode, phone, opening_hours)
                    VALUES (?, ?, '1 Test Street', 'SO17 1BJ', '000', 'Monday-Sunday: 0:00-23:59')
                    """)) {
                ps.setInt(1, clinicId);
                ps.setString(2, CLINIC_PREFIX + nonce);
                ps.executeUpdate();
            }
            serviceId = nextId(c, "Service", "service_id");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO \"Service\" (service_id, name, description, duration_minutes) VALUES (?, ?, 'setup check', 30)")) {
                ps.setInt(1, serviceId);
                ps.setString(2, SERVICE_PREFIX + nonce);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO \"ClinicService\" (clinic_id, service_id) VALUES (?, ?)")) {
                ps.setInt(1, clinicId);
                ps.setInt(2, serviceId);
                ps.executeUpdate();
            }
        }
    }

    private static int nextId(Connection c, String table, String column) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM \"" + table + "\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String email(String who) {
        return EMAIL_PREFIX + who + "." + nonce + EMAIL_DOMAIN;
    }

    // ------------------------------------------------------------------ 5. SMTP

    @Test
    @Order(10)
    @DisplayName("5a. SMTP login works (connects and authenticates, sends nothing)")
    void step5a_smtpLogin() {
        requireStep(envOk, "step 1 (.env) failed");
        Dotenv env = dotenv();
        String host = blankToNull(env.get("SMTP_HOST"));
        if (host == null) {
            skip("SMTP_HOST is not set, so the app runs but sends no email "
                    + "(booking confirmations, reminders, password reset codes). See .env.example.");
        }
        String user = blankToNull(env.get("SMTP_USERNAME"));
        String password = blankToNull(env.get("SMTP_PASSWORD"));
        assertNotNull(user, "SMTP_HOST is set but SMTP_USERNAME is empty");
        assertNotNull(password, "SMTP_HOST is set but SMTP_PASSWORD is empty (Gmail: use an App Password)");
        assertNotNull(blankToNull(env.get("MAIL_FROM")), "MAIL_FROM is empty: it is the sender address of every email");
        int port = Integer.parseInt(blankToNull(env.get("SMTP_PORT")) == null ? "587" : env.get("SMTP_PORT").strip());

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        try (Transport transport = Session.getInstance(props).getTransport("smtp")) {
            transport.connect(host, port, user, password);
            assertTrue(transport.isConnected());
            ok("logged in to " + host + ":" + port + " (no email sent)");
        } catch (AuthenticationFailedException e) {
            fail("The SMTP server rejected the login. Check SMTP_USERNAME and SMTP_PASSWORD "
                    + "(Gmail needs an App Password, not your normal password).");
        } catch (MessagingException e) {
            fail("Could not reach " + host + ":" + port + ". Check SMTP_HOST, SMTP_PORT (587 STARTTLS or 465 SSL) "
                    + "and that your network allows outgoing SMTP. (" + e.getMessage() + ")");
        }
    }

    @Test
    @Order(11)
    @DisplayName("5b. a real email is sent through the app's EmailSender (only when SETUP_CHECK_EMAIL_TO is set)")
    void step5b_sendEmail() {
        String to = blankToNull(dotenv().get("SETUP_CHECK_EMAIL_TO"));
        if (to == null) {
            skip("set SETUP_CHECK_EMAIL_TO=you@example.com (environment or .env) to also "
                    + "send yourself one test email");
        }
        requireStep(blankToNull(dotenv().get("SMTP_HOST")) != null, "SMTP_HOST is not set");
        // mvn test sets email.disabled=true so nothing is ever sent by accident; lift it for this one send.
        String before = System.getProperty("email.disabled");
        System.setProperty("email.disabled", "false");
        try {
            boolean sent = new EmailSender().send(new BookingConfirmationEmail(to, "Setup check",
                    "SETUP-CHECK", "Setup check (not a real appointment)", LocalDateTime.now().plusDays(1),
                    "Setup check clinic", null, false, null));
            assertTrue(sent, "EmailSender could not send to " + to + "; the [Email] lines above say why");
            ok("sent a test booking confirmation to " + to);
        } finally {
            if (before == null) System.clearProperty("email.disabled");
            else System.setProperty("email.disabled", before);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // ------------------------------------------------------------------ 6. cleanup

    @Test
    @Order(12)
    @DisplayName("6. everything the check created is removed")
    void step6_cleanUp() throws SQLException {
        requireStep(dbOk, "step 2 (database connection) failed");
        List<String> notes = removeLeftovers();
        try (Connection c = DB.connect()) {
            assertEquals(0, count(c, "SELECT COUNT(*) FROM \"User\" WHERE email LIKE ?", EMAIL_PREFIX + "%" + EMAIL_DOMAIN),
                    "setup-check users left behind");
            assertEquals(0, count(c, "SELECT COUNT(*) FROM \"Clinic\" WHERE name LIKE ?", CLINIC_PREFIX + "%"),
                    "setup-check clinics left behind");
            assertEquals(0, count(c, "SELECT COUNT(*) FROM \"Service\" WHERE name LIKE ?", SERVICE_PREFIX + "%"),
                    "setup-check services left behind");
        }
        notes.forEach(SetupCheckTest::note);
        ok("cleaned up: no setup-check users, clinics, services, slots, bookings or reminders remain");
    }

    @AfterAll
    void cleanUpEvenIfAStepFailed() {
        if (!dbOk) return;
        try {
            removeLeftovers();
        } catch (SQLException e) {
            System.err.println("[setup-check] could not clean up: " + e.getMessage());
        }
    }

    private static int count(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Removes every row this check creates, from this run or an earlier one that was interrupted.
     * Idempotent. Returns notes about anything that could not be removed.
     */
    private static List<String> removeLeftovers() throws SQLException {
        List<String> notes = new ArrayList<>();
        UserDAO users = new UserDAO();
        List<Integer> userIds = ids("SELECT user_id FROM \"User\" WHERE email LIKE ?", EMAIL_PREFIX + "%" + EMAIL_DOMAIN);
        // Users first: deleteUserById removes their reminders, bookings, booking details and memberships.
        for (int id : userIds) users.deleteUserById(id);

        List<Integer> clinics = ids("SELECT clinic_id FROM \"Clinic\" WHERE name LIKE ?", CLINIC_PREFIX + "%");
        List<Integer> services = ids("SELECT service_id FROM \"Service\" WHERE name LIKE ?", SERVICE_PREFIX + "%");
        try (Connection c = DB.connect()) {
            c.setAutoCommit(false);
            try {
                for (int clinic : clinics) {
                    for (String table : List.of("Timeslot", "ClinicService", "StaffClinic", "ClinicStaffCode")) {
                        update(c, "DELETE FROM \"" + table + "\" WHERE clinic_id = ?", clinic);
                    }
                }
                for (int service : services) {
                    update(c, "DELETE FROM \"Timeslot\" WHERE service_id = ?", service);
                    update(c, "DELETE FROM \"ClinicService\" WHERE service_id = ?", service);
                    update(c, "DELETE FROM \"Service\" WHERE service_id = ?", service);
                }
                for (int clinic : clinics) update(c, "DELETE FROM \"Clinic\" WHERE clinic_id = ?", clinic);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }

            // "AuditLog" is append-only (a trigger refuses DELETE). Lift it for the check's own rows only,
            // inside one transaction, so the guard is back on whatever happens.
            List<Integer> auditClinics = new ArrayList<>(clinics);
            if (!userIds.isEmpty() || !auditClinics.isEmpty()) {
                try {
                    try (Statement s = c.createStatement()) {
                        s.execute("ALTER TABLE \"AuditLog\" DISABLE TRIGGER audit_log_append_only");
                    }
                    for (int clinic : auditClinics) update(c, "DELETE FROM \"AuditLog\" WHERE clinic_id = ?", clinic);
                    for (int id : userIds) {
                        update(c, "DELETE FROM \"AuditLog\" WHERE staff_user_id = ? OR patient_user_id = ?", id, id);
                    }
                    try (Statement s = c.createStatement()) {
                        s.execute("ALTER TABLE \"AuditLog\" ENABLE TRIGGER audit_log_append_only");
                    }
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    notes.add("audit rows for the check's clinic/users were kept (" + e.getMessage()
                            + "); they hold ids only and are harmless");
                }
            }
            c.setAutoCommit(true);
        }
        return notes;
    }

    private static List<Integer> ids(String sql, String param) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = DB.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    private static void update(Connection c, String sql, int... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setInt(i + 1, params[i]);
            ps.executeUpdate();
        }
    }
}
