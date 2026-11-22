package app;

import model.AccessibilitySettings;
import model.Book;
import model.StaffAccess;
import model.StaffRole;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataTest {

    @AfterEach
    void cleanUp() {
        Data.clearCurrentUser();
        Data.setSettings(null);
    }

    @Test
    void logoutDiscardsTheBookingDraft() {
        Data.setCurrentUser(1, "a@example.com", "Ann Smith");
        Book draft = Data.getBooking();
        Data.clearCurrentUser();
        assertNotSame(draft, Data.getBooking());
        assertEquals(-1, Data.getCurrentUserId());
        assertEquals(-1, Data.getLastSeenTestCount());
    }

    @Test
    void staffAccessIsStoredAndClearedOnLogout() {
        assertEquals(StaffRole.PATIENT, Data.getCurrentRole());
        assertEquals(-1, Data.getCurrentClinicId());

        Data.setCurrentUser(7, "s@example.com", "Sam");
        Data.setStaffAccess(new StaffAccess(7, StaffRole.CLINIC_ADMIN, List.of(2, 5), false));
        assertEquals(StaffRole.CLINIC_ADMIN, Data.getCurrentRole());
        assertTrue(Data.isStaff());
        assertEquals(List.of(2, 5), Data.getStaffClinicIds());
        assertEquals(2, Data.getCurrentClinicId(), "starts in the first clinic");
        Data.setCurrentClinicId(5);
        assertEquals(5, Data.getCurrentClinicId());
        assertThrows(IllegalArgumentException.class, () -> Data.setCurrentClinicId(3));
        assertThrows(UnsupportedOperationException.class, () -> Data.getStaffClinicIds().add(3));

        Data.clearCurrentUser();
        assertEquals(StaffRole.PATIENT, Data.getCurrentRole());
        assertFalse(Data.isStaff());
        assertTrue(Data.getStaffClinicIds().isEmpty());
        assertEquals(-1, Data.getCurrentClinicId());
    }

    @Test
    void staffWithNoClinicHasNoCurrentClinic() {
        Data.setStaffAccess(new StaffAccess(7, StaffRole.CLINICIAN, List.of(), false));
        assertEquals(StaffRole.CLINICIAN, Data.getCurrentRole());
        assertEquals(-1, Data.getCurrentClinicId());
        Data.setStaffAccess(null);
        assertEquals(StaffRole.PATIENT, Data.getCurrentRole());
    }

    @Test
    void settingsAreNeverNull() {
        Data.setSettings(null);
        assertNotNull(Data.getSettings());
        assertSame(Data.getSettings(), Data.getSettings());
    }

    @Test
    void settingsSurviveLogoutUntilAnotherUserSignsIn() {
        Data.setCurrentUser(1, "a@example.com", "Ann");
        AccessibilitySettings anns = new AccessibilitySettings(true, true, AccessibilitySettings.TextSize.LARGE);
        Data.setSettings(anns);
        assertSame(anns, Data.getSettings());

        Data.clearCurrentUser();
        assertSame(anns, Data.getSettings(), "a late save for the user who logged out still sees their values");

        Data.setCurrentUser(2, "b@example.com", "Ben");
        AccessibilitySettings bens = Data.getSettings();
        assertNotSame(anns, bens);
        assertFalse(bens.isHighContrast());
        assertEquals(AccessibilitySettings.TextSize.MEDIUM, bens.getTextSize());
    }
}
