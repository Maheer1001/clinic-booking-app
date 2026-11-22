package model;

import java.time.LocalDate;

/**
 * A prescription's current state in the database, re-read just before a dose reminder is sent so a
 * course stopped or shortened by staff takes effect without the patient logging in again.
 *
 * @param activelyTaking false once staff have stopped it (a NULL column counts as taking)
 * @param endDate        last day of the course, or null for ongoing
 */
public record PrescriptionState(boolean activelyTaking, LocalDate endDate) {

    /** True while reminders for a dose due on {@code day} should still go out. */
    public boolean wantsRemindersOn(LocalDate day) {
        return activelyTaking && (endDate == null || !day.isAfter(endDate));
    }
}
