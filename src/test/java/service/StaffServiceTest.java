package service;

import model.AuditEntry;
import model.AuditEntry.Action;
import model.PrescriptionDraft;
import model.StaffAccess;
import model.StaffMember;
import model.StaffRole;
import model.StaffTest;
import model.StaffTimeslot;
import model.TestDraft;
import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** StaffService authorisation, clinic scoping, auditing and rules, against an in-memory repository. */
class StaffServiceTest {

    private static final int CLINIC_1 = 1, CLINIC_2 = 2;
    private static final int CLINICIAN = 10, ADMIN = 11, OTHER_CLINIC_STAFF = 12, PATIENT_ACCOUNT = 20;
    private static final int PATIENT_HERE = 30, PATIENT_ELSEWHERE = 31;
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 10);

    private FakeStaffRepository repo;
    private final List<Integer> emailed = new ArrayList<>();
    private StaffService service;

    @BeforeEach
    void setUp() {
        repo = new FakeStaffRepository();
        repo.addStaff(CLINICIAN, StaffRole.CLINICIAN, CLINIC_1);
        repo.addStaff(ADMIN, StaffRole.CLINIC_ADMIN, CLINIC_1);
        repo.addStaff(OTHER_CLINIC_STAFF, StaffRole.CLINICIAN, CLINIC_2);
        repo.access.put(PATIENT_ACCOUNT, new StaffAccess(PATIENT_ACCOUNT, StaffRole.PATIENT, List.of(), false));
        repo.addAppointment(1, CLINIC_1, PATIENT_HERE, TODAY, "CONFIRMED");
        repo.addAppointment(2, CLINIC_2, PATIENT_ELSEWHERE, TODAY, "CONFIRMED");
        repo.addAppointment(3, CLINIC_1, PATIENT_HERE, TODAY.plusDays(3), "CONFIRMED");
        repo.timeslots.put(500, new StaffTimeslot(500, CLINIC_1, 1, "STI test", TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 3, 2));
        Clock clock = Clock.fixed(Instant.parse("2030-01-10T10:00:00Z"), ZoneOffset.UTC);
        service = new StaffService(repo, (patient, date) -> emailed.add(patient), clock);
    }

    private static TestDraft draft(TestResultStatus... results) {
        List<TestResult> lines = new ArrayList<>();
        String[] conditions = {"Chlamydia", "Gonorrhoea", "Syphilis"};
        for (int i = 0; i < results.length; i++) lines.add(new TestResult(conditions[i], results[i]));
        return new TestDraft(1, "STI screen", TODAY, "notes", null, lines);
    }

    private Set<Action> auditedActions() {
        return repo.audits.stream().map(AuditEntry::action).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------ who may call

    @Test
    void patientAccount_isRejected_andNothingIsReadOrAudited() {
        assertEquals(StaffResult.Status.NOT_STAFF, service.dayList(PATIENT_ACCOUNT, CLINIC_1, TODAY).status());
        assertEquals(StaffResult.Status.NOT_STAFF, service.patientRecord(PATIENT_ACCOUNT, CLINIC_1, PATIENT_HERE).status());
        assertEquals(StaffResult.Status.NOT_STAFF, service.access(PATIENT_ACCOUNT).status());
        assertEquals(StaffResult.Status.NOT_STAFF, service.dayList(999, CLINIC_1, TODAY).status());
        assertTrue(repo.audits.isEmpty());
    }

    @Test
    void staffFromAnotherClinic_isRejected() {
        StaffResult<?> r = service.dayList(OTHER_CLINIC_STAFF, CLINIC_1, TODAY);
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, r.status());
        assertEquals("You don't have access to this clinic.", r.message());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC,
                service.markCompleted(OTHER_CLINIC_STAFF, CLINIC_1, 1).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC,
                service.createTest(OTHER_CLINIC_STAFF, CLINIC_1, PATIENT_HERE, draft(TestResultStatus.NEGATIVE)).status());
        assertTrue(repo.audits.isEmpty());
    }

    @Test
    void roleIsReReadOnEveryCall() {
        assertTrue(service.dayList(CLINICIAN, CLINIC_1, TODAY).ok());
        repo.access.put(CLINICIAN, new StaffAccess(CLINICIAN, StaffRole.CLINICIAN, List.of(), false)); // removed
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(CLINICIAN, CLINIC_1, TODAY).status());
    }

    @Test
    void adminOnlyActions_rejectedForClinicians() {
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.listStaff(CLINICIAN, CLINIC_1).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.removeStaff(CLINICIAN, CLINIC_1, ADMIN).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.promoteStaff(CLINICIAN, CLINIC_1, CLINICIAN).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.demoteStaff(CLINICIAN, CLINIC_1, ADMIN).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.rotateStaffCode(CLINICIAN, CLINIC_1).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.createTimeslot(CLINICIAN, CLINIC_1, 1, TODAY.plusDays(2),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 2).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.updateTimeslot(CLINICIAN, CLINIC_1, 500, TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 10).status());
        assertEquals("Only a clinic admin can do this.", service.listStaff(CLINICIAN, CLINIC_1).message());
        assertEquals(StaffRole.CLINIC_ADMIN, repo.access.get(ADMIN).role());
        assertTrue(repo.codeHashes.isEmpty());
        assertEquals(3, repo.timeslots.get(500).capacity());
        assertTrue(repo.audits.isEmpty());
        // clinicians can still view the slots
        assertTrue(service.timeslots(CLINICIAN, CLINIC_1, TODAY.plusDays(1)).ok());
    }

    // ------------------------------------------------------------ clinic scoping

    @Test
    void unrelatedPatient_isHidden() {
        assertEquals(StaffResult.Status.NOT_FOUND, service.patientRecord(CLINICIAN, CLINIC_1, PATIENT_ELSEWHERE).status());
        assertEquals(StaffResult.Status.NOT_FOUND, service.appointment(CLINICIAN, CLINIC_1, 2).status());
        assertEquals(StaffResult.Status.NOT_FOUND,
                service.createTest(CLINICIAN, CLINIC_1, PATIENT_ELSEWHERE, draft(TestResultStatus.NEGATIVE)).status());
        assertEquals(StaffResult.Status.NOT_FOUND, service.prescribe(CLINICIAN, CLINIC_1, PATIENT_ELSEWHERE,
                new PrescriptionDraft(1, null, "100mg", 2, 0, TODAY, null, null)).status());
        assertTrue(service.searchPatients(CLINICIAN, CLINIC_1, "patient").value().stream()
                .noneMatch(p -> p.userId() == PATIENT_ELSEWHERE));
        // the day list only has this clinic's appointment
        assertEquals(List.of(1), service.dayList(CLINICIAN, CLINIC_1, TODAY).value().stream()
                .map(a -> a.appId()).toList());
    }

    @Test
    void testAtAnotherClinic_cannotBeReadOrReleased() {
        int otherTest = service.createTest(OTHER_CLINIC_STAFF, CLINIC_2, PATIENT_ELSEWHERE,
                new TestDraft(null, "STI screen", TODAY, null, null,
                        List.of(new TestResult("HIV", TestResultStatus.NEGATIVE)))).value().testId();
        assertEquals(StaffResult.Status.NOT_FOUND, service.test(CLINICIAN, CLINIC_1, otherTest).status());
        assertEquals(StaffResult.Status.NOT_FOUND, service.releaseTest(CLINICIAN, CLINIC_1, otherTest, true).status());
        assertEquals(StaffResult.Status.NOT_FOUND,
                service.updateTest(CLINICIAN, CLINIC_1, otherTest, draft(TestResultStatus.NEGATIVE), true).status());
        assertNull(repo.tests.get(otherTest).releasedAt());
    }

    @Test
    void appointmentOfAnotherPatient_cannotBeLinkedToATest() {
        repo.addAppointment(4, CLINIC_1, 32, TODAY, "CONFIRMED");
        TestDraft linkedToOther = new TestDraft(4, "STI screen", TODAY, null, null, List.of());
        StaffResult<StaffTest> r = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE, linkedToOther);
        assertEquals(StaffResult.Status.INVALID, r.status());
        assertEquals("That appointment isn't this patient's booking at this clinic.", r.message());
    }

    // ------------------------------------------------------------ audit

    @Test
    void everyReadAndWrite_isAudited() {
        service.dayList(CLINICIAN, CLINIC_1, TODAY);
        service.appointment(CLINICIAN, CLINIC_1, 1);
        service.markCompleted(CLINICIAN, CLINIC_1, 1);
        service.markNoShow(CLINICIAN, CLINIC_1, 1);
        service.clearOutcome(CLINICIAN, CLINIC_1, 1);
        service.saveStaffNotes(CLINICIAN, CLINIC_1, 1, "Seen by nurse");
        service.searchPatients(CLINICIAN, CLINIC_1, "pat");
        service.patientRecord(CLINICIAN, CLINIC_1, PATIENT_HERE);
        int testId = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE, draft(TestResultStatus.PENDING)).value().testId();
        service.test(CLINICIAN, CLINIC_1, testId);
        service.updateTest(CLINICIAN, CLINIC_1, testId, draft(TestResultStatus.NEGATIVE), false);
        service.releaseTest(CLINICIAN, CLINIC_1, testId, false);
        int rx = service.prescribe(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new PrescriptionDraft(1, testId, "100mg", 2, 0, TODAY, TODAY.plusDays(7), "With food")).value().prescriptionId();
        service.stopPrescription(CLINICIAN, CLINIC_1, rx);
        service.promoteStaff(ADMIN, CLINIC_1, CLINICIAN);
        service.demoteStaff(ADMIN, CLINIC_1, CLINICIAN);
        service.rotateStaffCode(ADMIN, CLINIC_1);
        int slot = service.createTimeslot(ADMIN, CLINIC_1, 1, TODAY.plusDays(2), LocalTime.of(9, 0),
                LocalTime.of(9, 30), 2).value().timeslotId();
        service.updateTimeslot(ADMIN, CLINIC_1, slot, TODAY.plusDays(2), LocalTime.of(9, 0), LocalTime.of(9, 30), 4);
        repo.addPending(13, CLINIC_1);
        repo.addPending(14, CLINIC_1);
        service.approveStaff(ADMIN, CLINIC_1, 13);
        service.rejectStaff(ADMIN, CLINIC_1, 14);
        service.removeStaff(ADMIN, CLINIC_1, CLINICIAN);

        assertEquals(Set.of(Action.values()).stream()
                        .filter(a -> a != Action.STAFF_SIGN_UP && a != Action.STAFF_SEEDED)
                        .collect(Collectors.toSet()),
                auditedActions());
        // every audit row names the acting staff member and the clinic; patient rows name the patient
        for (AuditEntry e : repo.audits) {
            assertTrue(e.staffUserId() == CLINICIAN || e.staffUserId() == ADMIN);
            assertEquals(CLINIC_1, e.clinicId());
        }
        assertTrue(repo.audits.stream().filter(e -> e.action() == Action.CREATE_TEST)
                .allMatch(e -> e.entityId() == testId && e.patientUserId() == PATIENT_HERE));
    }

    @Test
    void auditFailure_returnsNoData() {
        repo.failAudit = true;
        StaffResult<?> r = service.patientRecord(CLINICIAN, CLINIC_1, PATIENT_HERE);
        assertEquals(StaffResult.Status.DB_ERROR, r.status());
        assertNull(r.value());
        assertNull(service.dayList(CLINICIAN, CLINIC_1, TODAY).value());
    }

    // ------------------------------------------------------------ appointments

    @Test
    void outcome_rules() {
        StaffResult<model.StaffAppointment> done = service.markCompleted(CLINICIAN, CLINIC_1, 1);
        assertTrue(done.ok());
        assertEquals("COMPLETED", done.value().booking().status());
        assertNotNull(done.value().attendedAt());
        StaffResult<model.StaffAppointment> noShow = service.markNoShow(CLINICIAN, CLINIC_1, 1);
        assertEquals("NO_SHOW", noShow.value().booking().status());
        assertNull(noShow.value().attendedAt());

        StaffResult<?> future = service.markCompleted(CLINICIAN, CLINIC_1, 3);
        assertEquals("You can record attendance from the day of the appointment.", future.message());

        repo.addAppointment(5, CLINIC_1, PATIENT_HERE, TODAY, "CANCELLED");
        assertEquals("This appointment was cancelled.", service.markNoShow(CLINICIAN, CLINIC_1, 5).message());
        assertEquals(StaffResult.Status.INVALID,
                service.saveStaffNotes(CLINICIAN, CLINIC_1, 1, "x".repeat(StaffService.MAX_NOTES + 1)).status());
    }

    // ------------------------------------------------------------ tests: draft and release

    @Test
    void createTest_isADraft() {
        StaffResult<StaffTest> r = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE, draft(TestResultStatus.NEGATIVE));
        assertTrue(r.ok());
        assertFalse(r.value().released());
        assertEquals(CLINICIAN, r.value().clinicianId());
        assertTrue(emailed.isEmpty());
    }

    @Test
    void releasingAPositive_needsConfirmation_thenEmailsDiscreetly() {
        int testId = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE,
                draft(TestResultStatus.NEGATIVE, TestResultStatus.POSITIVE)).value().testId();

        StaffResult<StaffService.ReleaseOutcome> first = service.releaseTest(CLINICIAN, CLINIC_1, testId, false);
        assertEquals(StaffResult.Status.NEEDS_CONFIRMATION, first.status());
        assertTrue(first.value().anyPositive());
        assertFalse(first.value().released());
        assertNull(repo.tests.get(testId).releasedAt());
        assertTrue(emailed.isEmpty());

        StaffResult<StaffService.ReleaseOutcome> confirmed = service.releaseTest(CLINICIAN, CLINIC_1, testId, true);
        assertTrue(confirmed.ok());
        assertTrue(confirmed.value().released());
        assertTrue(confirmed.value().anyPositive());
        assertNotNull(repo.tests.get(testId).releasedAt());
        assertEquals(List.of(PATIENT_HERE), emailed);

        assertEquals("This test has already been released.",
                service.releaseTest(CLINICIAN, CLINIC_1, testId, true).message());
    }

    @Test
    void releasingANegative_needsNoConfirmation() {
        int testId = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE, draft(TestResultStatus.NEGATIVE)).value().testId();
        StaffResult<StaffService.ReleaseOutcome> r = service.releaseTest(CLINICIAN, CLINIC_1, testId, false);
        assertTrue(r.ok());
        assertFalse(r.value().anyPositive());
    }

    @Test
    void releasingWithoutResults_isRefused() {
        int testId = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE, draft()).value().testId();
        assertEquals("Add at least one result before releasing.",
                service.releaseTest(CLINICIAN, CLINIC_1, testId, true).message());
    }

    @Test
    void pendingToPositive_onAReleasedTest_needsConfirmation_thenAlertsThePatient() {
        int testId = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE,
                draft(TestResultStatus.NEGATIVE, TestResultStatus.PENDING)).value().testId();
        service.releaseTest(CLINICIAN, CLINIC_1, testId, false);
        emailed.clear();

        TestDraft positive = draft(TestResultStatus.NEGATIVE, TestResultStatus.POSITIVE);
        StaffResult<StaffService.SaveTestOutcome> unconfirmed = service.updateTest(CLINICIAN, CLINIC_1, testId, positive, false);
        assertEquals(StaffResult.Status.NEEDS_CONFIRMATION, unconfirmed.status());
        assertEquals(TestResultStatus.PENDING, repo.tests.get(testId).results().get(1).result());

        StaffResult<StaffService.SaveTestOutcome> saved = service.updateTest(CLINICIAN, CLINIC_1, testId, positive, true);
        assertTrue(saved.ok());
        assertTrue(saved.value().patientAlerted());
        assertEquals(List.of(PATIENT_HERE), emailed);
    }

    @Test
    void editingADraft_neverAlerts() {
        int testId = service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE, draft(TestResultStatus.PENDING)).value().testId();
        StaffResult<StaffService.SaveTestOutcome> r = service.updateTest(CLINICIAN, CLINIC_1, testId,
                draft(TestResultStatus.POSITIVE), false);
        assertTrue(r.ok());
        assertFalse(r.value().patientAlerted());
        assertTrue(emailed.isEmpty());
    }

    @Test
    void testDraft_validation() {
        assertEquals("Enter the test type.", service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new TestDraft(null, " ", TODAY, null, null, List.of())).message());
        assertEquals("The test date can't be in the future.", service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new TestDraft(null, "STI", TODAY.plusDays(1), null, null, List.of())).message());
        assertEquals("Each condition can only be listed once.", service.createTest(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new TestDraft(null, "STI", TODAY, null, null, List.of(
                        new TestResult("HIV", TestResultStatus.NEGATIVE),
                        new TestResult("hiv ", TestResultStatus.POSITIVE)))).message());
    }

    // ------------------------------------------------------------ prescriptions

    @Test
    void prescribe_setsClinician_andStopEarlyEndsToday() {
        StaffResult<model.StaffPrescription> p = service.prescribe(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new PrescriptionDraft(1, null, " 100mg ", 2, 0, TODAY, TODAY.plusDays(7), " "));
        assertTrue(p.ok());
        assertEquals(CLINICIAN, p.value().prescribingClinicianId());
        assertEquals("100mg", p.value().dosage());
        assertNull(p.value().instructions());

        StaffResult<model.StaffPrescription> stopped = service.stopPrescription(CLINICIAN, CLINIC_1, p.value().prescriptionId());
        assertFalse(stopped.value().active());
        assertEquals(TODAY, stopped.value().endDate());
        assertEquals("This prescription has already been stopped.",
                service.stopPrescription(CLINICIAN, CLINIC_1, p.value().prescriptionId()).message());

        assertEquals("Choose a medicine from the list.", service.prescribe(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new PrescriptionDraft(99, null, "1", null, null, TODAY, null, null)).message());
        assertEquals("The end date can't be before the start date.", service.prescribe(CLINICIAN, CLINIC_1, PATIENT_HERE,
                new PrescriptionDraft(1, null, "1", null, null, TODAY, TODAY.minusDays(1), null)).message());
    }

    // ------------------------------------------------------------ clinic admin

    @Test
    void capacity_neverBelowBookedCount() {
        StaffResult<StaffTimeslot> low = service.updateTimeslot(ADMIN, CLINIC_1, 500, TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1);
        assertEquals(StaffResult.Status.INVALID, low.status());
        assertEquals("Capacity can't be lower than the 2 places already booked.", low.message());
        assertEquals(3, repo.timeslots.get(500).capacity());

        assertTrue(service.updateTimeslot(ADMIN, CLINIC_1, 500, TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 2).ok());
        assertEquals(2, repo.timeslots.get(500).capacity());

        // a booking lands between the read and the update: the SQL guard refuses it
        repo.bookedCountRace = 3;
        StaffResult<StaffTimeslot> raced = service.updateTimeslot(ADMIN, CLINIC_1, 500, TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 2);
        assertEquals("Capacity can't be lower than the 3 places already booked.", raced.message());
        assertEquals(2, repo.timeslots.get(500).capacity());
    }

    @Test
    void bookedSlot_cannotMove_andSlotRulesApply() {
        assertEquals("This slot has bookings, so only its capacity can change.", service.updateTimeslot(ADMIN, CLINIC_1,
                500, TODAY.plusDays(2), LocalTime.of(9, 0), LocalTime.of(9, 30), 5).message());
        assertEquals("The end time must be after the start time.", service.createTimeslot(ADMIN, CLINIC_1, 1,
                TODAY.plusDays(2), LocalTime.of(9, 0), LocalTime.of(9, 0), 2).message());
        assertEquals("This clinic doesn't offer that service.", service.createTimeslot(ADMIN, CLINIC_1, 7,
                TODAY.plusDays(2), LocalTime.of(9, 0), LocalTime.of(9, 30), 2).message());
        assertEquals("Choose today or a later date.", service.createTimeslot(ADMIN, CLINIC_1, 1,
                TODAY.minusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30), 2).message());
        // clinic 2's admin cannot touch clinic 1's slot
        repo.addStaff(13, StaffRole.CLINIC_ADMIN, CLINIC_2);
        assertEquals(StaffResult.Status.NOT_FOUND, service.updateTimeslot(13, CLINIC_2, 500, TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 5).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.updateTimeslot(13, CLINIC_1, 500, TODAY.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 5).status());
    }

    @Test
    void lastAdmin_cannotBeRemovedOrDemoted() {
        assertTrue(service.removeStaff(ADMIN, CLINIC_1, ADMIN).message().startsWith("A clinic needs at least one admin"));
        assertTrue(service.demoteStaff(ADMIN, CLINIC_1, ADMIN).message().startsWith("A clinic needs at least one admin"));
        assertEquals(StaffResult.Status.NOT_FOUND, service.removeStaff(ADMIN, CLINIC_1, OTHER_CLINIC_STAFF).status());
        assertTrue(service.promoteStaff(ADMIN, CLINIC_1, CLINICIAN).ok());
        assertTrue(service.demoteStaff(ADMIN, CLINIC_1, ADMIN).ok());
    }

    @Test
    void adminCanRemoveAClinician_whoThenLosesAccess() {
        assertTrue(service.dayList(CLINICIAN, CLINIC_1, TODAY).ok());
        StaffResult<?> removed = service.removeStaff(ADMIN, CLINIC_1, CLINICIAN);
        assertTrue(removed.ok());
        assertEquals("Removed from this clinic.", removed.message());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(CLINICIAN, CLINIC_1, TODAY).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.patientRecord(CLINICIAN, CLINIC_1, PATIENT_HERE).status());
    }

    @Test
    void staffCodeStatus_nagsForMissingOrDemoCode_andIsAdminOnly() {
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.staffCodeStatus(CLINICIAN, CLINIC_1).status());
        StaffResult<StaffService.StaffCodeStatus> none = service.staffCodeStatus(ADMIN, CLINIC_1);
        assertFalse(none.value().hasCode());
        assertNotNull(none.message());

        repo.codeHashes.put(CLINIC_1, PasswordHasher.hash("clinic1"));
        repo.demoCodes.add(CLINIC_1);
        StaffResult<StaffService.StaffCodeStatus> demo = service.staffCodeStatus(ADMIN, CLINIC_1);
        assertTrue(demo.value().demo());
        assertEquals("This clinic still uses the demo staff code. Rotate it before real use.", demo.message());

        service.rotateStaffCode(ADMIN, CLINIC_1);
        StaffResult<StaffService.StaffCodeStatus> rotated = service.staffCodeStatus(ADMIN, CLINIC_1);
        assertTrue(rotated.value().hasCode());
        assertFalse(rotated.value().demo());
        assertNull(rotated.message());
    }

    // ------------------------------------------------------------ approval of new staff

    private static final int PENDING_STAFF = 13;

    @Test
    void pendingStaff_seeNothingAtTheClinic() {
        repo.addPending(PENDING_STAFF, CLINIC_1);
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(PENDING_STAFF, CLINIC_1, TODAY).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.patientRecord(PENDING_STAFF, CLINIC_1, PATIENT_HERE).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.searchPatients(PENDING_STAFF, CLINIC_1, "Patient").status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.timeslots(PENDING_STAFF, CLINIC_1, TODAY).status());
        assertTrue(service.access(PENDING_STAFF).value().clinicIds().isEmpty());
        assertTrue(repo.audits.isEmpty(), "nothing read, nothing audited");
        assertTrue(service.listStaff(ADMIN, CLINIC_1).value().stream().noneMatch(m -> m.userId() == PENDING_STAFF),
                "not on the staff list until approved");
    }

    @Test
    void approvalActions_areAdminOnly_andScopedToTheClinic() {
        repo.addPending(PENDING_STAFF, CLINIC_1);
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.pendingStaff(CLINICIAN, CLINIC_1).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.approveStaff(CLINICIAN, CLINIC_1, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.rejectStaff(CLINICIAN, CLINIC_1, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.approveStaff(ADMIN, CLINIC_2, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.approveStaff(PENDING_STAFF, CLINIC_1, PENDING_STAFF).status(),
                "can't approve yourself");
        assertEquals(List.of(PENDING_STAFF),
                service.pendingStaff(ADMIN, CLINIC_1).value().stream().map(m -> m.userId()).toList());
        assertEquals(StaffResult.Status.NOT_FOUND, service.approveStaff(ADMIN, CLINIC_1, CLINICIAN).status(),
                "already-active staff are not pending");
    }

    @Test
    void approve_givesAccess_andIsAudited() {
        repo.addPending(PENDING_STAFF, CLINIC_1);
        StaffResult<List<model.StaffMember>> r = service.approveStaff(ADMIN, CLINIC_1, PENDING_STAFF);
        assertTrue(r.ok(), r.message());
        assertTrue(r.value().isEmpty());
        assertEquals("Approved. They can now use the staff area.", r.message());
        assertTrue(auditedActions().contains(Action.APPROVE_STAFF));
        assertTrue(service.dayList(PENDING_STAFF, CLINIC_1, TODAY).ok());
        assertTrue(service.listStaff(ADMIN, CLINIC_1).value().stream().anyMatch(m -> m.userId() == PENDING_STAFF));
        assertEquals(StaffResult.Status.NOT_FOUND, service.approveStaff(ADMIN, CLINIC_1, PENDING_STAFF).status());
    }

    @Test
    void reject_removesTheRequest_andIsAudited() {
        repo.addPending(PENDING_STAFF, CLINIC_1);
        StaffResult<List<model.StaffMember>> r = service.rejectStaff(ADMIN, CLINIC_1, PENDING_STAFF);
        assertTrue(r.ok(), r.message());
        assertTrue(r.value().isEmpty());
        assertTrue(auditedActions().contains(Action.REJECT_STAFF));
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(PENDING_STAFF, CLINIC_1, TODAY).status());
        assertEquals(StaffResult.Status.NOT_FOUND, service.rejectStaff(ADMIN, CLINIC_1, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.NOT_FOUND, service.rejectStaff(ADMIN, CLINIC_1, CLINICIAN).status(),
                "reject never removes active staff");
    }

    @Test
    void rotateStaffCode_returnsCodeOnce_andStoresOnlyAHash() {
        StaffResult<String> r = service.rotateStaffCode(ADMIN, CLINIC_1);
        assertTrue(r.ok());
        String code = r.value();
        assertEquals(StaffService.STAFF_CODE_LENGTH, code.length());
        String stored = repo.codeHashes.get(CLINIC_1);
        assertNotEquals(code, stored);
        assertTrue(PasswordHasher.verify(code, stored));
    }

    // ------------------------------------------------------------ roles are per clinic

    private static final int MIXED = 40;        // CLINIC_ADMIN at clinic 1, CLINICIAN at clinic 2
    private static final int CLINIC_2_ADMIN = 41;

    /** MIXED: admin at 1, clinician at 2; clinic 2 has its own admin and a pending sign-up. */
    private void mixedRoles() {
        repo.addMembership(MIXED, CLINIC_1, StaffRole.CLINIC_ADMIN);
        repo.addMembership(MIXED, CLINIC_2, StaffRole.CLINICIAN);
        repo.addStaff(CLINIC_2_ADMIN, StaffRole.CLINIC_ADMIN, CLINIC_2);
        repo.addPending(PENDING_STAFF, CLINIC_2);
    }

    @Test
    void adminAtOneClinic_cannotApproveDeactivateOrEditAtAnother_whereTheyAreAClinician() {
        mixedRoles();
        assertTrue(service.listStaff(MIXED, CLINIC_1).ok(), "admin at clinic 1");
        assertTrue(service.dayList(MIXED, CLINIC_2, TODAY).ok(), "still a clinician at clinic 2");

        assertEquals(StaffResult.Status.ADMIN_ONLY, service.pendingStaff(MIXED, CLINIC_2).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.approveStaff(MIXED, CLINIC_2, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.rejectStaff(MIXED, CLINIC_2, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.removeStaff(MIXED, CLINIC_2, OTHER_CLINIC_STAFF).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.promoteStaff(MIXED, CLINIC_2, OTHER_CLINIC_STAFF).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.promoteStaff(MIXED, CLINIC_2, MIXED).status(),
                "no promoting yourself with another clinic's admin role");
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.demoteStaff(MIXED, CLINIC_2, CLINIC_2_ADMIN).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.rotateStaffCode(MIXED, CLINIC_2).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.staffCodeStatus(MIXED, CLINIC_2).status());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.createTimeslot(MIXED, CLINIC_2, 1, TODAY.plusDays(2),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 2).status());

        assertEquals(List.of(PENDING_STAFF), repo.pendingStaff(CLINIC_2).stream().map(m -> m.userId()).toList());
        assertTrue(repo.staff(CLINIC_2).stream().anyMatch(m -> m.userId() == OTHER_CLINIC_STAFF));
        assertEquals(StaffRole.CLINIC_ADMIN, repo.access.get(CLINIC_2_ADMIN).roleAt(CLINIC_2));
        assertFalse(repo.codeHashes.containsKey(CLINIC_2));
        assertTrue(repo.audits.stream().noneMatch(a -> a.clinicId() == CLINIC_2 && a.action() != Action.VIEW_DAY_LIST),
                "nothing written at clinic 2");
    }

    @Test
    void adminAtOneClinic_hasNoAccessAtAClinicTheyDoNotBelongTo() {
        repo.addPending(PENDING_STAFF, CLINIC_2);
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.approveStaff(ADMIN, CLINIC_2, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.rejectStaff(ADMIN, CLINIC_2, PENDING_STAFF).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.removeStaff(ADMIN, CLINIC_2, OTHER_CLINIC_STAFF).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.promoteStaff(ADMIN, CLINIC_2, OTHER_CLINIC_STAFF).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.listStaff(ADMIN, CLINIC_2).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(ADMIN, CLINIC_2, TODAY).status());
        assertTrue(repo.audits.isEmpty());
    }

    @Test
    void clinicianAtOneClinic_hasNoAccessAtAnother() {
        assertTrue(service.dayList(CLINICIAN, CLINIC_1, TODAY).ok());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(CLINICIAN, CLINIC_2, TODAY).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.appointment(CLINICIAN, CLINIC_2, 2).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC,
                service.patientRecord(CLINICIAN, CLINIC_2, PATIENT_ELSEWHERE).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.searchPatients(CLINICIAN, CLINIC_2, "patient").status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.markCompleted(CLINICIAN, CLINIC_2, 2).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.timeslots(CLINICIAN, CLINIC_2, TODAY).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.listStaff(CLINICIAN, CLINIC_2).status());
    }

    @Test
    void promoteAtOneClinic_doesNotChangeTheRoleAtAnother() {
        mixedRoles();
        repo.addMembership(OTHER_CLINIC_STAFF, CLINIC_1, StaffRole.CLINICIAN); // clinician at 1 and 2
        StaffResult<List<StaffMember>> promoted = service.promoteStaff(MIXED, CLINIC_1, OTHER_CLINIC_STAFF);
        assertTrue(promoted.ok(), promoted.message());

        StaffAccess a = repo.access.get(OTHER_CLINIC_STAFF);
        assertEquals(StaffRole.CLINIC_ADMIN, a.roleAt(CLINIC_1));
        assertEquals(StaffRole.CLINICIAN, a.roleAt(CLINIC_2));
        assertTrue(service.listStaff(OTHER_CLINIC_STAFF, CLINIC_1).ok());
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.listStaff(OTHER_CLINIC_STAFF, CLINIC_2).status());
        assertEquals(StaffRole.CLINICIAN, service.listStaff(CLINIC_2_ADMIN, CLINIC_2).value().stream()
                .filter(m -> m.userId() == OTHER_CLINIC_STAFF).findFirst().orElseThrow().role(),
                "clinic 2's staff list still shows a clinician");
    }

    @Test
    void demoteAtOneClinic_keepsAdminAtAnother_andTheLastAdminRuleIsPerClinic() {
        mixedRoles();
        repo.addMembership(CLINIC_2_ADMIN, CLINIC_1, StaffRole.CLINIC_ADMIN); // admin at 1 and 2
        // clinic 1 has ADMIN, MIXED and CLINIC_2_ADMIN as admins: demote CLINIC_2_ADMIN there
        assertTrue(service.demoteStaff(ADMIN, CLINIC_1, CLINIC_2_ADMIN).ok());
        StaffAccess a = repo.access.get(CLINIC_2_ADMIN);
        assertEquals(StaffRole.CLINICIAN, a.roleAt(CLINIC_1));
        assertEquals(StaffRole.CLINIC_ADMIN, a.roleAt(CLINIC_2));
        assertTrue(service.rotateStaffCode(CLINIC_2_ADMIN, CLINIC_2).ok());

        // admins at clinic 1 do not count towards clinic 2: CLINIC_2_ADMIN is still its last admin
        assertTrue(service.demoteStaff(CLINIC_2_ADMIN, CLINIC_2, CLINIC_2_ADMIN).message()
                .startsWith("A clinic needs at least one admin"));
        assertTrue(service.removeStaff(CLINIC_2_ADMIN, CLINIC_2, CLINIC_2_ADMIN).message()
                .startsWith("A clinic needs at least one admin"));
        assertEquals(1, service.listStaff(CLINIC_2_ADMIN, CLINIC_2).value().stream()
                .filter(m -> m.role() == StaffRole.CLINIC_ADMIN).count());
    }

    @Test
    void approvalAtOneClinic_givesAccessThereOnly_asAClinician() {
        mixedRoles();
        assertTrue(service.approveStaff(CLINIC_2_ADMIN, CLINIC_2, PENDING_STAFF).ok());
        StaffAccess a = repo.access.get(PENDING_STAFF);
        assertEquals(List.of(CLINIC_2), a.clinicIds());
        assertEquals(StaffRole.CLINICIAN, a.roleAt(CLINIC_2));
        assertEquals(StaffResult.Status.ADMIN_ONLY, service.listStaff(PENDING_STAFF, CLINIC_2).status());
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(PENDING_STAFF, CLINIC_1, TODAY).status());
    }

    @Test
    void removeAtOneClinic_keepsTheOtherMembershipAndItsRole() {
        mixedRoles();
        assertTrue(service.removeStaff(CLINIC_2_ADMIN, CLINIC_2, MIXED).ok());
        StaffAccess a = repo.access.get(MIXED);
        assertEquals(List.of(CLINIC_1), a.clinicIds());
        assertTrue(a.isAdminAt(CLINIC_1));
        assertEquals(StaffResult.Status.NOT_AT_CLINIC, service.dayList(MIXED, CLINIC_2, TODAY).status());
        assertTrue(service.listStaff(MIXED, CLINIC_1).ok());
    }
}
