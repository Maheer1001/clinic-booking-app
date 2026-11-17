package app;

import model.AccessibilitySettings;
import model.Book;
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
