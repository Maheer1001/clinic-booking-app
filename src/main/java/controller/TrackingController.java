package controller;

import app.Data;
import javafx.scene.Node;
import model.Test;
import service.NotificationDispatcher;
import service.TrackingService;
import service.email.TestResultEmail;
import view.Navigator;
import view.TrackingHubView;
import view.TrackingView;
import view.notification.TestResultNotification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles the Tracking section: test results, prescriptions, and appointment history.
 * Performs set up tasks and passes over to dedicated section controllers when needed.
 * Also checks for newly added test results and notifies the user.
 */
public class TrackingController {

    private final TrackingTestController tc;
    private final TrackingPrescriptionController pc;
    private final TrackingAppointmentController ac;

    public TrackingController(Navigator nav, TrackingService model) {
        TrackingView view = new TrackingView();

        notifyForNewTestResults(model);

        tc = new TrackingTestController(nav, view, model);
        pc = new TrackingPrescriptionController(nav, view, model);
        ac = new TrackingAppointmentController(nav, view, model);

        view.getTestResultsButton().setOnAction(e -> nav.goToPage(tc.getMenuPage(), "Test Results", false));
        view.getPrescriptionsButton().setOnAction(e -> nav.goToPage(pc.getMenuPage(), "Prescription List", false));
        view.getAppointmentsButton().setOnAction(e -> nav.goToPage(ac.getMenuPage(), "Appointment History", false));
    }


    public Node getTrackingMainScene() {
        return TrackingHubView.createHub(
                ac.getMenuPage(),
                tc.getMenuPage(),
                pc.getMenuPage()
        );
    }

    /** True if the user has at least one appointment, test, or prescription to show in Tracking. */
    static boolean hasAnyTrackingRecords(TrackingService model) {
        return !model.getPastAppointments().isEmpty()
                || !model.getTestResults().isEmpty()
                || !model.getPrescriptions().isEmpty();
    }

    // Package-private for testing.
    void notifyForNewTestResults(TrackingService model) {
        notifyForNewTestResults(model, new NotificationDispatcher());
    }

    // Package-private for testing.
    void notifyForNewTestResults(TrackingService model, NotificationDispatcher dispatcher) {
        List<Test> tests = model.getTestResults();
        int currentCount = tests.size();
        int lastCount = Data.getLastSeenTestCount();

        // First time entering Tracking after login: establish baseline, don't notify.
        if (lastCount < 0) {
            Data.setLastSeenTestCount(currentCount);
            return;
        }

        if (currentCount <= lastCount) {
            Data.setLastSeenTestCount(currentCount);
            return;
        }

        int newCount = currentCount - lastCount;
        if (newCount <= 0) {
            return;
        }

        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            Data.setLastSeenTestCount(currentCount);
            return;
        }

        String recipientEmail = Data.getCurrentUserEmail();
        String recipientName = Data.getCurrentUserName();
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = recipientEmail != null ? recipientEmail : "";
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yy");

        // Tests are ordered newest-first; treat the first `newCount` as new entries.
        for (int i = 0; i < newCount && i < tests.size(); i++) {
            Test t = tests.get(i);
            String testType = t.testType();
            LocalDate date;
            try {
                date = LocalDate.parse(t.testDate(), fmt);
            } catch (Exception e) {
                date = null;
            }
            String status = "Available";

            TestResultEmail email = null;
            if (recipientEmail != null && !recipientEmail.isBlank() && date != null) {
                // Discreet: the test type (e.g. the infection tested for) is not put in the inbox.
                email = new TestResultEmail(recipientEmail, recipientName, testType, date, status, true);
            }

            TestResultNotification notification = null;
            if (date != null) {
                notification = new TestResultNotification(recipientName, testType, date, status);
            }

            // Email goes out in the background so opening Tracking is not held up by SMTP.
            dispatcher.notifyUserAsync(userId, email, notification, null);
        }

        Data.setLastSeenTestCount(currentCount);
    }
}
