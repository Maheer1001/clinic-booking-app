package dao;

import service.AccountService;

import java.sql.SQLException;

/** Account fields changed from the account/settings screens, by user id. Implemented by {@link UserDAO}. */
public interface AccountRepository {

    /** Name, phone, date of birth and email, or null if the user does not exist. */
    AccountService.AccountInfo getAccountInfo(int userId) throws SQLException;

    boolean updateName(int userId, String firstName, String lastName) throws SQLException;

    String getEmailByUserId(int userId) throws SQLException;

    String getPasswordHashByUserId(int userId) throws SQLException;

    boolean updatePasswordHashByUserId(int userId, String passwordHash) throws SQLException;

    /** True if another account already uses this email, ignoring case. */
    boolean emailTakenByOtherUser(int userId, String email) throws SQLException;

    boolean updateEmail(int userId, String newEmail) throws SQLException;
}
