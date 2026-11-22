package controller;

import model.StaffTest;
import model.TestDraft;
import service.StaffResult;
import service.StaffService.ReleaseOutcome;
import service.StaffService.SaveTestOutcome;

import java.util.function.Consumer;

/**
 * The test editor's save and release steps. A new test is always created as a draft; "Release to
 * patient" saves the form first, then releases it. When the service answers NEEDS_CONFIRMATION (a
 * positive result about to reach the patient) the {@link Confirmer} is asked and, on yes, the call is
 * repeated with confirmation; on no nothing more is saved or released.
 */
public class StaffTestEditorController {

    /** Asks the user to confirm (a dialog in the app). True to go ahead. */
    @FunctionalInterface
    public interface Confirmer {
        boolean confirm(String message);
    }

    /**
     * @param ok       the step finished
     * @param message  what to show (the service's message, or why nothing happened)
     * @param test     the test as now stored (null until it has been created)
     * @param released true once the test is released to the patient
     */
    public record Outcome(boolean ok, String message, StaffTest test, boolean released) {}

    public static final String NOT_SAVED = "Nothing was saved.";
    public static final String NOT_RELEASED = "The test was saved as a draft but not released.";

    private final StaffController staff;
    private final int patientUserId;
    private StaffTest test;

    /** @param existing the test being edited, or null for a new one */
    public StaffTestEditorController(StaffController staff, int patientUserId, StaffTest existing) {
        this.staff = staff;
        this.patientUserId = patientUserId;
        this.test = existing;
    }

    public StaffTest test() {
        return test;
    }

    public boolean isNew() {
        return test == null;
    }

    public boolean isReleased() {
        return test != null && test.released();
    }

    /** Creates the draft, or saves changes (asking first if a released test gains a positive). */
    public void save(TestDraft draft, Confirmer confirmer, Consumer<Outcome> done) {
        if (test == null) {
            staff.<StaffTest>call((s, u, c) -> s.createTest(u, c, patientUserId, draft), r -> {
                if (r.ok()) test = r.value();
                done.accept(new Outcome(r.ok(), r.message(), test, false));
            });
            return;
        }
        int testId = test.testId();
        staff.<SaveTestOutcome>call((s, u, c) -> s.updateTest(u, c, testId, draft, false), r -> {
            if (r.status() == StaffResult.Status.NEEDS_CONFIRMATION) {
                if (!confirmer.confirm(r.message())) {
                    done.accept(new Outcome(false, NOT_SAVED, test, isReleased()));
                    return;
                }
                staff.<SaveTestOutcome>call((s, u, c) -> s.updateTest(u, c, testId, draft, true),
                        confirmed -> finishUpdate(confirmed, done));
                return;
            }
            finishUpdate(r, done);
        });
    }

    private void finishUpdate(StaffResult<SaveTestOutcome> r, Consumer<Outcome> done) {
        if (r.ok()) test = r.value().test();
        done.accept(new Outcome(r.ok(), r.message(), test, isReleased()));
    }

    /** Saves the form as a draft, then releases it (asking first when a result is positive). */
    public void saveAndRelease(TestDraft draft, Confirmer confirmer, Consumer<Outcome> done) {
        save(draft, confirmer, saved -> {
            if (!saved.ok()) {
                done.accept(saved);
                return;
            }
            int testId = test.testId();
            staff.<ReleaseOutcome>call((s, u, c) -> s.releaseTest(u, c, testId, false), r -> {
                if (r.status() == StaffResult.Status.NEEDS_CONFIRMATION && r.value() != null && r.value().anyPositive()) {
                    if (!confirmer.confirm(r.message())) {
                        done.accept(new Outcome(false, NOT_RELEASED, test, false));
                        return;
                    }
                    staff.<ReleaseOutcome>call((s, u, c) -> s.releaseTest(u, c, testId, true),
                            confirmed -> finishRelease(testId, confirmed, done));
                    return;
                }
                finishRelease(testId, r, done);
            });
        });
    }

    private void finishRelease(int testId, StaffResult<ReleaseOutcome> r, Consumer<Outcome> done) {
        if (!r.ok()) {
            done.accept(new Outcome(false, r.message(), test, false));
            return;
        }
        // Re-read so the editor shows the release time and the Released pill.
        staff.<StaffTest>call((s, u, c) -> s.test(u, c, testId), t -> {
            if (t.ok()) test = t.value();
            done.accept(new Outcome(true, r.message(), test, true));
        });
    }
}
