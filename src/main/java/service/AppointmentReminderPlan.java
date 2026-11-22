package service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * What scheduling an appointment's reminders actually did: when the first email goes out, and how
 * many of the chosen reminder times were skipped because they had already passed or would only be
 * sent at or after the appointment (e.g. pushed there by quiet hours or a time of day). Skipped
 * times are never sent late, so the save message has to say so.
 *
 * @param firstSend        when the first reminder will be sent, or empty if none was scheduled
 * @param passed           reminder times skipped because they had already passed
 * @param afterAppointment reminder times skipped because they fall at or after the appointment
 */
public record AppointmentReminderPlan(Optional<LocalDateTime> firstSend, int passed, int afterAppointment) {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm", Locale.UK);

    /** Nothing was scheduled because the appointment itself has already passed. */
    public static AppointmentReminderPlan pastAppointment() {
        return new AppointmentReminderPlan(Optional.empty(), 0, 0);
    }

    public int skipped() {
        return passed + afterAppointment;
    }

    /** Message shown after saving, e.g. "Saved. Your next reminder will be emailed on Mon 1 Dec at 09:00." */
    public String message() {
        if (firstSend.isPresent()) {
            String next = "Saved. Your next reminder will be emailed on " + firstSend.get().format(WHEN) + ".";
            if (skipped() == 0) {
                return next;
            }
            return next + (skipped() == 1
                    ? " One reminder time " + reason(true) + ", so it won't be sent."
                    : " " + skipped() + " reminder times " + reason(false) + ", so they won't be sent.");
        }
        if (skipped() == 0) {
            return "Saved, but this appointment has already passed, so no reminder will be sent.";
        }
        return skipped() == 1
                ? "Saved, but this reminder time " + reason(true) + ", so it won't be sent."
                : "Saved, but these reminder times " + reason(false) + ", so none will be sent.";
    }

    private String reason(boolean singular) {
        if (afterAppointment == 0) {
            return singular ? "has already passed" : "have already passed";
        }
        if (passed == 0) {
            return singular ? "falls after the appointment" : "fall after the appointment";
        }
        return "have already passed or fall after the appointment";
    }
}
