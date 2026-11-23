package controller;

import app.DB;
import app.Data;
import dao.SecurityQuestionDAO;
import dao.UserDAO;
import model.SecurityQuestion;
import org.junit.jupiter.api.*;
import service.AccountSecurityService;
import service.AccountService;
import service.AuthService;
import service.PasswordHasher;
import service.SecurityQuestionService;

import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Account APIs end to end against an in-memory H2 copy of the real schema (DB.initTables), with the
 * reminder hook replaced by a recorder.
 */
class AccountControllerTest {

    private static String savedUrl, savedUser, savedPass;

    private final UserDAO users = new UserDAO();
    private final SecurityQuestionService questions =
            new SecurityQuestionService(new SecurityQuestionDAO(), Clock.systemDefaultZone());
    private final List<String> moved = new ArrayList<>();
    private final AccountController controller = new AccountController(new AccountService(),
            new AccountSecurityService(users, questions), questions,
            (id, oldEmail, newEmail) -> moved.add(id + ":" + oldEmail + "->" + newEmail));

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:account_controller_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect()) {
            DB.initTables(c);
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
        Data.setCurrentSession(null);
    }

    private int newUser(String email, String password) throws Exception {
        assertTrue(users.insertUser(email, PasswordHasher.hash(password)));
        return users.getUserIdByEmail(email);
    }

    @Test
    void signUp_withSecurityQuestion_storesHashedAnswer() throws Exception {
        AuthService auth = new AuthService(users, questions);
        assertEquals(AuthService.SignUpResult.INVALID_SECURITY_QUESTION,
                auth.signUp("q1@example.com", "password1", true, "NOT_A_QUESTION", "x"));
        assertEquals(AuthService.SignUpResult.INVALID_SECURITY_ANSWER,
                auth.signUp("q1@example.com", "password1", true, "FIRST_JOB", " "));
        assertFalse(users.emailExists("q1@example.com"), "nothing created when the question is invalid");
        assertEquals(AuthService.SignUpResult.WEAK_PASSWORD,
                auth.signUp("q1@example.com", "weak", true, "FIRST_JOB", "x"), "form order: password first");

        assertEquals(AuthService.SignUpResult.SUCCESS,
                auth.signUp("Q1@Example.com", "password1", true, "MOTHERS_MAIDEN_NAME", " O'Brien "));
        int id = users.getUserIdByEmail("q1@example.com");
        assertTrue(controller.hasSecurityQuestion(id));
        assertEquals(SecurityQuestion.MOTHERS_MAIDEN_NAME, controller.getQuestion(id).orElseThrow());
        assertTrue(controller.verifyAnswer(id, "o'brien").correct());
    }

    @Test
    void securityQuestion_lockout_isStoredInTheDatabase() throws Exception {
        int id = newUser("lock@example.com", "password1");
        assertFalse(controller.hasSecurityQuestion(id));
        assertEquals(SecurityQuestionService.SetResult.SUCCESS, controller.setSecurityQuestion(id, "FIRST_PET", "Rex"));
        assertEquals(SecurityQuestionService.SetResult.ALREADY_SET, controller.setSecurityQuestion(id, "FIRST_PET", "Fido"));

        for (int i = 0; i < SecurityQuestionService.MAX_ATTEMPTS; i++) controller.verifyAnswer(id, "fido");
        // a brand-new service reading the same database is still locked
        SecurityQuestionService fresh = new SecurityQuestionService();
        assertEquals(SecurityQuestionService.VerifyStatus.LOCKED, fresh.verifyAnswer(id, "rex").status());
    }

    @Test
    void changePassword_thenLoginUsesTheNewPassword() throws Exception {
        int id = newUser("pw@example.com", "password1");
        assertEquals(AccountSecurityService.ChangePasswordResult.WRONG_CURRENT_PASSWORD,
                controller.changePassword(id, "password2", "newpassword9"));
        assertEquals(AccountSecurityService.ChangePasswordResult.SUCCESS,
                controller.changePassword(id, "password1", "newpassword9"));
        AuthService auth = new AuthService(users, questions);
        assertEquals(AuthService.SignInResult.WRONG_PASSWORD, auth.signIn("pw@example.com", "password1"));
        assertEquals(AuthService.SignInResult.SUCCESS, auth.signIn("pw@example.com", "newpassword9"));
    }

    @Test
    void changeEmail_updatesDb_session_andMovesReminders() throws Exception {
        int id = newUser("old@example.com", "password1");
        newUser("used@example.com", "password1");
        controller.setSecurityQuestion(id, "CHILDHOOD_TOWN", "Leeds");
        Data.setCurrentUser(id, "old@example.com", "Sam Lee");

        assertEquals(AccountSecurityService.ChangeEmailResult.EMAIL_ALREADY_USED,
                controller.changeEmail(id, "leeds", "USED@example.com").result());
        assertEquals(AccountSecurityService.ChangeEmailResult.WRONG_ANSWER,
                controller.changeEmail(id, "york", "fresh@example.com").result());
        assertTrue(moved.isEmpty());

        AccountSecurityService.EmailChange ok = controller.changeEmail(id, " LEEDS ", "Fresh@Example.com");
        assertTrue(ok.success(), ok.message());
        assertEquals("fresh@example.com", users.getEmailByUserId(id));
        assertEquals("fresh@example.com", Data.getCurrentUserEmail());
        assertEquals("Sam Lee", Data.getCurrentUserName());
        assertEquals(List.of(id + ":old@example.com->fresh@example.com"), moved);
        assertEquals(AuthService.SignInResult.SUCCESS,
                new AuthService(users, questions).signIn("FRESH@example.com", "password1"));
    }

    @Test
    void accountName_missing_thenSaved() throws Exception {
        int id = newUser("name@example.com", "password1");
        Data.setCurrentUser(id, "name@example.com", null);
        assertTrue(controller.accountNameMissing(id));
        assertEquals(AccountSecurityService.SaveNameResult.INVALID_NAME, controller.saveNameToAccount(id, " ", "Lee"));
        assertEquals(AccountSecurityService.SaveNameResult.SUCCESS, controller.saveNameToAccount(id, " Sam ", "Lee"));
        assertFalse(controller.accountNameMissing(id));
        assertEquals("Sam Lee", users.getDisplayNameByUserId(id));
        assertEquals("Sam Lee", Data.getCurrentUserName());
    }

    @Test
    void saveAccount_withoutDateOfBirth_succeeds() throws Exception {
        int id = newUser("nodob@example.com", "password1");
        assertEquals(AccountService.SaveResult.SUCCESS,
                controller.saveAccount(id, "Alex", "Taylor", null, null, "nodob@example.com"));
        AccountService.AccountInfo info = controller.loadAccount(id);
        assertNull(info.dob);
        assertEquals("Alex", info.firstName);
        // a date that is given is still checked
        assertEquals(AccountService.SaveResult.INVALID_DOB,
                controller.saveAccount(id, "Alex", "Taylor", null, LocalDate.now().plusDays(1), "nodob@example.com"));
    }

    @Test
    void emailTakenByOtherUser_ignoresCase_andTheUserThemself() throws Exception {
        int a = newUser("case@example.com", "password1");
        int b = newUser("case2@example.com", "password1");
        assertTrue(users.emailTakenByOtherUser(b, "CASE@example.com"));
        assertFalse(users.emailTakenByOtherUser(a, "Case@Example.com"));
        assertFalse(users.emailTakenByOtherUser(a, "nobody@example.com"));
    }
}
