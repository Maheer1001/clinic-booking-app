package service;

import dao.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.AccountSecurityService.ChangeEmailResult;
import service.AccountSecurityService.ChangePasswordResult;
import service.AccountSecurityService.SaveNameResult;

import java.sql.SQLException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountSecurityServiceTest {

    /** In-memory accounts keyed by id. */
    static final class FakeAccounts implements AccountRepository {
        final Map<Integer, String[]> rows = new HashMap<>(); // first, last, email, passwordHash
        boolean fail;
        SQLException updateEmailFailure;

        void add(int id, String first, String last, String email, String password) {
            rows.put(id, new String[]{first, last, email, PasswordHasher.hash(password)});
        }

        private void check() throws SQLException { if (fail) throw new SQLException("down"); }

        @Override
        public AccountService.AccountInfo getAccountInfo(int userId) throws SQLException {
            check();
            String[] r = rows.get(userId);
            return r == null ? null : new AccountService.AccountInfo(r[0], r[1], null, null, r[2]);
        }

        @Override
        public boolean updateName(int userId, String firstName, String lastName) throws SQLException {
            check();
            String[] r = rows.get(userId);
            if (r == null) return false;
            r[0] = firstName;
            r[1] = lastName;
            return true;
        }

        @Override
        public String getEmailByUserId(int userId) throws SQLException {
            check();
            String[] r = rows.get(userId);
            return r == null ? null : r[2];
        }

        @Override
        public String getPasswordHashByUserId(int userId) throws SQLException {
            check();
            String[] r = rows.get(userId);
            return r == null ? null : r[3];
        }

        @Override
        public boolean updatePasswordHashByUserId(int userId, String passwordHash) throws SQLException {
            check();
            String[] r = rows.get(userId);
            if (r == null) return false;
            r[3] = passwordHash;
            return true;
        }

        @Override
        public boolean emailTakenByOtherUser(int userId, String email) throws SQLException {
            check();
            return rows.entrySet().stream()
                    .anyMatch(e -> e.getKey() != userId && e.getValue()[2].equalsIgnoreCase(email));
        }

        @Override
        public boolean updateEmail(int userId, String newEmail) throws SQLException {
            check();
            if (updateEmailFailure != null) throw updateEmailFailure;
            String[] r = rows.get(userId);
            if (r == null) return false;
            r[2] = newEmail;
            return true;
        }
    }

    private final FakeAccounts accounts = new FakeAccounts();
    private final SecurityQuestionServiceTest.FakeRepo questions = new SecurityQuestionServiceTest.FakeRepo();
    private final SecurityQuestionService questionService =
            new SecurityQuestionService(questions, Clock.systemDefaultZone());
    private final AccountSecurityService service = new AccountSecurityService(accounts, questionService);

    @BeforeEach
    void users() {
        accounts.add(1, null, null, "me@example.com", "oldpass123");
        accounts.add(2, "Other", "Person", "taken@example.com", "otherpass1");
        questions.addUser(1);
        questions.addUser(2);
    }

    // ---------------------------------------------------------------- name

    @Test
    void accountNameMissing_whenEitherNameIsBlank() {
        assertTrue(service.accountNameMissing(1));
        accounts.rows.get(1)[0] = "Sam";
        assertTrue(service.accountNameMissing(1), "last name still missing");
        accounts.rows.get(1)[1] = "  ";
        assertTrue(service.accountNameMissing(1));
        assertFalse(service.accountNameMissing(2));
        assertFalse(service.accountNameMissing(99), "unknown user: don't offer");
        accounts.fail = true;
        assertFalse(service.accountNameMissing(1), "error: don't offer");
    }

    @Test
    void saveName_trims_andValidates() {
        assertEquals(SaveNameResult.SUCCESS, service.saveName(1, "  Sam ", " Lee "));
        assertEquals("Sam", accounts.rows.get(1)[0]);
        assertEquals("Lee", accounts.rows.get(1)[1]);
        assertFalse(service.accountNameMissing(1));

        assertEquals(SaveNameResult.INVALID_NAME, service.saveName(1, "", "Lee"));
        assertEquals(SaveNameResult.INVALID_NAME, service.saveName(1, "Sam", null));
        assertEquals(SaveNameResult.INVALID_NAME, service.saveName(1, "x".repeat(51), "Lee"));
        assertEquals(SaveNameResult.USER_NOT_FOUND, service.saveName(99, "Sam", "Lee"));
        accounts.fail = true;
        assertEquals(SaveNameResult.DB_ERROR, service.saveName(1, "Sam", "Lee"));
    }

    // ---------------------------------------------------------------- password

    @Test
    void changePassword_success_replacesTheHash() {
        assertEquals(ChangePasswordResult.SUCCESS, service.changePassword(1, "oldpass123", "newpass456"));
        String hash = accounts.rows.get(1)[3];
        assertTrue(hash.startsWith("pbkdf2$"));
        assertTrue(PasswordHasher.verify("newpass456", hash));
        assertFalse(PasswordHasher.verify("oldpass123", hash));
    }

    @Test
    void changePassword_wrongCurrent_isRejected_andNothingChanges() {
        String before = accounts.rows.get(1)[3];
        assertEquals(ChangePasswordResult.WRONG_CURRENT_PASSWORD, service.changePassword(1, "nope12345", "newpass456"));
        assertEquals(ChangePasswordResult.WRONG_CURRENT_PASSWORD, service.changePassword(1, null, "newpass456"));
        assertEquals(before, accounts.rows.get(1)[3]);
    }

    @Test
    void changePassword_usesTheSignUpRules() {
        assertEquals(ChangePasswordResult.WEAK_PASSWORD, service.changePassword(1, "oldpass123", "short1"));
        assertEquals(ChangePasswordResult.WEAK_PASSWORD, service.changePassword(1, "oldpass123", "lettersonly"));
        assertEquals(ChangePasswordResult.WEAK_PASSWORD, service.changePassword(1, "oldpass123", "12345678"));
        assertEquals(ChangePasswordResult.WEAK_PASSWORD, service.changePassword(1, "oldpass123", null));
        assertEquals(ChangePasswordResult.SAME_PASSWORD, service.changePassword(1, "oldpass123", "oldpass123"));
        assertEquals(ChangePasswordResult.USER_NOT_FOUND, service.changePassword(99, "oldpass123", "newpass456"));
        assertTrue(ChangePasswordResult.WEAK_PASSWORD.message().contains(AuthService.PASSWORD_RULES));
        accounts.fail = true;
        assertEquals(ChangePasswordResult.DB_ERROR, service.changePassword(1, "oldpass123", "newpass456"));
    }

    // ---------------------------------------------------------------- email

    @Test
    void changeEmail_needsTheRightAnswer_thenSavesNormalisedEmail() {
        questionService.setSecurityQuestion(1, "FIRST_JOB", "Paper Round");

        AccountSecurityService.EmailChange wrong = service.changeEmail(1, "milk round", "new@example.com");
        assertEquals(ChangeEmailResult.WRONG_ANSWER, wrong.result());
        assertTrue(wrong.message().contains("attempts left"));
        assertEquals("me@example.com", accounts.rows.get(1)[2]);

        AccountSecurityService.EmailChange ok = service.changeEmail(1, "  paper   ROUND ", "  New@Example.COM ");
        assertTrue(ok.success());
        assertEquals("me@example.com", ok.oldEmail());
        assertEquals("new@example.com", ok.newEmail());
        assertEquals("new@example.com", accounts.rows.get(1)[2]);
        assertEquals(0, questions.rows.get(1).failedAttempts(), "correct answer resets the count");
    }

    @Test
    void changeEmail_rejectsBadFormat_beforeUsingAnAttempt() {
        questionService.setSecurityQuestion(1, "FIRST_JOB", "Paper Round");
        assertEquals(ChangeEmailResult.INVALID_EMAIL, service.changeEmail(1, "wrong", "not-an-email").result());
        assertEquals(ChangeEmailResult.INVALID_EMAIL, service.changeEmail(1, "wrong", null).result());
        assertEquals(0, questions.rows.get(1).failedAttempts());
        assertEquals(ChangeEmailResult.SAME_EMAIL, service.changeEmail(1, "wrong", "ME@example.com").result());
        assertEquals(0, questions.rows.get(1).failedAttempts());
    }

    @Test
    void changeEmail_racingAnotherAccount_uniqueIndexGivesEmailAlreadyUsed() {
        questionService.setSecurityQuestion(1, "FIRST_JOB", "Paper Round");
        accounts.updateEmailFailure = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"user_email_lower_key\"", "23505");
        assertEquals(ChangeEmailResult.EMAIL_ALREADY_USED,
                service.changeEmail(1, "paper round", "new@example.com").result());
        accounts.updateEmailFailure = new SQLException("connection lost", "08006");
        assertEquals(ChangeEmailResult.DB_ERROR,
                service.changeEmail(1, "paper round", "new@example.com").result());
    }

    @Test
    void changeEmail_emailUsedByAnotherAccount_ignoringCase() {
        questionService.setSecurityQuestion(1, "FIRST_JOB", "Paper Round");
        assertEquals(ChangeEmailResult.EMAIL_ALREADY_USED,
                service.changeEmail(1, "paper round", "TAKEN@example.com").result());
        assertEquals("me@example.com", accounts.rows.get(1)[2]);
    }

    @Test
    void changeEmail_withoutQuestion_orLocked() {
        assertEquals(ChangeEmailResult.NO_SECURITY_QUESTION,
                service.changeEmail(1, "anything", "new@example.com").result());

        questionService.setSecurityQuestion(1, "FIRST_JOB", "Paper Round");
        for (int i = 0; i < SecurityQuestionService.MAX_ATTEMPTS; i++) {
            service.changeEmail(1, "wrong", "new@example.com");
        }
        AccountSecurityService.EmailChange locked = service.changeEmail(1, "paper round", "new@example.com");
        assertEquals(ChangeEmailResult.LOCKED, locked.result());
        assertTrue(locked.message().startsWith("Too many wrong answers"));
        assertEquals("me@example.com", accounts.rows.get(1)[2]);
    }

    // ---------------------------------------------------------------- security question

    @Test
    void changeSecurityQuestion_needsThePassword() {
        questionService.setSecurityQuestion(1, "FIRST_JOB", "Paper Round");
        assertEquals(SecurityQuestionService.SetResult.WRONG_PASSWORD,
                service.changeSecurityQuestion(1, "bad", "FIRST_PET", "Rex"));
        assertTrue(questionService.verifyAnswer(1, "paper round").correct());

        assertEquals(SecurityQuestionService.SetResult.SUCCESS,
                service.changeSecurityQuestion(1, "oldpass123", "FIRST_PET", "Rex"));
        assertEquals(model.SecurityQuestion.FIRST_PET, questionService.getQuestion(1).orElseThrow());
        assertTrue(questionService.verifyAnswer(1, "REX").correct());
        assertEquals(SecurityQuestionService.SetResult.UNKNOWN_QUESTION,
                service.changeSecurityQuestion(1, "oldpass123", "??", "Rex"));
    }
}
