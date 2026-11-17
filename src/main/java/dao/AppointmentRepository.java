package dao;

import model.Appointment;
import model.AppointmentDetails;
import model.AvailableTimeslot;
import model.Book;
import model.UpcomingAppointmentDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contract for appointment data access.
 */
public interface AppointmentRepository {
    /** Persists an appointment; returns the new {@code app_id} when the insert succeeds. */
    Optional<Integer> save(Book booking, int userId, int timeslotId);
    /**
     * Cancels a confirmed appointment and frees its place in the slot.
     *
     * @return false if the appointment was not confirmed (e.g. already cancelled) or the update failed
     */
    boolean cancelAppointment(int appointmentId);
    void rescheduleAppointment(int appointmentId, LocalDate newDate);
    /**
     * Moves a confirmed appointment to an already reserved slot row, freeing its old place.
     *
     * @return false if nothing was moved (same slot, not confirmed, or the update failed)
     */
    boolean rescheduleToNewTimeslot(int appointmentId, int newTimeslotId);
    /** Reserves {@code slot} (creating its row if needed) and moves the appointment there. */
    boolean rescheduleToSlot(int appointmentId, AvailableTimeslot slot);
    Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId);
    /** The slot row an appointment is booked into. */
    Optional<Integer> getTimeslotIdByAppointmentId(int appointmentId);
    /** True if the appointment exists, is CONFIRMED, belongs to the user, and has not started yet. */
    boolean isUpcomingAppointmentForUser(int appointmentId, int userId);
    List<AppointmentDetails> getPastAppointmentDetailsByUserId(int userId);
    /** Confirmed appointments that have not started yet, soonest first. */
    List<UpcomingAppointmentDetails> getUpcomingAppointmentDetailsByUserId(int userId);
    List<Appointment> getUpcomingAppointmentsForUser(int userId);
    List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date);
    /** Date of the user's most recent past (including cancelled) appointment, for booking reminders. */
    Optional<LocalDate> getLastAppointmentDateByUserId(int userId);

    List<LocalDate> getAvailableDates(int clinicId, int serviceId);
}
