package service;

import dao.SecurityQuestionRepository;
import model.SecurityQuestion;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static service.SecurityQuestionService.SetResult;
import static service.SecurityQuestionService.VerifyStatus;

class SecurityQuestionServiceTest {

    /** In-memory "User" security columns. */
    static final class FakeRepo implements SecurityQuestionRepository {
        final Map<Integer, Stored> rows = new HashMap<>();
        boolean fail;

        void addUser(int id) { rows.put(id, new Stored(null, null, 0, null)); }

        @Override
        public Optional<Stored> find(int userId) throws SQLException {
            if (fail) throw new SQLException("down");
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public boolean save(int userId, String questionKey, String answerHash) throws SQLException {
            if (fail) throw new SQLException("down");
            if (!rows.containsKey(userId)) return false;
            rows.put(userId, new Stored(questionKey, answerHash, 0, null));
            return true;
        }

        @Override
        public void recordAttempts(int userId, int failedAttempts, LocalDateTime lockedUntil) throws SQLException {
            if (fail) throw new SQLException("down");
            Stored s = rows.get(userId);
            rows.put(userId, new Stored(s.questionKey(), s.answerHash(), failedAttempts, lockedUntil));
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final FakeRepo repo = new FakeRepo();
    private final MutableClock clock = new MutableClock();
    private final SecurityQuestionService service = new SecurityQuestionService(repo, clock);

    @Test
    void normalizeAnswer_trimsCollapsesSpacesAndIgnoresCase() {
        assertEquals("st mary's", SecurityQuestionService.normalizeAnswer("  St   MARY'S \t"));
        assertEquals("ab cd", SecurityQuestionService.normalizeAnswer("ab\n\ncd"));
        // full-width letters fold to ASCII under NFKC
        assertEquals("smith", SecurityQuestionService.normalizeAnswer("ＳＭＩＴＨ"));
        assertNull(SecurityQuestionService.normalizeAnswer(null));
    }

    @Test
    void isValidAnswer_lengthLimitsAfterNormalising() {
        assertFalse(SecurityQuestionService.isValidAnswer(null));
        assertFalse(SecurityQuestionService.isValidAnswer("   "));
        assertFalse(SecurityQuestionService.isValidAnswer(" a "));
        assertTrue(SecurityQuestionService.isValidAnswer("Li"));
        assertTrue(SecurityQuestionService.isValidAnswer("x".repeat(SecurityQuestionService.MAX_ANSWER_LENGTH)));
        assertFalse(SecurityQuestionService.isValidAnswer("x".repeat(SecurityQuestionService.MAX_ANSWER_LENGTH + 1)));
    }

    @Test
    void questionList_hasTheRequiredQuestions_andKeysRoundTrip() {
        assertTrue(SecurityQuestion.all().size() >= 6);
        assertTrue(SecurityQuestion.all().contains(SecurityQuestion.MOTHERS_MAIDEN_NAME));
        assertTrue(SecurityQuestion.all().contains(SecurityQuestion.FIRST_JOB));
        assertTrue(SecurityQuestion.all().contains(SecurityQuestion.CHILDHOOD_TOWN));
        assertTrue(SecurityQuestion.all().contains(SecurityQuestion.FIRST_SECONDARY_TEACHER));
        for (SecurityQuestion q : SecurityQuestion.all()) {
            assertEquals(Optional.of(q), SecurityQuestion.fromKey(q.key()));
            assertFalse(q.prompt().isBlank());
        }
        assertEquals(Optional.of(SecurityQuestion.FIRST_JOB), SecurityQuestion.fromKey(" first_job "));
        assertEquals(Optional.empty(), SecurityQuestion.fromKey("NOPE"));
        assertEquals(Optional.empty(), SecurityQuestion.fromKey(null));
    }

    @Test
    void set_storesAHashNotTheAnswer_andVerifyIsNormalised() {
        repo.addUser(1);
        assertEquals(SetResult.SUCCESS, service.setSecurityQuestion(1, "CHILDHOOD_TOWN", "  Milton   Keynes "));

        String hash = repo.rows.get(1).answerHash();
        assertTrue(hash.startsWith("pbkdf2$"), "hashed with PasswordHasher");
        assertFalse(hash.toLowerCase().contains("milton"));
        assertEquals("CHILDHOOD_TOWN", repo.rows.get(1).questionKey());

        assertTrue(service.hasSecurityQuestion(1));
        assertEquals(Optional.of(SecurityQuestion.CHILDHOOD_TOWN), service.getQuestion(1));
        assertTrue(service.verifyAnswer(1, "milton keynes").correct());
        assertTrue(service.verifyAnswer(1, "MILTON KEYNES").correct());
        assertEquals(VerifyStatus.WRONG_ANSWER, service.verifyAnswer(1, "miltonkeynes").status());
    }

    @Test
    void set_rejectsUnknownQuestion_badAnswer_missingUser_andOverwrite() {
        repo.addUser(1);
        assertEquals(SetResult.UNKNOWN_QUESTION, service.setSecurityQuestion(1, "FAVOURITE_COLOUR", "blue"));
        assertEquals(SetResult.INVALID_ANSWER, service.setSecurityQuestion(1, "FIRST_JOB", " "));
        assertEquals(SetResult.USER_NOT_FOUND, service.setSecurityQuestion(2, "FIRST_JOB", "paper round"));
        assertFalse(service.hasSecurityQuestion(1));

        assertEquals(SetResult.SUCCESS, service.setSecurityQuestion(1, "FIRST_JOB", "paper round"));
        assertEquals(SetResult.ALREADY_SET, service.setSecurityQuestion(1, "FIRST_PET", "rex"));
        assertEquals(Optional.of(SecurityQuestion.FIRST_JOB), service.getQuestion(1));
        assertFalse(SetResult.ALREADY_SET.message().isBlank());
    }

    @Test
    void verify_withoutQuestion_isNotSet() {
        repo.addUser(1);
        assertEquals(VerifyStatus.NOT_SET, service.verifyAnswer(1, "x").status());
        assertEquals(VerifyStatus.NOT_SET, service.verifyAnswer(99, "x").status());
        assertFalse(service.hasSecurityQuestion(99));
    }

    @Test
    void wrongAnswers_countDown_thenLock_evenForTheRightAnswer() {
        repo.addUser(1);
        service.setSecurityQuestion(1, "FIRST_PET", "Rex");

        for (int left = SecurityQuestionService.MAX_ATTEMPTS - 1; left >= 1; left--) {
            SecurityQuestionService.VerifyResult r = service.verifyAnswer(1, "fido");
            assertEquals(VerifyStatus.WRONG_ANSWER, r.status());
            assertEquals(left, r.attemptsLeft());
        }
        SecurityQuestionService.VerifyResult locked = service.verifyAnswer(1, "fido");
        assertEquals(VerifyStatus.LOCKED, locked.status());
        assertEquals(LocalDateTime.now(clock).plusMinutes(SecurityQuestionService.LOCK_MINUTES), locked.lockedUntil());
        assertTrue(locked.message().contains("Try again"));

        // the lock is in the stored row, so a new service (new session / app restart) still sees it
        SecurityQuestionService afterRestart = new SecurityQuestionService(repo, clock);
        assertEquals(VerifyStatus.LOCKED, afterRestart.verifyAnswer(1, "rex").status());

        clock.advance(Duration.ofMinutes(SecurityQuestionService.LOCK_MINUTES).plusSeconds(1));
        assertEquals(VerifyStatus.WRONG_ANSWER, service.verifyAnswer(1, "fido").status(), "count starts again");
        assertEquals(1, repo.rows.get(1).failedAttempts());
        assertTrue(service.verifyAnswer(1, "rex").correct());
        assertEquals(0, repo.rows.get(1).failedAttempts(), "correct answer resets the count");
        assertNull(repo.rows.get(1).lockedUntil());
    }

    @Test
    void wrongAnswerMessage_saysHowManyAttemptsAreLeft() {
        assertEquals("That answer doesn't match. 1 attempt left.",
                new SecurityQuestionService.VerifyResult(VerifyStatus.WRONG_ANSWER, 1, null).message());
        assertEquals("That answer doesn't match. 3 attempts left.",
                new SecurityQuestionService.VerifyResult(VerifyStatus.WRONG_ANSWER, 3, null).message());
        assertEquals("", new SecurityQuestionService.VerifyResult(VerifyStatus.CORRECT, 5, null).message());
    }

    @Test
    void databaseErrors_areReported_notThrown() {
        repo.addUser(1);
        repo.fail = true;
        assertEquals(SetResult.DB_ERROR, service.setSecurityQuestion(1, "FIRST_JOB", "paper round"));
        assertEquals(VerifyStatus.DB_ERROR, service.verifyAnswer(1, "x").status());
        assertFalse(service.hasSecurityQuestion(1));
    }
}
