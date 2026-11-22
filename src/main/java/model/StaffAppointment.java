package model;

import java.time.LocalDateTime;

/**
 * An appointment as clinic staff see it (day list and detail page).
 *
 * @param booking     time, service, status, the patient's own notes and, when given at booking, the
 *                    personal details ({@code booking.details()} is null when none were stored).
 *                    Sexual-health data (gender, sexuality) belongs on the detail page only, never a list card.
 * @param patientName name for the card: the name given at booking, else the account name, else the email
 * @param staffNotes  staff-only notes (never shown to the patient)
 * @param attendedAt  set when marked COMPLETED (UTC)
 */
public record StaffAppointment(ClinicBookingDetails booking, String patientName, String staffNotes,
                               LocalDateTime attendedAt) {

    public int appId() {
        return booking.appId();
    }

    public int patientUserId() {
        return booking.userId();
    }

    public AppointmentStatus status() {
        return AppointmentStatus.fromDb(booking.status());
    }

    @Override
    public String toString() {
        return "StaffAppointment[appId=" + booking.appId() + ", date=" + booking.date() + ", status="
                + booking.status() + ", <personal details hidden>]";
    }
}
