package service;

import dao.UserDAO;
import dao.UserRepository;
import service.email.EmailSender;
import service.email.PasswordResetOtpEmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stateless API over an in-memory OTP store.
 * OTPs expire after 10 minutes, are tied to a single email address, can be used once,
 * and are discarded after {@link #MAX_VERIFY_ATTEMPTS} wrong guesses.
 */
public class PasswordResetService {

    public enum SendOtpResult {
        SENT,
        EMAIL_NOT_FOUND,
        INVALID_EMAIL,
        TOO_SOON,
        EMAIL_SEND_FAILED,
        DB_ERROR
    }

    public enum VerifyOtpResult {
        VALID,
        INVALID,
        EXPIRED,
        TOO_MANY_ATTEMPTS
    }

    public enum ResetPasswordResult {
        SUCCESS,
        WEAK_PASSWORD,
        OTP_NOT_VALIDATED,
        EMAIL_NOT_FOUND,
        DB_ERROR
    }

    static final Duration OTP_TTL = Duration.ofMinutes(10);
    /** Minimum gap between two codes for the same email, to stop inbox flooding. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    /** Wrong guesses allowed per code before it is thrown away (a 6-digit code is otherwise brute-forceable). */
    static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom RNG = new SecureRandom();

    private static final class OtpRecord {
        final String code;
        final Instant issuedAt;
        final Instant expiresAt;
        final AtomicInteger failedAttempts = new AtomicInteger();

        OtpRecord(String code, Instant issuedAt, Instant expiresAt) {
            this.code = code;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<String, OtpRecord> otpByEmail = new ConcurrentHashMap<>();
    private static final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();
    private static final Map<String, Instant> validatedEmailUntil = new ConcurrentHashMap<>();

    private final UserRepository userRepo;
    private final EmailSender emailSender;
    private final Clock clock;

    public PasswordResetService() {
        this(new UserDAO(), new EmailSender());
    }

    public PasswordResetService(UserRepository userRepo, EmailSender emailSender) {
        this(userRepo, emailSender, Clock.systemUTC());
    }

    PasswordResetService(UserRepository userRepo, EmailSender emailSender, Clock clock) {
        this.userRepo = userRepo;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    public SendOtpResult sendOtp(String emailRaw) {
        String email = normalizeEmail(emailRaw);
        if (!AuthService.isValidEmail(email)) return SendOtpResult.INVALID_EMAIL;

        Instant now = clock.instant();
        Instant last = lastSentAt.get(email);
        if (last != null && now.isBefore(last.plus(RESEND_COOLDOWN))) return SendOtpResult.TOO_SOON;

        try {
            if (!userRepo.emailExists(email)) return SendOtpResult.EMAIL_NOT_FOUND;

            String code = generate6DigitCode();
            // only the most recently issued OTP should be valid
            otpByEmail.put(email, new OtpRecord(code, now, now.plus(OTP_TTL)));
            lastSentAt.put(email, now);
            // if a user requests a new code, they must validate the new one
            validatedEmailUntil.remove(email);

            boolean ok = emailSender.send(new PasswordResetOtpEmail(email, email, code, OTP_TTL));
            if (!ok) {
                // let the user retry straight away if delivery failed
                lastSentAt.remove(email);
            }
            return ok ? SendOtpResult.SENT : SendOtpResult.EMAIL_SEND_FAILED;
        } catch (SQLException e) {
            return SendOtpResult.DB_ERROR;
        }
    }

    public VerifyOtpResult verifyOtp(String emailRaw, String codeRaw) {
        String email = normalizeEmail(emailRaw);
        String code = codeRaw != null ? codeRaw.trim() : "";
        if (!AuthService.isValidEmail(email) || code.isBlank()) return VerifyOtpResult.INVALID;

        OtpRecord rec = otpByEmail.get(email);
        if (rec == null) return VerifyOtpResult.INVALID;
        if (clock.instant().isAfter(rec.expiresAt)) {
            otpByEmail.remove(email, rec);
            return VerifyOtpResult.EXPIRED;
        }
        if (!constantTimeEquals(rec.code, code)) {
            if (rec.failedAttempts.incrementAndGet() >= MAX_VERIFY_ATTEMPTS) {
                otpByEmail.remove(email, rec);
                // allow a fresh code to be requested immediately
                lastSentAt.remove(email, rec.issuedAt);
                return VerifyOtpResult.TOO_MANY_ATTEMPTS;
            }
            return VerifyOtpResult.INVALID;
        }

        // single-use: consume OTP immediately and mark email as validated until expiry
        if (!otpByEmail.remove(email, rec)) return VerifyOtpResult.INVALID;
        validatedEmailUntil.put(email, rec.expiresAt);
        return VerifyOtpResult.VALID;
    }

    public ResetPasswordResult resetPassword(String emailRaw, String newPassword) {
        String email = normalizeEmail(emailRaw);
        if (!AuthService.isValidEmail(email)) return ResetPasswordResult.EMAIL_NOT_FOUND;
        if (!AuthService.isStrongEnoughPassword(newPassword)) return ResetPasswordResult.WEAK_PASSWORD;

        try {
            if (!userRepo.emailExists(email)) return ResetPasswordResult.EMAIL_NOT_FOUND;

            Instant validUntil = validatedEmailUntil.get(email);
            if (validUntil == null) return ResetPasswordResult.OTP_NOT_VALIDATED;
            if (clock.instant().isAfter(validUntil)) {
                validatedEmailUntil.remove(email);
                return ResetPasswordResult.OTP_NOT_VALIDATED;
            }

            String hash = AuthService.hashPassword(newPassword);
            boolean ok = userRepo.updatePasswordHashByEmail(email, hash);
            otpByEmail.remove(email);
            validatedEmailUntil.remove(email);
            return ok ? ResetPasswordResult.SUCCESS : ResetPasswordResult.DB_ERROR;
        } catch (SQLException e) {
            return ResetPasswordResult.DB_ERROR;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeEmail(String email) {
        return AuthService.normalize(email);
    }

    private static String generate6DigitCode() {
        int n = RNG.nextInt(1_000_000);
        return String.format("%06d", n);
    }
}
