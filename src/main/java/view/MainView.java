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
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.util.List;

public class MainView {

    private final Button quickBookButton = UI.primaryButton("+ Book Appointment");

    private final ToggleButton bookingButton = UI.navButton("Booking");
    private final ToggleButton trackingButton = UI.navButton("Tracking");
    private final ToggleButton remindersButton = UI.navButton("Reminders");
    /** Entry point for per-user default appointment reminder configuration (behaviour can be moved later). */
    private final Button deleteUser = UI.destructiveButton("Delete account");
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
        LocalDate today = LocalDate.now();

        if (appointments == null || appointments.isEmpty()) {
            list.getChildren().add(UI.emptyState(UI.ICON_CALENDAR, "Nothing booked yet",
                    "Book a test, consultation or treatment at a clinic near you. It takes about a minute."));
        } else {
            for (UpcomingAppointmentDetails apt : appointments) {

                Label service = new Label(apt.serviceName());
                service.getStyleClass().add("appointment-title");
                Label when = new Label(Dates.upcoming(apt.date(), today) + "  ·  " + Dates.timeRange(apt.startTime(), apt.endTime()));
                when.getStyleClass().add("appointment-meta");
                Label where = new Label(apt.clinicName());
                where.getStyleClass().add("appointment-place");
                VBox details = new VBox(3, service, when, where);

                Button btn = new Button();
                btn.setGraphic(details);
                btn.getStyleClass().add("appointment-card");
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setAlignment(Pos.CENTER_LEFT);
                btn.setAccessibleText(apt.serviceName() + ", " + Dates.full(Dates.parseStored(apt.date()))
                        + " at " + apt.startTime() + ", " + apt.clinicName() + ". Open details");

                int appId = apt.appId();

                btn.setOnAction(e ->
                        nav.goToPage(
                                getDetailsPage(nav, controller, dispatcher, appId),
                                "Appointment",
                                false
                        )
                );

                list.getChildren().add(btn);
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label heading = new Label("Upcoming appointments");
        heading.getStyleClass().add("section-heading");
        Button bookButton = copyOf(quickBookButton);
        bookButton.setMaxWidth(Double.MAX_VALUE);
        VBox bordered = UI.card(12, heading, scroll, bookButton);
        VBox.setVgrow(bordered, Priority.ALWAYS);

        VBox root = new VBox(14, greeting(appointments, today), bordered);
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
        LocalDate date = Dates.parseStored(apt.date());

        VBox card = UI.card(14, UI.detailTitle(apt.serviceName()));
        String countdown = Dates.countdown(date, LocalDate.now());
        if (!countdown.isEmpty()) {
            Label chip = new Label(countdown.substring(0, 1).toUpperCase() + countdown.substring(1));
            chip.getStyleClass().add("chip");
            card.getChildren().add(chip);
        }
        card.getChildren().addAll(
                UI.detail("WHEN", Dates.dateTime(apt.date(), apt.startTime(), apt.endTime())),
                UI.detail("CLINIC", apt.clinicName()));
        List<String> notes = apt.getNotesLines();
        if (!notes.isEmpty()) {
            card.getChildren().add(UI.detail("YOUR NOTES", String.join("\n", notes)));
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
        Button cancel = UI.destructiveButton("Cancel appointment");
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
                String dateTimeStr = Dates.dateTime(apt.date(), apt.startTime(), apt.endTime());

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

        return UI.page(card, reschedule, cancel);
    }


    static boolean confirmCancel(UpcomingAppointmentDetails apt) {
        ButtonType cancelIt = new ButtonType("Cancel appointment", ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType("Keep it", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", cancelIt, keep);
        alert.setTitle("Cancel appointment");
        alert.setHeaderText("Cancel your " + apt.serviceName() + "?");
        LocalDate date = Dates.parseStored(apt.date());
        alert.setContentText(Dates.dateTime(apt.date(), apt.startTime(), apt.endTime())
                + "\n" + apt.clinicName() + "\n\nThe time will be released and you will get a cancellation email.");
        UI.styleDialog(alert, Data.getScene());
        DialogPane pane = alert.getDialogPane();
        pane.lookupButton(cancelIt).getStyleClass().add(UI.BTN_DANGER);
        pane.lookupButton(keep).getStyleClass().add(UI.ALERT_IGNORE);
        // Default to the safe choice so Enter does not cancel.
        ((Button) pane.lookupButton(cancelIt)).setDefaultButton(false);
        ((Button) pane.lookupButton(keep)).setDefaultButton(true);
        return alert.showAndWait().filter(b -> b == cancelIt).isPresent();
    }

    public Button getQuickBookButton() { return quickBookButton; }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("settings-section-title");
        return l;
    }

    /** "Hi Alex" and a one-line summary of what is coming up. */
    static VBox greeting(List<UpcomingAppointmentDetails> appointments, LocalDate today) {
        model.User user = Data.getCurrentUser();
        String first = user != null && user.firstName() != null && !user.firstName().isBlank() ? user.firstName().trim() : null;
        Label hello = new Label(first != null ? "Hi " + first : "Welcome");
        hello.getStyleClass().add("home-greeting");
        Label summary = new Label(summary(appointments, today));
        summary.getStyleClass().add("home-summary");
        summary.setWrapText(true);
        summary.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox box = new VBox(2, hello, summary);
        box.setPadding(new Insets(0, 4, 0, 4));
        return box;
    }

    /** One line under the greeting, e.g. "Your STI test is tomorrow at 10:00. 2 appointments booked." */
    static String summary(List<UpcomingAppointmentDetails> appointments, LocalDate today) {
        if (appointments == null || appointments.isEmpty()) {
            return "You have no upcoming appointments.";
        }
        UpcomingAppointmentDetails next = appointments.getFirst();
        String when = Dates.countdown(Dates.parseStored(next.date()), today);
        String nextLine = when.isEmpty() ? "Your next appointment is " + next.serviceName() + "."
                : "Your " + next.serviceName() + " is " + when + " at " + next.startTime() + ".";
        if (appointments.size() == 1) return nextLine;
        return nextLine + " " + appointments.size() + " appointments booked.";
    }

    public Node getSettingsScene() {
        Label accountLabel = sectionTitle("Account");
        Node accountSettings = AccountView.getPage(Data.getCurrentUserId());
        Button delete = copyOf(deleteUser);
        VBox deleteBox = new VBox(delete);
        deleteBox.setAlignment(Pos.CENTER);
        deleteBox.setPadding(new Insets(6, 0, 4, 0));
        VBox account = UI.card(5, accountLabel, accountSettings, deleteBox);

        Label accessibilityLabel = sectionTitle("Accessibility");
        Node accessibilitySettings = AccessibilitySettingsView.getPage();
        VBox accessibility = UI.card(5, accessibilityLabel, accessibilitySettings);

        Label reminderDefaultsLabel = sectionTitle("Reminder defaults");
        Node reminderDefaultsSettings = new ReminderDefaultsView(new ReminderController(Data.getCurrentUserId())).getReminderDefaultsView();
        VBox reminderDefaults = UI.card(5, reminderDefaultsLabel, reminderDefaultsSettings);

        VBox root = UI.pageTop(account, accessibility, reminderDefaults);
        root.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox logout = new VBox(copyOf(logOutButton));
        logout.setAlignment(Pos.CENTER);
        logout.setPadding(new Insets(10, 0, 10, 0));

        // Let the settings list take the spare height so Log Out stays pinned at the bottom.
        VBox.setVgrow(sp, Priority.ALWAYS);
        return new VBox(10, sp, logout);
    }

}

