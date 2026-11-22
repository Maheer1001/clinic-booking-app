package view.reminders;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.ReminderSchedule;
import model.ReminderTemplateOverrides;
import view.ui.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for appointment reminder schedule JSON and template-overrides JSON (same encoding as {@code AppointmentReminder}).
 */
public final class ReminderTemplateForm {

    public record Payload(boolean notificationsEnabled, String scheduleJson, String templateOverridesJson) {}

    private final VBox scheduleRows;
    private final TextField quietStartField;
    private final TextField quietEndField;
    private final TextField emailFromNameField;
    private final TextField emailSubjectField;
    private final TextField notifTitleField;
    private final TextField notifPreviewField;
    private final TextArea notifBodyField;
    private final ChoiceBox<String> discreetChoice;
    private final ChoiceBox<String> notifyChoice;
    private final TitledPane timePane;
    private final TitledPane emailPane;
    private final TitledPane notifPane;
    /** True while fields are being filled in from saved settings, so that does not count as an edit. */
    private boolean populating = true;

    public ReminderTemplateForm(String initialCustomDays, String initialCustomMessage, boolean initialNotify) {
        Label scheduleLabel = new Label("When should we remind you?");
        scheduleLabel.setWrapText(true);
        scheduleLabel.setMaxWidth(Double.MAX_VALUE);
        this.scheduleRows = new VBox(6);

        ReminderSchedule existingSchedule = ReminderSchedule.fromString(initialCustomDays, 2);

        if (!existingSchedule.getReminders().isEmpty()) {
            for (ReminderSchedule.ReminderRule r : existingSchedule.getReminders()) {
                addScheduleRuleRow(r);
            }
        } else {
            addScheduleRuleRow(new ReminderSchedule.ReminderRule(2, "day", null, null));
        }

        Button addReminderBtn = UI.positiveButton("Add another reminder");
        addReminderBtn.setMaxWidth(Double.MAX_VALUE);
        addReminderBtn.setOnAction(e -> {
            addScheduleRuleRow(new ReminderSchedule.ReminderRule(1, "day", null, null));
            scheduleEdited();
        });

        quietStartField = UI.textField("");
        quietStartField.setPromptText("Start HH:mm");
        quietStartField.textProperty().addListener((o, a, b) -> scheduleEdited());
        quietEndField = UI.textField("");
        quietEndField.setPromptText("End HH:mm");
        quietEndField.textProperty().addListener((o, a, b) -> scheduleEdited());
        Label quietLabel = new Label("Quiet hours (optional, 24h):");
        quietLabel.setWrapText(true);
        quietLabel.setMaxWidth(Double.MAX_VALUE);
        HBox quietFields = new HBox(8, quietStartField, quietEndField);
        quietFields.setAlignment(Pos.CENTER_LEFT);
        VBox quietRow = new VBox(6, quietLabel, quietFields);
        quietRow.setMaxWidth(Double.MAX_VALUE);

        if (existingSchedule.getQuietHours() != null) {
            if (existingSchedule.getQuietHours().start() != null) {
                quietStartField.setText(existingSchedule.getQuietHours().start());
            }
            if (existingSchedule.getQuietHours().end() != null) {
                quietEndField.setText(existingSchedule.getQuietHours().end());
            }
        }

        VBox scheduleBox = new VBox(8, scheduleRows, addReminderBtn, quietRow);

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(initialCustomMessage);

        emailFromNameField = UI.textField(overrides.getEmailFromName());
        emailFromNameField.setPromptText("Email sender name (optional)");
        emailSubjectField = UI.textField(overrides.getEmailSubject());
        emailSubjectField.setPromptText("Email subject (optional)");

        notifTitleField = UI.textField(overrides.getNotificationTitle());
        notifTitleField.setPromptText("Notification title (optional)");
        notifPreviewField = UI.textField(overrides.getNotificationPreview());
        notifPreviewField.setPromptText("Notification preview line (optional)");
        notifBodyField = new TextArea(overrides.getNotificationBody());
        notifBodyField.setPromptText("Notification full body (optional)");
        notifBodyField.setPrefRowCount(3);

        discreetChoice = DiscreetReminderChoice.create();
        DiscreetReminderChoice.applyFromOverride(discreetChoice, overrides.getDiscreetOverride());
        notifyChoice = ReminderNotifyChoice.create();
        ReminderNotifyChoice.applyEnabled(notifyChoice, initialNotify);

        VBox timeContent = new VBox(6, scheduleLabel, scheduleBox);
        timePane = new TitledPane("Time", timeContent);
        timePane.setExpanded(false);

        VBox emailContent = new VBox(6,
                new Label("Email customisation"),
                emailFromNameField,
                emailSubjectField
        );
        emailPane = new TitledPane("Email", emailContent);
        emailPane.setExpanded(false);

        VBox notifContent = new VBox(6,
                new Label("Notification customisation"),
                notifTitleField,
                notifPreviewField,
                notifBodyField
        );
        notifPane = new TitledPane("Notification", notifContent);
        notifPane.setExpanded(false);

        for (TitledPane pane : List.of(timePane, emailPane, notifPane)) {
            pane.getStyleClass().add(UI.REMINDER_TITLED_PANE);
        }
        populating = false;
    }

