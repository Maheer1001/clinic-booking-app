package service;

import dao.StaffDAO;
import dao.StaffRepository;
import model.AppointmentStatus;
import model.AuditEntry;
import model.AuditEntry.Action;
import model.Medicine;
import model.PrescriptionDraft;
import model.StaffAccess;
import model.StaffAppointment;
import model.StaffMember;
import model.StaffPatient;
import model.StaffPrescription;
import model.StaffRole;
import model.StaffTest;
import model.StaffTimeslot;
import model.TestDraft;
import model.TestResult;
import model.TestResultStatus;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single entry point for everything clinic staff do. No JavaFX: every method talks to the
 * database (and some send email), so call them off the JavaFX thread (e.g. {@code Background.run}).
 *
 * <p>Every method takes the acting staff member's user id and the clinic they are working in, and
 * re-reads that user's role and clinic membership from the database before doing anything: a patient
 * account gets NOT_STAFF, a clinic they don't work at NOT_AT_CLINIC, and admin-only actions ADMIN_ONLY
 * for a clinician. A membership still waiting for the clinic admin's approval (PENDING) does not count:
 * {@link StaffRepository#access} only lists ACTIVE clinics. Patients are only visible if they have an appointment at the clinic (others come
 * back NOT_FOUND, the same as a record that does not exist), and only that clinic's records are
 * returned. Every read of a patient record and every write appends to the AuditLog (ids only); if the
 * audit row cannot be written the data is not returned.
 *
 * <p>Timestamps written (attended_at, released_at, result updated_at) are UTC.
 */
public class StaffService {

    public static final int MAX_NOTES = 2000;
    public static final int MAX_SHORT_TEXT = 50;
    public static final int MIN_SEARCH = 2;
    public static final int SEARCH_LIMIT = 50;
    public static final int MAX_CAPACITY = 50;
    public static final int STAFF_CODE_LENGTH = 10;
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RNG = new SecureRandom();

    /** A patient's record at one clinic: only that clinic's appointments, tests (incl. drafts) and prescriptions. */
    public record PatientRecord(StaffPatient patient, List<StaffAppointment> appointments, List<StaffTest> tests,
                                List<StaffPrescription> prescriptions) {
        @Override
        public String toString() {
            return "PatientRecord[userId=" + patient.userId() + ", <contents hidden>]";
        }
    }

    /**
     * @param released    true once released (false when confirmation is still needed)
     * @param anyPositive at least one result is POSITIVE (the UI must have confirmed)
     * @param emailSent   the discreet "a result is available" email went out
     */
    public record ReleaseOutcome(int testId, boolean released, boolean anyPositive, boolean emailSent) {}

    /**
     * @param test           the test as saved
     * @param patientAlerted a released test had a result change to POSITIVE/NEGATIVE: the patient was
     *                       emailed (discreetly) and will see it as new in the app
     */
    public record SaveTestOutcome(StaffTest test, boolean patientAlerted) {}

    @FunctionalInterface
    private interface Work<T> {
        StaffResult<T> run(StaffAccess access) throws SQLException;
    }

    private final StaffRepository repo;
    private final ResultReleaseNotifier notifier;
    private final Clock clock;

    public StaffService() {
        this(new StaffDAO(), ResultReleaseNotifier.email(), Clock.systemDefaultZone());
    }

    public StaffService(StaffRepository repo, ResultReleaseNotifier notifier, Clock clock) {
        this.repo = repo;
        this.notifier = notifier;
        this.clock = clock;
    }

    // ================================================================ access

    /** The user's role and clinics (for a clinic switcher). NOT_STAFF for a patient. */
    public StaffResult<StaffAccess> access(int staffUserId) {
        try {
            Optional<StaffAccess> a = repo.access(staffUserId);
            if (a.isEmpty() || !a.get().isStaff()) return StaffResult.fail(StaffResult.Status.NOT_STAFF);
            return StaffResult.ok(a.get());
        } catch (SQLException e) {
            return dbError(e);
        }
    }

    // ================================================================ appointments

