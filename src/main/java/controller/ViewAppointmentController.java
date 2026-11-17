package controller;

import dao.AppointmentRepository;
import dao.AppointmentDAO;
import model.AvailableTimeslot;
import model.UpcomingAppointmentDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controller for viewing, cancelling, and rescheduling appointments.
 */
public class ViewAppointmentController {

    private final int userId;
    private final AppointmentRepository appointmentDAO;

    public ViewAppointmentController(int userId) {
        this(userId, new AppointmentDAO());
    }

    public ViewAppointmentController(int userId, AppointmentRepository appointmentDAO) {
        this.userId = userId;
        this.appointmentDAO = appointmentDAO;
    }

    public List<UpcomingAppointmentDetails> getUpcomingAppointments() {
        return appointmentDAO.getUpcomingAppointmentDetailsByUserId(userId);
    }

    public Optional<UpcomingAppointmentDetails> getAppointmentDetails(int appId) {
        return appointmentDAO.getUpcomingAppointmentDetailsByUserId(userId)
                .stream()
                .filter(a -> a.appId() == appId)
                .findFirst();
    }

    public void cancelAppointment(int appointmentId) {
        appointmentDAO.cancelAppointment(appointmentId);
    }

    public boolean rescheduleAppointment(int appointmentId, LocalDate newDate) {
        if (!appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) {
            return false;
        }
        if (newDate == null || newDate.isBefore(LocalDate.now())) {
            return false;
        }
        appointmentDAO.rescheduleAppointment(appointmentId, newDate);
        return true;
    }

    public Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId) {
        return appointmentDAO.getClinicAndServiceIdsForAppointment(appointmentId);
    }

    public List<AvailableTimeslot> getAvailableTimeslotsForReschedule(int appointmentId, LocalDate date) {
        Optional<int[]> ids = appointmentDAO.getClinicAndServiceIdsForAppointment(appointmentId);
        if (ids.isEmpty() || date == null) return List.of();
        int[] clinicService = ids.get();
        return appointmentDAO.getAvailableTimeslots(clinicService[0], clinicService[1], date);
    }

    public boolean rescheduleToNewTimeslot(int appointmentId, int newTimeslotId) {
        if (!appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) {
            return false;
        }
        appointmentDAO.rescheduleToNewTimeslot(appointmentId, newTimeslotId);
        return true;
    }

    public boolean isUpcomingAppointmentForUser(int appointmentId) {
        return appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId);
    }
    public List<LocalDate> getAvailableDatesForReschedule(int appointmentId) {
        Optional<int[]> ids = appointmentDAO.getClinicAndServiceIdsForAppointment(appointmentId);

        if (ids.isEmpty()) return List.of();

        int[] clinicService = ids.get();
        int clinicId = clinicService[0];
        int serviceId = clinicService[1];

        return appointmentDAO.getAvailableDates(clinicId, serviceId);
    }
}
