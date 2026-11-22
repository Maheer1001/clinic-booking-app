package service;

import dao.SecurityQuestionDAO;
import dao.SecurityQuestionRepository;
import dao.SecurityQuestionRepository.Stored;
import model.SecurityQuestion;

import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Security question chosen at sign-up and checked before sensitive changes (e.g. the email address).
 *
 * The answer is normalised ({@link #normalizeAnswer}) and then hashed with {@link PasswordHasher}, the
 * same salted PBKDF2 used for passwords; the plain answer is never stored or logged.
 *
 * Wrong answers are counted in the database: after {@link #MAX_ATTEMPTS} in a row the question is
 * locked for {@link #LOCK_MINUTES} minutes. The count is persisted rather than kept per session so
 * that restarting the app or logging in again does not give unlimited guesses; a correct answer or a
 * new question resets it.
 */
public class SecurityQuestionService {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;
    public static final int MIN_ANSWER_LENGTH = 2;
    public static final int MAX_ANSWER_LENGTH = 100;

    public enum SetResult {
        SUCCESS(""),
        UNKNOWN_QUESTION("Choose a security question from the list."),
        INVALID_ANSWER("Your answer must be " + MIN_ANSWER_LENGTH + " to " + MAX_ANSWER_LENGTH + " characters."),
        ALREADY_SET("You already have a security question. Enter your password to change it."),
        WRONG_PASSWORD("Your current password is incorrect."),
        USER_NOT_FOUND("We couldn't find your account. Please log in again."),
        DB_ERROR("We couldn't reach the server. Please try again.");

        private final String message;

        SetResult(String message) { this.message = message; }

        /** Text to show the user ("" for SUCCESS). */
        public String message() { return message; }
    }

    public enum VerifyStatus { CORRECT, WRONG_ANSWER, LOCKED, NOT_SET, DB_ERROR }

    /**
     * @param attemptsLeft wrong answers allowed before the lock (0 when locked)
     * @param lockedUntil  when answering is allowed again, or null when not locked
     */
    public record VerifyResult(VerifyStatus status, int attemptsLeft, LocalDateTime lockedUntil) {
        public boolean correct() {
            return status == VerifyStatus.CORRECT;
        }

        /** Text to show the user ("" when correct). */
        public String message() {
            return switch (status) {
                case CORRECT -> "";
                case WRONG_ANSWER -> "That answer doesn't match. " + attemptsLeft
                        + (attemptsLeft == 1 ? " attempt" : " attempts") + " left.";
                case LOCKED -> "Too many wrong answers. Try again after "
                        + (lockedUntil == null ? LOCK_MINUTES + " minutes"
                           : lockedUntil.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) + ".";
                case NOT_SET -> "You haven't set a security question yet.";
                case DB_ERROR -> "We couldn't reach the server. Please try again.";
            };
        }
    }

    private final SecurityQuestionRepository repository;
    private final Clock clock;

    public SecurityQuestionService() {
        this(new SecurityQuestionDAO(), Clock.systemDefaultZone());
    }

    public SecurityQuestionService(SecurityQuestionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Trims, collapses runs of whitespace to one space, applies Unicode NFKC and lower-cases, so
     * "  Smith ", "SMITH" and "smith" match. Returns null for null.
     */
    public static String normalizeAnswer(String answer) {
        if (answer == null) return null;
        String n = Normalizer.normalize(answer, Normalizer.Form.NFKC);
        return n.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** True if the answer is acceptable once normalised. */
    public static boolean isValidAnswer(String answer) {
        String n = normalizeAnswer(answer);
        return n != null && n.length() >= MIN_ANSWER_LENGTH && n.length() <= MAX_ANSWER_LENGTH;
    }

    /**
     * Sets the user's first security question (e.g. at sign-up). Refuses to overwrite an existing one
     * ({@link SetResult#ALREADY_SET}); changing it goes through
     * {@link AccountSecurityService#changeSecurityQuestion}, which checks the password first.
     */
    public SetResult setSecurityQuestion(int userId, String questionKey, String answer) {
        Optional<SecurityQuestion> q = SecurityQuestion.fromKey(questionKey);
        if (q.isEmpty()) return SetResult.UNKNOWN_QUESTION;
        if (!isValidAnswer(answer)) return SetResult.INVALID_ANSWER;
        try {
            Optional<Stored> stored = repository.find(userId);
            if (stored.isEmpty()) return SetResult.USER_NOT_FOUND;
            if (stored.get().answerHash() != null) return SetResult.ALREADY_SET;
            return write(userId, q.get(), answer);
        } catch (SQLException e) {
            return SetResult.DB_ERROR;
        }
    }

    /** Replaces the question and answer without checks beyond validation. Callers must have verified the user. */
    SetResult replaceSecurityQuestion(int userId, String questionKey, String answer) {
        Optional<SecurityQuestion> q = SecurityQuestion.fromKey(questionKey);
        if (q.isEmpty()) return SetResult.UNKNOWN_QUESTION;
        if (!isValidAnswer(answer)) return SetResult.INVALID_ANSWER;
        try {
            return write(userId, q.get(), answer);
        } catch (SQLException e) {
            return SetResult.DB_ERROR;
        }
    }

    private SetResult write(int userId, SecurityQuestion q, String answer) throws SQLException {
        String hash = PasswordHasher.hash(normalizeAnswer(answer));
        return repository.save(userId, q.key(), hash) ? SetResult.SUCCESS : SetResult.USER_NOT_FOUND;
    }

    /** True if the user has chosen a question and answer. False also when the database can't be read. */
    public boolean hasSecurityQuestion(int userId) {
        return getQuestion(userId).isPresent();
    }

    /** The user's chosen question, or empty if none is set (or the database can't be read). */
    public Optional<SecurityQuestion> getQuestion(int userId) {
        try {
            return repository.find(userId)
                    .filter(s -> s.answerHash() != null)
                    .flatMap(s -> SecurityQuestion.fromKey(s.questionKey()));
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /** Checks an answer, counting wrong ones towards the lock. */
    public VerifyResult verifyAnswer(int userId, String answer) {
        try {
            Optional<Stored> found = repository.find(userId);
            if (found.isEmpty() || found.get().answerHash() == null) {
                return new VerifyResult(VerifyStatus.NOT_SET, 0, null);
            }
            Stored s = found.get();
            LocalDateTime now = LocalDateTime.now(clock);
            boolean lockActive = s.lockedUntil() != null && now.isBefore(s.lockedUntil());
            if (lockActive) {
                return new VerifyResult(VerifyStatus.LOCKED, 0, s.lockedUntil());
            }
            // An expired lock starts the count again.
            int failed = s.lockedUntil() != null ? 0 : Math.max(0, s.failedAttempts());

            String normalized = normalizeAnswer(answer);
            if (normalized != null && !normalized.isEmpty() && PasswordHasher.verify(normalized, s.answerHash())) {
                if (s.failedAttempts() != 0 || s.lockedUntil() != null) {
                    repository.recordAttempts(userId, 0, null);
                }
                return new VerifyResult(VerifyStatus.CORRECT, MAX_ATTEMPTS, null);
            }

            failed++;
            if (failed >= MAX_ATTEMPTS) {
                LocalDateTime until = now.plusMinutes(LOCK_MINUTES);
                repository.recordAttempts(userId, failed, until);
                return new VerifyResult(VerifyStatus.LOCKED, 0, until);
            }
            repository.recordAttempts(userId, failed, null);
            return new VerifyResult(VerifyStatus.WRONG_ANSWER, MAX_ATTEMPTS - failed, null);
        } catch (SQLException e) {
            return new VerifyResult(VerifyStatus.DB_ERROR, 0, null);
        }
    }
}
