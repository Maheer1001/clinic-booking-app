package service;

import dao.UserDAO;
import dao.UserRepository;
import model.BookingCommunicationPreferences;
import model.User;
import service.email.EmailSender;
import service.email.TestResultEmail;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Tells a patient a result is ready. Always the discreet {@link TestResultEmail}: it never states the
 * result or the test type, only that something is available to view after logging in.
 *
 * <p>Unlike {@link NotificationDispatcher} (which only notifies the logged-in user), this sends to the
 * patient while a staff member is logged in. It sends on the calling thread, so call it off the
 * JavaFX thread ({@link StaffService} does).
 */
@FunctionalInterface
public interface ResultReleaseNotifier {

    /** Status line in the email: deliberately says nothing about the result. */
    String STATUS = "Available";

    /** @return true if the email was sent */
    boolean resultAvailable(int patientUserId, LocalDate testDate);

    /** Does nothing (tests, or when email is not wanted). */
    ResultReleaseNotifier NONE = (patientUserId, testDate) -> false;

    /** The real one: looks up the patient's email and name and sends the discreet email. */
    static ResultReleaseNotifier email() {
        return email(new UserDAO(), new EmailSender());
    }

    /**
     * Reads the patient's communication preferences the same way booking emails do
     * ({@link BookingCommunicationPrefs#load(UserRepository, int)}) and uses their chosen sender name.
     * The content stays discreet whatever they chose for bookings: a result email never names the test.
     */
    static ResultReleaseNotifier email(UserRepository users, EmailSender sender) {
        return (patientUserId, testDate) -> {
            try {
                Optional<User> user = users.findById(patientUserId);
                if (user.isEmpty() || user.get().email().isBlank()) return false;
                User u = user.get();
                BookingCommunicationPreferences prefs = BookingCommunicationPrefs.load(users, patientUserId);
                TestResultEmail mail = new TestResultEmail(u.email(), u.getDisplayName(), null,
                        testDate != null ? testDate : LocalDate.now(), STATUS, true,
                        prefs.confirmationTemplate().getEmailFromName());
                return sender.send(mail);
            } catch (SQLException | RuntimeException e) {
                System.err.println("[ResultRelease] Could not send the result email (" + e.getClass().getSimpleName() + ")");
                return false;
            }
        };
    }
}
