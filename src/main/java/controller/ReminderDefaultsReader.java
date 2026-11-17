package controller;

import model.AppointmentReminderDefaults;

import java.util.Optional;

/**
 * Loads a user's saved default appointment reminder template (for post-booking shortcuts).
 */
@FunctionalInterface
public interface ReminderDefaultsReader {

    Optional<AppointmentReminderDefaults> load(int userId);

    static ReminderDefaultsReader production() {
        return userId -> new ReminderController(userId).getDefaultAppointmentReminderTemplate();
    }
}
