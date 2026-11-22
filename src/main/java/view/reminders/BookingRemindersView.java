package view.reminders;

import app.Data;
import controller.ReminderController;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.BookingCommunicationPreferences;
import model.BookingReminder;
import model.ReminderTemplateOverrides;
import service.NotificationDispatcher;
import service.NotificationScheduler;
import service.email.BookingConfirmationEmail;
import service.email.CancellationEmail;
import service.email.MakeAppointmentReminderEmail;
import service.email.RescheduleConfirmationEmail;
import view.Navigator;
import view.notification.BookingConfirmationNotification;
import view.notification.CancellationNotification;
import view.notification.MakeAppointmentReminderNotification;
import view.notification.RescheduleConfirmationNotification;
import view.ui.UI;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for managing booking reminders.
 */
public class BookingRemindersView {

    private final Navigator nav;
    private final ReminderController controller;

    public BookingRemindersView(Navigator nav, ReminderController controller) {
        this.nav = nav;
        this.controller = controller;
    }

    /**
     * Content for the Reminders hub “Booking reminders” tab (single settings form).
     */
    public static Node createBookingReminderTabBody(ReminderController controller) {
        VBox inner = buildBookingForm(controller);
        inner.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(inner);
        sp.setFitToWidth(true);
        return sp;
    }

    public void show() {
        ScrollPane scroll = new ScrollPane(UI.page(buildBookingForm(controller)));
        scroll.setFitToWidth(true);

        nav.goToPage(scroll, "Booking Reminders", false);
    }

