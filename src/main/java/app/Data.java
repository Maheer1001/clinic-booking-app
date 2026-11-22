package app;

import javafx.scene.Scene;
import model.AccessibilitySettings;
import model.Book;
import model.Session;
import model.User;

/**
 * Application-scoped state: current booking and logged-in user.
 * Set after login; used by booking flow and reminder views.
 */
public class Data {

    private static Book booking = new Book();

    private static Session currentSession = null;
    // Last-seen counters for tracking notifications (per app session).
    // -1 means "not initialised yet" (first time user opens tracking).
    private static int lastSeenTestCount = -1;

    private static AccessibilitySettings settings;
    /** User id that {@link #settings} belongs to (-1 if none). */
    private static int settingsOwnerId = -1;

    private static Scene scene;

    /**
     * Draft state for Create Account screen (so returning from Terms/Privacy keeps filled fields).
     * Stored in-memory only.
     */
    public static final class SignUpDraft {
        public String email = "";
        public String password = "";
        public String confirmPassword = "";
        public boolean termsAccepted = false;
        public boolean privacyAccepted = false;
        public boolean termsReadToEnd = false;
        public boolean privacyReadToEnd = false;
        /** {@link model.SecurityQuestion} key, or null when none is chosen yet. */
        public String securityQuestionKey = null;
        public String securityAnswer = "";
    }

    private static final SignUpDraft signUpDraft = new SignUpDraft();

    public static SignUpDraft getSignUpDraft() {
        return signUpDraft;
    }

    public static Book getBooking() {
        return booking;
    }

    public static void reset() {
        booking = new Book();
    }

    public static int getCurrentUserId() {
        return currentSession != null ? currentSession.getUserId() : -1;
    }

    public static String getCurrentUserEmail() {
        return currentSession != null ? currentSession.getUser().email() : null;
    }

    public static String getCurrentUserName() {
        return currentSession != null ? currentSession.getUser().getDisplayName() : null;
    }

    public static Session getCurrentSession() {
        return currentSession;
    }

    public static User getCurrentUser() {
        return currentSession != null ? currentSession.getUser() : null;
    }

    public static void setCurrentSession(Session session) {
        currentSession = session;
    }

    /** Legacy: set current user by raw fields (creates Session with User). */
    public static void setCurrentUser(int userId, String email, String name) {
        String first = "";
        String last = "";
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+", 2);
            first = parts[0];
            last = parts.length > 1 ? parts[1] : "";
        }
        User user = new User(userId, email != null ? email : "", first, last);
        currentSession = new Session(Session.generateSessionId(), user);
    }

    /**
     * Forgets everything tied to the signed-in user (called on log out / account deletion), so the
     * next user does not inherit a half-filled booking that may contain their personal details.
     */
    public static void clearCurrentUser() {
        currentSession = null;
        lastSeenTestCount = -1;
        booking = new Book();
        // Settings are kept until the next user signs in (see getSettings): a pending debounced
        // save from the user who just logged out still needs to read their own values.
    }

    public static int getLastSeenTestCount() {
        return lastSeenTestCount;
    }

    public static void setLastSeenTestCount(int count) {
        lastSeenTestCount = count;
    }

    /**
     * The signed-in user's accessibility settings, or defaults if they could not be loaded. Never null.
     * If a different user has signed in since the settings were set (e.g. loading theirs failed),
     * defaults are returned instead of leaking the previous user's settings.
     */
    public static AccessibilitySettings getSettings() {
        int current = getCurrentUserId();
        boolean otherUsersSettings = current >= 0 && settingsOwnerId != current;
        if (settings == null || otherUsersSettings) {
            settings = new AccessibilitySettings();
            settingsOwnerId = current;
        }
        return settings;
    }

    public static void setSettings(AccessibilitySettings settings) {
        Data.settings = settings;
        Data.settingsOwnerId = getCurrentUserId();
    }

    public static Scene getScene() {
        return scene;
    }

    public static void setScene(Scene scene) {
        Data.scene = scene;
    }
}
