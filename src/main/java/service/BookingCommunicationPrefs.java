package service;

import dao.UserDAO;
import model.BookingCommunicationPreferences;

import java.sql.SQLException;

/**
 * Loads {@link BookingCommunicationPreferences} for the current user from persistence.
 */
public final class BookingCommunicationPrefs {

    private BookingCommunicationPrefs() {
    }

    public static BookingCommunicationPreferences load(int userId) {
        if (userId < 0) {
            return BookingCommunicationPreferences.defaults();
        }
        try {
            return new UserDAO().getBookingCommunicationPreferences(userId);
        } catch (SQLException e) {
            return BookingCommunicationPreferences.defaults();
        }
    }
}
