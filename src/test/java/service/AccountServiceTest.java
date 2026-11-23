package service;

import app.DB;
import dao.UserDAO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class AccountServiceTest {

    @Test
    void saveAccount_invalidEmail_shouldReturnInvalidEmail() {
        AccountService s = new AccountService();
        AccountService.SaveResult r = s.saveAccount(
                1,
                "Alex", "Taylor",
                "123456789",
                LocalDate.of(2000, 1, 1),
                "not-an-email"
        );
        assertEquals(AccountService.SaveResult.INVALID_EMAIL, r);
    }

    @Test
    void saveAccount_invalidPhone_shouldReturnInvalidPhone() {
        AccountService s = new AccountService();
        AccountService.SaveResult r = s.saveAccount(
                1,
                "Alex", "Taylor",
                "abc!!!",
                LocalDate.of(2000, 1, 1),
                "a@test.com"
        );
        assertEquals(AccountService.SaveResult.INVALID_PHONE, r);
    }

    @Test
    void saveAccount_futureDob_shouldReturnInvalidDob() {
        AccountService s = new AccountService();
        AccountService.SaveResult r = s.saveAccount(
                1,
                "Alex", "Taylor",
                "07123456789",
                LocalDate.now().plusDays(1),
                "a@test.com"
        );
        assertEquals(AccountService.SaveResult.INVALID_DOB, r);
    }

    @Test
    void saveAccount_implausiblyOldDob_shouldReturnInvalidDob() {
        AccountService s = new AccountService();
        assertEquals(AccountService.SaveResult.INVALID_DOB, s.saveAccount(
                1, "Alex", "Taylor", null, LocalDate.of(1850, 1, 1), "a@test.com"));
    }

    @Test
    void saveAccount_nameLongerThanColumn_shouldReturnInvalidName() {
        AccountService s = new AccountService();
        String longName = "a".repeat(AccountService.MAX_NAME_LENGTH + 1);
        assertEquals(AccountService.SaveResult.INVALID_NAME, s.saveAccount(
                1, longName, "Taylor", null, LocalDate.of(2000, 1, 1), "a@test.com"));
        assertEquals(AccountService.SaveResult.INVALID_NAME, s.saveAccount(
                1, "Alex", longName, null, LocalDate.of(2000, 1, 1), "a@test.com"));
    }

    @Test
    @Tag("db")
    void saveAccount_emailAlreadyUsed_shouldReturnEmailAlreadyUsed() throws Exception {
        // Needs the real DB: one user holds the email, another user then tries to save it.
        setup.SetupCheck.ensureSchema();
        UserDAO dao = new UserDAO();

        String used = uniqueEmail();
        String another = uniqueEmail();

        deleteByEmail(used);
        deleteByEmail(another);

        try {
            dao.insertUser(used, "dummyhash");
            dao.insertUser(another, "dummyhash");
            Integer anotherId = dao.getUserIdByEmail(another);
            assertNotNull(anotherId);

            AccountService s = new AccountService();
            AccountService.SaveResult r = s.saveAccount(
                    anotherId,
                    "T", "U",
                    "07123456789",
                    LocalDate.of(2000, 1, 1),
                    used
            );

            assertEquals(AccountService.SaveResult.EMAIL_ALREADY_USED, r);
        } finally {
            deleteByEmail(used);
            deleteByEmail(another);
        }
    }

    @Test
    @Tag("db")
    void saveAccount_validInputs_shouldSucceed() throws Exception {
        setup.SetupCheck.ensureSchema();
        UserDAO dao = new UserDAO();

        String email = uniqueEmail();
        deleteByEmail(email);

        try {
            dao.insertUser(email, "dummyhash");
            Integer userId = dao.getUserIdByEmail(email);
            assertNotNull(userId);

            AccountService s = new AccountService();
            AccountService.SaveResult r = s.saveAccount(
                    userId,
                    "Alex",
                    "Taylor",
                    "07123456789",
                    LocalDate.of(2000, 1, 1),
                    email
            );

            assertEquals(AccountService.SaveResult.SUCCESS, r);

            // read it back
            AccountService.AccountInfo info = dao.getAccountInfo(userId);
            assertNotNull(info);
            assertEquals("Alex", info.firstName);
            assertEquals("Taylor", info.lastName);
            assertEquals("07123456789", info.phone);
            assertEquals(LocalDate.of(2000, 1, 1), info.dob);
            assertEquals(email, info.email);
        } finally {
            deleteByEmail(email);
        }
    }

    private static void deleteByEmail(String email) throws Exception {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM \"User\" WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
    }

    private static String uniqueEmail() {
        return "svc_" + System.nanoTime() + "@example.com";
    }
}