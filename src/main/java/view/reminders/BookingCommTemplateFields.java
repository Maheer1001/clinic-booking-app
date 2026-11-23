package view.reminders;

import view.ui.UI;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.ReminderTemplateOverrides;

/**
 * Email + notification customisation for a single transactional channel (confirmation, reschedule, or cancellation),
 * using the same optional fields as {@link ReminderTemplateForm}'s email/notification panels.
 */
public final class BookingCommTemplateFields {

    private final TextField emailFromNameField;
    private final TextField emailSubjectField;
    private final TextArea emailBodyIntroField;
    private final TextField notifTitleField;
    private final TextField notifPreviewField;
    private final TextArea notifBodyField;

    public BookingCommTemplateFields(ReminderTemplateOverrides initial) {
        ReminderTemplateOverrides o = initial != null
                ? initial
                : new ReminderTemplateOverrides(null, null, null, null, null, null, null);

        emailFromNameField = UI.textField(blankToEmpty(o.getEmailFromName()));
        emailFromNameField.setPromptText("Email sender name (optional)");
        emailSubjectField = UI.textField(blankToEmpty(o.getEmailSubject()));
        emailSubjectField.setPromptText("Email subject (optional)");
        emailBodyIntroField = new TextArea(blankToEmpty(o.getEmailBodyIntro()));
        emailBodyIntroField.setPromptText("Email body intro (optional, appears after greeting)");
        emailBodyIntroField.setPrefRowCount(2);

        notifTitleField = UI.textField(blankToEmpty(o.getNotificationTitle()));
        notifTitleField.setPromptText("Notification title (optional)");
        notifPreviewField = UI.textField(blankToEmpty(o.getNotificationPreview()));
        notifPreviewField.setPromptText("Notification preview line (optional)");
        notifBodyField = new TextArea(blankToEmpty(o.getNotificationBody()));
        notifBodyField.setPromptText("Notification full body (optional)");
        notifBodyField.setPrefRowCount(3);
    }

    private static String blankToEmpty(String s) {
        return s != null ? s : "";
    }

    public Node asEditor() {
        return new VBox(8,
                emailSectionContent(),
                notificationSectionContent()
        );
    }

    /** Content for a {@code TitledPane("Email", …)} — transactional templates. */
    public VBox emailSectionContent() {
        return new VBox(8,
                new Label("Email customisation"),
                emailFromNameField,
                emailSubjectField,
                new Label("Email body intro (optional)"),
                emailBodyIntroField
        );
    }

    /** Content for a {@code TitledPane("Notification", …)} — transactional templates. */
    public VBox notificationSectionContent() {
        return new VBox(8,
                new Label("Notification customisation"),
                notifTitleField,
                notifPreviewField,
                notifBodyField
        );
    }

    /** Serialises overrides; omit empty payload (returns null). */
    public String toOverridesJsonOrNull() {
        ReminderTemplateOverrides o = new ReminderTemplateOverrides(
                emailSubjectField.getText(),
                emailBodyIntroField.getText(),
                emailFromNameField.getText(),
                notifTitleField.getText(),
                notifPreviewField.getText(),
                notifBodyField.getText(),
                null
        );
        String json = o.toJson();
        return json == null || json.isBlank() ? null : json;
    }

    /** Like {@link ReminderTemplateForm#collectOverridesForPreview()} for tests. */
    public ReminderTemplateOverrides collectOverridesForPreview() {
        return new ReminderTemplateOverrides(
                emailSubjectField.getText(),
                emailBodyIntroField.getText(),
                emailFromNameField.getText(),
                notifTitleField.getText(),
                notifPreviewField.getText(),
                notifBodyField.getText(),
                null
        );
    }

    /** Stacked test buttons so labels are not truncated in narrow layouts. */
    public static VBox testButtonRow(Button testEmail, Button testNotif) {
        testEmail.setMaxWidth(Double.MAX_VALUE);
        testNotif.setMaxWidth(Double.MAX_VALUE);
        VBox v = new VBox(8, testEmail, testNotif);
        VBox.setVgrow(testEmail, Priority.NEVER);
        VBox.setVgrow(testNotif, Priority.NEVER);
        v.setFillWidth(true);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }
}
