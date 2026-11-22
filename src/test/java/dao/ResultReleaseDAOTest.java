package dao;

import app.DB;
import model.AuditEntry;
import model.NewResults;
import model.StaffRole;
import model.TestDraft;
import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.*;
import service.ResultAlertService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Patient side of draft/release (H2): drafts are hidden, a release alerts once, a later
 * PENDING -> POSITIVE change alerts again, and the migration backfill runs only once.
 */
class ResultReleaseDAOTest {

    private static final String URL = "jdbc:h2:mem:result_release_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final LocalDate DAY = LocalDate.of(2030, 3, 4);
    private static final LocalDateTime T0 = LocalDateTime.of(2030, 3, 4, 9, 0);
    private static String savedUrl, savedUser, savedPass;
    private static int patient, clinician;

    private final StaffDAO staff = new StaffDAO();
    private final TestDAO tests = new TestDAO();
    private final UserDAO users = new UserDAO();
    private final ResultAlertService alerts = new ResultAlertService();

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl(URL);
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            DB.initTables(c);
            s.execute("INSERT INTO \"Service\" (service_id, name) VALUES (1, 'STI test')");
            s.execute("INSERT INTO \"Medicine\" (medicine_id, name) VALUES (1, 'Doxycycline')");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, service_id, clinic_id, capacity, booked_count) "
                    + "VALUES (1, DATE '2030-03-04', 1, 1, 3, 1)");
        }
        new UserDAO().insertUser("patient@example.com", "h");
        patient = new UserDAO().getUserIdByEmail("patient@example.com");
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (1, " + patient + ", 1, 'COMPLETED')");
        }
        clinician = new StaffAccountDAO().createStaffUser("c@example.com", "h", StaffRole.CLINICIAN, 1, false);
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @BeforeEach
    void clear() throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM \"Prescription\"");
            s.execute("DELETE FROM \"TestResult\"");
            s.execute("DELETE FROM \"Test\"");
            s.execute("UPDATE \"User\" SET last_results_seen_at = NULL");
        }
    }

    private AuditEntry audit(AuditEntry.Action a) {
        return AuditEntry.of(clinician, 1, a, AuditEntry.TEST, null, patient);
    }

    private int draftTest(TestResultStatus status, LocalDateTime at) throws Exception {
        return staff.createTest(1, patient, clinician, new TestDraft(1, "STI screen", DAY, null, null,
                List.of(new TestResult("HIV", status))), at, audit(AuditEntry.Action.CREATE_TEST));
    }

    @Test
    void draft_isHiddenFromThePatient_untilReleased() throws Exception {
        int id = draftTest(TestResultStatus.NEGATIVE, T0);
        staff.prescribe(1, patient, clinician, new model.PrescriptionDraft(1, id, "100mg", 1, 0, DAY, null, null),
                T0, audit(AuditEntry.Action.PRESCRIBE));
        assertTrue(tests.getTestsByUserId(patient).isEmpty());
        assertTrue(new PrescriptionDAO().getPrescriptionsByUserId(patient).isEmpty(),
                "a prescription from a draft test would give the result away");
        assertTrue(alerts.newResults(patient).isEmpty());

        assertTrue(staff.releaseTest(1, id, T0.plusMinutes(5), audit(AuditEntry.Action.RELEASE_TEST)));
        assertEquals(1, tests.getTestsByUserId(patient).size());
        assertEquals(1, new PrescriptionDAO().getPrescriptionsByUserId(patient).size());
    }

    @Test
    void release_alertsOnce_thenPendingToPositive_alertsAgain() throws Exception {
        int id = draftTest(TestResultStatus.PENDING, T0);
        staff.releaseTest(1, id, T0.plusMinutes(5), audit(AuditEntry.Action.RELEASE_TEST));

        NewResults first = alerts.newResults(patient);
        assertEquals(1, first.count());
        assertEquals(id, first.tests().get(0).testId());
        assertFalse(first.tests().get(0).resultUpdated());
        assertEquals(T0.plusMinutes(5), first.watermark());
        assertTrue(alerts.markSeen(patient, first.watermark()));
        assertEquals(T0.plusMinutes(5), users.getLastResultsSeenAt(patient));
        assertTrue(alerts.newResults(patient).isEmpty(), "seen results do not alert again");

        // saving with the same values changes nothing and does not alert
        staff.updateTest(1, id, new TestDraft(1, "STI screen", DAY, "note", null,
                List.of(new TestResult("HIV", TestResultStatus.PENDING))), T0.plusHours(1), audit(AuditEntry.Action.UPDATE_TEST));
        assertTrue(alerts.newResults(patient).isEmpty());

        staff.updateTest(1, id, new TestDraft(1, "STI screen", DAY, null, null,
                List.of(new TestResult("HIV", TestResultStatus.POSITIVE))), T0.plusHours(2), audit(AuditEntry.Action.UPDATE_TEST));
        NewResults again = alerts.newResults(patient);
        assertEquals(1, again.count());
        assertTrue(again.tests().get(0).resultUpdated());
        assertEquals(T0.plusHours(2), again.watermark());

        alerts.markSeen(patient, again.watermark());
        assertTrue(alerts.newResults(patient).isEmpty());
        // the watermark never moves back
        assertFalse(users.setLastResultsSeenAt(patient, T0));
        assertEquals(T0.plusHours(2), users.getLastResultsSeenAt(patient));
    }

    @Test
    void resultChangedBeforeRelease_onlyCountsAsTheRelease() throws Exception {
        int id = draftTest(TestResultStatus.PENDING, T0);
        staff.updateTest(1, id, new TestDraft(1, "STI screen", DAY, null, null,
                List.of(new TestResult("HIV", TestResultStatus.NEGATIVE))), T0.plusMinutes(1), audit(AuditEntry.Action.UPDATE_TEST));
        users.setLastResultsSeenAt(patient, T0.plusMinutes(2));
        assertTrue(alerts.newResults(patient).isEmpty(), "still a draft");
        staff.releaseTest(1, id, T0.plusMinutes(10), audit(AuditEntry.Action.RELEASE_TEST));
        NewResults n = alerts.newResults(patient);
        assertEquals(1, n.count());
        assertFalse(n.tests().get(0).resultUpdated());
    }

    @Test
    void releaseBackfill_runsOnce_andMigrationIsIdempotent() throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            // a test written before draft/release existed
            s.execute("INSERT INTO \"Test\" (test_id, user_id, clinic_id, test_type, created_at) VALUES (900, "
                    + patient + ", 1, 'Old', TIMESTAMP '2025-01-01 10:00:00')");
            s.execute("DELETE FROM \"SchemaMigration\" WHERE name = '" + DB.RELEASE_BACKFILL + "'");
            DB.applyStaffLoginMigration(c);
            assertEquals("2025-01-01 10:00", scalar(s, "SELECT CAST(released_at AS VARCHAR(16)) FROM \"Test\" WHERE test_id = 900"));
            assertNotNull(users.getLastResultsSeenAt(patient), "existing users have seen the old results");
            assertTrue(alerts.newResults(patient).isEmpty());

            // a new draft is never released by running the migration again
            s.execute("INSERT INTO \"Test\" (test_id, user_id, clinic_id, test_type) VALUES (901, " + patient + ", 1, 'Draft')");
            DB.applyStaffLoginMigration(c);
            DB.initTables(c);
            assertNull(scalar(s, "SELECT released_at FROM \"Test\" WHERE test_id = 901"));
            assertEquals("1", scalar(s, "SELECT COUNT(*) FROM \"SchemaMigration\""));
        }
    }

    private static String scalar(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