    private static VBox buildBookingForm(ReminderController controller) {
        BookingCommunicationPreferences commPrefs = controller.getBookingCommunicationPreferences();

        List<BookingReminder> existingReminders = controller.getBookingReminders();
        BookingReminder existing = existingReminders.isEmpty() ? null : existingReminders.getFirst();

        ChoiceBox<String> notifyChoice = ReminderNotifyChoice.create();
        ReminderNotifyChoice.applyEnabled(notifyChoice, existing != null && existing.isNotify());

        Label daysLabel = new Label("Remind me if it has been this many days since my last visit:");
        daysLabel.setWrapText(true);
        daysLabel.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> daysSpinner = new Spinner<>(30, 365, existing != null ? existing.getDaysSinceLast() : 180);
        UI.tuneIntSpinner(daysSpinner);
        daysSpinner.valueProperty().addListener((o, a, b) -> ReminderNotifyChoice.applyEnabled(notifyChoice, true));
        HBox daysSpinnerRow = new HBox(10, daysSpinner);
        daysSpinnerRow.setAlignment(Pos.CENTER_LEFT);
        VBox daysBlock = new VBox(8, daysLabel, daysSpinnerRow);
        daysBlock.setMaxWidth(Double.MAX_VALUE);

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(
                existing != null ? existing.getCustomMessage() : null
        );

        TextField emailFromNameField = UI.textField(overrides.getEmailFromName());
        emailFromNameField.setPromptText("Email sender name (optional)");
        TextField emailSubjectField = UI.textField(overrides.getEmailSubject());
        emailSubjectField.setPromptText("Email subject (optional)");

        TextField notifTitleField = UI.textField(overrides.getNotificationTitle());
        notifTitleField.setPromptText("Notification title (optional)");
        TextField notifPreviewField = UI.textField(overrides.getNotificationPreview());
        notifPreviewField.setPromptText("Notification preview line (optional)");
        TextArea notifBodyField = new TextArea(overrides.getNotificationBody());
        notifBodyField.setPromptText("Notification full body (optional)");
        notifBodyField.setPrefRowCount(3);

        ChoiceBox<String> discreetChoice = DiscreetReminderChoice.create();
        DiscreetReminderChoice.applyFromOverride(discreetChoice, overrides.getDiscreetOverride());

        ChoiceBox<String> confirmationCommChoice = newCommStyleChoice(commPrefs.confirmationDiscreet());
        ChoiceBox<String> rescheduleCommChoice = newCommStyleChoice(commPrefs.rescheduleDiscreet());
        ChoiceBox<String> cancellationCommChoice = newCommStyleChoice(commPrefs.cancellationDiscreet());

        BookingCommTemplateFields confirmationFields = new BookingCommTemplateFields(commPrefs.confirmationTemplate());
        BookingCommTemplateFields rescheduleFields = new BookingCommTemplateFields(commPrefs.rescheduleTemplate());
        BookingCommTemplateFields cancellationFields = new BookingCommTemplateFields(commPrefs.cancellationTemplate());

        Label saveConfirmReminder = UI.inlineSaveConfirmation();
        Label saveConfirmConfirmation = UI.inlineSaveConfirmation();
        Label saveConfirmReschedule = UI.inlineSaveConfirmation();
        Label saveConfirmCancellation = UI.inlineSaveConfirmation();

        // Plain VBox like other Reminders hub tabs — hub shell already uses UI.PAGE padding.
        VBox root = new VBox(16);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setFillWidth(true);

        root.getChildren().addAll(
                reminderEmailsNotificationsSection(
                        controller,
                        notifyChoice,
                        daysBlock,
                        discreetChoice,
                        emailFromNameField,
                        emailSubjectField,
                        notifTitleField,
                        notifPreviewField,
                        notifBodyField,
                        daysSpinner,
                        saveConfirmReminder),
                transactionalSection(
                        "Booking confirmations",
                        "Applies when you complete a booking (confirmation email and in-app message).",
                        confirmationCommChoice,
                        confirmationFields,
                        saveConfirmConfirmation,
                        () -> saveCommunicationPreferences(controller,
                                confirmationCommChoice,
                                rescheduleCommChoice,
                                cancellationCommChoice,
                                confirmationFields,
                                rescheduleFields,
                                cancellationFields),
                        lbl -> sendConfirmationTest(confirmationFields, confirmationCommChoice, lbl),
                        () -> showConfirmationTestNotif(confirmationFields, confirmationCommChoice)),
                transactionalSection(
                        "Reschedule messages",
                        "Applies when you reschedule an appointment.",
                        rescheduleCommChoice,
                        rescheduleFields,
                        saveConfirmReschedule,
                        () -> saveCommunicationPreferences(controller,
                                confirmationCommChoice,
                                rescheduleCommChoice,
                                cancellationCommChoice,
                                confirmationFields,
                                rescheduleFields,
                                cancellationFields),
                        lbl -> sendRescheduleTest(rescheduleFields, rescheduleCommChoice, lbl),
                        () -> showRescheduleTestNotif(rescheduleFields, rescheduleCommChoice)),
                transactionalSection(
                        "Cancellation messages",
                        "Applies when you cancel an appointment.",
                        cancellationCommChoice,
                        cancellationFields,
                        saveConfirmCancellation,
                        () -> saveCommunicationPreferences(controller,
                                confirmationCommChoice,
                                rescheduleCommChoice,
                                cancellationCommChoice,
                                confirmationFields,
                                rescheduleFields,
                                cancellationFields),
                        lbl -> sendCancellationTest(cancellationFields, cancellationCommChoice, lbl),
                        () -> showCancellationTestNotif(cancellationFields, cancellationCommChoice))
        );

        return root;
    }

