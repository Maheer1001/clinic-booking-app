package model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One appointment at a clinic with the personal details given when it was booked, for a staff view.
 * {@code notes} is the patient's "Anything your clinician should know?" text (Appointment.notes).
 * Like {@link BookingDetails}, {@link #toString()} hides the personal values.
 */
public record ClinicBookingDetails(int appId, int userId, LocalDate date, LocalTime startTime, LocalTime endTime,
                                   String serviceName, String status, String notes, BookingDetails details) {

    @Override
    public String toString() {
        return "ClinicBookingDetails[appId=" + appId + ", date=" + date + ", startTime=" + startTime
                + ", status=" + status + ", <personal details hidden>]";
    }
}
