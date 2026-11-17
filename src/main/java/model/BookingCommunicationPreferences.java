package model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User preferences for how transactional booking emails and in-app notifications are worded.
 * Stored as JSON in {@code User.booking_comm_prefs}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingCommunicationPreferences(
        @JsonProperty("confirmation_discreet") boolean confirmationDiscreet,
        @JsonProperty("reschedule_discreet") boolean rescheduleDiscreet,
        @JsonProperty("cancellation_discreet") boolean cancellationDiscreet,
        /** JSON from {@link ReminderTemplateOverrides#toJson()} for confirmation channel; optional. */
        @JsonProperty("confirmation_overrides") String confirmationOverridesJson,
        @JsonProperty("reschedule_overrides") String rescheduleOverridesJson,
        @JsonProperty("cancellation_overrides") String cancellationOverridesJson
) {
    public static BookingCommunicationPreferences defaults() {
        return new BookingCommunicationPreferences(true, true, true, null, null, null);
    }

    public ReminderTemplateOverrides confirmationTemplate() {
        return ReminderTemplateOverrides.fromString(jsonOrEmpty(confirmationOverridesJson));
    }

    public ReminderTemplateOverrides rescheduleTemplate() {
        return ReminderTemplateOverrides.fromString(jsonOrEmpty(rescheduleOverridesJson));
    }

    public ReminderTemplateOverrides cancellationTemplate() {
        return ReminderTemplateOverrides.fromString(jsonOrEmpty(cancellationOverridesJson));
    }

    private static String jsonOrEmpty(String raw) {
        return raw != null ? raw : "";
    }
}
