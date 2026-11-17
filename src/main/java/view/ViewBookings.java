package view;

import app.Data;
import controller.ViewAppointmentController;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.BookingCommunicationPreferences;
import model.ReminderTemplateOverrides;
import model.UpcomingAppointmentDetails;
import service.BookingCommunicationPrefs;
import service.NotificationDispatcher;
import service.email.CancellationEmail;
import view.notification.CancellationNotification;
import view.ui.UI;

import java.util.List;

/**
 * Lists the user's upcoming appointments. Clicking one shows details with Reschedule/Cancel.
 */
public class ViewBookings {

    public static Node getPage(Navigator nav) {
        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            return UI.page(UI.muted("Please log in to view bookings."));
        }

        ViewAppointmentController controller = new ViewAppointmentController(userId);
        NotificationDispatcher dispatcher = new NotificationDispatcher();
        List<UpcomingAppointmentDetails> upcoming = controller.getUpcomingAppointments();

        VBox listVbox = new VBox(10);
        for (UpcomingAppointmentDetails apt : upcoming) {
            Button btn = UI.primaryButton(apt.serviceName() + " — " + apt.startTime() + " " + apt.date());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setWrapText(true);
            int appId = apt.appId();
            btn.setOnAction(e -> nav.goToPage(getDetailsPage(nav, controller, dispatcher, appId), "Appointment Details", false));
            listVbox.getChildren().add(btn);
        }

        ScrollPane sp = new ScrollPane(listVbox);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);

        return UI.pageTop(
                listVbox.getChildren().isEmpty() ? UI.muted("You have no upcoming appointments.") : sp);
    }

    private static Node getDetailsPage(Navigator nav, ViewAppointmentController controller, NotificationDispatcher dispatcher, int appId) {
        var aptOpt = controller.getAppointmentDetails(appId);
        if (aptOpt.isEmpty()) {
            return UI.page(UI.muted("Appointment not found."));
        }
        UpcomingAppointmentDetails apt = aptOpt.get();

        Label clinic = UI.body("Clinic: " + apt.clinicName());
        Label service = UI.body("Service: " + apt.serviceName());
        Label dateTime = UI.body("Date: " + apt.date() + " " + apt.startTime() + " - " + apt.endTime());
        VBox notesBox = new VBox(5);
        if (!apt.getNotesLines().isEmpty()) {
            notesBox.getChildren().add(UI.heading("Notes:"));
            for (String line : apt.getNotesLines()) {
                notesBox.getChildren().add(UI.body(line));
            }
        }

        Button reschedule = UI.primaryButton("Reschedule");
        reschedule.setOnAction(e -> nav.goToPage(Reschedule.getPageWithAppId(nav, appId), "Reschedule", false));
        Button cancel = UI.primaryButton("Cancel appointment");
        cancel.setOnAction(e -> {
            controller.cancelAppointment(appId);
            String recipientEmail = Data.getCurrentUserEmail();
            String recipientName = Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "Patient";
            if (recipientEmail != null && !recipientEmail.isBlank()) {
                String appointmentRef = "APP-" + appId;
                String dateTimeStr = apt.date() + " " + apt.startTime() + " - " + apt.endTime();
                BookingCommunicationPreferences commPrefs = BookingCommunicationPrefs.load(Data.getCurrentUserId());
                boolean cancellationDiscreet = commPrefs.cancellationDiscreet();
                ReminderTemplateOverrides cancellationTpl = commPrefs.cancellationTemplate();
                dispatcher.notifyUser(
                        Data.getCurrentUserId(),
                        new CancellationEmail(
                                recipientEmail, recipientName, appointmentRef,
                                apt.serviceName(), apt.clinicName(), dateTimeStr, cancellationDiscreet,
                                cancellationTpl),
                        new CancellationNotification(
                                recipientName, appointmentRef, apt.serviceName(), apt.clinicName(), dateTimeStr,
                                cancellationDiscreet,
                                cancellationTpl
                        )
                );
            }
            nav.goToPage(getPage(nav), "View Bookings", false);
        });

        return UI.page(UI.card(10, clinic, service, dateTime, notesBox), reschedule, cancel);
    }
}
