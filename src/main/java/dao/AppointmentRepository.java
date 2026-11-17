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
    void cancelAppointment(int appointmentId);
    void rescheduleAppointment(int appointmentId, LocalDate newDate);
    void rescheduleToNewTimeslot(int appointmentId, int newTimeslotId);
    /** Reserves {@code slot} (creating its row if needed) and moves the appointment there. */
    boolean rescheduleToSlot(int appointmentId, AvailableTimeslot slot);
    Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId);
    /** True if the appointment exists, is CONFIRMED, belongs to the user, and is in the future. */
    boolean isUpcomingAppointmentForUser(int appointmentId, int userId);
    List<AppointmentDetails> getPastAppointmentDetailsByUserId(int userId);
    List<UpcomingAppointmentDetails> getUpcomingAppointmentDetailsByUserId(int userId);
    List<Appointment> getUpcomingAppointmentsForUser(int userId);
    List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date);
    /** Date of the user's most recent past (including cancelled) appointment, for booking reminders. */
    Optional<LocalDate> getLastAppointmentDateByUserId(int userId);

    List<LocalDate> getAvailableDates(int clinicId, int serviceId);
}
