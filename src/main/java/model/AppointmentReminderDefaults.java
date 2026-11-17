package model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stored default appointment reminder settings for a user (same fields as per-appointment
 * {@link AppointmentReminder#getCustomDays}, {@link AppointmentReminder#getCustomMessage}, {@link AppointmentReminder#isNotify}).
 * Serialized as JSON in {@code User.default_reminder_config}.
 */
public record AppointmentReminderDefaults(
        @JsonProperty("notify") boolean notificationsEnabled,
        @JsonProperty("custom_days") String customDays,
        @JsonProperty("custom_message") String customMessage) {

    public AppointmentReminderDefaults {
        customDays = customDays != null ? customDays : "";
        customMessage = customMessage != null ? customMessage : "";
    }

    /** True when the user has saved something non-trivial (for optional UI such as post-booking shortcuts). */
    public boolean hasSavedTemplate() {
        return notificationsEnabled
                || (customDays != null && !customDays.isBlank())
                || (customMessage != null && !customMessage.isBlank());
    }
}
