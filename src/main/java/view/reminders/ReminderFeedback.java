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

    private ReminderFeedback() {}

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
        if (saved) {
            clearError(label);
            UI.showSaveConfirmation(label);
        } else {
            showError(label, SAVE_FAILED);
        }
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
