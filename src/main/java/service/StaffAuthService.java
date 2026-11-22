package service;

import dao.StaffAccountDAO;
import dao.StaffAccountRepository;
import model.SecurityQuestion;
import model.StaffRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Staff sign-up ("I'm clinic staff" on the sign-up screen). The same checks as a patient sign-up
 * ({@link AuthService}) plus the clinic's staff code, which decides the clinic the new CLINICIAN
 * account is tied to. No JavaFX here; it hashes (PBKDF2) once per clinic, so call it off the JavaFX
 * thread. Results carry a {@code message()} ready to show.
 *
 * <p>A wrong code never says which clinics exist: every clinic's code is checked every time (no early
 * exit, so the time taken does not hint at a match), and wrong codes are counted in the database:
 * {@link #MAX_ATTEMPTS_PER_EMAIL} per email and {@link #MAX_ATTEMPTS_OVERALL} overall within
 * {@link #WINDOW_MINUTES} minutes.
 */
public class StaffAuthService {

    public static final int MAX_ATTEMPTS_PER_EMAIL = 5;
    public static final int MAX_ATTEMPTS_OVERALL = 50;
    public static final int WINDOW_MINUTES = 15;

    public enum Status {
        SUCCESS("Your staff account is ready. Log in to continue."),
        TERMS_NOT_ACCEPTED("Please accept the Terms of Service and Privacy Policy."),
        INVALID_EMAIL("Please enter a valid email address."),
        WEAK_PASSWORD("Password is too weak. " + AuthService.PASSWORD_RULES),
        INVALID_SECURITY_QUESTION("Please choose a security question."),
        INVALID_SECURITY_ANSWER("Your answer must be " + SecurityQuestionService.MIN_ANSWER_LENGTH + " to "
                + SecurityQuestionService.MAX_ANSWER_LENGTH + " characters."),
        MISSING_STAFF_CODE("Enter the staff code your clinic gave you."),
        INVALID_STAFF_CODE("That staff code isn't valid. Check it with your clinic admin."),
        TOO_MANY_ATTEMPTS("Too many wrong staff codes. Try again in " + WINDOW_MINUTES + " minutes."),
        EMAIL_ALREADY_USED("An account with this email already exists. Try logging in or resetting your password."),
        DB_ERROR("We couldn't reach the server. Please try again.");

        private final String message;

        Status(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** @param clinicId the clinic the account was tied to (SUCCESS only, otherwise null) */
    public record StaffSignUpResult(Status status, Integer clinicId, Integer userId) {
        public boolean success() {
            return status == Status.SUCCESS;
        }

        public String message() {
            return status.message();
        }

        static StaffSignUpResult of(Status s) {
            return new StaffSignUpResult(s, null, null);
        }
    }

    private final StaffAccountRepository repository;
    private final SecurityQuestionService securityQuestions;
    private final Clock clock;

    public StaffAuthService() {
        this(new StaffAccountDAO(), new SecurityQuestionService(), Clock.systemDefaultZone());
    }

    public StaffAuthService(StaffAccountRepository repository, SecurityQuestionService securityQuestions, Clock clock) {
        this.repository = repository;
        this.securityQuestions = securityQuestions;
        this.clock = clock;
    }

    public StaffSignUpResult signUpStaff(String email, String password, boolean acceptedTerms, String clinicCode,
                                         String securityQuestionKey, String securityAnswer) {
        String normalized = AuthService.normalize(email);
        // Same order as the patient form, then the code.
        if (!acceptedTerms) return StaffSignUpResult.of(Status.TERMS_NOT_ACCEPTED);
        if (!AuthService.isValidEmail(normalized)) return StaffSignUpResult.of(Status.INVALID_EMAIL);
        if (!AuthService.isStrongEnoughPassword(password)) return StaffSignUpResult.of(Status.WEAK_PASSWORD);
        if (SecurityQuestion.fromKey(securityQuestionKey).isEmpty()) {
            return StaffSignUpResult.of(Status.INVALID_SECURITY_QUESTION);
        }
        if (!SecurityQuestionService.isValidAnswer(securityAnswer)) {
            return StaffSignUpResult.of(Status.INVALID_SECURITY_ANSWER);
        }
        String code = normalizeCode(clinicCode);
        if (code.isEmpty()) return StaffSignUpResult.of(Status.MISSING_STAFF_CODE);

        String emailHash = emailHash(normalized);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            LocalDateTime since = now.minusMinutes(WINDOW_MINUTES);
            if (repository.failedCodeAttempts(emailHash, since) >= MAX_ATTEMPTS_PER_EMAIL
                    || repository.failedCodeAttemptsAll(since) >= MAX_ATTEMPTS_OVERALL) {
                return StaffSignUpResult.of(Status.TOO_MANY_ATTEMPTS);
            }

            Integer clinicId = matchClinic(code, repository.allStaffCodeHashes());
            if (clinicId == null) {
                repository.recordFailedCodeAttempt(emailHash, now);
                return StaffSignUpResult.of(Status.INVALID_STAFF_CODE);
            }

            if (repository.emailExists(normalized)) return StaffSignUpResult.of(Status.EMAIL_ALREADY_USED);

            int userId = repository.createStaffUser(normalized, PasswordHasher.hash(password),
                    StaffRole.CLINICIAN, clinicId, false);
            repository.clearFailedCodeAttempts(emailHash);
            // As for patients: if the question cannot be saved the account still exists and the
            // app can ask for a question later (SecurityQuestionService.hasSecurityQuestion).
            securityQuestions.setSecurityQuestion(userId, securityQuestionKey, securityAnswer);
            return new StaffSignUpResult(Status.SUCCESS, clinicId, userId);
        } catch (SQLException e) {
            System.err.println("[StaffSignUp] Database error (SQLState " + e.getSQLState() + ")");
            return StaffSignUpResult.of(Status.DB_ERROR);
        }
    }

    /** Codes are compared trimmed and lower-case, so "Clinic1 " matches "clinic1". */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.strip().toLowerCase();
    }

    /** Checks the code against every clinic (no early exit) and returns the matching clinic, if any. */
    static Integer matchClinic(String normalizedCode, List<StaffAccountRepository.ClinicCodeHash> hashes) {
        Integer match = null;
        for (StaffAccountRepository.ClinicCodeHash h : hashes) {
            boolean ok;
            try {
                ok = PasswordHasher.verify(normalizedCode, h.codeHash());
            } catch (RuntimeException e) {
                ok = false;
            }
            if (ok && match == null) match = h.clinicId();
        }
        return match;
    }

    /** SHA-256 of the normalised email, so the attempt table never holds an address. */
    static String emailHash(String normalizedEmail) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
