package view.reminders;

import javafx.scene.control.Label;
import view.ui.UI;

/**
 * Inline status messages for the reminder cards (saving, sending test emails), including failures,
 * so the user is never told something worked when it did not.
 */
final class ReminderFeedback {

    static final String SENDING = "Sending test email…";
    static final String SEND_FAILED = "The test email could not be sent. Check your connection and try again.";
    static final String SAVE_FAILED = "Your changes could not be saved. Please try again.";
    static final String SAVED_OFF = "Saved. Reminders are off, so nothing will be sent. Choose Enabled to get them.";

    private ReminderFeedback() {}

    /** The same styled dialog on every card when a test email can't be sent for lack of an address. */
    static void noEmailAlert() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("No email address");
        alert.setHeaderText("No email address");
        alert.setContentText("Add an email address to your account to send a test email.");
        UI.styleDialog(alert, app.Data.getScene());
        alert.showAndWait();
    }

    static void sending(Label label) {
        clearError(label);
        label.setText(SENDING);
    }

    static void testEmailResult(Label label, boolean sent) {
        if (sent) {
            clearError(label);
            UI.showTestEmailConfirmation(label);
        } else {
            showError(label, SEND_FAILED);
        }
    }

    static void saveResult(Label label, boolean saved) {
        saveResult(label, saved, (String) null);
    }

    /** "Saved. {detail}" on success (same wording on every reminder card and the defaults). */
    static void saveResult(Label label, boolean saved, String detail) {
        if (saved) {
            clearError(label);
            UI.showSaveConfirmation(label, detail);
        } else {
            showError(label, SAVE_FAILED);
        }
    }

    /**
     * Result of saving a reminder card. Saving with reminders switched off says so plainly: before,
     * it showed the same "Saved" as a working reminder, so people thought reminders could not be set.
     */
    static void saveResult(Label label, boolean saved, boolean enabled) {
        if (saved && !enabled) {
            clearError(label);
            label.setText(SAVED_OFF);
        } else {
            saveResult(label, saved);
        }
    }


    /**
     * After saving with reminders on: says when the first email will actually go out, so the user can
     * see the reminder is really set (or that its times have already passed).
     */
    static void scheduled(Label label, java.util.Optional<java.time.LocalDateTime> firstSend, String whenNone) {
        clearError(label);
        label.setText(firstSend
                .map(t -> "Saved. Your next reminder will be emailed on " + view.ui.Dates.full(t.toLocalDate()) + " at " + view.ui.Dates.time(t.toLocalTime()) + ".")
                .orElse(whenNone));
    }

    /** After saving an appointment reminder: when it goes out, and any reminder times that won't be sent. */
    static void scheduled(Label label, service.AppointmentReminderPlan plan) {
        clearError(label);
        label.setText(plan.message());
    }

    /** Shows a problem with what was typed (nothing is saved). */
    static void invalid(Label label, String message) {
        showError(label, message);
    }

    static void reset(Label label) {
        clearError(label);
        UI.showResetToDefaultsConfirmation(label);
    }

    private static void showError(Label label, String text) {
        if (!label.getStyleClass().contains(UI.ERROR)) {
            label.getStyleClass().add(UI.ERROR);
        }
        label.setText(text);
    }

    private static void clearError(Label label) {
        label.getStyleClass().remove(UI.ERROR);
    }
}
