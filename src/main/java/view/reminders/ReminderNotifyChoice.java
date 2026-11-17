package view.reminders;

import javafx.scene.control.ChoiceBox;

/**
 * Whether scheduled / in-app notifications are on for a reminder.
 */
public final class ReminderNotifyChoice {

    public static final String ENABLED = "Enabled";
    public static final String DISABLED = "Disabled";

    private ReminderNotifyChoice() {}

    public static ChoiceBox<String> create() {
        ChoiceBox<String> cb = new ChoiceBox<>();
        cb.getItems().addAll(ENABLED, DISABLED);
        return cb;
    }

    public static void applyEnabled(ChoiceBox<String> cb, boolean enabled) {
        cb.setValue(enabled ? ENABLED : DISABLED);
    }

    public static boolean isEnabled(ChoiceBox<String> cb) {
        return ENABLED.equals(cb.getValue());
    }
}
