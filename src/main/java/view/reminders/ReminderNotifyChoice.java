package view.reminders;

import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

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
        // never squeezed to "Disa..." when the text is large; the row wraps instead (see row())
        cb.setMinWidth(Region.USE_PREF_SIZE);
        return cb;
    }

    /**
     * The on/off and discreet dropdowns side by side. At large text sizes they no longer fit the
     * 420px window on one line, so the second drops onto its own line instead of both being cut off.
     */
    public static FlowPane row(ChoiceBox<String> notify, ChoiceBox<String> discreet) {
        FlowPane row = new FlowPane(16, 8, notify, discreet);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public static void applyEnabled(ChoiceBox<String> cb, boolean enabled) {
        cb.setValue(enabled ? ENABLED : DISABLED);
    }

    public static boolean isEnabled(ChoiceBox<String> cb) {
        return ENABLED.equals(cb.getValue());
    }
}
