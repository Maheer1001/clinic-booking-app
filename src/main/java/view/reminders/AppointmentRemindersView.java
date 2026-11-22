package view.reminders;

import app.Data;
import controller.ReminderController;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Appointment;
import model.AppointmentReminder;
import model.AppointmentReminderDefaults;
import model.ReminderTemplateOverrides;
import service.NotificationDispatcher;
import service.email.AppointmentReminderEmail;
import view.Navigator;
import view.notification.AppointmentReminderNotification;
import view.ui.UI;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Screen for managing appointment reminders.
 */
public class AppointmentRemindersView {

    private final Navigator nav;
    private final ReminderController controller;

    public AppointmentRemindersView(Navigator nav, ReminderController controller) {
        this.nav = nav;
        this.controller = controller;
    }

    /**
     * Content for the Reminders hub Appointments tab (scrollable list or empty state).
     */
    public static Node createAppointmentTabBody(ReminderController controller) {
        VBox listBox = buildAppointmentListVBox(controller);
        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private static VBox buildAppointmentListVBox(ReminderController controller) {
        VBox listBox = new VBox(15);
        List<Appointment> appointments = controller.getUpcomingAppointments();
        List<AppointmentReminder> existingReminders = controller.getAppointmentReminders();
        Optional<AppointmentReminderDefaults> defaults = controller.getDefaultAppointmentReminderTemplate()
                .filter(AppointmentReminderDefaults::hasSavedTemplate);

        if (appointments.isEmpty()) {
            listBox.getChildren().add(UI.emptyState(UI.ICON_BELL, "No upcoming appointments",
                    "Book an appointment and you can choose when we remind you about it here."));
        } else {
            for (Appointment appt : appointments) {
                AppointmentReminder existing = existingReminders.stream()
                        .filter(r -> r.getAppId() == appt.getAppId())
                        .findFirst()
                        .orElse(null);
                listBox.getChildren().add(createAppointmentCard(appt, existing, controller, defaults));
            }
        }
        return listBox;
    }

    public void show() {
        ScrollPane scrollPane = new ScrollPane(UI.page(buildAppointmentListVBox(controller)));
        scrollPane.setFitToWidth(true);

        nav.goToPage(scrollPane, "Appointment Reminders", false);
    }

    private static VBox createAppointmentCard(Appointment appt, AppointmentReminder existing, ReminderController controller,
                                             Optional<AppointmentReminderDefaults> defaults) {
        String dateStr = view.ui.Dates.dateTime(appt.getAppointmentDateTime().toLocalDate(),
                appt.getAppointmentDateTime().toLocalTime(), null);

        HBox headerRow = UI.reminderCardHeader(appt.getServiceName(), dateStr);

        // The doctor is not recorded yet ("Clinician" is a placeholder), so only name a real one.
        String doctor = appt.getDoctorName();
        boolean namedDoctor = doctor != null && !doctor.isBlank() && !doctor.equalsIgnoreCase("Clinician");
        Label detailLabel = UI.body(appt.getClinicName() + (namedDoctor ? " with " + doctor : ""));
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(Double.MAX_VALUE);

        ReminderTemplateForm form = new ReminderTemplateForm(
                existing != null ? existing.getCustomDays() : null,
                existing != null ? existing.getCustomMessage() : null,
                existing != null && existing.isNotify());

        Button saveBtn = UI.primaryButton("Save reminder");
        Button testEmailBtn = UI.backButton("Send test email");
        Button testNotifBtn = UI.backButton("Preview notification");
        Button resetBtn = UI.backButton("Reset to default");

        Label saveConfirm = UI.inlineSaveConfirmation();

        resetBtn.setOnAction(e -> {
            if (defaults == null || defaults.isEmpty()) return;
            AppointmentReminderDefaults d = defaults.get();
            form.resetAllFields(
                    d.customDays().isBlank() ? null : d.customDays(),
                    d.customMessage().isBlank() ? null : d.customMessage(),
                    d.notificationsEnabled());
            ReminderFeedback.reset(saveConfirm);
        });

        saveBtn.setOnAction(e -> {
            String problem = form.validationError();
            if (problem != null) {
                ReminderFeedback.invalid(saveConfirm, problem);
                return;
            }
            ReminderTemplateForm.Payload p = form.collectPayload();
            boolean saved = controller.saveAppointmentReminder(appt.getAppId(), p.notificationsEnabled(), p.scheduleJson(), p.templateOverridesJson());
            ReminderFeedback.saveResult(saveConfirm, saved, p.notificationsEnabled());

            if (saved && p.notificationsEnabled()) {
                String email = Data.getCurrentUserEmail();
                String name = Data.getCurrentUserName();
                if (email == null) email = "";
                if (name == null || name.isBlank()) name = email;
                ReminderFeedback.scheduled(saveConfirm,
                        controller.scheduleAppointmentReminderWithSchedule(appt, email, name, p.scheduleJson(), p.templateOverridesJson()),
                        "Saved, but all of these reminder times have already passed.");
            }
        });

        testEmailBtn.setOnAction(e -> {
            String recipientEmail = Data.getCurrentUserEmail();
            String recipientName = Data.getCurrentUserName();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "No email address is set for the current user.");
                DialogPane dialogPane = alert.getDialogPane();
                dialogPane.getStylesheets().addAll(Data.getScene().getStylesheets());
                Stage stage = (Stage) dialogPane.getScene().getWindow();
                stage.getIcons().add(new Image(AppointmentRemindersView.class.getResource("/icons/safe.png").toExternalForm()));
                dialogPane.setGraphic(null);
                alert.showAndWait();
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
                            appt.getServiceName(), appt.getAppointmentDateTime(),
                            appt.getClinicName(), appt.getDoctorName(),
                            current.getEmailSubject(), current.getEmailBodyIntro(), current.getEmailFromName(),
                            DiscreetReminderChoice.isDiscreet(current)
                    ),
                    null,
                    sent -> {
                        testEmailBtn.setDisable(false);
                        ReminderFeedback.testEmailResult(saveConfirm, sent);
                    }
            );
        });

        testNotifBtn.setOnAction(e -> {
            ReminderTemplateOverrides current = form.collectOverridesForPreview();

            String titleOverride = current.getNotificationTitle();
            String bodyOverride = current.getNotificationBody();

            AppointmentReminderNotification notification = new AppointmentReminderNotification(
                    Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "",
                    appt.getServiceName(),
                    appt.getAppointmentDateTime(), appt.getClinicName(), appt.getDoctorName(),
                    titleOverride,
                    current.getNotificationPreview(),
                    bodyOverride,
                    current.getDiscreetOverride()
            );
            notification.show();
        });

        saveBtn.setMaxWidth(Double.MAX_VALUE);
        testEmailBtn.setMaxWidth(Double.MAX_VALUE);
        testNotifBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setMaxWidth(Double.MAX_VALUE);

        VBox bottom = new VBox(8);
        bottom.getChildren().addAll(testEmailBtn, testNotifBtn);
        if (defaults != null && defaults.isPresent()) {
            bottom.getChildren().add(resetBtn);
        }
        bottom.getChildren().addAll(saveBtn, saveConfirm);
        bottom.setAlignment(Pos.TOP_CENTER);
        bottom.setFillWidth(true);
        bottom.setMaxWidth(Double.MAX_VALUE);

        return UI.card(10,
                headerRow,
                detailLabel,
                form.getNotifyAndDiscreetRow(),
                form.getTimePane(),
                form.getEmailPane(),
                form.getNotifPane(),
                bottom
        );
    }
}
