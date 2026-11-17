package service;

import dao.UserDAO;
import dao.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private static final int MIN_PASSWORD_LENGTH = 8;

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
            if (storedHash == null) return SignInResult.USER_NOT_FOUND;

            String inputHash = hashPassword(password);
            return storedHash.equals(inputHash) ? SignInResult.SUCCESS : SignInResult.WRONG_PASSWORD;
        } catch (SQLException e) {
            return SignInResult.DB_ERROR;
        }
    }

    static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    static boolean isStrongEnoughPassword(String password) {
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
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String normalize(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
