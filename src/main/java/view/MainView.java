package view;

import app.Data;
import controller.ReminderController;
import controller.ViewAppointmentController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.BookingCommunicationPreferences;
import model.ReminderTemplateOverrides;
import model.UpcomingAppointmentDetails;
import service.BookingCommunicationPrefs;
import service.NotificationDispatcher;
import service.AsyncNotifier;
import service.email.CancellationEmail;
import view.notification.CancellationNotification;
import view.reminders.ReminderDefaultsView;
import view.ui.UI;

import java.util.List;

public class MainView {

    private final Button quickBookButton = UI.primaryButton("+ Book Appointment");

    private final ToggleButton bookingButton = UI.navButton("Booking");
    private final ToggleButton trackingButton = UI.navButton("Tracking");
    private final ToggleButton remindersButton = UI.navButton("Reminders");
    /** Entry point for per-user default appointment reminder configuration (behaviour can be moved later). */
    private final Button deleteUser = UI.dangerButton("Delete User");
    private final Button logOutButton = UI.menuButton("Log Out");

    public ToggleButton getBookingButton() {return bookingButton;}
    public ToggleButton getTrackingButton() {return trackingButton;}
    public ToggleButton getRemindersButton() {return remindersButton;}
    public Button getDeleteUser() {return deleteUser;}
    public Button getLogOutButton() { return logOutButton; }

    /**
     * A node can only be in one place in the scene graph, but home and Settings pages are rebuilt and
     * can be kept in more than one tab's back stack at once. Reusing the same button instance moved it
     * out of the older page, which then came back without its Log Out / Delete / Book button.
     * Each page therefore gets its own button that shares the action of the long-lived one
     * (whose handler is set by MainController).
     */
    static Button copyOf(Button template, String... extraStyleClasses) {
        Button b = new Button();
        b.textProperty().bind(template.textProperty());
        b.getStyleClass().setAll(template.getStyleClass());
        b.getStyleClass().addAll(extraStyleClasses);
        b.onActionProperty().bind(template.onActionProperty());
        b.disableProperty().bind(template.disableProperty());
        return b;
    }

    public Node getPage(Navigator nav) {

        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            return UI.page(
                    UI.muted("Please log in to view bookings.")
            );
        }

        ViewAppointmentController controller = new ViewAppointmentController(userId);
        NotificationDispatcher dispatcher = new NotificationDispatcher();
        List<UpcomingAppointmentDetails> appointments = controller.getUpcomingAppointments();

        VBox list = new VBox(10);

