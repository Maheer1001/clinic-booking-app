package controller;

import app.Data;
import model.SecurityQuestion;
import model.Session;
import model.User;
import service.AccountSecurityService;
import service.AccountSecurityService.ChangePasswordResult;
import service.AccountSecurityService.EmailChange;
import service.AccountSecurityService.SaveNameResult;
import service.AccountService;
import service.AuthService;
import service.NotificationScheduler;
import service.SecurityQuestionService;
import service.SecurityQuestionService.SetResult;
import service.SecurityQuestionService.VerifyResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What the account/settings, sign-up and booking screens call to read and change the account.
 * No JavaFX here. Every method except {@link #securityQuestions()} talks to the database and most hash
 * (PBKDF2, ~0.1-0.3 s), so call them off the JavaFX thread (e.g. with {@code Background.run}).
 * Results carry a {@code message()} ready to show the user.
 */
public class AccountController {

    /** Called after an email change is saved, to move anything keyed by the old address. */
    @FunctionalInterface
    public interface EmailChangedHook {
        void emailChanged(int userId, String oldEmail, String newEmail);
    }

    private final AccountService accountService;
    private final AccountSecurityService securityService;
    private final SecurityQuestionService questionService;
    private final EmailChangedHook emailChangedHook;

    public AccountController() {
        this(new AccountService(), new SecurityQuestionService(), AccountController::moveRemindersToNewEmail);
    }

    private AccountController(AccountService accountService, SecurityQuestionService questionService,
                              EmailChangedHook hook) {
        this(accountService, new AccountSecurityService(new dao.UserDAO(), questionService), questionService, hook);
    }

    public AccountController(AccountService accountService, AccountSecurityService securityService,
                             SecurityQuestionService questionService, EmailChangedHook emailChangedHook) {
        this.accountService = accountService;
        this.securityService = securityService;
        this.questionService = questionService;
        this.emailChangedHook = emailChangedHook;
    }

    // ---------------------------------------------------------------- settings

    public AccountService.AccountInfo loadAccount(int userId) {
        return accountService.loadAccount(userId);
    }

    /** Settings save. {@code dob} may be null (date of birth is optional). Also refreshes the signed-in name. */
    public AccountService.SaveResult saveAccount(int userId, String firstName, String lastName, String phone,
                                                 LocalDate dob, String email) {
        AccountService.SaveResult r = accountService.saveAccount(userId, firstName, lastName, phone, dob, email);
        if (r == AccountService.SaveResult.SUCCESS) {
            updateSessionUser(userId, AuthService.normalize(email), orEmpty(firstName), orEmpty(lastName));
        }
        return r;
    }

    // ---------------------------------------------------------------- name (booking flow)

    /** True when the account lacks a first or last name: offer "Save this name to your account?". */
    public boolean accountNameMissing(int userId) {
        return securityService.accountNameMissing(userId);
    }

    /** Saves the name onto the account and the signed-in session. */
    public SaveNameResult saveNameToAccount(int userId, String firstName, String lastName) {
        SaveNameResult r = securityService.saveName(userId, firstName, lastName);
        if (r == SaveNameResult.SUCCESS) {
            updateSessionUser(userId, null, firstName.strip(), lastName.strip());
        }
        return r;
    }

    // ---------------------------------------------------------------- security question

    /** The questions to offer, in display order (no database call). */
    public List<SecurityQuestion> securityQuestions() {
        return SecurityQuestion.all();
    }

    /** First-time set (sign-up or an account without one). Refuses to overwrite: see {@link #changeSecurityQuestion}. */
    public SetResult setSecurityQuestion(int userId, String questionKey, String answer) {
        return questionService.setSecurityQuestion(userId, questionKey, answer);
    }

    /** Replace the question and answer; needs the current password. */
    public SetResult changeSecurityQuestion(int userId, String currentPassword, String questionKey, String answer) {
        return securityService.changeSecurityQuestion(userId, currentPassword, questionKey, answer);
    }

    public boolean hasSecurityQuestion(int userId) {
        return questionService.hasSecurityQuestion(userId);
    }

    /** The user's question (show {@code prompt()}), or empty if none. */
    public Optional<SecurityQuestion> getQuestion(int userId) {
        return questionService.getQuestion(userId);
    }

    /** Checks an answer; 5 wrong in a row locks it for 15 minutes (stored, so it survives a restart). */
    public VerifyResult verifyAnswer(int userId, String answer) {
        return questionService.verifyAnswer(userId, answer);
    }

    // ---------------------------------------------------------------- password / email

    public ChangePasswordResult changePassword(int userId, String currentPassword, String newPassword) {
        return securityService.changePassword(userId, currentPassword, newPassword);
    }

    /**
     * Changes the email after a correct security answer. On success the signed-in session shows the new
     * address and reminders already scheduled for the old address are moved to the new one.
     */
    public EmailChange changeEmail(int userId, String securityAnswer, String newEmail) {
        EmailChange r = securityService.changeEmail(userId, securityAnswer, newEmail);
        if (r.success()) {
            updateSessionUser(userId, r.newEmail(), null, null);
            try {
                emailChangedHook.emailChanged(userId, r.oldEmail(), r.newEmail());
            } catch (RuntimeException e) {
                // The email is saved; reminders are rebuilt from the database at the next login anyway.
                System.err.println("[Account] Could not move reminders to the new email: " + e.getClass().getSimpleName());
            }
        }
        return r;
    }

    /**
     * Reminders live in memory with the recipient address captured when scheduled, and the booking
     * reminder is keyed by email. Cancel the old-address booking reminder, then re-schedule this user's
     * saved reminders for the new address (appointment and prescription reminders are keyed by id, so
     * scheduling them again replaces the old-address copies).
     */
    static void moveRemindersToNewEmail(int userId, String oldEmail, String newEmail) {
        NotificationScheduler.shared().cancelBookingReminders(oldEmail);
        String name = Data.getCurrentUserId() == userId ? Data.getCurrentUserName() : null;
        new ReminderController(userId).restoreScheduledReminders(newEmail, name);
    }

    /** Keeps the signed-in user's cached email/name in step with what was saved. Null keeps a field. */
    private static void updateSessionUser(int userId, String email, String first, String last) {
        Session s = Data.getCurrentSession();
        if (s == null || s.getUserId() != userId) return;
        User u = s.getUser();
        User updated = new User(userId,
                email != null ? email : u.email(),
                first != null || last != null ? first : u.firstName(),
                first != null || last != null ? last : u.lastName());
        Data.setCurrentSession(new Session(s.getSessionId(), userId, updated, s.getCreatedAt(), s.getExpiresAt()));
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s.strip();
    }
}
