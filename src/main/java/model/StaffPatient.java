package model;

import java.time.LocalDate;

/**
 * A patient as listed for staff: only patients with an appointment at the clinic are ever returned.
 * @param lastAppointmentDate their latest appointment date at this clinic
 */
public record StaffPatient(int userId, String displayName, String email, LocalDate lastAppointmentDate) {

    @Override
    public String toString() {
        return "StaffPatient[userId=" + userId + ", <personal details hidden>]";
    }
}