    /** Section 1 — same layout pattern as appointment / prescription reminder cards. */
    private static VBox reminderEmailsNotificationsSection(
            ReminderController controller,
            ChoiceBox<String> notifyChoice,
            VBox daysSchedulingBlock,
            ChoiceBox<String> discreetChoice,
            TextField emailFromNameField,
            TextField emailSubjectField,
            TextField notifTitleField,
            TextField notifPreviewField,
            TextArea notifBodyField,
            Spinner<Integer> daysSpinner,
            Label saveConfirmReminder) {

        Label explanation = UI.body(
                "Get reminded to book an appointment if it has been a while since your last visit.");
        explanation.setWrapText(true);

        HBox notifyDiscreetRow = new HBox(16, notifyChoice, discreetChoice);
        notifyDiscreetRow.setAlignment(Pos.CENTER_LEFT);

        Label timeIntro = UI.muted(
                "How long to wait before we nudge you to book again.");
        timeIntro.setWrapText(true);
        VBox timeInner = new VBox(8, timeIntro, daysSchedulingBlock);
        TitledPane timePane = styledPane("Time", timeInner);

        VBox emailInner = new VBox(8,
                new Label("Email customisation"),
                emailFromNameField,
                emailSubjectField
        );
        TitledPane emailPane = styledPane("Email", emailInner);

        VBox notifInner = new VBox(8,
                new Label("Notification customisation"),
                notifTitleField,
                notifPreviewField,
                notifBodyField
        );
        TitledPane notifPane = styledPane("Notification", notifInner);

        Button saveBtn = UI.primaryButton("Save");
        Button testEmailBtn = UI.secondaryButton("Send test email");
        Button previewNotifBtn = UI.secondaryButton("Preview notification");

        saveBtn.setOnAction(e -> {
            boolean notify = ReminderNotifyChoice.isEnabled(notifyChoice);
            int days = daysSpinner.getValue();
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

            boolean saved = controller.saveBookingReminder(notify, days, customJson, Data.getCurrentUserEmail());
            ReminderFeedback.saveResult(saveConfirmReminder, saved, notify);

            if (saved && notify) {
                String email = Data.getCurrentUserEmail();
                String name = Data.getCurrentUserName();
                if (email == null) email = "";
                if (name == null || name.isBlank()) name = email;
                ReminderFeedback.scheduled(saveConfirmReminder,
                        controller.scheduleBookingReminder(email, name, days, customJson),
                        controller.hasUpcomingAppointment()
                                ? "Saved. You already have an appointment booked, so we'll count from that visit."
                                : "Saved. We'll start counting from your first visit.");
            }
        });

        testEmailBtn.setOnAction(e -> {
            String recipientEmail = Data.getCurrentUserEmail();
            String recipientName = Data.getCurrentUserName();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                ReminderFeedback.noEmailAlert();
                return;
            }
            if (recipientName == null || recipientName.isBlank()) {
                recipientName = recipientEmail;
            }

            int days = daysSpinner.getValue();
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

            sendTestEmailAsync(
                    new MakeAppointmentReminderEmail(
                            recipientEmail, recipientName,
                            NotificationScheduler.bookingReminderReason(days),
                            "Routine Sexual Health Check-up",
                            current.getEmailSubject(), current.getEmailBodyIntro(), current.getEmailFromName(),
                            DiscreetReminderChoice.isDiscreet(current)
                    ),
                    testEmailBtn, saveConfirmReminder);
        });

        previewNotifBtn.setOnAction(e -> {
            int days = daysSpinner.getValue();
            ReminderTemplateOverrides current = new ReminderTemplateOverrides(
                    null,
                    null,
                    null,
                    notifTitleField.getText(),
                    notifPreviewField.getText(),
                    notifBodyField.getText(),
                    null
            );

            new MakeAppointmentReminderNotification(
                    Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "",
                    NotificationScheduler.bookingReminderReason(days),
                    "Routine Sexual Health Check-up",
                    current.getNotificationTitle(),
                    current.getNotificationPreview(),
                    current.getNotificationBody(),
                    DiscreetReminderChoice.toOverride(discreetChoice.getValue())
            ).show();
        });

        saveBtn.setMaxWidth(Double.MAX_VALUE);
        testEmailBtn.setMaxWidth(Double.MAX_VALUE);
        previewNotifBtn.setMaxWidth(Double.MAX_VALUE);

        VBox saveBlock = new VBox(8, testEmailBtn, previewNotifBtn, saveBtn, saveConfirmReminder);
        saveBlock.setAlignment(Pos.TOP_CENTER);
        saveBlock.setFillWidth(true);
        saveBlock.setMaxWidth(Double.MAX_VALUE);

