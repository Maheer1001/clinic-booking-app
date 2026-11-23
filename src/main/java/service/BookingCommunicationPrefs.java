package service;

import dao.UserDAO;
import dao.UserRepository;
import model.BookingCommunicationPreferences;

import java.sql.SQLException;

/**
 * Loads a user's {@link BookingCommunicationPreferences} from persistence, falling back to the
 * (discreet) defaults when there are none or they cannot be read.
 */
public final class BookingCommunicationPrefs {

    private BookingCommunicationPrefs() {
    }

    public static BookingCommunicationPreferences load(int userId) {
        return load(new UserDAO(), userId);
    }

    public static BookingCommunicationPreferences load(UserRepository users, int userId) {
        if (userId < 0) {
            return BookingCommunicationPreferences.defaults();
        }
        try {
            BookingCommunicationPreferences prefs = users.getBookingCommunicationPreferences(userId);
            return prefs != null ? prefs : BookingCommunicationPreferences.defaults();
        } catch (SQLException e) {
            return BookingCommunicationPreferences.defaults();
        }
    }
}
