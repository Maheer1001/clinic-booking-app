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
import model.Prescription;
import model.PrescriptionReminder;
import model.AppointmentReminderDefaults;
import model.ReminderTemplateOverrides;
import service.NotificationDispatcher;
import service.email.TreatmentReminderEmail;
import view.Navigator;
import view.notification.TreatmentReminderNotification;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Screen for managing treatment (prescription) reminders.
 */
public class TreatmentRemindersView {

    private final Navigator nav;
    private final ReminderController controller;

    public TreatmentRemindersView(Navigator nav, ReminderController controller) {
        this.nav = nav;
        this.controller = controller;
    }

    /**
     * Content for the Reminders hub Prescriptions tab.
     */
    public static Node createPrescriptionTabBody(ReminderController controller) {
        VBox listBox = buildPrescriptionListVBox(controller);
        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private static VBox buildPrescriptionListVBox(ReminderController controller) {
        VBox listBox = new VBox(15);
        List<Prescription> prescriptions = controller.getPrescriptionsForReminders();
        List<PrescriptionReminder> existingReminders = controller.getPrescriptionReminders();
        Optional<AppointmentReminderDefaults> defaults = controller.getDefaultAppointmentReminderTemplate()
                .filter(AppointmentReminderDefaults::hasSavedTemplate);

        if (prescriptions.isEmpty()) {
            Label empty = UI.muted("No current prescriptions.");
            empty.setWrapText(true);
            listBox.getChildren().add(empty);
        } else {
            for (Prescription rx : prescriptions) {
                PrescriptionReminder existing = existingReminders.stream()
                        .filter(r -> r.getPrescriptionId() == rx.getPrescriptionId())
                        .findFirst()
                        .orElse(null);
                listBox.getChildren().add(createPrescriptionCard(rx, existing, controller, defaults));
            }
        }
        return listBox;
    }

    public void show() {
        ScrollPane scrollPane = new ScrollPane(UI.page(buildPrescriptionListVBox(controller)));
        scrollPane.setFitToWidth(true);

        nav.goToPage(scrollPane, "Treatment Reminders", false);
    }

    /**
     * Date of the next dose reminder, as the scheduler will send it. (The end date used before was
     * null for ongoing prescriptions, which crashed the preview, and was not the next due date anyway.)
     */
    private static LocalDate nextDoseDate(int intervalHours) {
        return java.time.LocalDateTime.now().plusHours(intervalHours).toLocalDate();
    }

    private static VBox createPrescriptionCard(Prescription rx, PrescriptionReminder existing, ReminderController controller,
                                              Optional<AppointmentReminderDefaults> defaults) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");

        LocalDate end = rx.getEndDate();
        Label dateLabel;
        if (end == null) {
            dateLabel = UI.body("From " + rx.getStartDate().format(dateFmt));
        } else {
            dateLabel = UI.body("From " + rx.getStartDate().format(dateFmt)
                    + " to " + end.format(dateFmt));
        }
        dateLabel.setWrapText(true);
        dateLabel.setMaxWidth(Double.MAX_VALUE);

        HBox headerRow = UI.reminderCardHeader(rx.getMedicineName(), rx.getDosage());

        ChoiceBox<String> notifyChoice = ReminderNotifyChoice.create();
        ReminderNotifyChoice.applyEnabled(notifyChoice, existing != null && existing.isNotify());

        Label intervalLabel = new Label("Remind me every (hours):");
        intervalLabel.setWrapText(true);
        intervalLabel.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> intervalSpinner = new Spinner<>(1, 72,
                existing != null ? existing.getIntervalBetweenDoses() : 12);
        UI.tuneIntSpinner(intervalSpinner);
        HBox intervalSpinnerRow = new HBox(10, intervalSpinner);
        intervalSpinnerRow.setAlignment(Pos.CENTER_LEFT);
        VBox intervalBlock = new VBox(8, intervalLabel, intervalSpinnerRow);
        intervalBlock.setMaxWidth(Double.MAX_VALUE);

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(
                existing != null ? existing.getCustomMessage() : null
        );

        TextField emailFromNameField = new TextField(overrides.getEmailFromName());
        emailFromNameField.setPromptText("Email sender name (optional)");
        TextField emailSubjectField = new TextField(overrides.getEmailSubject());
        emailSubjectField.setPromptText("Email subject (optional)");

        TextField notifTitleField = new TextField(overrides.getNotificationTitle());
        notifTitleField.setPromptText("Notification title (optional)");
        TextField notifPreviewField = new TextField(overrides.getNotificationPreview());
        notifPreviewField.setPromptText("Notification preview line (optional)");
        TextArea notifBodyField = new TextArea(overrides.getNotificationBody());
        notifBodyField.setPromptText("Notification full body (optional)");
        notifBodyField.setPrefRowCount(3);

        ChoiceBox<String> discreetChoice = DiscreetReminderChoice.create();
        DiscreetReminderChoice.applyFromOverride(discreetChoice, overrides.getDiscreetOverride());

        HBox notifyDiscreetRow = new HBox(16, notifyChoice, discreetChoice);
        notifyDiscreetRow.setAlignment(Pos.CENTER_LEFT);

        Label timeIntro = UI.muted(
                "Set how often we should remind you between doses (based on your dosing schedule).");
        timeIntro.setWrapText(true);
        VBox timeContent = new VBox(8, timeIntro, intervalBlock);
        TitledPane timePane = new TitledPane("Time", timeContent);

        VBox emailContent = new VBox(6,
                new Label("Email customisation"),
                emailFromNameField,
                emailSubjectField
        );
        TitledPane emailPane = new TitledPane("Email", emailContent);

        VBox notifContent = new VBox(6,
                new Label("Notification customisation"),
                notifTitleField,
                notifPreviewField,
                notifBodyField
        );
        TitledPane notifPane = new TitledPane("Notification", notifContent);

        for (TitledPane p : List.of(timePane, emailPane, notifPane)) {
            p.getStyleClass().add(UI.REMINDER_TITLED_PANE);
            p.setExpanded(false);
        }

        Button saveBtn = UI.backButton("Save Reminder");
        Button testEmailBtn = UI.backButton("Send test email");
        Button testNotifBtn = UI.backButton("Preview notification");
        Button resetBtn = UI.backButton("Reset to default");
        Label saveConfirm = UI.inlineSaveConfirmation();

        boolean canReset = defaults != null && defaults.isPresent() && rx.getDosesPerDay() != null && rx.getDosesPerDay() > 0;
        resetBtn.setOnAction(e -> {
            if (!canReset) return;
            AppointmentReminderDefaults d = defaults.get();
            ReminderTemplateOverrides defOv = ReminderTemplateOverrides.fromString(d.customMessage());
            boolean notify = d.notificationsEnabled();
            Integer doses = rx.getDosesPerDay();
            int interval;
            if (doses == null || doses <= 0) {
                return;
            } else if (doses == 1) {
                interval = 24;
            } else {
                interval = Math.max(1, (int) Math.floor(24.0 / doses));
            }

            ReminderNotifyChoice.applyEnabled(notifyChoice, notify);
            DiscreetReminderChoice.applyFromOverride(discreetChoice, defOv.getDiscreetOverride());
            emailFromNameField.setText(defOv.getEmailFromName());
            emailSubjectField.setText(defOv.getEmailSubject());
            notifTitleField.setText(defOv.getNotificationTitle());
            notifPreviewField.setText(defOv.getNotificationPreview());
            notifBodyField.setText(defOv.getNotificationBody());
            intervalSpinner.getValueFactory().setValue(interval);
            ReminderFeedback.reset(saveConfirm);
        });

        saveBtn.setOnAction(e -> {
            boolean notify = ReminderNotifyChoice.isEnabled(notifyChoice);
            int interval = intervalSpinner.getValue();

            Boolean discreetOverride = DiscreetReminderChoice.toOverride(discreetChoice.getValue());

            ReminderTemplateOverrides toSave = new ReminderTemplateOverrides(
                    emailSubjectField.getText(),
                    null,
                    emailFromNameField.getText(),
                    notifTitleField.getText(),
                    notifPreviewField.getText(),
                    notifBodyField.getText(),
                    discreetOverride
            );
            String customJson = toSave.toJson();

            boolean saved = controller.savePrescriptionReminder(rx.getPrescriptionId(), notify, interval, customJson);
            ReminderFeedback.saveResult(saveConfirm, saved);

            if (saved && notify) {
                String email = Data.getCurrentUserEmail();
                String name = Data.getCurrentUserName();
                if (email == null) email = "";
                if (name == null || name.isBlank()) name = email;
                controller.scheduleTreatmentReminder(rx, email, name, interval, customJson);
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
                stage.getIcons().add(new Image(TreatmentRemindersView.class.getResource("/icons/safe.png").toExternalForm()));
                dialogPane.setGraphic(null);
                alert.showAndWait();
                return;
            }
            if (recipientName == null || recipientName.isBlank()) {
                recipientName = recipientEmail;
            }

            Boolean discreetOverride = DiscreetReminderChoice.toOverride(discreetChoice.getValue());

            ReminderTemplateOverrides current = new ReminderTemplateOverrides(
                    emailSubjectField.getText(),
                    null,
                    emailFromNameField.getText(),
                    null,
                    null,
                    null,
                    discreetOverride
            );

            ReminderFeedback.sending(saveConfirm);
            testEmailBtn.setDisable(true);
            new NotificationDispatcher().notifyUserAsync(
                    Data.getCurrentUserId(),
                    new TreatmentReminderEmail(
                            recipientEmail, recipientName,
                            rx.getMedicineName(), rx.getDosage(), nextDoseDate(intervalSpinner.getValue()),
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
            Boolean discreetOverride = DiscreetReminderChoice.toOverride(discreetChoice.getValue());

            ReminderTemplateOverrides current = new ReminderTemplateOverrides(
                    null,
                    null,
                    null,
                    notifTitleField.getText(),
                    notifPreviewField.getText(),
                    notifBodyField.getText(),
                    discreetOverride
            );

            TreatmentReminderNotification notification = new TreatmentReminderNotification(
                    Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "",
                    rx.getMedicineName(),
                    rx.getDosage(),
                    nextDoseDate(intervalSpinner.getValue()),
                    current.getNotificationTitle(),
                    current.getNotificationPreview(),
                    current.getNotificationBody(),
                    current.getDiscreetOverride()
            );
            notification.show();
        });

        saveBtn.setMaxWidth(Double.MAX_VALUE);
        testEmailBtn.setMaxWidth(Double.MAX_VALUE);
        testNotifBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setMaxWidth(Double.MAX_VALUE);

        VBox saveBlock = new VBox(8);
        saveBlock.getChildren().addAll(testEmailBtn, testNotifBtn);
        if (canReset) {
            saveBlock.getChildren().add(resetBtn);
        }
        saveBlock.getChildren().addAll(saveBtn, saveConfirm);
        saveBlock.setAlignment(Pos.TOP_CENTER);
        saveBlock.setFillWidth(true);
        saveBlock.setMaxWidth(Double.MAX_VALUE);

        return UI.card(8,
                headerRow,
                dateLabel,
                notifyDiscreetRow,
                timePane,
                emailPane,
                notifPane,
                saveBlock
        );
    }
}
