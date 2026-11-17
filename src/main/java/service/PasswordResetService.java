package service;

import dao.UserDAO;
import dao.UserRepository;
import service.email.EmailSender;
import service.email.PasswordResetOtpEmail;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateless API over an in-memory OTP store.
 * OTPs expire after 10 minutes and are tied to a single email address.
 */
public class PasswordResetService {

    public enum SendOtpResult {
        SENT,
        EMAIL_NOT_FOUND,
        INVALID_EMAIL,
        EMAIL_SEND_FAILED,
        DB_ERROR
    }

    public enum VerifyOtpResult {
        VALID,
        INVALID,
        EXPIRED
    }

    public enum ResetPasswordResult {
        SUCCESS,
        WEAK_PASSWORD,
        OTP_NOT_VALIDATED,
        EMAIL_NOT_FOUND,
        DB_ERROR
    }

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RNG = new SecureRandom();

    private static final class OtpRecord {
        final String code;
        final Instant expiresAt;

        OtpRecord(String code, Instant expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<String, OtpRecord> otpByEmail = new ConcurrentHashMap<>();
    private static final Map<String, Instant> validatedEmailUntil = new ConcurrentHashMap<>();

    private final UserRepository userRepo;
    private final EmailSender emailSender;

    public PasswordResetService() {
        this(new UserDAO(), new EmailSender());
    }

    public PasswordResetService(UserRepository userRepo, EmailSender emailSender) {
        this.userRepo = userRepo;
        this.emailSender = emailSender;
    }

    public SendOtpResult sendOtp(String emailRaw) {
        String email = normalizeEmail(emailRaw);
        if (!AuthService.isValidEmail(email)) return SendOtpResult.INVALID_EMAIL;

        try {
            if (!userRepo.emailExists(email)) return SendOtpResult.EMAIL_NOT_FOUND;

            String code = generate6DigitCode();
            // only the most recently issued OTP should be valid
            Instant expiresAt = Instant.now().plus(OTP_TTL);
            otpByEmail.put(email, new OtpRecord(code, expiresAt));
            // if a user requests a new code, they must validate the new one
            validatedEmailUntil.remove(email);

            boolean ok = emailSender.send(new PasswordResetOtpEmail(email, email, code, OTP_TTL));
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
        if (Instant.now().isAfter(rec.expiresAt)) {
            otpByEmail.remove(email);
            return VerifyOtpResult.EXPIRED;
        }
        if (!rec.code.equals(code)) return VerifyOtpResult.INVALID;

        // single-use: consume OTP immediately and mark email as validated until expiry
        otpByEmail.remove(email);
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
            if (Instant.now().isAfter(validUntil)) {
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

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    private static String generate6DigitCode() {
        int n = RNG.nextInt(1_000_000);
        return String.format("%06d", n);
    }
}

