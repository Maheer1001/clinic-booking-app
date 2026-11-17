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
        AppointmentReminderDefaults initial = controller.getDefaultAppointmentReminderTemplate()
                .orElse(new AppointmentReminderDefaults(false, "", ""));

        ReminderTemplateForm form = new ReminderTemplateForm(
                initial.customDays().isEmpty() ? null : initial.customDays(),
                initial.customMessage().isEmpty() ? null : initial.customMessage(),
                initial.notificationsEnabled());

        Label blurb = UI.body(
                "These settings use the same format as appointment reminders. "
                        + "They are saved for your account and can be applied to new bookings later.");

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
            ReminderTemplateForm.Payload p = form.collectPayload();
            controller.saveDefaultAppointmentReminderTemplate(
                    new AppointmentReminderDefaults(p.notificationsEnabled(), p.scheduleJson(), p.templateOverridesJson()));
            UI.showSaveConfirmation(saveConfirm);
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
            new NotificationDispatcher().notifyUser(
                    Data.getCurrentUserId(),
                    new AppointmentReminderEmail(
                            recipientEmail, recipientName,
                            previewAppt.getServiceName(), previewAppt.getAppointmentDateTime(),
                            previewAppt.getClinicName(), previewAppt.getDoctorName(),
                            current.getEmailSubject(), current.getEmailBodyIntro(), current.getEmailFromName()),
                    null
            );
            UI.showTestEmailConfirmation(saveConfirm);
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

        HBox bottomButtons = new HBox(8, testEmailBtn, testNotifBtn);
        bottomButtons.setAlignment(Pos.CENTER);

        VBox bottom = new VBox(10, bottomButtons, saveBtn, saveConfirm);
        bottom.setAlignment(Pos.CENTER);
        bottom.setMaxWidth(Double.MAX_VALUE);

        return UI.page(
                blurb,
                form.getNotifyAndDiscreetRow(),
                form.getTimePane(),
                form.getEmailPane(),
                form.getNotifPane(),
                bottom
        );
    }
}
