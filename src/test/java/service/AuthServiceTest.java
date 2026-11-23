package service;

import app.DB;
import org.junit.jupiter.api.*;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceTest {

    private static String savedUrl, savedUser, savedPass;

    @BeforeAll
    static void setupDatabase() throws Exception {
        savedUrl = DB.getUrl();
        savedUser = DB.getUsername();
        savedPass = DB.getPassword();
        DB.setUrl("jdbc:h2:mem:auth_test;DB_CLOSE_DELAY=-1");
        DB.setUsername("sa");
        DB.setPassword("");
        try (Connection c = DB.connect();
             var s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS "User" (
                    user_id INTEGER PRIMARY KEY,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50),
                    email VARCHAR(255),
                    password_hash VARCHAR(255),
                    phone_number VARCHAR(20),
                    DOB DATE,
                    gender VARCHAR(50),
                    sexuality VARCHAR(50),
                    disabilities VARCHAR(255),
                    email_verified BOOLEAN,
                    verification_token VARCHAR(255),
                    v_token_expiry TIMESTAMP,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    default_reminder_config TEXT,
                    booking_comm_prefs TEXT
                )
                """);
        }
    }

    @AfterAll
    static void restoreDatabase() {
        DB.setUrl(savedUrl);
        DB.setUsername(savedUser);
        DB.setPassword(savedPass);
    }

    @Test
    void signUp_newEmail_withTermsAccepted_shouldSucceed() {
        AuthService auth = new AuthService();

        String email = "test_" + System.currentTimeMillis() + "@example.com";

        AuthService.SignUpResult r =
                auth.signUp(email, "Password1", true);

        assertEquals(AuthService.SignUpResult.SUCCESS, r);
    }

    @Test
    void signUp_existingEmail_shouldFail() {
        AuthService auth = new AuthService();
        auth.signUp("exists@soton.ac.uk", "password123", true);

        assertEquals(AuthService.SignUpResult.EMAIL_ALREADY_USED,
                auth.signUp("exists@soton.ac.uk", "password123", true));
    }

    @Test
    void signUp_racingAnotherSignUp_uniqueIndexGivesEmailAlreadyUsed() {
        // emailExists said no, but another sign-up with the same email got in first
        FakeUserRepository users = new FakeUserRepository() {
            @Override public boolean insertUser(String email, String passwordHash) throws java.sql.SQLException {
                throw new java.sql.SQLException(
                        "ERROR: duplicate key value violates unique constraint \"user_email_lower_key\"", "23505");
            }
        };
        assertEquals(AuthService.SignUpResult.EMAIL_ALREADY_USED,
                new AuthService(users).signUp("race@example.com", "Password1", true));
    }

    @Test
    void signUp_withoutAcceptingTerms_shouldFail() {
        AuthService auth = new AuthService();
        assertEquals(AuthService.SignUpResult.TERMS_NOT_ACCEPTED,
                auth.signUp("terms@soton.ac.uk", "password123", false));
    }

    @Test
    void signUp_invalidEmail_shouldFail() {
        AuthService auth = new AuthService();
        assertEquals(AuthService.SignUpResult.INVALID_EMAIL,
                auth.signUp("not-an-email", "password123", true));
    }

    @Test
    void signUp_weakPassword_shouldFail() {
        AuthService auth = new AuthService();
        assertEquals(AuthService.SignUpResult.WEAK_PASSWORD,
                auth.signUp("weak@soton.ac.uk", "123", true));
    }

    @Test
    void signIn_correctCredentials_shouldSucceed() {
        AuthService auth = new AuthService();
        auth.signUp("login@soton.ac.uk", "password123", true);

        assertEquals(AuthService.SignInResult.SUCCESS,
                auth.signIn("login@soton.ac.uk", "password123"));
    }

    @Test
    void signIn_wrongPassword_shouldFail() {
        AuthService auth = new AuthService();
        auth.signUp("wrongpw@soton.ac.uk", "password123", true);

        assertEquals(AuthService.SignInResult.WRONG_PASSWORD,
                auth.signIn("wrongpw@soton.ac.uk", "nottherightpw"));
    }

    @Test
    void signIn_unknownUser_shouldFail() {
        AuthService auth = new AuthService();
        assertEquals(AuthService.SignInResult.USER_NOT_FOUND,
                auth.signIn("unknown@soton.ac.uk", "password123"));
    }

    @Test
    void signIn_emptyInputs_shouldFail() {
        AuthService auth = new AuthService();
        assertEquals(AuthService.SignInResult.INVALID_INPUT,
                auth.signIn("", "password123"));
        assertEquals(AuthService.SignInResult.INVALID_INPUT,
                auth.signIn("user@soton.ac.uk", ""));
        assertEquals(AuthService.SignInResult.INVALID_INPUT,
                auth.signIn("   ", "password123"));
        assertEquals(AuthService.SignInResult.INVALID_INPUT,
                auth.signIn("user@soton.ac.uk", "   "));
    }

    @Test
    void signUp_storesSaltedHash_notPlainOrSha256() throws Exception {
        AuthService auth = new AuthService();
        auth.signUp("salt1@soton.ac.uk", "password123", true);
        auth.signUp("salt2@soton.ac.uk", "password123", true);

        dao.UserDAO dao = new dao.UserDAO();
        String h1 = dao.getPasswordHashByEmail("salt1@soton.ac.uk");
        String h2 = dao.getPasswordHashByEmail("salt2@soton.ac.uk");
        assertTrue(h1.startsWith("pbkdf2$"));
        assertNotEquals(h1, h2, "same password must not produce the same stored hash");
        assertNotEquals(PasswordHasher.legacySha256Hex("password123"), h1);
    }

    @Test
    void signIn_legacySha256Hash_stillWorks_andIsUpgraded() throws Exception {
        dao.UserDAO dao = new dao.UserDAO();
        String email = "legacy@soton.ac.uk";
        dao.insertUser(email, PasswordHasher.legacySha256Hex("password123"));

        AuthService auth = new AuthService();
        assertEquals(AuthService.SignInResult.WRONG_PASSWORD, auth.signIn(email, "password124"));
        assertEquals(AuthService.SignInResult.SUCCESS, auth.signIn(email, "password123"));

        String upgraded = dao.getPasswordHashByEmail(email);
        assertTrue(upgraded.startsWith("pbkdf2$"), "legacy hash should be replaced after login");
        assertEquals(AuthService.SignInResult.SUCCESS, auth.signIn(email, "password123"));
    }

    @Test
    void signIn_emailIsTrimmedAndCaseInsensitive() {
        AuthService auth = new AuthService();
        auth.signUp("Mixed.Case@soton.ac.uk", "password123", true);

        assertEquals(AuthService.SignInResult.SUCCESS,
                auth.signIn("  MIXED.case@SOTON.ac.uk ", "password123"));
    }
}
