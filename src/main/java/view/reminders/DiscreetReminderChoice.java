package view.reminders;

import javafx.scene.control.ChoiceBox;

/**
 * Shared discreet vs full-detail choice — same wording as transactional booking comms (cancellation, etc.).
 */
public final class DiscreetReminderChoice {

    /** Visible label — keep in sync with ChoiceBox items. */
    public static final String DISCREET_MODE = "Discreet (minimal detail)";
    public static final String FULL_DETAILS = "Full details";

    private DiscreetReminderChoice() {}

    public static ChoiceBox<String> create() {
        ChoiceBox<String> cb = new ChoiceBox<>();
        cb.getItems().addAll(DISCREET_MODE, FULL_DETAILS);
        return cb;
    }

    /** Loads value from saved {@link model.ReminderTemplateOverrides} discreet flag (null defaults to discreet). */
    public static void applyFromOverride(ChoiceBox<String> cb, Boolean discreetOverride) {
        if (discreetOverride != null && !discreetOverride) {
            cb.setValue(FULL_DETAILS);
        } else {
            cb.setValue(DISCREET_MODE);
        }
    }

    /** Maps UI selection to stored override (always non-null Boolean for template JSON). */
    public static Boolean toOverride(String selected) {
        if (FULL_DETAILS.equals(selected)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    public static boolean isDiscreetSelected(ChoiceBox<String> cb) {
        return DISCREET_MODE.equals(cb.getValue());
    }
}
