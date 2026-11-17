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
            list.getChildren().add(UI.body("No upcoming appointments"));
        } else {
            for (UpcomingAppointmentDetails apt : appointments) {

                Button btn = UI.primaryButton(
                        apt.serviceName() + " — " +
                                apt.startTime() + " " +
                                apt.date()
                );

                btn.setWrapText(true);
                btn.setMaxWidth(Double.MAX_VALUE);

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

        VBox bordered = UI.card(10, scroll, quickBookButton);
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

        Button cancel = UI.primaryButton("Cancel appointment");

        cancel.setOnAction(e -> {
            controller.cancelAppointment(appId);

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

                dispatcher.notifyUser(
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


    public Button getQuickBookButton() { return quickBookButton; }

    public Node getSettingsScene() {
        Label accountLabel = UI.heading("Account Settings");
        Node accountSettings = AccountView.getPage(Data.getCurrentUserId());
        VBox account = UI.card(5, accountLabel, accountSettings, UI.page(deleteUser));

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

        VBox logout = new VBox(logOutButton);
        logout.setAlignment(Pos.CENTER);
        logout.setPadding(new Insets(10, 0, 10, 0));

        return new VBox(10, sp, logout);
    }

}

