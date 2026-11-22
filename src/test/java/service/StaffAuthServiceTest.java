package service;

import dao.SecurityQuestionRepository;
import dao.StaffAccountRepository;
import model.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Staff sign-up: the clinic code decides the clinic, wrong codes are limited and reveal nothing. */
class StaffAuthServiceTest {

    private static final String PASSWORD = "Password1";
    private static final String QUESTION = model.SecurityQuestion.all().get(0).key();

    private final MutableClock clock = new MutableClock(Instant.parse("2030-01-10T10:00:00Z"));
    private FakeRepo repo;
    private StaffAuthService service;

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        repo.codes.add(new StaffAccountRepository.ClinicCodeHash(1, PasswordHasher.hash("clinic1")));
        repo.codes.add(new StaffAccountRepository.ClinicCodeHash(2, PasswordHasher.hash("clinic2")));
        service = new StaffAuthService(repo, new SecurityQuestionService(new NoQuestions(), clock), clock);
    }

    @Test
    void rightCode_createsClinicianAtThatClinic() {
        StaffAuthService.StaffSignUpResult r = service.signUpStaff(" Nurse@Clinic.test ", PASSWORD, true,
                " Clinic2 ", QUESTION, "Rover");
        assertTrue(r.success(), r.message());
        assertEquals(2, r.clinicId());
        assertEquals(1, repo.created.size());
        FakeRepo.Created c = repo.created.get(0);
        assertEquals("nurse@clinic.test", c.email());
        assertEquals(StaffRole.CLINICIAN, c.role());
        assertEquals(2, c.clinicId());
        assertTrue(PasswordHasher.verify(PASSWORD, c.hash()));
    }

    @Test
    void wrongCode_isRejectedWithoutNamingClinics_andCounted() {
        StaffAuthService.StaffSignUpResult r = service.signUpStaff("nurse@clinic.test", PASSWORD, true,
                "clinic9", QUESTION, "Rover");
        assertEquals(StaffAuthService.Status.INVALID_STAFF_CODE, r.status());
        assertEquals("That staff code isn't valid. Check it with your clinic admin.", r.message());
        assertNull(r.clinicId());
        assertTrue(repo.created.isEmpty());
        assertEquals(1, repo.attempts.size());
        // the attempt is stored against a hash of the email, never the address
        assertFalse(repo.attempts.get(0).emailHash().contains("nurse"));
    }

    @Test
    void attemptLimit_locksAfterFive_evenWithTheRightCode_thenExpires() {
        for (int i = 0; i < StaffAuthService.MAX_ATTEMPTS_PER_EMAIL; i++) {
            service.signUpStaff("nurse@clinic.test", PASSWORD, true, "guess" + i, QUESTION, "Rover");
        }
        StaffAuthService.StaffSignUpResult locked = service.signUpStaff("nurse@clinic.test", PASSWORD, true,
                "clinic1", QUESTION, "Rover");
        assertEquals(StaffAuthService.Status.TOO_MANY_ATTEMPTS, locked.status());
        assertTrue(repo.created.isEmpty());

        clock.advanceMinutes(StaffAuthService.WINDOW_MINUTES + 1);
        assertTrue(service.signUpStaff("nurse@clinic.test", PASSWORD, true, "clinic1", QUESTION, "Rover").success());
        assertTrue(repo.attempts.isEmpty(), "attempts cleared after a successful sign-up");
    }

    @Test
    void overallLimit_stopsGuessingAcrossEmails() {
        for (int i = 0; i < StaffAuthService.MAX_ATTEMPTS_OVERALL; i++) {
            repo.recordFailedCodeAttempt("hash" + i, LocalDateTime.now(clock));
        }
        assertEquals(StaffAuthService.Status.TOO_MANY_ATTEMPTS,
                service.signUpStaff("new@clinic.test", PASSWORD, true, "clinic1", QUESTION, "Rover").status());
    }

    @Test
    void usualSignUpChecks_comeFirst() {
        assertEquals(StaffAuthService.Status.TERMS_NOT_ACCEPTED,
                service.signUpStaff("a@b.test", PASSWORD, false, "clinic1", QUESTION, "Rover").status());
        assertEquals(StaffAuthService.Status.INVALID_EMAIL,
                service.signUpStaff("not-an-email", PASSWORD, true, "clinic1", QUESTION, "Rover").status());
        assertEquals(StaffAuthService.Status.WEAK_PASSWORD,
                service.signUpStaff("a@b.test", "short", true, "clinic1", QUESTION, "Rover").status());
        assertEquals(StaffAuthService.Status.INVALID_SECURITY_QUESTION,
                service.signUpStaff("a@b.test", PASSWORD, true, "clinic1", "nope", "Rover").status());
        assertEquals(StaffAuthService.Status.INVALID_SECURITY_ANSWER,
                service.signUpStaff("a@b.test", PASSWORD, true, "clinic1", QUESTION, " ").status());
        assertEquals(StaffAuthService.Status.MISSING_STAFF_CODE,
                service.signUpStaff("a@b.test", PASSWORD, true, " ", QUESTION, "Rover").status());
        repo.existing.add("taken@b.test");
        assertEquals(StaffAuthService.Status.EMAIL_ALREADY_USED,
                service.signUpStaff("taken@b.test", PASSWORD, true, "clinic1", QUESTION, "Rover").status());
        assertTrue(repo.created.isEmpty());
        assertTrue(repo.attempts.isEmpty());
    }

    @Test
    void databaseDown_isReported() {
        repo.down = true;
        assertEquals(StaffAuthService.Status.DB_ERROR,
                service.signUpStaff("a@b.test", PASSWORD, true, "clinic1", QUESTION, "Rover").status());
    }

    // ------------------------------------------------------------ fakes

    private static final class FakeRepo implements StaffAccountRepository {
        record Attempt(String emailHash, LocalDateTime at) {}
        record Created(String email, String hash, StaffRole role, int clinicId) {}

        final List<ClinicCodeHash> codes = new ArrayList<>();
        final List<Attempt> attempts = new ArrayList<>();
        final List<Created> created = new ArrayList<>();
        final List<String> existing = new ArrayList<>();
        boolean down;

        private void check() throws SQLException {
            if (down) throw new SQLException("down", "08000");
        }

        @Override public List<ClinicCodeHash> allStaffCodeHashes() throws SQLException { check(); return codes; }

        @Override public int failedCodeAttempts(String emailHash, LocalDateTime since) throws SQLException {
            check();
            return (int) attempts.stream().filter(a -> a.emailHash().equals(emailHash) && !a.at().isBefore(since)).count();
        }

        @Override public int failedCodeAttemptsAll(LocalDateTime since) throws SQLException {
            check();
            return (int) attempts.stream().filter(a -> !a.at().isBefore(since)).count();
        }

        @Override public void recordFailedCodeAttempt(String emailHash, LocalDateTime at) {
            attempts.add(new Attempt(emailHash, at));
        }

        @Override public void clearFailedCodeAttempts(String emailHash) {
            attempts.removeIf(a -> a.emailHash().equals(emailHash));
        }

        @Override public boolean emailExists(String email) throws SQLException { check(); return existing.contains(email); }

        @Override public int createStaffUser(String email, String passwordHash, StaffRole role, int clinicId,
                                             boolean mustResetPassword) {
            created.add(new Created(email, passwordHash, role, clinicId));
            return 100 + created.size();
        }
    }

    private static final class NoQuestions implements SecurityQuestionRepository {
        final Map<Integer, Stored> saved = new HashMap<>();
        @Override public Optional<Stored> find(int userId) { return Optional.ofNullable(saved.get(userId)); }
        @Override public boolean save(int userId, String questionKey, String answerHash) {
            saved.put(userId, new Stored(questionKey, answerHash, 0, null));
            return true;
        }
        @Override public void recordAttempts(int userId, int failedAttempts, LocalDateTime lockedUntil) {}
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceMinutes(long m) { now = now.plusSeconds(60 * m); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
