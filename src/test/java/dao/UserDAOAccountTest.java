package dao;

import app.DB;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import service.AccountService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("db")
public class UserDAOAccountTest {

    @Test
    void updateAccountInfo_then_getAccountInfo_shouldReturnUpdatedValues() throws Exception {
        UserDAO dao = new UserDAO();

        String email = uniqueEmail();
        deleteByEmail(email);

        try {
            dao.insertUser(email, "dummyhash");
            Integer userId = dao.getUserIdByEmail(email);
            assertNotNull(userId);

            boolean ok = dao.updateAccountInfo(
                    userId,
                    "Alex",
                    "Taylor",
                    "123456789",
                    LocalDate.of(2000, 1, 2),
                    email
            );
            assertTrue(ok);

            AccountService.AccountInfo info = dao.getAccountInfo(userId);
            assertNotNull(info);
            assertEquals("Alex", info.firstName);
            assertEquals("Taylor", info.lastName);
            assertEquals("123456789", info.phone);
            assertEquals(LocalDate.of(2000, 1, 2), info.dob);
            assertEquals(email, info.email);
        } finally {
            deleteByEmail(email);
        }
    }

    @Test
    void updateAccountInfo_withNullDobAndPhone_shouldStoreNulls() throws Exception {
        UserDAO dao = new UserDAO();

        String email = uniqueEmail();
        deleteByEmail(email);

        try {
            dao.insertUser(email, "dummyhash");
            Integer userId = dao.getUserIdByEmail(email);
            assertNotNull(userId);

            boolean ok = dao.updateAccountInfo(
                    userId,
                    "Test",
                    "User",
                    null,
                    null,
                    email
            );
            assertTrue(ok);

            AccountService.AccountInfo info = dao.getAccountInfo(userId);
            assertNotNull(info);
            assertEquals("Test", info.firstName);
            assertEquals("User", info.lastName);
            assertNull(info.phone);
            assertNull(info.dob);
            assertEquals(email, info.email);
        } finally {
            deleteByEmail(email);
        }
    }

    @Test
    void emailUsedByOtherUser_shouldBeFalseForSameUser() throws Exception {
        UserDAO dao = new UserDAO();

        String email = uniqueEmail();
        deleteByEmail(email);

        try {
            dao.insertUser(email, "dummyhash");
            Integer userId = dao.getUserIdByEmail(email);
            assertNotNull(userId);

            assertFalse(dao.emailUsedByOtherUser(userId, email));
        } finally {
            deleteByEmail(email);
        }
    }

    @Test
    void emailUsedByOtherUser_shouldBeTrueForDifferentUser() throws Exception {
        UserDAO dao = new UserDAO();

        String email1 = uniqueEmail();
        String email2 = uniqueEmail();

        deleteByEmail(email1);
        deleteByEmail(email2);

        try {
            dao.insertUser(email1, "dummyhash");
            Integer id1 = dao.getUserIdByEmail(email1);
            assertNotNull(id1);

            dao.insertUser(email2, "dummyhash");
            Integer id2 = dao.getUserIdByEmail(email2);
            assertNotNull(id2);

            assertTrue(dao.emailUsedByOtherUser(id2, email1));
        } finally {
            deleteByEmail(email1);
            deleteByEmail(email2);
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
        return "test_" + System.nanoTime() + "@example.com";
    }
}