        if (appointments == null || appointments.isEmpty()) {
            Label emptyTitle = new Label("Nothing booked yet");
            emptyTitle.getStyleClass().add("empty-state-title");
            Label emptyBody = UI.muted("Book a test, consultation or treatment at a clinic near you.");
            emptyBody.setWrapText(true);
            VBox empty = new VBox(6, emptyTitle, emptyBody);
            empty.getStyleClass().add("empty-state");
            list.getChildren().add(empty);
        } else {
            for (UpcomingAppointmentDetails apt : appointments) {

                Label service = new Label(apt.serviceName());
                service.getStyleClass().add("appointment-title");
                Label when = new Label(apt.date() + "  ·  " + apt.startTime() + " – " + apt.endTime());
                when.getStyleClass().add("appointment-meta");
                Label where = new Label(apt.clinicName());
                where.getStyleClass().add("appointment-place");
                VBox details = new VBox(3, service, when, where);

                Button btn = new Button();
                btn.setGraphic(details);
                btn.getStyleClass().add("appointment-card");
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setAlignment(Pos.CENTER_LEFT);

                int appId = apt.appId();

                btn.setOnAction(e ->
                        nav.goToPage(
                                getDetailsPage(nav, controller, dispatcher, appId),
                                "Appointment details",
                                false
                        )
                );

                list.getChildren().add(btn);
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label heading = new Label("Upcoming appointments");
        heading.getStyleClass().add("section-heading");
        Button bookButton = copyOf(quickBookButton);
        bookButton.setMaxWidth(Double.MAX_VALUE);
        VBox bordered = UI.card(12, heading, scroll, bookButton);
        VBox.setVgrow(bordered, Priority.ALWAYS);

        VBox root = new VBox(bordered);
        root.getStyleClass().add(UI.PAGE);

        return root;
    }

    private Node getDetailsPage(Navigator nav,
                                ViewAppointmentController controller,
                                NotificationDispatcher dispatcher,
                                int appId) {

        var aptOpt = controller.getAppointmentDetails(appId);

        if (aptOpt.isEmpty()) {
            return UI.page(
                    UI.muted("Appointment not found.")
            );
        }

        UpcomingAppointmentDetails apt = aptOpt.get();

        Label clinic = UI.body("Clinic: " + apt.clinicName());
        Label service = UI.body("Service: " + apt.serviceName());
        Label dateTime = UI.body("Date: " +
                apt.date() + " " +
                apt.startTime() + " - " +
                apt.endTime());

        VBox notesBox = new VBox(5);

        List<String> notes = apt.getNotesLines();
        if (!notes.isEmpty()) {
            notesBox.getChildren().add(UI.heading("Your Notes:"));
            for (String line : notes) {
                notesBox.getChildren().add(UI.body(line));
            }
        }

        Button reschedule = UI.primaryButton("Reschedule");
        reschedule.setOnAction(e ->
                nav.goToPage(
                        Reschedule.getPageWithAppId(nav, appId),
                        "Reschedule",
                        false
                )
        );

        reschedule.setMaxWidth(Double.MAX_VALUE);
        // Destructive, so styled as such and confirmed: one stray click used to cancel the appointment.
        Button cancel = UI.dangerButton("Cancel appointment");
        cancel.setMaxWidth(Double.MAX_VALUE);

        cancel.setOnAction(e -> {
            if (!confirmCancel(apt)) return;
            cancel.setDisable(true);
            if (!controller.cancelAppointment(appId)) {
                // Already cancelled or no longer upcoming: don't send a cancellation email.
                nav.clearTabStack("booking");
                nav.goToHome();
                return;
            }

            String recipientEmail = Data.getCurrentUserEmail();
            String recipientName = Data.getCurrentUserName() != null
                    ? Data.getCurrentUserName()
                    : "Patient";

            if (recipientEmail != null && !recipientEmail.isBlank()) {

                String appointmentRef = "APP-" + appId;
                String dateTimeStr = apt.date() + " " +
                        apt.startTime() + " - " +
                        apt.endTime();

                BookingCommunicationPreferences commPrefs = BookingCommunicationPrefs.load(Data.getCurrentUserId());
                boolean cancellationDiscreet = commPrefs.cancellationDiscreet();
                ReminderTemplateOverrides cancellationTpl = commPrefs.cancellationTemplate();

                // Sent in the background so SMTP retries don't freeze the window.
                AsyncNotifier.notifyUser(
                        dispatcher,
                        Data.getCurrentUserId(),
                        new CancellationEmail(
                                recipientEmail,
                                recipientName,
                                appointmentRef,
                                apt.serviceName(),
                                apt.clinicName(),
                                dateTimeStr,
                                cancellationDiscreet,
                                cancellationTpl
                        ),
                        new CancellationNotification(
                                recipientName,
                                appointmentRef,
                                apt.serviceName(),
                                apt.clinicName(),
                                dateTimeStr,
                                cancellationDiscreet,
                                cancellationTpl
                        )
                );
            }
            nav.clearTabStack("booking");
            nav.goToHome();
        });

        return UI.page(UI.card(8,
                        clinic,
                        service,
                        dateTime,
                        notesBox),
                reschedule,
                cancel
        );
    }


    static boolean confirmCancel(UpcomingAppointmentDetails apt) {
        ButtonType cancelIt = new ButtonType("Cancel appointment", ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType("Keep it", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", cancelIt, keep);
        alert.setTitle("Cancel appointment");
        alert.setHeaderText("Cancel " + apt.serviceName() + "?");
        alert.setContentText(apt.date() + " at " + apt.startTime() + ", " + apt.clinicName());
        alert.setGraphic(null);
        DialogPane pane = alert.getDialogPane();
        if (Data.getScene() != null) {
            pane.getStylesheets().addAll(Data.getScene().getStylesheets());
        }
        pane.lookupButton(cancelIt).getStyleClass().add(UI.BTN_DANGER);
        pane.lookupButton(keep).getStyleClass().add(UI.ALERT_IGNORE);
        // Default to the safe choice so Enter does not cancel.
        ((Button) pane.lookupButton(cancelIt)).setDefaultButton(false);
        ((Button) pane.lookupButton(keep)).setDefaultButton(true);
        return alert.showAndWait().filter(b -> b == cancelIt).isPresent();
    }

    public Button getQuickBookButton() { return quickBookButton; }

    public Node getSettingsScene() {
        Label accountLabel = UI.heading("Account Settings");
        Node accountSettings = AccountView.getPage(Data.getCurrentUserId());
        VBox account = UI.card(5, accountLabel, accountSettings, UI.page(copyOf(deleteUser)));

        Label accessibilityLabel = UI.heading("Accessibility Settings");
        Node accessibilitySettings = AccessibilitySettingsView.getPage();
        VBox accessibility = UI.card(5, accessibilityLabel, accessibilitySettings);

        Label reminderDefaultsLabel = UI.heading("Reminder Defaults Settings");
        Node reminderDefaultsSettings = new ReminderDefaultsView(new ReminderController(Data.getCurrentUserId())).getReminderDefaultsView();
        VBox reminderDefaults = UI.card(5, reminderDefaultsLabel, reminderDefaultsSettings);

        VBox root = UI.pageTop(account, accessibility, reminderDefaults);
        root.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);

        VBox logout = new VBox(copyOf(logOutButton));
        logout.setAlignment(Pos.CENTER);
        logout.setPadding(new Insets(10, 0, 10, 0));

        // Let the settings list take the spare height so Log Out stays pinned at the bottom.
        VBox.setVgrow(sp, Priority.ALWAYS);
        return new VBox(10, sp, logout);
    }

}

