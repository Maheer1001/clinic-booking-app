package controller;

import app.Data;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
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
 *
 * <p>Each time the user comes back to the Tracking tab the data is fetched again and the three lists
 * rebuilt in place, keeping the chosen sub-tab, search text, sort, dates and scroll position.
 */
public class TrackingController {

    private final Navigator nav;
    private final TrackingService model;
    private final TrackingView view = new TrackingView();
    private TrackingTestController tc;
    private TrackingPrescriptionController pc;
    private TrackingAppointmentController ac;

    // The hub's three sub-tab bodies; a refresh swaps their contents, so the hub itself is kept.
    private final StackPane appointmentsBody = new StackPane();
    private final StackPane testsBody = new StackPane();
    private final StackPane prescriptionsBody = new StackPane();
    private Node hub;
    /** Bumped by each refresh, so only the latest one to finish is applied. */
    private int refreshGeneration;

    public TrackingController(Navigator nav, TrackingService model) {
        this.nav = nav;
        this.model = model;

        notifyForNewTestResults(model);
        useData(model);

        view.getTestResultsButton().setOnAction(e -> nav.goToPage(tc.getMenuPage(), "Test Results", false));
        view.getPrescriptionsButton().setOnAction(e -> nav.goToPage(pc.getMenuPage(), "Prescription List", false));
        view.getAppointmentsButton().setOnAction(e -> nav.goToPage(ac.getMenuPage(), "Appointment History", false));

        nav.setOnTabReturn("tracking", this::refresh);
    }

    private void useData(TrackingService data) {
        tc = new TrackingTestController(nav, view, data);
        pc = new TrackingPrescriptionController(nav, view, data);
        ac = new TrackingAppointmentController(nav, view, data);
    }

    public Node getTrackingMainScene() {
        if (hub == null) {
            appointmentsBody.getChildren().setAll(ac.getMenuPage());
            testsBody.getChildren().setAll(tc.getMenuPage());
            prescriptionsBody.getChildren().setAll(pc.getMenuPage());
            hub = TrackingHubView.createHub(appointmentsBody, testsBody, prescriptionsBody);
        }
        return hub;
    }

    /**
     * Fetches fresh data in the background, then rebuilds the lists under the user's current choices
     * and checks for new results. The Navigator calls this whenever the user returns to Tracking.
     */
    public void refresh() {
        int generation = ++refreshGeneration;
        int userId = Data.getCurrentUserId();
        Background.run(model::snapshot, fresh -> {
            // a newer refresh has started, or the user has logged out since this one began
            if (generation != refreshGeneration || Data.getCurrentUserId() != userId) return;
            applyRefresh(fresh);
        }, err -> { /* keep showing what is there; the next return to the tab tries again */ });
    }

    /** Rebuilds the lists from already fetched data (package-private for tests). */
    void applyRefresh(TrackingService fresh) {
        notifyForNewTestResults(fresh);
        useData(fresh);
        if (hub != null) {
            replaceBody(appointmentsBody, ac.getMenuPage());
            replaceBody(testsBody, tc.getMenuPage());
            replaceBody(prescriptionsBody, pc.getMenuPage());
        }
    }

    private static void replaceBody(StackPane body, Node freshPage) {
        ScrollPane outer = enclosingScrollPane(body);
        double outerScroll = outer == null ? 0 : outer.getVvalue();
        TrackingPageState state = body.getChildren().isEmpty()
                ? TrackingPageState.NONE : TrackingPageState.capture(body.getChildren().getFirst());
        body.getChildren().setAll(freshPage);
        state.applyTo(freshPage);
        if (outer != null) {
            // after the new content is laid out, otherwise the old height decides where it lands
            Platform.runLater(() -> outer.setVvalue(outerScroll));
        }
    }

    private static ScrollPane enclosingScrollPane(Node node) {
        for (Parent p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollPane sp) return sp;
        }
        return null;
    }

    /** True if the user has at least one appointment, test, or prescription to show in Tracking. */
    static boolean hasAnyTrackingRecords(TrackingService model) {
        return !model.getPastAppointments().isEmpty()
                || !model.getTestResults().isEmpty()
                || !model.getPrescriptions().isEmpty();
    }

    /**
     * The results to alert the user about: those added since they last looked, newest first. Also
     * records that they have now seen them. The first call after login only sets the baseline.
     *
     * <p>TODO: once the DB-backed "results released since last seen" API (User.last_results_seen_at,
     * released tests only) is merged, switch this method to it. It is the only place the alert
     * decides what is new, so nothing else needs to change.
     */
    List<Test> newResultsSinceLastSeen(TrackingService model) {
        List<Test> tests = model.getTestResults();
        int currentCount = tests.size();
        int lastCount = Data.getLastSeenTestCount();
        Data.setLastSeenTestCount(currentCount);
        if (lastCount < 0 || currentCount <= lastCount) {
            return List.of();
        }
        // Tests come most recently added first (by id); treat the first ones as the new entries.
        return tests.subList(0, currentCount - lastCount);
    }

    // Package-private for testing.
    void notifyForNewTestResults(TrackingService model) {
        notifyForNewTestResults(model, new NotificationDispatcher());
    }

    // Package-private for testing.
    void notifyForNewTestResults(TrackingService model, NotificationDispatcher dispatcher) {
        List<Test> newTests = newResultsSinceLastSeen(model);
        int userId = Data.getCurrentUserId();
        if (newTests.isEmpty() || userId < 0) {
            return;
        }

        String recipientEmail = Data.getCurrentUserEmail();
        String recipientName = Data.getCurrentUserName();
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = recipientEmail != null ? recipientEmail : "";
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yy");

        for (Test t : newTests) {
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
    }
}
