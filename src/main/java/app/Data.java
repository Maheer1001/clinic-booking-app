package app;

import javafx.scene.Scene;
import model.AccessibilitySettings;
import model.Book;
import model.Session;
import model.StaffAccess;
import model.StaffRole;
import model.User;

import java.util.List;

/**
 * Application-scoped state: current booking and logged-in user.
 * Set after login; used by booking flow and reminder views.
 */
public class Data {

    private static Book booking = new Book();

    private static Session currentSession = null;

    private static AccessibilitySettings settings;
    /** User id that {@link #settings} belongs to (-1 if none). */
    private static int settingsOwnerId = -1;

    private static Scene scene;

    /** Signed-in user's role and clinics (read at login; staff actions re-check them in the database). */
    private static StaffRole currentRole = StaffRole.PATIENT;
    private static List<Integer> staffClinicIds = List.of();
    /** The clinic a multi-clinic staff member is working in now (-1: none). */
    private static int currentClinicId = -1;

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
        /** "I'm clinic staff" ticked, and the clinic staff code typed so far. */
        public boolean staff = false;
        public String staffCode = "";
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
        booking = new Book();
        clearStaffAccess();
        // Settings are kept until the next user signs in (see getSettings): a pending debounced
        // save from the user who just logged out still needs to read their own values.
    }

    /**
     * Stores the signed-in user's role and clinics. The current clinic starts as the first one (lowest
     * id); a patient, or staff with no clinics, has none.
     */
    public static void setStaffAccess(StaffAccess access) {
        if (access == null) {
            clearStaffAccess();
            return;
        }
        currentRole = access.role();
        staffClinicIds = access.clinicIds();
        currentClinicId = staffClinicIds.isEmpty() ? -1 : staffClinicIds.getFirst();
    }

    /** Back to a patient with no clinics (log out, account deletion). */
    public static void clearStaffAccess() {
        currentRole = StaffRole.PATIENT;
        staffClinicIds = List.of();
        currentClinicId = -1;
    }

    /**
     * PATIENT unless a staff member is signed in. Never null. This is the account role (patient or
     * staff) only; what staff may do is per clinic ({@code StaffAccess.roleAt}).
     */
    public static StaffRole getCurrentRole() {
        return currentRole;
    }

    public static boolean isStaff() {
        return currentRole.isStaff();
    }

    /** The clinics the signed-in staff member works at (empty for patients). Unmodifiable. */
    public static List<Integer> getStaffClinicIds() {
        return staffClinicIds;
    }

    /** The clinic the staff member is working in now, or -1 if none. */
    public static int getCurrentClinicId() {
        return currentClinicId;
    }

    /** Switches the current clinic; only one of {@link #getStaffClinicIds()} is accepted. */
    public static void setCurrentClinicId(int clinicId) {
        if (!staffClinicIds.contains(clinicId)) {
            throw new IllegalArgumentException("Not one of this staff member's clinics: " + clinicId);
        }
        currentClinicId = clinicId;
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