    /** Every appointment at the clinic on the date (all statuses), earliest first. Audited per appointment. */
    public StaffResult<List<StaffAppointment>> dayList(int staffUserId, int clinicId, LocalDate date) {
        if (date == null) return StaffResult.invalid("Choose a date.");
        return run(staffUserId, clinicId, false, a -> {
            List<StaffAppointment> list = repo.dayList(clinicId, date);
            repo.audit(list.stream().map(x -> AuditEntry.of(staffUserId, clinicId, Action.VIEW_DAY_LIST,
                    AuditEntry.APPOINTMENT, x.appId(), x.patientUserId())).toList());
            return StaffResult.ok(list);
        });
    }

    /** One appointment with its booking details and staff notes. */
    public StaffResult<StaffAppointment> appointment(int staffUserId, int clinicId, int appId) {
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffAppointment> appt = repo.appointment(clinicId, appId);
            if (appt.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            repo.audit(List.of(AuditEntry.of(staffUserId, clinicId, Action.VIEW_APPOINTMENT,
                    AuditEntry.APPOINTMENT, appId, appt.get().patientUserId())));
            return StaffResult.ok(appt.get());
        });
    }

    /** Attended: status COMPLETED, attended_at now. */
    public StaffResult<StaffAppointment> markCompleted(int staffUserId, int clinicId, int appId) {
        return setOutcome(staffUserId, clinicId, appId, AppointmentStatus.COMPLETED);
    }

    public StaffResult<StaffAppointment> markNoShow(int staffUserId, int clinicId, int appId) {
        return setOutcome(staffUserId, clinicId, appId, AppointmentStatus.NO_SHOW);
    }

    /** Undo a COMPLETED / NO_SHOW marked by mistake (back to CONFIRMED). */
    public StaffResult<StaffAppointment> clearOutcome(int staffUserId, int clinicId, int appId) {
        return setOutcome(staffUserId, clinicId, appId, AppointmentStatus.CONFIRMED);
    }

    private StaffResult<StaffAppointment> setOutcome(int staffUserId, int clinicId, int appId, AppointmentStatus to) {
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffAppointment> appt = repo.appointment(clinicId, appId);
            if (appt.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            if (appt.get().status() == AppointmentStatus.CANCELLED) {
                return StaffResult.invalid("This appointment was cancelled.");
            }
            LocalDate date = appt.get().booking().date();
            if (to != AppointmentStatus.CONFIRMED && date != null && date.isAfter(today())) {
                return StaffResult.invalid("You can record attendance from the day of the appointment.");
            }
            Action action = switch (to) {
                case COMPLETED -> Action.MARK_COMPLETED;
                case NO_SHOW -> Action.MARK_NO_SHOW;
                default -> Action.CLEAR_OUTCOME;
            };
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, action, AuditEntry.APPOINTMENT, appId,
                    appt.get().patientUserId());
            if (!repo.setOutcome(clinicId, appId, to, nowUtc(), audit)) {
                return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            }
            return StaffResult.ok(repo.appointment(clinicId, appId).orElse(appt.get()), switch (to) {
                case COMPLETED -> "Marked as attended.";
                case NO_SHOW -> "Marked as did not attend.";
                default -> "Attendance cleared.";
            });
        });
    }

    /** Staff-only notes on an appointment (never shown to the patient). Blank clears them. */
    public StaffResult<StaffAppointment> saveStaffNotes(int staffUserId, int clinicId, int appId, String notes) {
        String clean = blankToNull(notes);
        if (clean != null && clean.length() > MAX_NOTES) {
            return StaffResult.invalid("Notes can be up to " + MAX_NOTES + " characters.");
        }
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffAppointment> appt = repo.appointment(clinicId, appId);
            if (appt.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.SAVE_STAFF_NOTES, AuditEntry.APPOINTMENT,
                    appId, appt.get().patientUserId());
            if (!repo.setStaffNotes(clinicId, appId, clean, nowUtc(), audit)) {
                return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            }
            return StaffResult.ok(repo.appointment(clinicId, appId).orElse(appt.get()), "Notes saved.");
        });
    }

    // ================================================================ patients

    /** Patients with an appointment at this clinic whose name or email contains the query. */
    public StaffResult<List<StaffPatient>> searchPatients(int staffUserId, int clinicId, String query) {
        String q = query == null ? "" : query.strip();
        if (q.length() < MIN_SEARCH) {
            return StaffResult.invalid("Type at least " + MIN_SEARCH + " letters of a name or email.");
        }
        return run(staffUserId, clinicId, false, a -> {
            List<StaffPatient> found = repo.searchPatients(clinicId, q, SEARCH_LIMIT);
            repo.audit(found.stream().map(p -> AuditEntry.of(staffUserId, clinicId, Action.SEARCH_PATIENTS,
                    AuditEntry.PATIENT, p.userId(), p.userId())).toList());
            return StaffResult.ok(found);
        });
    }

    /** The patient's appointments, tests and prescriptions at this clinic only. */
    public StaffResult<PatientRecord> patientRecord(int staffUserId, int clinicId, int patientUserId) {
        return run(staffUserId, clinicId, false, a -> {
            if (!repo.patientVisible(clinicId, patientUserId)) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            Optional<StaffPatient> p = repo.patient(clinicId, patientUserId);
            if (p.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            PatientRecord record = new PatientRecord(p.get(),
                    repo.patientAppointments(clinicId, patientUserId),
                    repo.patientTests(clinicId, patientUserId),
                    repo.patientPrescriptions(clinicId, patientUserId));
            repo.audit(List.of(AuditEntry.of(staffUserId, clinicId, Action.VIEW_PATIENT, AuditEntry.PATIENT,
                    patientUserId, patientUserId)));
            return StaffResult.ok(record);
        });
    }

    // ================================================================ tests

    public StaffResult<StaffTest> test(int staffUserId, int clinicId, int testId) {
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffTest> t = visibleTest(clinicId, testId);
            if (t.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            repo.audit(List.of(AuditEntry.of(staffUserId, clinicId, Action.VIEW_TEST, AuditEntry.TEST, testId,
                    t.get().patientUserId())));
            return StaffResult.ok(t.get());
        });
    }

    /** Creates the test as a DRAFT (the patient sees nothing until {@link #releaseTest}). */
    public StaffResult<StaffTest> createTest(int staffUserId, int clinicId, int patientUserId, TestDraft draft) {
        StaffResult<StaffTest> invalid = validate(draft);
        if (invalid != null) return invalid;
        return run(staffUserId, clinicId, false, a -> {
            if (!repo.patientVisible(clinicId, patientUserId)) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            StaffResult<StaffTest> badApp = checkAppointment(clinicId, patientUserId, draft.appId());
            if (badApp != null) return badApp;
            int testId = repo.createTest(clinicId, patientUserId, staffUserId, clean(draft), nowUtc(),
                    AuditEntry.of(staffUserId, clinicId, Action.CREATE_TEST, AuditEntry.TEST, null, patientUserId));
            if (testId <= 0) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            return repo.test(clinicId, testId)
                    .map(t -> StaffResult.ok(t, "Saved as a draft. The patient can't see it until you release it."))
                    .orElseGet(() -> StaffResult.fail(StaffResult.Status.NOT_FOUND));
        });
    }

    /**
     * Saves changes to a draft or released test. On a released test, a result newly set to POSITIVE
     * needs {@code confirmPositive} (NEEDS_CONFIRMATION otherwise, nothing saved), and any result that
     * becomes POSITIVE/NEGATIVE alerts the patient (discreet email + in-app "new result").
     */
    public StaffResult<SaveTestOutcome> updateTest(int staffUserId, int clinicId, int testId, TestDraft draft,
                                                   boolean confirmPositive) {
        StaffResult<SaveTestOutcome> invalid = validate(draft);
        if (invalid != null) return invalid;
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffTest> existing = visibleTest(clinicId, testId);
            if (existing.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            StaffTest before = existing.get();
            StaffResult<SaveTestOutcome> badApp = checkAppointment(clinicId, before.patientUserId(), draft.appId());
            if (badApp != null) return badApp;

            Map<String, TestResultStatus> old = new HashMap<>();
            for (StaffTest.Result r : before.results()) old.put(key(r.condition()), r.result());
            boolean newPositive = false;
            boolean newFinal = false;
            for (TestResult r : draft.results()) {
                TestResultStatus prev = old.get(key(r.condition()));
                if (prev == r.result()) continue;
                if (r.result() == TestResultStatus.POSITIVE) newPositive = true;
                if (r.result() != TestResultStatus.PENDING) newFinal = true;
            }
            if (before.released() && newPositive && !confirmPositive) {
                return StaffResult.confirm(new SaveTestOutcome(before, false),
                        "This shows the patient a positive result straight away. Check it, then confirm to save.");
            }
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.UPDATE_TEST, AuditEntry.TEST, testId,
                    before.patientUserId());
            if (!repo.updateTest(clinicId, testId, clean(draft), nowUtc(), audit)) {
                return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            }
            StaffTest after = repo.test(clinicId, testId).orElse(before);
            boolean alerted = before.released() && newFinal;
            if (alerted) notifier.resultAvailable(before.patientUserId(), after.testDate());
            return StaffResult.ok(new SaveTestOutcome(after, alerted),
                    alerted ? "Saved. The patient has been told a result is ready." : "Saved.");
        });
    }

    /**
     * Releases a draft to the patient and sends the discreet email. If any result is POSITIVE and
     * {@code confirmPositive} is false, nothing is released: NEEDS_CONFIRMATION comes back with
     * {@code value().anyPositive()} true so the UI can confirm and call again with true.
     */
    public StaffResult<ReleaseOutcome> releaseTest(int staffUserId, int clinicId, int testId, boolean confirmPositive) {
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffTest> t = visibleTest(clinicId, testId);
            if (t.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            StaffTest test = t.get();
            if (test.released()) return StaffResult.invalid("This test has already been released.");
            if (test.results().isEmpty()) return StaffResult.invalid("Add at least one result before releasing.");
            boolean positive = test.anyPositive();
            if (positive && !confirmPositive) {
                return StaffResult.confirm(new ReleaseOutcome(testId, false, true, false),
                        "This test has a positive result. Check it, then confirm to release it to the patient.");
            }
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.RELEASE_TEST, AuditEntry.TEST, testId,
                    test.patientUserId());
            if (!repo.releaseTest(clinicId, testId, nowUtc(), audit)) {
                return StaffResult.invalid("This test has already been released.");
            }
            boolean sent = notifier.resultAvailable(test.patientUserId(), test.testDate());
            return StaffResult.ok(new ReleaseOutcome(testId, true, positive, sent),
                    sent ? "Released. The patient has been emailed that a result is ready."
                         : "Released. The patient will see it next time they open the app.");
        });
    }

    // ================================================================ prescriptions

    public StaffResult<List<Medicine>> medicines(int staffUserId, int clinicId) {
        return run(staffUserId, clinicId, false, a -> StaffResult.ok(repo.medicines()));
    }

    /** Prescribes from the Medicine list; sets prescribing_clinician_id to the acting user. */
    public StaffResult<StaffPrescription> prescribe(int staffUserId, int clinicId, int patientUserId,
                                                    PrescriptionDraft draft) {
        StaffResult<StaffPrescription> invalid = validate(draft);
        if (invalid != null) return invalid;
        return run(staffUserId, clinicId, false, a -> {
            if (!repo.patientVisible(clinicId, patientUserId)) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            if (repo.medicines().stream().noneMatch(m -> m.medicineId() == draft.medicineId())) {
                return StaffResult.invalid("Choose a medicine from the list.");
            }
            if (draft.testId() != null) {
                Optional<StaffTest> t = repo.test(clinicId, draft.testId());
                if (t.isEmpty() || t.get().patientUserId() != patientUserId) {
                    return StaffResult.invalid("That test isn't this patient's test at this clinic.");
                }
            }
            PrescriptionDraft d = new PrescriptionDraft(draft.medicineId(), draft.testId(), draft.dosage().strip(),
                    draft.dosesPerDay(), draft.daysBetweenDoses(), draft.startDate(), draft.endDate(),
                    blankToNull(draft.instructions()));
            int id = repo.prescribe(clinicId, patientUserId, staffUserId, d, nowUtc(),
                    AuditEntry.of(staffUserId, clinicId, Action.PRESCRIBE, AuditEntry.PRESCRIPTION, null, patientUserId));
            if (id <= 0) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            return repo.prescription(clinicId, id)
                    .map(p -> StaffResult.ok(p, "Prescription added."))
                    .orElseGet(() -> StaffResult.fail(StaffResult.Status.NOT_FOUND));
        });
    }

    /** Stops a course early: no longer taken, ends today (reminders stop at the patient's next login). */
    public StaffResult<StaffPrescription> stopPrescription(int staffUserId, int clinicId, int prescriptionId) {
        return run(staffUserId, clinicId, false, a -> {
            Optional<StaffPrescription> p = repo.prescription(clinicId, prescriptionId);
            if (p.isEmpty() || !repo.patientVisible(clinicId, p.get().patientUserId())) {
                return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            }
            if (!p.get().active()) return StaffResult.invalid("This prescription has already been stopped.");
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.STOP_PRESCRIPTION, AuditEntry.PRESCRIPTION,
                    prescriptionId, p.get().patientUserId());
            if (!repo.stopPrescription(clinicId, prescriptionId, today(), nowUtc(), audit)) {
                return StaffResult.invalid("This prescription has already been stopped.");
            }
            return StaffResult.ok(repo.prescription(clinicId, prescriptionId).orElse(p.get()), "Prescription stopped.");
        });
    }

    // ================================================================ clinic admin

    public StaffResult<List<StaffMember>> listStaff(int staffUserId, int clinicId) {
        return run(staffUserId, clinicId, true, a -> StaffResult.ok(repo.staff(clinicId)));
    }

    /**
     * People who signed up with this clinic's staff code and are waiting for approval. Until approved
     * they are not staff here: every other call treats them as NOT_AT_CLINIC.
     */
    public StaffResult<List<StaffMember>> pendingStaff(int staffUserId, int clinicId) {
        return run(staffUserId, clinicId, true, a -> StaffResult.ok(repo.pendingStaff(clinicId)));
    }

    /** Approves a pending sign-up: they can use the staff area at this clinic. Returns the pending list left. */
    public StaffResult<List<StaffMember>> approveStaff(int staffUserId, int clinicId, int targetUserId) {
        return run(staffUserId, clinicId, true, a -> {
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.APPROVE_STAFF, AuditEntry.STAFF,
                    targetUserId, null);
            if (!repo.approveStaff(clinicId, targetUserId, staffUserId, nowUtc(), audit)) {
                return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            }
            return StaffResult.ok(repo.pendingStaff(clinicId), "Approved. They can now use the staff area.");
        });
    }

    /**
     * Rejects a pending sign-up: the membership is removed (their account stays, with no clinic).
     * Returns the pending list left.
     */
    public StaffResult<List<StaffMember>> rejectStaff(int staffUserId, int clinicId, int targetUserId) {
        return run(staffUserId, clinicId, true, a -> {
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.REJECT_STAFF, AuditEntry.STAFF,
                    targetUserId, null);
            if (!repo.rejectStaff(clinicId, targetUserId, audit)) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            return StaffResult.ok(repo.pendingStaff(clinicId), "Request rejected.");
        });
    }

    /** Removes the person from this clinic (their account stays). A clinic always keeps one admin. */
    public StaffResult<List<StaffMember>> removeStaff(int staffUserId, int clinicId, int targetUserId) {
        return run(staffUserId, clinicId, true, a -> {
            List<StaffMember> staff = repo.staff(clinicId);
            Optional<StaffMember> target = staff.stream().filter(m -> m.userId() == targetUserId).findFirst();
            if (target.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            if (target.get().role() == StaffRole.CLINIC_ADMIN && adminCount(staff) <= 1) {
                return StaffResult.invalid("A clinic needs at least one admin. Make someone else an admin first.");
            }
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.REMOVE_STAFF, AuditEntry.STAFF,
                    targetUserId, null);
            if (!repo.removeStaff(clinicId, targetUserId, audit)) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            return StaffResult.ok(repo.staff(clinicId), "Removed from this clinic.");
        });
    }

    /** Makes a clinician at this clinic a CLINIC_ADMIN (the role applies to all their clinics). */
    public StaffResult<List<StaffMember>> promoteStaff(int staffUserId, int clinicId, int targetUserId) {
        return changeRole(staffUserId, clinicId, targetUserId, StaffRole.CLINIC_ADMIN);
    }

    /** Makes an admin a CLINICIAN again; refused for the clinic's last admin. */
    public StaffResult<List<StaffMember>> demoteStaff(int staffUserId, int clinicId, int targetUserId) {
        return changeRole(staffUserId, clinicId, targetUserId, StaffRole.CLINICIAN);
    }

    private StaffResult<List<StaffMember>> changeRole(int staffUserId, int clinicId, int targetUserId, StaffRole to) {
        return run(staffUserId, clinicId, true, a -> {
            List<StaffMember> staff = repo.staff(clinicId);
            Optional<StaffMember> target = staff.stream().filter(m -> m.userId() == targetUserId).findFirst();
            if (target.isEmpty() || !target.get().role().isStaff()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            if (target.get().role() == to) {
                return StaffResult.invalid(to == StaffRole.CLINIC_ADMIN ? "They are already an admin." : "They are not an admin.");
            }
            if (to == StaffRole.CLINICIAN && adminCount(staff) <= 1) {
                return StaffResult.invalid("A clinic needs at least one admin. Make someone else an admin first.");
            }
            Action action = to == StaffRole.CLINIC_ADMIN ? Action.PROMOTE_STAFF : Action.DEMOTE_STAFF;
            if (!repo.setRole(clinicId, targetUserId, to,
                    AuditEntry.of(staffUserId, clinicId, action, AuditEntry.STAFF, targetUserId, null))) {
                return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            }
            return StaffResult.ok(repo.staff(clinicId), to == StaffRole.CLINIC_ADMIN ? "Now a clinic admin." : "Now a clinician.");
        });
    }

    /**
     * Replaces the clinic's staff code with a new random one, returned in {@code value()} to show the
     * admin once (only its hash is stored). The old code stops working; existing staff are unaffected.
     */
    public StaffResult<String> rotateStaffCode(int staffUserId, int clinicId) {
        return run(staffUserId, clinicId, true, a -> {
            String code = newStaffCode();
            repo.setStaffCodeHash(clinicId, PasswordHasher.hash(code), staffUserId, nowUtc(),
                    AuditEntry.of(staffUserId, clinicId, Action.ROTATE_STAFF_CODE, AuditEntry.STAFF_CODE, clinicId, null));
            return StaffResult.ok(code,
                    "New staff code created. Share it only with staff at this clinic: the old code no longer works.");
        });
    }

    /** @param hasCode false: nobody can sign up as staff here yet; demo: still the seeded clinic&lt;id&gt; code */
    public record StaffCodeStatus(boolean hasCode, boolean demo) {}

    /**
     * Whether the clinic has a staff code and whether it is still the seeded demo code. The message
     * (non-null when action is needed) is the nag to show the admin.
     */
    public StaffResult<StaffCodeStatus> staffCodeStatus(int staffUserId, int clinicId) {
        return run(staffUserId, clinicId, true, a -> {
            Optional<Boolean> demo = repo.staffCodeIsDemo(clinicId);
            if (demo.isEmpty()) {
                return StaffResult.ok(new StaffCodeStatus(false, false),
                        "This clinic has no staff code, so staff can't sign up. Create one to invite staff.");
            }
            return demo.get()
                    ? StaffResult.ok(new StaffCodeStatus(true, true),
                        "This clinic still uses the demo staff code. Rotate it before real use.")
                    : StaffResult.ok(new StaffCodeStatus(true, false));
        });
    }

    /** Any staff member can view the clinic's slots; only admins change them. */
    public StaffResult<List<StaffTimeslot>> timeslots(int staffUserId, int clinicId, LocalDate date) {
        if (date == null) return StaffResult.invalid("Choose a date.");
        return run(staffUserId, clinicId, false, a -> StaffResult.ok(repo.timeslots(clinicId, date)));
    }

    public StaffResult<StaffTimeslot> createTimeslot(int staffUserId, int clinicId, int serviceId, LocalDate date,
                                                     LocalTime start, LocalTime end, int capacity) {
        StaffResult<StaffTimeslot> invalid = validateSlot(date, start, end, capacity);
        if (invalid != null) return invalid;
        if (date.isBefore(today())) return StaffResult.invalid("Choose today or a later date.");
        return run(staffUserId, clinicId, true, a -> {
            if (!repo.clinicOffersService(clinicId, serviceId)) {
                return StaffResult.invalid("This clinic doesn't offer that service.");
            }
            int id = repo.createTimeslot(clinicId, serviceId, date, start, end, capacity,
                    AuditEntry.of(staffUserId, clinicId, Action.CREATE_TIMESLOT, AuditEntry.TIMESLOT, null, null));
            return repo.timeslot(clinicId, id)
                    .map(t -> StaffResult.ok(t, "Timeslot added."))
                    .orElseGet(() -> StaffResult.fail(StaffResult.Status.NOT_FOUND));
        });
    }

    /**
     * Changes a slot. Capacity can never go below the places already booked; a slot with bookings can
     * only have its capacity changed (moving it would move patients' appointments).
     */
    public StaffResult<StaffTimeslot> updateTimeslot(int staffUserId, int clinicId, int timeslotId, LocalDate date,
                                                     LocalTime start, LocalTime end, int capacity) {
        StaffResult<StaffTimeslot> invalid = validateSlot(date, start, end, capacity);
        if (invalid != null) return invalid;
        return run(staffUserId, clinicId, true, a -> {
            Optional<StaffTimeslot> slot = repo.timeslot(clinicId, timeslotId);
            if (slot.isEmpty()) return StaffResult.fail(StaffResult.Status.NOT_FOUND);
            StaffTimeslot s = slot.get();
            if (capacity < s.bookedCount()) return capacityTooLow(s.bookedCount());
            boolean moved = !date.equals(s.date()) || !start.equals(s.startTime()) || !end.equals(s.endTime());
            if (moved && s.bookedCount() > 0) {
                return StaffResult.invalid("This slot has bookings, so only its capacity can change.");
            }
            AuditEntry audit = AuditEntry.of(staffUserId, clinicId, Action.UPDATE_TIMESLOT, AuditEntry.TIMESLOT,
                    timeslotId, null);
            if (!repo.updateTimeslot(clinicId, timeslotId, date, start, end, capacity, audit)) {
                // booked_count moved on between the read and the update (checked again in SQL)
                int booked = repo.timeslot(clinicId, timeslotId).map(StaffTimeslot::bookedCount).orElse(capacity + 1);
                return capacityTooLow(booked);
            }
            return repo.timeslot(clinicId, timeslotId)
                    .map(t -> StaffResult.ok(t, "Timeslot saved."))
                    .orElseGet(() -> StaffResult.fail(StaffResult.Status.NOT_FOUND));
        });
    }

    // ================================================================ guard + helpers

    private <T> StaffResult<T> run(int staffUserId, int clinicId, boolean adminOnly, Work<T> work) {
        try {
            Optional<StaffAccess> access = repo.access(staffUserId);
            if (access.isEmpty() || !access.get().isStaff()) return StaffResult.fail(StaffResult.Status.NOT_STAFF);
            if (!access.get().worksAt(clinicId)) return StaffResult.fail(StaffResult.Status.NOT_AT_CLINIC);
            if (adminOnly && !access.get().isAdmin()) return StaffResult.fail(StaffResult.Status.ADMIN_ONLY);
            return work.run(access.get());
        } catch (SQLException e) {
            return dbError(e);
        }
    }

    private static <T> StaffResult<T> dbError(SQLException e) {
        System.err.println("[Staff] Database error (SQLState " + e.getSQLState() + ")");
        return StaffResult.fail(StaffResult.Status.DB_ERROR);
    }

    /** A test at the clinic whose patient is visible there. */
    private Optional<StaffTest> visibleTest(int clinicId, int testId) throws SQLException {
        Optional<StaffTest> t = repo.test(clinicId, testId);
        if (t.isEmpty() || !repo.patientVisible(clinicId, t.get().patientUserId())) return Optional.empty();
        return t;
    }

    private <T> StaffResult<T> checkAppointment(int clinicId, int patientUserId, Integer appId) throws SQLException {
        if (appId == null) return null;
        Optional<StaffAppointment> appt = repo.appointment(clinicId, appId);
        if (appt.isEmpty() || appt.get().patientUserId() != patientUserId) {
            return StaffResult.invalid("That appointment isn't this patient's booking at this clinic.");
        }
        return null;
    }

    private <T> StaffResult<T> validate(TestDraft d) {
        if (d == null) return StaffResult.invalid("Enter the test details.");
        String type = blankToNull(d.testType());
        if (type == null) return StaffResult.invalid("Enter the test type.");
        if (type.length() > MAX_SHORT_TEXT) return StaffResult.invalid("The test type can be up to " + MAX_SHORT_TEXT + " characters.");
        if (d.testDate() == null) return StaffResult.invalid("Enter the test date.");
        if (d.testDate().isAfter(today())) return StaffResult.invalid("The test date can't be in the future.");
        if (tooLong(d.clinicianNotes()) || tooLong(d.treatmentInstructions())) {
            return StaffResult.invalid("Notes and instructions can be up to " + MAX_NOTES + " characters.");
        }
        Set<String> seen = new HashSet<>();
        for (TestResult r : d.results()) {
            String condition = r == null ? null : blankToNull(r.condition());
            if (condition == null || r.result() == null) return StaffResult.invalid("Each result needs a condition and a result.");
            if (condition.length() > MAX_SHORT_TEXT) return StaffResult.invalid("A condition can be up to " + MAX_SHORT_TEXT + " characters.");
            if (!seen.add(key(condition))) return StaffResult.invalid("Each condition can only be listed once.");
        }
        return null;
    }

    private <T> StaffResult<T> validate(PrescriptionDraft d) {
        if (d == null) return StaffResult.invalid("Enter the prescription details.");
        String dosage = blankToNull(d.dosage());
        if (dosage == null) return StaffResult.invalid("Enter the dose.");
        if (dosage.length() > MAX_SHORT_TEXT) return StaffResult.invalid("The dose can be up to " + MAX_SHORT_TEXT + " characters.");
        if (d.dosesPerDay() != null && (d.dosesPerDay() < 1 || d.dosesPerDay() > 24)) {
            return StaffResult.invalid("Doses per day must be between 1 and 24.");
        }
        if (d.daysBetweenDoses() != null && (d.daysBetweenDoses() < 0 || d.daysBetweenDoses() > 365)) {
            return StaffResult.invalid("Days between doses must be between 0 and 365.");
        }
        if (d.startDate() == null) return StaffResult.invalid("Enter the start date.");
        if (d.endDate() != null && d.endDate().isBefore(d.startDate())) {
            return StaffResult.invalid("The end date can't be before the start date.");
        }
        if (tooLong(d.instructions())) return StaffResult.invalid("Instructions can be up to " + MAX_NOTES + " characters.");
        return null;
    }

    private static <T> StaffResult<T> validateSlot(LocalDate date, LocalTime start, LocalTime end, int capacity) {
        if (date == null || start == null || end == null) return StaffResult.invalid("Enter the date, start and end time.");
        if (!end.isAfter(start)) return StaffResult.invalid("The end time must be after the start time.");
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            return StaffResult.invalid("Capacity must be between 1 and " + MAX_CAPACITY + ".");
        }
        return null;
    }

    private static <T> StaffResult<T> capacityTooLow(int booked) {
        return StaffResult.invalid("Capacity can't be lower than the " + booked + (booked == 1 ? " place" : " places")
                + " already booked.");
    }

    private static TestDraft clean(TestDraft d) {
        List<TestResult> results = d.results().stream()
                .map(r -> new TestResult(r.condition().strip(), r.result())).toList();
        return new TestDraft(d.appId(), d.testType().strip(), d.testDate(), blankToNull(d.clinicianNotes()),
                blankToNull(d.treatmentInstructions()), results);
    }

    private static long adminCount(List<StaffMember> staff) {
        return staff.stream().filter(m -> m.role() == StaffRole.CLINIC_ADMIN).count();
    }

    static String newStaffCode() {
        StringBuilder sb = new StringBuilder(STAFF_CODE_LENGTH);
        for (int i = 0; i < STAFF_CODE_LENGTH; i++) sb.append(CODE_ALPHABET.charAt(RNG.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    private static String key(String condition) {
        return condition == null ? "" : condition.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean tooLong(String s) {
        return s != null && s.strip().length() > MAX_NOTES;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
