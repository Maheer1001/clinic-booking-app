package controller;

import model.AuditEntry;
import model.StaffAccess;
import model.StaffRole;
import model.TestDraft;
import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.FakeStaffRepository;
import service.StaffService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The test editor's save / release flow against the real StaffService and a fake repository. */
class StaffTestEditorControllerTest {

    private static final int CLINIC = 1, CLINICIAN = 10, PATIENT = 30, OTHER_PATIENT = 31;
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 10);

    private FakeStaffRepository repo;
    private final List<Integer> emailed = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private StaffController staff;

    @BeforeEach
    void setUp() {
        repo = new FakeStaffRepository();
        repo.addStaff(CLINICIAN, StaffRole.CLINICIAN, CLINIC);
        repo.addAppointment(1, CLINIC, PATIENT, TODAY, "CONFIRMED");
        repo.addAppointment(2, 2, OTHER_PATIENT, TODAY, "CONFIRMED");
        StaffService service = new StaffService(repo, (p, d) -> emailed.add(p),
                Clock.fixed(Instant.parse("2030-01-10T10:00:00Z"), ZoneOffset.UTC));
        staff = new StaffController(service, StaffController.INLINE,
                new StaffAccess(CLINICIAN, StaffRole.CLINICIAN, List.of(CLINIC), false));
    }

    private static TestDraft draft(TestResultStatus status) {
        return new TestDraft(1, "STI screen", TODAY, null, null, List.of(new TestResult("Chlamydia", status)));
    }

    private StaffTestEditorController.Confirmer answer(boolean yes) {
        return message -> {
            asked.add(message);
            return yes;
        };
    }

    private StaffTestEditorController.Outcome last;

    @Test
    void save_createsADraftOnce_thenUpdatesIt() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, PATIENT, null);
        editor.save(draft(TestResultStatus.PENDING), answer(true), o -> last = o);
        assertTrue(last.ok(), last.message());
        assertFalse(last.released());
        int id = editor.test().testId();

        editor.save(draft(TestResultStatus.POSITIVE), answer(true), o -> last = o);
        assertTrue(last.ok());
        assertEquals(id, editor.test().testId(), "the second save updates, it doesn't create another test");
        assertTrue(asked.isEmpty(), "a draft never needs confirming");
        assertTrue(emailed.isEmpty(), "editing a draft never alerts the patient");
    }

    @Test
    void releasePositive_asksFirst_andCancelReleasesNothing() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, PATIENT, null);
        editor.saveAndRelease(draft(TestResultStatus.POSITIVE), answer(false), o -> last = o);
        assertFalse(last.ok());
        assertEquals(StaffTestEditorController.NOT_RELEASED, last.message());
        assertEquals(1, asked.size());
        assertFalse(editor.isReleased());
        assertTrue(emailed.isEmpty());
        assertTrue(repo.audits.stream().noneMatch(a -> a.action() == AuditEntry.Action.RELEASE_TEST));
    }

    @Test
    void releasePositive_confirmed_releasesAndEmails() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, PATIENT, null);
        editor.saveAndRelease(draft(TestResultStatus.POSITIVE), answer(true), o -> last = o);
        assertTrue(last.ok(), last.message());
        assertTrue(last.released());
        assertTrue(editor.isReleased());
        assertEquals(List.of(PATIENT), emailed);
        assertEquals(1, asked.size());
    }

    @Test
    void releaseNegative_needsNoConfirmation() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, PATIENT, null);
        editor.saveAndRelease(draft(TestResultStatus.NEGATIVE), answer(false), o -> last = o);
        assertTrue(last.ok(), last.message());
        assertTrue(asked.isEmpty());
        assertTrue(editor.isReleased());
    }

    @Test
    void releasedTest_newPositive_asksBeforeSaving_andNoKeepsTheOldResult() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, PATIENT, null);
        editor.saveAndRelease(draft(TestResultStatus.PENDING), answer(true), o -> last = o);
        assertTrue(editor.isReleased());
        emailed.clear();

        editor.save(draft(TestResultStatus.POSITIVE), answer(false), o -> last = o);
        assertFalse(last.ok());
        assertEquals(StaffTestEditorController.NOT_SAVED, last.message());
        assertEquals(TestResultStatus.PENDING, editor.test().results().get(0).result());
        assertTrue(emailed.isEmpty());

        editor.save(draft(TestResultStatus.POSITIVE), answer(true), o -> last = o);
        assertTrue(last.ok(), last.message());
        assertEquals(TestResultStatus.POSITIVE, editor.test().results().get(0).result());
        assertEquals(List.of(PATIENT), emailed, "the patient is alerted to the changed result");
    }

    @Test
    void invalidDraft_showsTheServiceMessage_andCreatesNothing() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, PATIENT, null);
        editor.save(new TestDraft(null, " ", TODAY, null, null, List.of()), answer(true), o -> last = o);
        assertFalse(last.ok());
        assertEquals("Enter the test type.", last.message());
        assertTrue(editor.isNew());
    }

    @Test
    void patientNotSeenHere_isNotFound() {
        StaffTestEditorController editor = new StaffTestEditorController(staff, OTHER_PATIENT, null);
        editor.save(new TestDraft(null, "STI screen", TODAY, null, null, List.of()), answer(true), o -> last = o);
        assertFalse(last.ok());
        assertTrue(editor.isNew());
    }
}
