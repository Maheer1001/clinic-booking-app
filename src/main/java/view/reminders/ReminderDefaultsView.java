package view.reminders;

import app.Data;
import controller.ReminderController;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.Appointment;
import model.AppointmentReminderDefaults;
import model.ReminderTemplateOverrides;
import service.NotificationDispatcher;
import service.email.AppointmentReminderEmail;
import view.Navigator;
import view.notification.AppointmentReminderNotification;
import view.ui.UI;

import java.time.LocalDateTime;

/**
 * Screen for editing the user's default appointment reminder template (stored on {@code User.default_reminder_config}).
 * Same payload shape as per-appointment {@link model.AppointmentReminder}.
 */
public class ReminderDefaultsView {

    private final ReminderController controller;

    public ReminderDefaultsView(ReminderController controller) {
        this.controller = controller;
    }

    public Node getReminderDefaultsView() {
        // With nothing saved, show what new bookings really get (on, one day before, discreet) rather
        // than "Disabled": saving that unchanged used to turn reminders off for every new booking.
        AppointmentReminderDefaults initial = controller.getDefaultAppointmentReminderTemplate()
                .filter(AppointmentReminderDefaults::hasSavedTemplate)
                .orElse(new AppointmentReminderDefaults(true,
                        ReminderController.NEW_BOOKING_SCHEDULE, ReminderController.NEW_BOOKING_MESSAGE));

        ReminderTemplateForm form = new ReminderTemplateForm(
                initial.customDays().isEmpty() ? null : initial.customDays(),
                initial.customMessage().isEmpty() ? null : initial.customMessage(),
                initial.notificationsEnabled());

        Label blurb = UI.body(
                "Every new booking gets a reminder with these settings. "
                        + "You can still change the reminder for each appointment in Reminders.");

        Appointment previewAppt = new Appointment(
                0,
                controller.getUserId(),
                "Sample appointment",
                "Sample clinic",
                "Dr Preview",
                LocalDateTime.now().plusDays(3).withSecond(0).withNano(0));

        Button saveBtn = UI.primaryButton("Save defaults");
        Button testEmailBtn = UI.backButton("Send preview email");
        Button testNotifBtn = UI.backButton("Preview notification");
        Label saveConfirm = UI.inlineSaveConfirmation();

        saveBtn.setOnAction(e -> {
            String problem = form.validationError();
            if (problem != null) {
                ReminderFeedback.invalid(saveConfirm, problem);
                return;
            }
            ReminderTemplateForm.Payload p = form.collectPayload();
            boolean saved;
            try {
                controller.saveDefaultAppointmentReminderTemplate(
                        new AppointmentReminderDefaults(p.notificationsEnabled(), p.scheduleJson(), p.templateOverridesJson()));
                saved = true;
            } catch (RuntimeException ex) {
                System.err.println("Could not save reminder defaults: " + ex.getMessage());
                saved = false;
            }
            if (saved && !p.notificationsEnabled()) {
                saveConfirm.setText("Saved. New bookings will not get a reminder until you choose Enabled.");
            } else {
                ReminderFeedback.saveResult(saveConfirm, saved);
            }
        });

        testEmailBtn.setOnAction(e -> {
            String recipientEmail = Data.getCurrentUserEmail();
            String recipientName = Data.getCurrentUserName();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                new Alert(Alert.AlertType.INFORMATION, "No email address is set for the current user.").showAndWait();
                return;
            }
            if (recipientName == null || recipientName.isBlank()) {
                recipientName = recipientEmail;
            }
            ReminderTemplateOverrides current = form.collectOverridesForPreview();
            ReminderFeedback.sending(saveConfirm);
            testEmailBtn.setDisable(true);
            new NotificationDispatcher().notifyUserAsync(
                    Data.getCurrentUserId(),
                    new AppointmentReminderEmail(
                            recipientEmail, recipientName,
                            previewAppt.getServiceName(), previewAppt.getAppointmentDateTime(),
                            previewAppt.getClinicName(), previewAppt.getDoctorName(),
                            current.getEmailSubject(), current.getEmailBodyIntro(), current.getEmailFromName(),
                            DiscreetReminderChoice.isDiscreet(current)),
                    null,
                    sent -> {
                        testEmailBtn.setDisable(false);
                        ReminderFeedback.testEmailResult(saveConfirm, sent);
                    }
            );
        });

        testNotifBtn.setOnAction(e -> {
            ReminderTemplateOverrides current = form.collectOverridesForPreview();
            AppointmentReminderNotification notification = new AppointmentReminderNotification(
                    Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "",
                    previewAppt.getServiceName(),
                    previewAppt.getAppointmentDateTime(),
                    previewAppt.getClinicName(),
                    previewAppt.getDoctorName(),
                    current.getNotificationTitle(),
                    current.getNotificationPreview(),
                    current.getNotificationBody(),
                    current.getDiscreetOverride()
            );
            notification.show();
        });

        testEmailBtn.setMaxWidth(Double.MAX_VALUE);
        testNotifBtn.setMaxWidth(Double.MAX_VALUE);
        VBox bottomButtons = new VBox(8, testEmailBtn, testNotifBtn);
        bottomButtons.setAlignment(Pos.CENTER);

        VBox bottom = new VBox(10, bottomButtons, saveBtn, saveConfirm);
        bottom.setAlignment(Pos.CENTER);
        bottom.setMaxWidth(Double.MAX_VALUE);

        // Already inside a settings card, so no extra page padding (it squeezed the dropdowns).
        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(4, 0, 0, 0));
        content.getChildren().addAll(
                blurb,
                form.getNotifyAndDiscreetRow(),
                form.getTimePane(),
                form.getEmailPane(),
                form.getNotifPane(),
                bottom
        );
        return content;
    }
}