    /**
     * Changing when to be reminded only makes sense with reminders on, so it switches them on.
     * Before, every card started as "Disabled" and saving a new schedule quietly kept it off.
     */
    private void scheduleEdited() {
        if (!populating) {
            ReminderNotifyChoice.applyEnabled(notifyChoice, true);
        }
    }

    /** The last reminder row cannot be removed: an empty schedule used to be saved as "2 days before". */
    private void refreshRemoveButtons() {
        boolean onlyOne = scheduleRows.getChildren().size() <= 1;
        int number = 0;
        for (javafx.scene.Node n : scheduleRows.getChildren()) {
            if (n.getUserData() instanceof RuleRow r) {
                number++;
                r.remove().setDisable(onlyOne);
                r.title().setText("Reminder " + number);
                n.getStyleClass().remove("first");
                if (number == 1) n.getStyleClass().add("first");
            }
        }
    }

    /** The controls of one reminder row, kept with the row so saving reads them directly. */
    private record RuleRow(Label title, Button remove, Spinner<Integer> amount, ChoiceBox<String> unit,
                           TextField timeOfDay, CheckBox repeat, Spinner<Integer> repeatAmount,
                           ChoiceBox<String> repeatUnit) {}

    private void addScheduleRuleRow(ReminderSchedule.ReminderRule rule) {
        int amount = rule != null && rule.amount() > 0 ? rule.amount() : 1;
        String unit = rule != null && rule.unit() != null ? rule.unit() : "day";

        Spinner<Integer> amountSpin = new Spinner<>(1, 365, amount);
        UI.tuneIntSpinner(amountSpin);
        ChoiceBox<String> unitChoice = new ChoiceBox<>();
        unitChoice.getItems().addAll("second", "minute", "hour", "day", "week");
        unitChoice.setValue(unit);

        TextField timeOfDayField = UI.textField("");
        timeOfDayField.setPromptText("HH:mm (optional)");
        if (rule != null && rule.timeOfDay() != null) {
            timeOfDayField.setText(rule.timeOfDay());
        }

        CheckBox repeatCheck = new CheckBox("Repeat every");
        Spinner<Integer> repeatAmount = new Spinner<>(1, 365, 1);
        UI.tuneIntSpinner(repeatAmount);
        ChoiceBox<String> repeatUnit = new ChoiceBox<>();
        repeatUnit.getItems().addAll("second", "minute", "hour", "day", "week");
        repeatUnit.setValue("day");
        if (rule != null && rule.repeatEvery() != null) {
            repeatCheck.setSelected(true);
            int ra = rule.repeatEvery().amount() > 0 ? rule.repeatEvery().amount() : 1;
            String ru = rule.repeatEvery().unit() != null ? rule.repeatEvery().unit() : "day";
            repeatAmount.getValueFactory().setValue(ra);
            repeatUnit.setValue(ru);
        }

        // The repeat interval only applies when "Repeat every" is ticked.
        repeatAmount.disableProperty().bind(repeatCheck.selectedProperty().not());
        repeatUnit.disableProperty().bind(repeatCheck.selectedProperty().not());

        // Each reminder gets its own heading ("Reminder 2") with Remove beside it, and a divider
        // above it, so it is clear which fields belong to which reminder.
        Label title = UI.heading("Reminder");
        Button removeBtn = UI.dangerButton("Remove");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, headerSpacer, removeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        Label leadCaption = UI.muted(
                "Lead time: choose the amount and unit before the appointment.");
        leadCaption.setWrapText(true);
        leadCaption.setMaxWidth(Double.MAX_VALUE);
        HBox leadControls = new HBox(8, amountSpin, unitChoice);
        leadControls.setAlignment(Pos.CENTER_LEFT);
        VBox line1 = new VBox(6, leadCaption, leadControls);

        Label todCaption = new Label("Time of day (optional, 24h):");
        todCaption.setWrapText(true);
        todCaption.setMaxWidth(Double.MAX_VALUE);
        timeOfDayField.setMaxWidth(Double.MAX_VALUE);
        VBox line2 = new VBox(6, todCaption, timeOfDayField);

        HBox repeatRow = new HBox(8, repeatAmount, repeatUnit);
        repeatRow.setAlignment(Pos.CENTER_LEFT);
        VBox line3 = new VBox(6, repeatCheck, repeatRow);

        VBox row = new VBox(8, header, line1, line2, line3);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("reminder-rule");
        row.setUserData(new RuleRow(title, removeBtn, amountSpin, unitChoice, timeOfDayField,
                repeatCheck, repeatAmount, repeatUnit));
        scheduleRows.getChildren().add(row);
        removeBtn.setOnAction(ev -> {
            if (scheduleRows.getChildren().size() > 1) {
                scheduleRows.getChildren().remove(row);
                refreshRemoveButtons();
                scheduleEdited();
            }
        });
        refreshRemoveButtons();

        amountSpin.valueProperty().addListener((o, a, b) -> scheduleEdited());
        unitChoice.valueProperty().addListener((o, a, b) -> scheduleEdited());
        timeOfDayField.textProperty().addListener((o, a, b) -> scheduleEdited());
        repeatCheck.selectedProperty().addListener((o, a, b) -> scheduleEdited());
        repeatAmount.valueProperty().addListener((o, a, b) -> scheduleEdited());
        repeatUnit.valueProperty().addListener((o, a, b) -> scheduleEdited());
    }

