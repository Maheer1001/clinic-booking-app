package controller;

import app.Data;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import model.NewResults;
import service.NotificationDispatcher;
import service.ResultAlertService;
import service.TrackingService;
import view.Navigator;
import view.TrackingHubView;
import view.TrackingView;
import view.notification.NewResultsNotification;
import view.notification.Notification;
import view.notification.PopupQueue;

/**
 * Handles the Tracking section: test results, prescriptions, and appointment history.
 * Performs set up tasks and passes over to dedicated section controllers when needed.
 * Also checks the database for newly released test results and shows the patient an in-app alert.
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
    private final ResultAlertService alerts;
    private final NotificationDispatcher dispatcher;
    private final PopupQueue popups = PopupQueue.shared();
    /** True while a new-result check is running, so a quick tab return does not alert twice. */
    private boolean checkingResults;

    public TrackingController(Navigator nav, TrackingService model) {
        this(nav, model, new ResultAlertService(), new NotificationDispatcher());
    }

    /** With the new-result source and popup dispatcher given (package-private for tests). */
    TrackingController(Navigator nav, TrackingService model, ResultAlertService alerts, NotificationDispatcher dispatcher) {
        this.nav = nav;
        this.model = model;
        this.alerts = alerts;
        this.dispatcher = dispatcher;

        useData(model);
        checkForNewResults();

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
        checkForNewResults();
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
        useData(fresh);
        if (hub != null) {
            replaceBody(appointmentsBody, ac.getMenuPage());
            replaceBody(testsBody, tc.getMenuPage());
            replaceBody(prescriptionsBody, pc.getMenuPage());
        }
    }

    private static void replaceBody(StackPane body, Node freshPage) {
        ScrollPane outer = enclosingScrollPane(body);
        double scrollTop = outer == null ? 0 : scrollTop(outer);
        TrackingPageState state = body.getChildren().isEmpty()
                ? TrackingPageState.NONE : TrackingPageState.capture(body.getChildren().getFirst());
        body.getChildren().setAll(freshPage);
        state.applyTo(freshPage);
        if (outer != null) {
            // Lay the new list out now so the same distance from the top can be restored, and again
            // after the next pulse, when wrapped text may have changed the height once more.
            outer.applyCss();
            outer.layout();
            setScrollTop(outer, scrollTop);
            Platform.runLater(() -> setScrollTop(outer, scrollTop));
        }
    }

    /** How far down the content is scrolled, in pixels (kept as pixels because new rows change the height). */
    private static double scrollTop(ScrollPane sp) {
        return sp.getVvalue() * scrollRange(sp);
    }

    private static void setScrollTop(ScrollPane sp, double px) {
        double range = scrollRange(sp);
        sp.setVvalue(range <= 0 ? 0 : Math.min(1, px / range));
    }

    private static double scrollRange(ScrollPane sp) {
        Node content = sp.getContent();
        if (content == null) return 0;
        return Math.max(0, content.getLayoutBounds().getHeight() - sp.getViewportBounds().getHeight());
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
     * Asks the database, in the background, for results released (or changed to a final result)
     * since the patient last looked, shows the in-app alert for them and then records them as seen.
     * Runs on the first Tracking visit after login and on every return to the tab, so results
     * released while the patient was logged out are announced too. No email: staff release sends it.
     */
    void checkForNewResults() {
        int userId = Data.getCurrentUserId();
        if (userId < 0 || checkingResults) return;
        checkingResults = true;
        Background.run(() -> alerts.newResults(userId), n -> {
            checkingResults = false;
            if (announceNewResults(userId, n)) {
                // the watermark that came with these results, so one released meanwhile stays new
                Background.run(() -> alerts.markSeen(userId, n.watermark()), ok -> {}, err -> {});
            }
        }, err -> checkingResults = false);
    }

    /**
     * Shows one discreet popup for the new results (test type and date only, never the result): the
     * single-result popup for one, "You have N new results" listing them for more. It waits its turn in
     * the {@link PopupQueue} behind any other popup (e.g. the security-question prompt after login).
     * True if they were handed to the patient they belong to, i.e. they can now be marked seen.
     * Package-private for tests.
     */
    boolean announceNewResults(int userId, NewResults n) {
        if (n == null || n.isEmpty() || userId < 0 || Data.getCurrentUserId() != userId) return false;
        String name = Data.getCurrentUserName();
        if (name == null || name.isBlank()) {
            String email = Data.getCurrentUserEmail();
            name = email != null ? email : "";
        }
        Notification popup = NewResultsNotification.forResults(name, n.tests());
        dispatcher.notifyUser(userId, null, popups.queued(popup, () -> Data.getCurrentUserId() == userId));
        return true;
    }
}
