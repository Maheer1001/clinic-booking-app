package dao;

import app.DB;
import app.StaffSeed;
import model.AppointmentStatus;
import model.AuditEntry;
import model.AuditEntry.Action;
import model.PrescriptionDraft;
import model.StaffAccess;
import model.StaffAppointment;
import model.StaffRole;
import model.StaffTest;
import model.TestDraft;
import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.*;
import service.PasswordHasher;
import service.SecurityQuestionService;
import service.StaffAuthService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StaffDAO against an in-memory H2 copy of the real schema with two clinics: nothing from one
 * clinic is visible or writable from the other, and patients without an appointment there are hidden.
 */
class StaffDAOTest {

    private static final String URL = "jdbc:h2:mem:staff_dao_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final int C1 = 1, C2 = 2;
    private static final LocalDate DAY = LocalDate.of(2030, 3, 4);
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 3, 4, 12, 0);
    private static String savedUrl, savedUser, savedPass;

    private static int patientOne;   // appointment at clinic 1 only
    private static int patientTwo;   // appointment at clinic 2 only
    private static int patientBoth;  // appointments at both
    private static int clinician;    // clinic 1
    private final StaffDAO dao = new StaffDAO();

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
            s.execute("INSERT INTO \"Clinic\" (clinic_id, name) VALUES (1, 'One'), (2, 'Two')");
            s.execute("INSERT INTO \"Service\" (service_id, name, duration_minutes) VALUES (1, 'STI test', 30)");
            s.execute("INSERT INTO \"ClinicService\" (clinic_id, service_id) VALUES (1, 1), (2, 1)");
            s.execute("INSERT INTO \"Medicine\" (medicine_id, name) VALUES (1, 'Doxycycline')");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, start_time, end_time, service_id, clinic_id, capacity, booked_count) "
                    + "VALUES (100, DATE '2030-03-04', TIME '10:00:00', TIME '10:30:00', 1, 1, 3, 2)");
            s.execute("INSERT INTO \"Timeslot\" (timeslot_id, date, start_time, end_time, service_id, clinic_id, capacity, booked_count) "
                    + "VALUES (200, DATE '2030-03-04', TIME '10:00:00', TIME '10:30:00', 1, 2, 3, 2)");
            // seed codes now the clinics exist (initTables ran before them)
            assertEquals(2, StaffSeed.seedMissingStaffCodes(c));
            assertEquals(0, StaffSeed.seedMissingStaffCodes(c));
        }
        UserDAO users = new UserDAO();
        patientOne = newUser(users, "one@example.com", "Alex", "One");
        patientTwo = newUser(users, "two@example.com", "Alex", "Two");
        patientBoth = newUser(users, "both@example.com", "Alex", "Both");
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (1, " + patientOne + ", 100, 'CONFIRMED')");
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (2, " + patientTwo + ", 200, 'CONFIRMED')");
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (3, " + patientBoth + ", 100, 'CONFIRMED')");
            s.execute("INSERT INTO \"Appointment\" (app_id, user_id, timeslot_id, status) VALUES (4, " + patientBoth + ", 200, 'CONFIRMED')");
            s.execute("INSERT INTO \"BookingDetails\" (app_id, first_name, last_name, gender) VALUES (1, 'Sam', 'Booked', 'x')");
        }
        clinician = new StaffAccountDAO().createStaffUser("clinician@example.com", "h", StaffRole.CLINICIAN, C1, false);
    }

    private static int newUser(UserDAO users, String email, String first, String last) throws Exception {
        users.insertUser(email, "h");
        int id = users.getUserIdByEmail(email);
        users.updateName(id, first, last);
        return id;
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    private static AuditEntry audit(Action a, Integer patient) {
        return AuditEntry.of(clinician, C1, a, "x", null, patient);
    }

    private static int count(String sql) throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static TestDraft draft(String condition, TestResultStatus status) {
        return new TestDraft(null, "STI screen", DAY, null, null, List.of(new TestResult(condition, status)));
    }

    @Test
    void access_readsRoleAndClinics() throws Exception {
        StaffAccess a = dao.access(clinician).orElseThrow();
        assertEquals(StaffRole.CLINICIAN, a.role());
        assertEquals(List.of(C1), a.clinicIds());
        StaffAccess p = new UserDAO().getStaffAccess(patientOne).orElseThrow();
        assertEquals(StaffRole.PATIENT, p.role());
        assertTrue(p.clinicIds().isEmpty());
    }

    @Test
    void dayList_onlyThisClinic_withBookingDetailsWhenGiven() throws Exception {
        List<StaffAppointment> day = dao.dayList(C1, DAY);
        assertEquals(List.of(1, 3), day.stream().map(StaffAppointment::appId).toList());
        assertEquals("Sam Booked", day.get(0).patientName());
        assertNotNull(day.get(0).booking().details());
        assertEquals("Alex Both", day.get(1).patientName());
        assertNull(day.get(1).booking().details());
        assertTrue(dao.appointment(C1, 2).isEmpty());
        assertTrue(dao.appointment(C2, 2).isPresent());
    }

    @Test
    void patients_onlyVisibleWithAnAppointmentHere() throws Exception {
        assertTrue(dao.patientVisible(C1, patientOne));
        assertFalse(dao.patientVisible(C1, patientTwo));
        assertTrue(dao.patient(C1, patientTwo).isEmpty());
        assertTrue(dao.searchPatients(C1, "two", 50).isEmpty());
        assertEquals(List.of(patientOne, patientBoth).stream().sorted().toList(),
                dao.searchPatients(C1, "alex", 50).stream().map(p -> p.userId()).sorted().toList());
        // booking-details names are searchable, but only for this clinic's bookings
        assertEquals(1, dao.searchPatients(C1, "booked", 50).size());
        assertTrue(dao.searchPatients(C2, "booked", 50).isEmpty());
        // staff accounts are never listed as patients
        assertTrue(dao.searchPatients(C1, "clinician", 50).isEmpty());
        assertEquals(List.of(3), dao.patientAppointments(C1, patientBoth).stream().map(StaffAppointment::appId).toList());
        assertTrue(dao.patientAppointments(C1, patientTwo).isEmpty());
    }

    @Test
    void tests_andPrescriptions_doNotLeakAcrossClinics() throws Exception {
        int here = dao.createTest(C1, patientBoth, clinician, draft("HIV", TestResultStatus.NEGATIVE), NOW,
                audit(Action.CREATE_TEST, patientBoth));
        int there = dao.createTest(C2, patientBoth, clinician, draft("HIV", TestResultStatus.POSITIVE), NOW,
                audit(Action.CREATE_TEST, patientBoth));
        assertTrue(here > 0 && there > 0);
        assertEquals(0, dao.createTest(C1, patientTwo, clinician, draft("HIV", TestResultStatus.NEGATIVE), NOW,
                audit(Action.CREATE_TEST, patientTwo)), "patient without an appointment here");

        assertEquals(List.of(here), dao.patientTests(C1, patientBoth).stream().map(StaffTest::testId).toList());
        assertTrue(dao.test(C1, there).isEmpty());
        assertFalse(dao.updateTest(C1, there, draft("HIV", TestResultStatus.NEGATIVE), NOW, audit(Action.UPDATE_TEST, patientBoth)));
        assertFalse(dao.releaseTest(C1, there, NOW, audit(Action.RELEASE_TEST, patientBoth)));
        assertNull(dao.test(C2, there).orElseThrow().releasedAt());
        assertEquals(TestResultStatus.POSITIVE, dao.test(C2, there).orElseThrow().results().get(0).result());

        int rx = dao.prescribe(C1, patientBoth, clinician,
                new PrescriptionDraft(1, here, "100mg", 2, 0, DAY, DAY.plusDays(7), null), NOW,
                audit(Action.PRESCRIBE, patientBoth));
        assertTrue(rx > 0);
        assertEquals(0, dao.prescribe(C1, patientTwo, clinician,
                new PrescriptionDraft(1, null, "100mg", 2, 0, DAY, null, null), NOW, audit(Action.PRESCRIBE, patientTwo)));
        assertEquals(clinician, dao.prescription(C1, rx).orElseThrow().prescribingClinicianId());
        assertTrue(dao.prescription(C2, rx).isEmpty());
        assertTrue(dao.patientPrescriptions(C2, patientBoth).isEmpty());
        // shorten: never from another clinic, never later or the same day, then earlier works
        assertFalse(dao.shortenPrescription(C2, rx, DAY.plusDays(3), NOW, audit(Action.SHORTEN_PRESCRIPTION, patientBoth)));
        assertFalse(dao.shortenPrescription(C1, rx, DAY.plusDays(7), NOW, audit(Action.SHORTEN_PRESCRIPTION, patientBoth)));
        assertTrue(dao.shortenPrescription(C1, rx, DAY.plusDays(3), NOW, audit(Action.SHORTEN_PRESCRIPTION, patientBoth)));
        assertEquals(DAY.plusDays(3), dao.prescription(C1, rx).orElseThrow().endDate());
        assertTrue(dao.prescription(C1, rx).orElseThrow().active());
        assertFalse(dao.stopPrescription(C2, rx, DAY, NOW, audit(Action.STOP_PRESCRIPTION, patientBoth)));
        assertTrue(dao.stopPrescription(C1, rx, DAY, NOW, audit(Action.STOP_PRESCRIPTION, patientBoth)));
        assertFalse(dao.prescription(C1, rx).orElseThrow().active());
        assertEquals(DAY, dao.prescription(C1, rx).orElseThrow().endDate());
        assertFalse(dao.shortenPrescription(C1, rx, DAY.minusDays(1), NOW, audit(Action.SHORTEN_PRESCRIPTION, patientBoth)),
                "a stopped course can't be shortened");
    }

    @Test
    void updateTest_diffsResultsByCondition_andMovesUpdatedAtOnlyOnChange() throws Exception {
        TestDraft first = new TestDraft(1, "STI screen", DAY, null, null, List.of(
                new TestResult("Chlamydia", TestResultStatus.NEGATIVE), new TestResult("HIV", TestResultStatus.PENDING)));
        int id = dao.createTest(C1, patientOne, clinician, first, NOW, audit(Action.CREATE_TEST, patientOne));
        LocalDateTime later = NOW.plusHours(1);
        TestDraft second = new TestDraft(1, "STI screen", DAY, "notes", null, List.of(
                new TestResult("chlamydia", TestResultStatus.NEGATIVE), new TestResult("HIV", TestResultStatus.POSITIVE),
                new TestResult("Syphilis", TestResultStatus.NEGATIVE)));
        assertTrue(dao.updateTest(C1, id, second, later, audit(Action.UPDATE_TEST, patientOne)));
        List<StaffTest.Result> results = dao.test(C1, id).orElseThrow().results();
        assertEquals(3, results.size());
        assertEquals(NOW, results.get(0).updatedAt(), "unchanged result keeps its timestamp");
        assertEquals(later, results.get(1).updatedAt());
        assertEquals(TestResultStatus.POSITIVE, results.get(1).result());

        assertTrue(dao.updateTest(C1, id, draft("HIV", TestResultStatus.POSITIVE), later, audit(Action.UPDATE_TEST, patientOne)));
        assertEquals(1, dao.test(C1, id).orElseThrow().results().size(), "conditions no longer listed are removed");
    }

    @Test
    void appointmentWrites_scopedToClinic() throws Exception {
        assertFalse(dao.setOutcome(C1, 2, AppointmentStatus.COMPLETED, NOW, audit(Action.MARK_COMPLETED, patientTwo)));
        assertFalse(dao.setStaffNotes(C1, 2, "x", NOW, audit(Action.SAVE_STAFF_NOTES, patientTwo)));
        assertTrue(dao.setOutcome(C1, 1, AppointmentStatus.COMPLETED, NOW, audit(Action.MARK_COMPLETED, patientOne)));
        StaffAppointment a = dao.appointment(C1, 1).orElseThrow();
        assertEquals("COMPLETED", a.booking().status());
        assertEquals(NOW, a.attendedAt());
        assertTrue(dao.setStaffNotes(C1, 1, "Seen", NOW, audit(Action.SAVE_STAFF_NOTES, patientOne)));
        assertEquals("Seen", dao.appointment(C1, 1).orElseThrow().staffNotes());
        assertTrue(dao.setOutcome(C1, 1, AppointmentStatus.CONFIRMED, NOW, audit(Action.CLEAR_OUTCOME, patientOne)));
        assertNull(dao.appointment(C1, 1).orElseThrow().attendedAt());
    }

    @Test
    void timeslots_capacityNeverBelowBooked_andScoped() throws Exception {
        LocalTime ten = LocalTime.of(10, 0), half = LocalTime.of(10, 30);
        assertFalse(dao.updateTimeslot(C1, 100, DAY, ten, half, 1, audit(Action.UPDATE_TIMESLOT, null)));
        assertFalse(dao.updateTimeslot(C1, 200, DAY, ten, half, 5, audit(Action.UPDATE_TIMESLOT, null)));
        assertTrue(dao.updateTimeslot(C1, 100, DAY, ten, half, 2, audit(Action.UPDATE_TIMESLOT, null)));
        assertEquals(2, dao.timeslot(C1, 100).orElseThrow().capacity());
        assertEquals(3, dao.timeslot(C2, 200).orElseThrow().capacity());
        int created = dao.createTimeslot(C1, 1, DAY.plusDays(1), ten, half, 4, audit(Action.CREATE_TIMESLOT, null));
        assertEquals(0, dao.timeslot(C1, created).orElseThrow().bookedCount());
        assertTrue(dao.timeslot(C2, created).isEmpty());
        assertTrue(dao.clinicOffersService(C1, 1));
        assertFalse(dao.clinicOffersService(C1, 9));
    }

    @Test
    void staffAdmin_scopedToClinic() throws Exception {
        int other = new StaffAccountDAO().createStaffUser("other@example.com", "h", StaffRole.CLINICIAN, C2, false);
        assertEquals(List.of(clinician), dao.staff(C1).stream().map(m -> m.userId()).toList());
        assertFalse(dao.setRole(C1, other, StaffRole.CLINIC_ADMIN, audit(Action.PROMOTE_STAFF, null)));
        assertFalse(dao.setRole(C1, patientOne, StaffRole.CLINIC_ADMIN, audit(Action.PROMOTE_STAFF, null)),
                "never turns a patient into staff");
        assertFalse(dao.removeStaff(C1, other, audit(Action.REMOVE_STAFF, null)));
        assertTrue(dao.removeStaff(C2, other, AuditEntry.of(other, C2, Action.REMOVE_STAFF, "x", other, null)));
        assertTrue(dao.access(other).orElseThrow().clinicIds().isEmpty());

        dao.setStaffCodeHash(C2, PasswordHasher.hash("newcode"), clinician, NOW, audit(Action.ROTATE_STAFF_CODE, null));
        String c2Hash = new StaffAccountDAO().allStaffCodeHashes().stream()
                .filter(h -> h.clinicId() == C2).findFirst().orElseThrow().codeHash();
        assertTrue(PasswordHasher.verify("newcode", c2Hash));
        assertFalse(PasswordHasher.verify("clinic2", c2Hash));
    }

    @Test
    void writes_appendAuditRows_inTheSameTransaction() throws Exception {
        int before = count("SELECT COUNT(*) FROM \"AuditLog\"");
        assertTrue(dao.setStaffNotes(C1, 3, "n", NOW, audit(Action.SAVE_STAFF_NOTES, patientBoth)));
        assertFalse(dao.setStaffNotes(C1, 2, "n", NOW, audit(Action.SAVE_STAFF_NOTES, patientTwo)));
        assertEquals(before + 1, count("SELECT COUNT(*) FROM \"AuditLog\""), "a refused write leaves no audit row");
        dao.audit(List.of(audit(Action.VIEW_PATIENT, patientOne), audit(Action.VIEW_PATIENT, patientBoth)));
        assertEquals(before + 3, count("SELECT COUNT(*) FROM \"AuditLog\""));
    }

    @Test
    void demoCodes_onlyWithTheFlag_markedDemo_andNeverOverwriteARotatedCode() throws Exception {
        int clinic = 7;
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"Clinic\" (clinic_id, name) VALUES (7, 'Seven')");
            try {
                DB.setDemoStaffCodes(false);
                DB.initTables(c);
                assertTrue(dao.staffCodeIsDemo(clinic).isEmpty(), "no flag: no code");

                DB.setDemoStaffCodes(true);
                DB.initTables(c);
                assertEquals(Optional.of(true), dao.staffCodeIsDemo(clinic));
                assertTrue(PasswordHasher.verify("clinic7", codeHash(clinic)));

                dao.setStaffCodeHash(clinic, PasswordHasher.hash("rotated123"), clinician, NOW,
                        AuditEntry.of(clinician, clinic, Action.ROTATE_STAFF_CODE, "x", clinic, null));
                DB.initTables(c);
                assertEquals(Optional.of(false), dao.staffCodeIsDemo(clinic));
                assertTrue(PasswordHasher.verify("rotated123", codeHash(clinic)), "restart keeps the rotated code");
                assertFalse(PasswordHasher.verify("clinic7", codeHash(clinic)));
            } finally {
                DB.setDemoStaffCodes(null);
            }
        }
    }

    private static String codeHash(int clinicId) throws Exception {
        return new StaffAccountDAO().allStaffCodeHashes().stream()
                .filter(h -> h.clinicId() == clinicId).findFirst().orElseThrow().codeHash();
    }

    @Test
    void approval_pendingHasNoAccess_approveActivates_rejectDeletes_scopedToClinic() throws Exception {
        StaffAccountDAO accounts = new StaffAccountDAO();
        int p1 = accounts.createStaffUser("pending.one@example.com", "h", StaffRole.CLINICIAN, C1, false, true);
        int p2 = accounts.createStaffUser("pending.two@example.com", "h", StaffRole.CLINICIAN, C1, false, true);
        assertTrue(dao.access(p1).orElseThrow().clinicIds().isEmpty());
        assertTrue(dao.staff(C1).stream().noneMatch(m -> m.userId() == p1), "pending is not on the staff list");
        assertEquals(List.of(p1, p2), dao.pendingStaff(C1).stream().map(m -> m.userId()).sorted().toList());
        assertTrue(dao.pendingStaff(C2).isEmpty());
        assertFalse(dao.setRole(C1, p1, StaffRole.CLINIC_ADMIN, audit(Action.PROMOTE_STAFF, null)),
                "a pending member can't be promoted");
        assertFalse(dao.removeStaff(C1, p1, audit(Action.REMOVE_STAFF, null)), "remove is for active staff");

        assertFalse(dao.approveStaff(C2, p1, clinician, NOW, audit(Action.APPROVE_STAFF, null)), "other clinic");
        int audits = count("SELECT COUNT(*) FROM \"AuditLog\" WHERE action = 'APPROVE_STAFF'");
        assertTrue(dao.approveStaff(C1, p1, clinician, NOW, audit(Action.APPROVE_STAFF, null)));
        assertEquals(audits + 1, count("SELECT COUNT(*) FROM \"AuditLog\" WHERE action = 'APPROVE_STAFF'"));
        assertEquals(List.of(C1), dao.access(p1).orElseThrow().clinicIds());
        assertEquals(1, count("SELECT COUNT(*) FROM \"StaffClinic\" WHERE user_id = " + p1
                + " AND approved_by = " + clinician + " AND approved_at IS NOT NULL"));
        assertFalse(dao.approveStaff(C1, p1, clinician, NOW, audit(Action.APPROVE_STAFF, null)), "only once");
        assertFalse(dao.rejectStaff(C1, p1, audit(Action.REJECT_STAFF, null)), "reject never removes active staff");

        assertFalse(dao.rejectStaff(C2, p2, audit(Action.REJECT_STAFF, null)));
        assertTrue(dao.rejectStaff(C1, p2, audit(Action.REJECT_STAFF, null)));
        assertEquals(0, count("SELECT COUNT(*) FROM \"StaffClinic\" WHERE user_id = " + p2));
        assertEquals(1, count("SELECT COUNT(*) FROM \"AuditLog\" WHERE action = 'REJECT_STAFF'"));
        assertTrue(dao.pendingStaff(C1).isEmpty());
        assertTrue(dao.removeStaff(C1, p1, audit(Action.REMOVE_STAFF, null)));
    }

    @Test
    void migration_existingMembershipsBecomeActive_newRowsDefaultToPending_andRerunsSafely() throws Exception {
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            DB.applyStaffLoginMigration(c);
            DB.applyStaffLoginMigration(c);
            s.execute("INSERT INTO \"StaffClinic\" (user_id, clinic_id) VALUES (" + patientTwo + ", 9)");
        }
        assertEquals(1, count("SELECT COUNT(*) FROM \"StaffClinic\" WHERE user_id = " + patientTwo
                + " AND status = 'PENDING'"), "a row that doesn't say otherwise is never auto-approved");
        assertEquals(1, count("SELECT COUNT(*) FROM \"StaffClinic\" WHERE user_id = " + clinician
                + " AND status = 'ACTIVE'"));
        try (Connection c = DB.connect(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM \"StaffClinic\" WHERE user_id = " + patientTwo);
        }
    }

    @Test
    void seedClinicAdmin_refusesAnExistingAccount_andCreatesANewOne() throws Exception {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> StaffSeed.seedClinicAdmin(7, "one@example.com", "Password1"));
        assertTrue(e.getMessage().contains("already exists"));
        assertEquals(StaffRole.PATIENT, new UserDAO().getStaffAccess(patientOne).orElseThrow().role());

        int admin = StaffSeed.seedClinicAdmin(7, "first.admin@example.com", "Password1");
        StaffAccess a = new UserDAO().getStaffAccess(admin).orElseThrow();
        assertEquals(StaffRole.CLINIC_ADMIN, a.role());
        assertEquals(List.of(7), a.clinicIds());
        assertTrue(a.mustResetPassword());
    }

    @Test
    void staffSignUp_endToEnd_andDeletionRemovesMembership() throws Exception {
        StaffAuthService auth = new StaffAuthService(new StaffAccountDAO(), new SecurityQuestionService(),
                Clock.systemUTC());
        String q = model.SecurityQuestion.all().get(0).key();
        assertEquals(StaffAuthService.Status.INVALID_STAFF_CODE,
                auth.signUpStaff("new.staff@example.com", "Password1", true, "clinic3", q, "Rover").status());
        assertEquals(1, count("SELECT COUNT(*) FROM \"StaffCodeAttempt\""));
        StaffAuthService.StaffSignUpResult ok =
                auth.signUpStaff("new.staff@example.com", "Password1", true, " CLINIC1", q, "Rover");
        assertTrue(ok.success(), ok.message());
        assertEquals(0, count("SELECT COUNT(*) FROM \"StaffCodeAttempt\""));
        Optional<StaffAccess> a = new UserDAO().getStaffAccess(ok.userId());
        assertEquals(StaffRole.CLINICIAN, a.orElseThrow().role());
        assertEquals(List.of(), a.get().clinicIds(), "pending until the clinic admin approves");
        assertEquals(List.of(ok.userId()), dao.pendingStaff(C1).stream().map(m -> m.userId()).toList());
        assertTrue(dao.approveStaff(C1, ok.userId(), clinician, NOW, audit(Action.APPROVE_STAFF, null)));
        assertEquals(List.of(C1), new UserDAO().getStaffAccess(ok.userId()).orElseThrow().clinicIds());
        assertTrue(new SecurityQuestionService().hasSecurityQuestion(ok.userId()));

        new UserDAO().deleteUserById(ok.userId());
        assertEquals(0, count("SELECT COUNT(*) FROM \"StaffClinic\" WHERE user_id = " + ok.userId()));
        assertTrue(new UserDAO().getStaffAccess(ok.userId()).isEmpty());
    }
}