    /**
     * Replace all fields in the form (notify, schedule, quiet hours, and all template overrides)
     * from the given stored defaults. This does not auto-save.
     */
    public void resetAllFields(String customDays, String customMessage, boolean notifyEnabled) {
        populating = true;
        try {
            fill(customDays, customMessage, notifyEnabled);
        } finally {
            populating = false;
        }
    }

    private void fill(String customDays, String customMessage, boolean notifyEnabled) {
        ReminderNotifyChoice.applyEnabled(notifyChoice, notifyEnabled);

        ReminderSchedule schedule = ReminderSchedule.fromString(customDays, 2);
        scheduleRows.getChildren().clear();
        if (!schedule.getReminders().isEmpty()) {
            for (ReminderSchedule.ReminderRule r : schedule.getReminders()) {
                addScheduleRuleRow(r);
            }
        } else {
            addScheduleRuleRow(new ReminderSchedule.ReminderRule(2, "day", null, null));
        }

        quietStartField.setText("");
        quietEndField.setText("");
        if (schedule.getQuietHours() != null) {
            if (schedule.getQuietHours().start() != null) quietStartField.setText(schedule.getQuietHours().start());
            if (schedule.getQuietHours().end() != null) quietEndField.setText(schedule.getQuietHours().end());
        }

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);
        emailFromNameField.setText(overrides.getEmailFromName());
        emailSubjectField.setText(overrides.getEmailSubject());
        notifTitleField.setText(overrides.getNotificationTitle());
        notifPreviewField.setText(overrides.getNotificationPreview());
        notifBodyField.setText(overrides.getNotificationBody());
        DiscreetReminderChoice.applyFromOverride(discreetChoice, overrides.getDiscreetOverride());
    }

    public TitledPane getTimePane() {
        return timePane;
    }

    public TitledPane getEmailPane() {
        return emailPane;
    }

    public TitledPane getNotifPane() {
        return notifPane;
    }

    /** Compact left-aligned row: notification on/off + discreet mode. */
    public HBox getNotifyAndDiscreetRow() {
        HBox row = new HBox(16, notifyChoice, discreetChoice);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** True when reminders are switched on in this form. */
    public boolean isEnabled() {
        return ReminderNotifyChoice.isEnabled(notifyChoice);
    }

    /**
     * Checks the typed times before saving. A time such as "9am" used to be saved and then silently
     * ignored, so the reminder went out at a different time from the one shown.
     *
     * @return a message for the user, or null when everything can be saved
     */
    public String validationError() {
        for (javafx.scene.Node n : scheduleRows.getChildren()) {
            if (!(n.getUserData() instanceof RuleRow r)) continue;
            if (isBadTime(r.timeOfDay().getText())) {
                return "Time of day must be a 24-hour time such as 09:00.";
            }
        }
        String qs = quietStartField.getText() == null ? "" : quietStartField.getText().trim();
        String qe = quietEndField.getText() == null ? "" : quietEndField.getText().trim();
        if (isBadTime(qs) || isBadTime(qe)) {
            return "Quiet hours must be 24-hour times such as 22:00 and 07:00.";
        }
        if (qs.isEmpty() != qe.isEmpty()) {
            return "Enter both a start and an end for quiet hours, or leave both empty.";
        }
        return null;
    }

    private static boolean isBadTime(String text) {
        return text != null && !text.isBlank() && ReminderSchedule.parseTime(text) == null;
    }

    /** Saves times as HH:mm so "9:00" and "09:00" are stored the same way. */
    private static String normalisedTime(String text) {
        java.time.LocalTime t = ReminderSchedule.parseTime(text);
        return t == null ? null : t.toString();
    }

    public Payload collectPayload() {
        boolean notificationsEnabled = ReminderNotifyChoice.isEnabled(notifyChoice);
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
        String templateJson = toSave.toJson();

        List<ReminderSchedule.ReminderRule> rules = new ArrayList<>();
        for (javafx.scene.Node n : scheduleRows.getChildren()) {
            if (!(n.getUserData() instanceof RuleRow r)) continue;
            Spinner<Integer> amountSpin = r.amount();
            ChoiceBox<String> unitChoice = r.unit();
            TextField timeOfDayField = r.timeOfDay();
            CheckBox repeatCheck = r.repeat();
            Spinner<Integer> repeatAmount = r.repeatAmount();
            ChoiceBox<String> repeatUnit = r.repeatUnit();

            int amt = amountSpin.getValue();
            String u = unitChoice.getValue();
            String timeOfDay = normalisedTime(timeOfDayField.getText());

            ReminderSchedule.RepeatEvery repeat = null;
            if (repeatCheck.isSelected()) {
                repeat = new ReminderSchedule.RepeatEvery(repeatAmount.getValue(), repeatUnit.getValue());
            }
            rules.add(new ReminderSchedule.ReminderRule(amt, u, timeOfDay, repeat));
        }

        ReminderSchedule.QuietHours quiet = null;
        String qs = quietStartField.getText() != null ? quietStartField.getText().trim() : "";
        String qe = quietEndField.getText() != null ? quietEndField.getText().trim() : "";
        if (normalisedTime(qs) != null && normalisedTime(qe) != null) {
            quiet = new ReminderSchedule.QuietHours(normalisedTime(qs), normalisedTime(qe));
        }

        ReminderSchedule schedule = new ReminderSchedule(rules, quiet);
        return new Payload(notificationsEnabled, schedule.toJson(), templateJson);
    }

    public ReminderTemplateOverrides collectOverridesForPreview() {
        ReminderTemplateForm.Payload p = collectPayload();
        return ReminderTemplateOverrides.fromString(p.templateOverridesJson());
    }
}