        return UI.card(12,
                UI.titleSmWrapping("Check-up reminders"),
                explanation,
                notifyDiscreetRow,
                timePane,
                emailPane,
                notifPane,
                saveBlock
        );
    }

    private static VBox transactionalSection(
            String sectionTitle,
            String explanationText,
            ChoiceBox<String> commChoice,
            BookingCommTemplateFields fields,
            Label saveConfirm,
            Runnable saveAction,
            Consumer<Label> testEmailAction,
            Runnable previewNotificationAction) {

        Label explanation = UI.body(explanationText);
        explanation.setWrapText(true);

        HBox commRow = new HBox(commChoice);
        commRow.setAlignment(Pos.CENTER_LEFT);

        TitledPane emailPane = styledPane("Email", fields.emailSectionContent());
        TitledPane notifPane = styledPane("Notification", fields.notificationSectionContent());

        Button saveBtn = UI.primaryButton("Save");
        Button testEmailBtn = UI.secondaryButton("Send test email");
        Button previewBtn = UI.secondaryButton("Preview notification");

        saveBtn.setOnAction(e -> {
            boolean saved;
            try {
                saveAction.run();
                saved = true;
            } catch (RuntimeException ex) {
                System.err.println("Could not save communication preferences: " + ex.getMessage());
                saved = false;
            }
            ReminderFeedback.saveResult(saveConfirm, saved, "Future emails like this will use these settings.");
        });
        testEmailBtn.setOnAction(e -> testEmailAction.accept(saveConfirm));
        previewBtn.setOnAction(e -> previewNotificationAction.run());

        saveBtn.setMaxWidth(Double.MAX_VALUE);
        testEmailBtn.setMaxWidth(Double.MAX_VALUE);
        previewBtn.setMaxWidth(Double.MAX_VALUE);

        VBox actions = new VBox(8, testEmailBtn, previewBtn, saveBtn, saveConfirm);
        actions.setAlignment(Pos.TOP_CENTER);
        actions.setFillWidth(true);
        actions.setMaxWidth(Double.MAX_VALUE);

        return UI.card(12,
                UI.titleSmWrapping(sectionTitle),
                explanation,
                commRow,
                emailPane,
                notifPane,
                actions
        );
    }

    private static void sendConfirmationTest(BookingCommTemplateFields fields, ChoiceBox<String> discreetChoice,
                                             Label testEmailFeedback) {
        String recipientEmail = Data.getCurrentUserEmail();
        String recipientName = Data.getCurrentUserName();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            ReminderFeedback.noEmailAlert();
            return;
        }
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = recipientEmail;
        }
        boolean disc = commChoiceIsDiscreet(discreetChoice);
        ReminderTemplateOverrides ov = fields.collectOverridesForPreview();
        sendTestEmailAsync(
                new BookingConfirmationEmail(
                        recipientEmail, recipientName,
                        "APP-999", "Example service",
                        LocalDateTime.now(), "Example clinic", "Clinician",
                        disc, ov),
                null, testEmailFeedback);
    }

    private static void showConfirmationTestNotif(BookingCommTemplateFields fields, ChoiceBox<String> discreetChoice) {
        String name = Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "";
        boolean disc = commChoiceIsDiscreet(discreetChoice);
        ReminderTemplateOverrides ov = fields.collectOverridesForPreview();
        new BookingConfirmationNotification(
                name, "APP-999", "Example service",
                LocalDateTime.now(), "Example clinic", "Clinician",
                disc, ov
        ).show();
    }

    /** A realistic date for previews and test emails, written like every other date in the app. */
    private static String sampleWhen(int daysFromNow) {
        return view.ui.Dates.dateTime(java.time.LocalDate.now().plusDays(daysFromNow),
                java.time.LocalTime.of(10, 0), java.time.LocalTime.of(10, 30));
    }

    private static void sendRescheduleTest(BookingCommTemplateFields fields, ChoiceBox<String> discreetChoice,
                                           Label testEmailFeedback) {
        String recipientEmail = Data.getCurrentUserEmail();
        String recipientName = Data.getCurrentUserName();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            ReminderFeedback.noEmailAlert();
            return;
        }
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = recipientEmail;
        }
        boolean disc = commChoiceIsDiscreet(discreetChoice);
        ReminderTemplateOverrides ov = fields.collectOverridesForPreview();
        sendTestEmailAsync(
                new RescheduleConfirmationEmail(
                        recipientEmail, recipientName,
                        "APP-999", "Example service", "Example clinic",
                        sampleWhen(7), sampleWhen(9),
                        disc, ov),
                null, testEmailFeedback);
    }

    private static void showRescheduleTestNotif(BookingCommTemplateFields fields, ChoiceBox<String> discreetChoice) {
        String name = Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "";
        boolean disc = commChoiceIsDiscreet(discreetChoice);
        ReminderTemplateOverrides ov = fields.collectOverridesForPreview();
        new RescheduleConfirmationNotification(
                name, "APP-999", "Example service", "Example clinic",
                sampleWhen(7), sampleWhen(9),
                disc, ov
        ).show();
    }

    private static void sendCancellationTest(BookingCommTemplateFields fields, ChoiceBox<String> discreetChoice,
                                               Label testEmailFeedback) {
        String recipientEmail = Data.getCurrentUserEmail();
        String recipientName = Data.getCurrentUserName();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            ReminderFeedback.noEmailAlert();
            return;
        }
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = recipientEmail;
        }
        boolean disc = commChoiceIsDiscreet(discreetChoice);
        ReminderTemplateOverrides ov = fields.collectOverridesForPreview();
        sendTestEmailAsync(
                new CancellationEmail(
                        recipientEmail, recipientName,
                        "APP-999", "Example service", "Example clinic",
                        sampleWhen(7),
                        disc, ov),
                null, testEmailFeedback);
    }

    /** Sends off the FX thread and reports the real outcome in the card's status label. */
    private static void sendTestEmailAsync(service.email.Email email, Button button, Label feedback) {
        ReminderFeedback.sending(feedback);
        if (button != null) {
            button.setDisable(true);
        }
        new NotificationDispatcher().notifyUserAsync(Data.getCurrentUserId(), email, null, sent -> {
            if (button != null) {
                button.setDisable(false);
            }
            ReminderFeedback.testEmailResult(feedback, sent);
        });
    }

    private static void showCancellationTestNotif(BookingCommTemplateFields fields, ChoiceBox<String> discreetChoice) {
        String name = Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "";
        boolean disc = commChoiceIsDiscreet(discreetChoice);
        ReminderTemplateOverrides ov = fields.collectOverridesForPreview();
        new CancellationNotification(
                name, "APP-999", "Example service", "Example clinic",
                sampleWhen(7),
                disc, ov
        ).show();
    }

    private static void saveCommunicationPreferences(
            ReminderController controller,
            ChoiceBox<String> confirmationCommChoice,
            ChoiceBox<String> rescheduleCommChoice,
            ChoiceBox<String> cancellationCommChoice,
            BookingCommTemplateFields confirmationFields,
            BookingCommTemplateFields rescheduleFields,
            BookingCommTemplateFields cancellationFields) {
        controller.saveBookingCommunicationPreferences(new BookingCommunicationPreferences(
                commChoiceIsDiscreet(confirmationCommChoice),
                commChoiceIsDiscreet(rescheduleCommChoice),
                commChoiceIsDiscreet(cancellationCommChoice),
                confirmationFields.toOverridesJsonOrNull(),
                rescheduleFields.toOverridesJsonOrNull(),
                cancellationFields.toOverridesJsonOrNull()
        ));
    }

    private static TitledPane styledPane(String title, Node content) {
        TitledPane p = new TitledPane(title, content);
        p.setExpanded(false);
        p.getStyleClass().add(UI.REMINDER_TITLED_PANE);
        return p;
    }

    private static ChoiceBox<String> newCommStyleChoice(boolean discreet) {
        ChoiceBox<String> cb = DiscreetReminderChoice.create();
        DiscreetReminderChoice.applyFromOverride(cb, discreet ? Boolean.TRUE : Boolean.FALSE);
        return cb;
    }

    private static boolean commChoiceIsDiscreet(ChoiceBox<String> cb) {
        return DiscreetReminderChoice.isDiscreetSelected(cb);
    }
}
