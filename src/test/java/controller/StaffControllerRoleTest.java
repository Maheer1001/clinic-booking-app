package controller;

import app.Data;
import javafx.application.Platform;
import model.StaffAccess;
import model.StaffRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Roles are per clinic: what the staff screens offer follows the clinic switcher. */
class StaffControllerRoleTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // already started by another test class
        }
    }

    @AfterEach
    void signOut() {
        Data.clearStaffAccess();
    }

    /** Switches clinic on the JavaFX thread, as the clinic switcher does. */
    private static void switchTo(StaffController staff, int clinicId) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                staff.switchClinic(clinicId);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    void isAdmin_isTheRoleAtTheCurrentClinic_andChangesWithTheSwitcher() throws Exception {
        StaffAccess access = new StaffAccess(42, StaffRole.CLINICIAN,
                Map.of(1, StaffRole.CLINIC_ADMIN, 2, StaffRole.CLINICIAN), false);
        Data.setStaffAccess(access);
        StaffController staff = new StaffController(null, StaffController.INLINE, access);

        assertEquals(1, staff.clinicId());
        assertTrue(staff.isAdmin());
        assertEquals(StaffRole.CLINIC_ADMIN, staff.role());

        switchTo(staff, 2);
        assertEquals(2, staff.clinicId());
        assertFalse(staff.isAdmin(), "only a clinician at clinic 2");
        assertEquals(StaffRole.CLINICIAN, staff.role());

        switchTo(staff, 1);
        assertTrue(staff.isAdmin());
    }

    @Test
    void aClinicTheyDoNotWorkAt_isNeverSwitchedTo_soNoRoleComesFromIt() throws Exception {
        StaffAccess access = new StaffAccess(42, StaffRole.CLINICIAN, Map.of(1, StaffRole.CLINICIAN), false);
        Data.setStaffAccess(access);
        StaffController staff = new StaffController(null, StaffController.INLINE, access);
        switchTo(staff, 3);
        assertEquals(1, staff.clinicId());
        assertFalse(staff.isAdmin());
        assertEquals(StaffRole.PATIENT, access.roleAt(3));
        assertFalse(access.isAdminAt(3));
    }
}
