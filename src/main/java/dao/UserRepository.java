package dao;

import app.DB;
import model.AppointmentReminderDefaults;
import model.BookingCommunicationPreferences;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Contract for user data access.
 */
public interface UserRepository {
    boolean emailExists(String email) throws SQLException;
    boolean insertUser(String email, String passwordHash) throws SQLException;
    String getPasswordHashByEmail(String email) throws SQLException;
    boolean updatePasswordHashByEmail(String email, String passwordHash) throws SQLException;
    Integer getUserIdByEmail(String email) throws SQLException;
    String getEmailByUserId(int userId) throws SQLException;
    String getDisplayNameByUserId(int userId) throws SQLException;
    Optional<User> findById(int userId) throws SQLException;
    void deleteUserById(int userId) throws SQLException;
    int getDefaultClinic(int userId) throws SQLException;
    boolean defaultClinicExists(int userID) throws SQLException;
    boolean updateDefaultClinic(int userId, int clinicId) throws SQLException;

    /** JSON payload matching *{@link AppointmentReminderDefaults} fields (notify, custom_days, custom_message). */
    Optional<AppointmentReminderDefaults> getDefaultAppointmentReminderTemplate(int userId) throws SQLException;

    void saveDefaultAppointmentReminderTemplate(int userId, AppointmentReminderDefaults defaults) throws SQLException;

    BookingCommunicationPreferences getBookingCommunicationPreferences(int userId) throws SQLException;

    void saveBookingCommunicationPreferences(int userId, BookingCommunicationPreferences prefs) throws SQLException;
}
