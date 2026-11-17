package service;

import dao.UserRepository;
import model.AppointmentReminderDefaults;
import model.BookingCommunicationPreferences;
import model.User;
import org.junit.jupiter.api.Test;
import service.email.Email;
import service.email.EmailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Unique per test, because the OTP store is static. */
    private static String freshEmail() {
        return "reset" + SEQ.incrementAndGet() + "_" + System.nanoTime() + "@example.com";
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private static final class CapturingSender extends EmailSender {
        String lastCode;
        boolean result = true;
        @Override public boolean send(Email email) {
            lastCode = String.valueOf(email.getTemplateContext().get("otpCode"));
            return result;
        }
    }

    private static final class FakeUsers implements UserRepository {
        final Map<String, String> hashes = new HashMap<>();
        @Override public boolean emailExists(String email) { return hashes.containsKey(email); }
        @Override public boolean insertUser(String email, String passwordHash) { hashes.put(email, passwordHash); return true; }
        @Override public String getPasswordHashByEmail(String email) { return hashes.get(email); }
        @Override public boolean updatePasswordHashByEmail(String email, String passwordHash) {
            if (!hashes.containsKey(email)) return false;
            hashes.put(email, passwordHash);
            return true;
        }
        @Override public Integer getUserIdByEmail(String email) { return null; }
        @Override public String getEmailByUserId(int userId) { return null; }
        @Override public String getDisplayNameByUserId(int userId) { return null; }
        @Override public Optional<User> findById(int userId) { return Optional.empty(); }
        @Override public void deleteUserById(int userId) { }
        @Override public int getDefaultClinic(int userId) { return -1; }
        @Override public boolean defaultClinicExists(int userID) { return false; }
        @Override public boolean updateDefaultClinic(int userId, int clinicId) { return false; }
        @Override public Optional<AppointmentReminderDefaults> getDefaultAppointmentReminderTemplate(int userId) { return Optional.empty(); }
        @Override public void saveDefaultAppointmentReminderTemplate(int userId, AppointmentReminderDefaults defaults) { }
        @Override public BookingCommunicationPreferences getBookingCommunicationPreferences(int userId) { return BookingCommunicationPreferences.defaults(); }
        @Override public void saveBookingCommunicationPreferences(int userId, BookingCommunicationPreferences prefs) { }
    }

    private final FakeUsers users = new FakeUsers();
    private final CapturingSender sender = new CapturingSender();
    private final MutableClock clock = new MutableClock();
    private final PasswordResetService service = new PasswordResetService(users, sender, clock);

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void fullFlow_resetsPassword_andCodeIsSingleUse() {
        String email = freshEmail();
        users.insertUser(email, "old");

        assertEquals(PasswordResetService.SendOtpResult.SENT, service.sendOtp(email));
        String code = sender.lastCode;
        assertEquals(PasswordResetService.VerifyOtpResult.VALID, service.verifyOtp(email, code));
        assertEquals(PasswordResetService.VerifyOtpResult.INVALID, service.verifyOtp(email, code));

        assertEquals(PasswordResetService.ResetPasswordResult.SUCCESS, service.resetPassword(email, "NewPassw0rd"));
        assertTrue(PasswordHasher.verify("NewPassw0rd", users.getPasswordHashByEmail(email)));
        assertEquals(PasswordResetService.ResetPasswordResult.OTP_NOT_VALIDATED,
                service.resetPassword(email, "Another1pass"));
    }

    @Test
    void resetWithoutVerifying_isRejected() {
        String email = freshEmail();
        users.insertUser(email, "old");
        service.sendOtp(email);
        assertEquals(PasswordResetService.ResetPasswordResult.OTP_NOT_VALIDATED,
                service.resetPassword(email, "NewPassw0rd"));
        assertEquals("old", users.getPasswordHashByEmail(email));
    }

    @Test
    void tooManyWrongGuesses_discardsCode() {
        String email = freshEmail();
        users.insertUser(email, "old");
        service.sendOtp(email);
        String code = sender.lastCode;

        for (int i = 1; i < PasswordResetService.MAX_VERIFY_ATTEMPTS; i++) {
            assertEquals(PasswordResetService.VerifyOtpResult.INVALID, service.verifyOtp(email, wrongCode(code)));
        }
        assertEquals(PasswordResetService.VerifyOtpResult.TOO_MANY_ATTEMPTS, service.verifyOtp(email, wrongCode(code)));
        // even the right code is now useless
        assertEquals(PasswordResetService.VerifyOtpResult.INVALID, service.verifyOtp(email, code));
        // and a new code can be requested straight away
        assertEquals(PasswordResetService.SendOtpResult.SENT, service.sendOtp(email));
    }

    @Test
    void expiredCode_isRejected() {
        String email = freshEmail();
        users.insertUser(email, "old");
        service.sendOtp(email);
        String code = sender.lastCode;

        clock.advance(PasswordResetService.OTP_TTL.plusSeconds(1));
        assertEquals(PasswordResetService.VerifyOtpResult.EXPIRED, service.verifyOtp(email, code));
    }

    @Test
    void resendWithinCooldown_isRejected_thenAllowed() {
        String email = freshEmail();
        users.insertUser(email, "old");
        assertEquals(PasswordResetService.SendOtpResult.SENT, service.sendOtp(email));
        String first = sender.lastCode;

        assertEquals(PasswordResetService.SendOtpResult.TOO_SOON, service.sendOtp(email));
        clock.advance(PasswordResetService.RESEND_COOLDOWN.plusSeconds(1));
        assertEquals(PasswordResetService.SendOtpResult.SENT, service.sendOtp(email));

        // only the newest code is valid
        if (!first.equals(sender.lastCode)) {
            assertEquals(PasswordResetService.VerifyOtpResult.INVALID, service.verifyOtp(email, first));
        }
        assertEquals(PasswordResetService.VerifyOtpResult.VALID, service.verifyOtp(email, sender.lastCode));
    }

    @Test
    void failedDelivery_doesNotStartCooldown() {
        String email = freshEmail();
        users.insertUser(email, "old");
        sender.result = false;
        assertEquals(PasswordResetService.SendOtpResult.EMAIL_SEND_FAILED, service.sendOtp(email));
        sender.result = true;
        assertEquals(PasswordResetService.SendOtpResult.SENT, service.sendOtp(email));
    }

    @Test
    void unknownOrInvalidEmail_isReported() {
        assertEquals(PasswordResetService.SendOtpResult.EMAIL_NOT_FOUND, service.sendOtp(freshEmail()));
        assertEquals(PasswordResetService.SendOtpResult.INVALID_EMAIL, service.sendOtp("not-an-email"));
        assertNull(sender.lastCode);
    }
}
