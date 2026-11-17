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
        addReminderBtn.setOnAction(e -> addScheduleRuleRow(new ReminderSchedule.ReminderRule(1, "day", null, null)));

        quietStartField = UI.textField("");
        quietStartField.setPromptText("Start HH:mm");
        quietEndField = UI.textField("");
        quietEndField.setPromptText("End HH:mm");
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
    }

    private void addScheduleRuleRow(ReminderSchedule.ReminderRule rule) {
        int amount = rule != null && rule.amount() > 0 ? rule.amount() : 1;
        String unit = rule != null && rule.unit() != null ? rule.unit() : "day";

        Spinner<Integer> amountSpin = new Spinner<>(0, 365, amount);
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

        Button removeBtn = UI.dangerButton("Remove");
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

        VBox line3 = new VBox(6);
        line3.getChildren().add(repeatCheck);
        Region repeatSpacer = new Region();
        HBox.setHgrow(repeatSpacer, Priority.ALWAYS);
        HBox repeatRow = new HBox(8, repeatAmount, repeatUnit, repeatSpacer, removeBtn);
        repeatRow.setAlignment(Pos.CENTER_LEFT);
        line3.getChildren().add(repeatRow);

        VBox row = new VBox(8, line1, line2, line3);
        row.setMaxWidth(Double.MAX_VALUE);
        scheduleRows.getChildren().add(row);
        removeBtn.setOnAction(ev -> scheduleRows.getChildren().remove(row));
    }

    /**
     * Replace all fields in the form (notify, schedule, quiet hours, and all template overrides)
     * from the given stored defaults. This does not auto-save.
     */
    public void resetAllFields(String customDays, String customMessage, boolean notifyEnabled) {
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
            if (!(n instanceof VBox row)) continue;
            if (row.getChildren().size() < 3) continue;
            VBox line1 = (VBox) row.getChildren().get(0);
            VBox line2 = (VBox) row.getChildren().get(1);
            VBox line3 = (VBox) row.getChildren().get(2);

            HBox leadControls = (HBox) line1.getChildren().get(1);
            Spinner<Integer> amountSpin = (Spinner<Integer>) leadControls.getChildren().get(0);
            ChoiceBox<String> unitChoice = (ChoiceBox<String>) leadControls.getChildren().get(1);
            TextField timeOfDayField = (TextField) line2.getChildren().get(1);
            CheckBox repeatCheck = (CheckBox) line3.getChildren().get(0);
            HBox repeatRow = (HBox) line3.getChildren().get(1);
            Spinner<Integer> repeatAmount = (Spinner<Integer>) repeatRow.getChildren().get(0);
            ChoiceBox<String> repeatUnit = (ChoiceBox<String>) repeatRow.getChildren().get(1);

            int amt = amountSpin.getValue();
            String u = unitChoice.getValue();
            String timeOfDay = timeOfDayField.getText();

            ReminderSchedule.RepeatEvery repeat = null;
            if (repeatCheck.isSelected()) {
                repeat = new ReminderSchedule.RepeatEvery(repeatAmount.getValue(), repeatUnit.getValue());
            }
            rules.add(new ReminderSchedule.ReminderRule(amt, u, timeOfDay, repeat));
        }

        ReminderSchedule.QuietHours quiet = null;
        String qs = quietStartField.getText() != null ? quietStartField.getText().trim() : "";
        String qe = quietEndField.getText() != null ? quietEndField.getText().trim() : "";
        if (!qs.isEmpty() && !qe.isEmpty()) {
            quiet = new ReminderSchedule.QuietHours(qs, qe);
        }

        ReminderSchedule schedule = new ReminderSchedule(rules, quiet);
        return new Payload(notificationsEnabled, schedule.toJson(), templateJson);
    }

    public ReminderTemplateOverrides collectOverridesForPreview() {
        ReminderTemplateForm.Payload p = collectPayload();
        return ReminderTemplateOverrides.fromString(p.templateOverridesJson());
    }
}
