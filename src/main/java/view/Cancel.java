package view;

import app.Data;
import controller.ViewAppointmentController;
import javafx.scene.Node;
import javafx.scene.control.*;
import model.BookingCommunicationPreferences;
import model.ReminderTemplateOverrides;
import service.AsyncNotifier;
import service.BookingCommunicationPrefs;
import service.NotificationDispatcher;
import service.email.CancellationEmail;
import view.notification.CancellationNotification;
import view.ui.UI;

import java.util.Optional;

public class Cancel {

    public static Node getPage() {
        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            return UI.page(UI.muted("Please log in to cancel an appointment."));
        }

        TextField appointmentId = new TextField();
        appointmentId.setPromptText("Enter Appointment ID");

        Button cancelBtn = UI.primaryButton("Cancel Appointment");
        Label result = new Label();

        ViewAppointmentController controller = new ViewAppointmentController(userId);
        NotificationDispatcher dispatcher = new NotificationDispatcher();
        cancelBtn.setOnAction(e -> {
            result.setText("");
            try {
                int id = Integer.parseInt(appointmentId.getText().trim());
                Optional<model.UpcomingAppointmentDetails> aptOpt = controller.getAppointmentDetails(id);
                if (aptOpt.isEmpty()) {
                    result.setText("Appointment not found or you cannot cancel it.");
                    return;
                }
                model.UpcomingAppointmentDetails apt = aptOpt.get();
                // Same confirmation as the other cancel buttons; this one cancelled on the first click.
                if (!MainView.confirmCancel(apt)) return;
                if (!controller.cancelAppointment(id)) {
                    result.setText("This appointment could not be cancelled. It may already be cancelled.");
                    return;
                }
                result.setText("Appointment cancelled.");
                String recipientEmail = Data.getCurrentUserEmail();
                String recipientName = Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "Patient";
                if (recipientEmail != null && !recipientEmail.isBlank()) {
                    String appointmentRef = "APP-" + id;
                    String dateTime = apt.date() + " " + apt.startTime() + " - " + apt.endTime();
                    BookingCommunicationPreferences commPrefs = BookingCommunicationPrefs.load(userId);
                    boolean cancellationDiscreet = commPrefs.cancellationDiscreet();
                    ReminderTemplateOverrides cancellationTpl = commPrefs.cancellationTemplate();
                    // In the background like the other cancel buttons, so SMTP retries don't freeze the window.
                    AsyncNotifier.notifyUser(
                            dispatcher,
                            userId,
                            new CancellationEmail(
                                    recipientEmail, recipientName, appointmentRef,
                                    apt.serviceName(), apt.clinicName(), dateTime, cancellationDiscreet,
                                    cancellationTpl),
                            new CancellationNotification(
                                    recipientName, appointmentRef, apt.serviceName(), apt.clinicName(), dateTime,
                                    cancellationDiscreet,
                                    cancellationTpl
                            )
                    );
                }
            } catch (NumberFormatException ex) {
                result.setText("Please enter a valid appointment ID.");
            }
        });

        return UI.page(
                appointmentId, cancelBtn, result);
    }
}
