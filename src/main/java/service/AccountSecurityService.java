package service;

import app.DB;
import dao.AccountRepository;
import dao.UserDAO;

import java.sql.SQLException;

/**
 * Account changes that need proof of identity (password, email, security question) plus saving a
 * name onto the account. Uses the sign-up password rules ({@link AuthService#isStrongEnoughPassword}),
 * the same hashing ({@link PasswordHasher}) and the same email normalisation as login.
 */
public class AccountSecurityService {

    public enum ChangePasswordResult {
        SUCCESS(""),
        WRONG_CURRENT_PASSWORD("Your current password is incorrect."),
        WEAK_PASSWORD("Password is too weak. " + AuthService.PASSWORD_RULES),
        SAME_PASSWORD("Choose a new password that is different from your current one."),
        USER_NOT_FOUND("We couldn't find your account. Please log in again."),
        DB_ERROR("We couldn't reach the server. Please try again.");

        private final String message;

        ChangePasswordResult(String message) { this.message = message; }

        /** Text to show the user ("" for SUCCESS). */
        public String message() { return message; }
    }

    public enum ChangeEmailResult {
        SUCCESS(""),
        INVALID_EMAIL("Enter a valid email address."),
        SAME_EMAIL("That is already your email address."),
        NO_SECURITY_QUESTION("Set a security question before changing your email."),
        WRONG_ANSWER("That answer doesn't match."),
        LOCKED("Too many wrong answers. Try again later."),
        EMAIL_ALREADY_USED("An account with this email already exists."),
        USER_NOT_FOUND("We couldn't find your account. Please log in again."),
        DB_ERROR("We couldn't reach the server. Please try again.");

        private final String message;

        ChangeEmailResult(String message) { this.message = message; }

        /**
         * Text to show the user ("" for SUCCESS). For WRONG_ANSWER/LOCKED, {@link EmailChange#message()}
         * adds the attempts left / unlock time.
         */
        public String message() { return message; }
    }

    /**
     * Outcome of {@link #changeEmail}.
     *
     * @param verify   the security answer check, or null when it was not reached (e.g. invalid email)
     * @param oldEmail the previous address on SUCCESS (to move reminders over), else null
     * @param newEmail the normalised new address on SUCCESS, else null
     */
    public record EmailChange(ChangeEmailResult result, SecurityQuestionService.VerifyResult verify,
                              String oldEmail, String newEmail) {
        public boolean success() {
            return result == ChangeEmailResult.SUCCESS;
        }

        public String message() {
            if (verify != null && (result == ChangeEmailResult.WRONG_ANSWER || result == ChangeEmailResult.LOCKED)) {
                return verify.message();
            }
            return result.message();
        }
    }

    public enum SaveNameResult {
        SUCCESS(""),
        INVALID_NAME("Enter your first and last name (up to " + AccountService.MAX_NAME_LENGTH + " characters each)."),
        USER_NOT_FOUND("We couldn't find your account. Please log in again."),
        DB_ERROR("We couldn't reach the server. Please try again.");

        private final String message;

        SaveNameResult(String message) { this.message = message; }

        public String message() { return message; }
    }

    private final AccountRepository accounts;
    private final SecurityQuestionService securityQuestions;

    public AccountSecurityService() {
        this(new UserDAO(), new SecurityQuestionService());
    }

    public AccountSecurityService(AccountRepository accounts, SecurityQuestionService securityQuestions) {
        this.accounts = accounts;
        this.securityQuestions = securityQuestions;
    }

    // ---------------------------------------------------------------- name

