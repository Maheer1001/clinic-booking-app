package service;

import dao.UserDAO;
import dao.UserRepository;

import java.sql.SQLException;
import java.util.regex.Pattern;

public class AuthService {

    public enum SignUpResult {
        SUCCESS,
        TERMS_NOT_ACCEPTED,
        INVALID_EMAIL,
        WEAK_PASSWORD,
        EMAIL_ALREADY_USED,
        DB_ERROR
    }

    public enum SignInResult {
        SUCCESS,
        INVALID_INPUT,
        USER_NOT_FOUND,
        WRONG_PASSWORD,
        DB_ERROR
    }

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Shown to users wherever a new password is chosen, so the rules are known up front. */
    public static final String PASSWORD_RULES =
            "At least " + MIN_PASSWORD_LENGTH + " characters, including a letter and a number.";

    /**
     * Hash checked when the email is unknown, so a failed login takes about as long
     * whether or not the account exists.
     */
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password-0");

    private final UserRepository userDAO;

    /**
     * Production constructor — uses the real UserDAO.
     */
    public AuthService() {
        this(new UserDAO());
    }

    /**
     * Testable constructor — accepts any UserRepository implementation.
     */
    public AuthService(UserRepository userDAO) {
        this.userDAO = userDAO;
    }

    public SignUpResult signUp(String email, String password, boolean acceptedTerms) {
        email = normalize(email);

        if (!acceptedTerms) return SignUpResult.TERMS_NOT_ACCEPTED;
        if (!isValidEmail(email)) return SignUpResult.INVALID_EMAIL;
        if (!isStrongEnoughPassword(password)) return SignUpResult.WEAK_PASSWORD;

        try {
            if (userDAO.emailExists(email)) return SignUpResult.EMAIL_ALREADY_USED;

            String passwordHash = hashPassword(password);
            boolean inserted = userDAO.insertUser(email, passwordHash);
            return inserted ? SignUpResult.SUCCESS : SignUpResult.DB_ERROR;
        } catch (SQLException e) {
            return SignUpResult.DB_ERROR;
        }
    }

    public SignInResult signIn(String email, String password) {
        email = normalize(email);

        if (isBlank(email) || isBlank(password)) return SignInResult.INVALID_INPUT;
        if (!isValidEmail(email)) return SignInResult.INVALID_INPUT;

        try {
            String storedHash = userDAO.getPasswordHashByEmail(email);
            if (storedHash == null) {
                PasswordHasher.verify(password, DUMMY_HASH);
                return SignInResult.USER_NOT_FOUND;
            }

            if (!PasswordHasher.verify(password, storedHash)) return SignInResult.WRONG_PASSWORD;

            if (PasswordHasher.needsRehash(storedHash)) {
                // Upgrade legacy unsalted hashes now that we know the plaintext.
                try {
                    userDAO.updatePasswordHashByEmail(email, hashPassword(password));
                } catch (SQLException ignored) {
                    // Login still succeeds; the upgrade is retried next time.
                }
            }
            return SignInResult.SUCCESS;
        } catch (SQLException e) {
            return SignInResult.DB_ERROR;
        }
    }

    public static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isStrongEnoughPassword(String password) {
        if (isBlank(password)) return false;
        if (password.length() < MIN_PASSWORD_LENGTH) return false;

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch)) hasLetter = true;
            if (Character.isDigit(ch)) hasDigit = true;
        }

        return hasLetter && hasDigit;
    }

    static String hashPassword(String password) {
        return PasswordHasher.hash(password);
    }

    /** Trims and lower-cases an email the same way sign-up and sign-in do. */
    public static String normalize(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
