package controller;

import app.Data;
import model.StaffAccess;
import service.StaffResult;
import service.StaffService;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What every staff screen shares: who is acting, the clinic they are working in, and a way to call
 * {@link StaffService} off the JavaFX thread. The service re-checks role and clinic on every call, so
 * the {@link StaffAccess} held here only decides what the screens offer.
 *
 * <p>The current clinic is {@link Data#getCurrentClinicId()}, shared by all three staff tabs; when the
 * clinic switcher changes it, {@link #clinicChanges()} tells the open tab pages to reload. A clinic the
 * user does not work at is replaced by their first one.
 */
public class StaffController {

    /** Runs a service call and hands the result back (on the JavaFX thread in the app, inline in tests). */
    public interface Runner {
        <T> void run(Supplier<StaffResult<T>> work, Consumer<StaffResult<T>> done);
    }

    /** One staff operation: the service, the acting user and the current clinic. */
    @FunctionalInterface
    public interface Call<T> {
        StaffResult<T> apply(StaffService service, int staffUserId, int clinicId);
    }

    /** Background threads; an unexpected exception reads as DB_ERROR (its message is never shown). */
    public static final Runner BACKGROUND = new Runner() {
        @Override
        public <T> void run(Supplier<StaffResult<T>> work, Consumer<StaffResult<T>> done) {
            Background.run(work, done, ex -> {
                System.err.println("[Staff] Unexpected error: " + ex.getClass().getSimpleName());
                done.accept(StaffResult.fail(StaffResult.Status.DB_ERROR));
            });
        }
    };

    /** Runs the work straight away on the calling thread (tests). */
    public static final Runner INLINE = new Runner() {
        @Override
        public <T> void run(Supplier<StaffResult<T>> work, Consumer<StaffResult<T>> done) {
            done.accept(work.get());
        }
    };

    /** Bumped on every clinic switch (the new clinic id); tab pages listen to it. */
    private static final javafx.beans.property.IntegerProperty CLINIC_CHANGES =
            new javafx.beans.property.SimpleIntegerProperty(0);

    public static javafx.beans.value.ObservableIntegerValue clinicChanges() {
        return CLINIC_CHANGES;
    }

    private final StaffService service;
    private final Runner runner;
    private final StaffAccess access;

    public StaffController(StaffAccess access) {
        this(new StaffService(), BACKGROUND, access);
    }

    public StaffController(StaffService service, Runner runner, StaffAccess access) {
        this.service = service;
        this.runner = runner;
        this.access = access;
    }

    public StaffAccess access() {
        return access;
    }

    public int staffUserId() {
        return access.userId();
    }

    public boolean isAdmin() {
        return access.isAdmin();
    }

    /** The clinics the user can switch between (ACTIVE memberships only). */
    public List<Integer> clinicIds() {
        return access.clinicIds();
    }

    /** The clinic being worked in, or 0 if the user has no clinic yet (waiting for approval). */
    public int clinicId() {
        int current = Data.getCurrentClinicId();
        if (access.worksAt(current)) return current;
        return access.clinicIds().isEmpty() ? 0 : access.clinicIds().get(0);
    }

    /** Switches every staff tab to another of the user's clinics (call on the JavaFX thread). */
    public void switchClinic(int clinicId) {
        if (!access.worksAt(clinicId) || clinicId == clinicId()) return;
        if (Data.getStaffClinicIds().contains(clinicId)) Data.setCurrentClinicId(clinicId);
        CLINIC_CHANGES.set(clinicId);
    }

    /** Calls the service for the current clinic off the JavaFX thread and passes the result to {@code done}. */
    public <T> void call(Call<T> call, Consumer<StaffResult<T>> done) {
        int staff = staffUserId();
        int clinic = clinicId();
        runner.run(() -> call.apply(service, staff, clinic), done);
    }

    /** Runs other slow work (e.g. reading clinic names) the same way. */
    public <T> void load(Supplier<T> work, Consumer<StaffResult<T>> done) {
        runner.run(() -> StaffResult.ok(work.get()), done);
    }
}