    /**
     * True when the account has no first name or no last name, so the booking flow can offer
     * "Save this name to your account?". False when the account can't be read (don't nag on errors).
     */
    public boolean accountNameMissing(int userId) {
        try {
            AccountService.AccountInfo info = accounts.getAccountInfo(userId);
            if (info == null) return false;
            return isBlank(info.firstName) || isBlank(info.lastName);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Saves first and last name (both required, trimmed, at most 50 characters) onto the account. */
    public SaveNameResult saveName(int userId, String firstName, String lastName) {
        if (!isValidName(firstName) || !isValidName(lastName)) return SaveNameResult.INVALID_NAME;
        try {
            return accounts.updateName(userId, firstName.strip(), lastName.strip())
                    ? SaveNameResult.SUCCESS : SaveNameResult.USER_NOT_FOUND;
        } catch (SQLException e) {
            return SaveNameResult.DB_ERROR;
        }
    }

    static boolean isValidName(String name) {
        return !isBlank(name) && name.strip().length() <= AccountService.MAX_NAME_LENGTH;
    }

    // ---------------------------------------------------------------- password

    /**
     * Changes the password after checking the current one. The new password must meet the sign-up rules
     * and differ from the current one.
     */
    public ChangePasswordResult changePassword(int userId, String currentPassword, String newPassword) {
        if (!AuthService.isStrongEnoughPassword(newPassword)) return ChangePasswordResult.WEAK_PASSWORD;
        try {
            String stored = accounts.getPasswordHashByUserId(userId);
            if (stored == null) return ChangePasswordResult.USER_NOT_FOUND;
            if (currentPassword == null || !PasswordHasher.verify(currentPassword, stored)) {
                return ChangePasswordResult.WRONG_CURRENT_PASSWORD;
            }
            if (newPassword.equals(currentPassword)) return ChangePasswordResult.SAME_PASSWORD;
            return accounts.updatePasswordHashByUserId(userId, PasswordHasher.hash(newPassword))
                    ? ChangePasswordResult.SUCCESS : ChangePasswordResult.USER_NOT_FOUND;
        } catch (SQLException e) {
            return ChangePasswordResult.DB_ERROR;
        }
    }

    // ---------------------------------------------------------------- email

    /**
     * Changes the email after a correct security answer. The new address is trimmed and lower-cased
     * like login, must be a valid format, and must not be used by another account (ignoring case).
     * The format is checked before the answer (so a typo does not use up an attempt) and uniqueness
     * after it (so the check can't be used to discover other accounts' emails).
     */
    public EmailChange changeEmail(int userId, String securityAnswer, String newEmail) {
        String email = AuthService.normalize(newEmail);
        if (!AuthService.isValidEmail(email)) return fail(ChangeEmailResult.INVALID_EMAIL, null);
        try {
            String current = accounts.getEmailByUserId(userId);
            if (current == null) return fail(ChangeEmailResult.USER_NOT_FOUND, null);
            if (email.equalsIgnoreCase(current.trim())) return fail(ChangeEmailResult.SAME_EMAIL, null);

            SecurityQuestionService.VerifyResult verify = securityQuestions.verifyAnswer(userId, securityAnswer);
            switch (verify.status()) {
                case CORRECT -> { }
                case WRONG_ANSWER -> { return fail(ChangeEmailResult.WRONG_ANSWER, verify); }
                case LOCKED -> { return fail(ChangeEmailResult.LOCKED, verify); }
                case NOT_SET -> { return fail(ChangeEmailResult.NO_SECURITY_QUESTION, verify); }
                case DB_ERROR -> { return fail(ChangeEmailResult.DB_ERROR, verify); }
            }

            if (accounts.emailTakenByOtherUser(userId, email)) return fail(ChangeEmailResult.EMAIL_ALREADY_USED, verify);
            if (!accounts.updateEmail(userId, email)) return fail(ChangeEmailResult.USER_NOT_FOUND, verify);
            return new EmailChange(ChangeEmailResult.SUCCESS, verify, current, email);
        } catch (SQLException e) {
            // The unique email index catches a race with another sign-up or email change.
            return fail(DB.isDuplicateEmail(e) ? ChangeEmailResult.EMAIL_ALREADY_USED
                    : ChangeEmailResult.DB_ERROR, null);
        }
    }

    private static EmailChange fail(ChangeEmailResult r, SecurityQuestionService.VerifyResult verify) {
        return new EmailChange(r, verify, null, null);
    }

    // ---------------------------------------------------------------- security question

    /**
     * Replaces an existing security question (or sets the first one) after checking the password, so a
     * left-open session can't swap the answer and then change the email.
     */
    public SecurityQuestionService.SetResult changeSecurityQuestion(int userId, String currentPassword,
                                                                    String questionKey, String answer) {
        try {
            String stored = accounts.getPasswordHashByUserId(userId);
            if (stored == null) return SecurityQuestionService.SetResult.USER_NOT_FOUND;
            if (currentPassword == null || !PasswordHasher.verify(currentPassword, stored)) {
                return SecurityQuestionService.SetResult.WRONG_PASSWORD;
            }
        } catch (SQLException e) {
            return SecurityQuestionService.SetResult.DB_ERROR;
        }
        return securityQuestions.replaceSecurityQuestion(userId, questionKey, answer);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
