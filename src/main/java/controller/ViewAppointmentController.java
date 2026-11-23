package controller;

import app.Data;
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

    /** Keeps pending reminders in step when an appointment is cancelled or moved. */
    public interface ReminderSync {
        void cancelled(int appId);
        void moved(int appId);

        ReminderSync NONE = new ReminderSync() {
            public void cancelled(int appId) {}
            public void moved(int appId) {}
        };
    }

    private final int userId;
    private final AppointmentRepository appointmentDAO;
    private final ReminderSync reminderSync;

    public ViewAppointmentController(int userId) {
        this(userId, new AppointmentDAO(), new ReminderSync() {
            public void cancelled(int appId) {
                new ReminderController(userId).cancelAppointmentReminders(appId);
            }
            public void moved(int appId) {
                new ReminderController(userId).rescheduleAppointmentReminder(
                        appId, Data.getCurrentUserEmail(), Data.getCurrentUserName());
            }
        });
    }

    public ViewAppointmentController(int userId, AppointmentRepository appointmentDAO) {
        this(userId, appointmentDAO, ReminderSync.NONE);
    }

    public ViewAppointmentController(int userId, AppointmentRepository appointmentDAO, ReminderSync reminderSync) {
        this.userId = userId;
        this.appointmentDAO = appointmentDAO;
        this.reminderSync = reminderSync;
    }

    private void syncReminders(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            // The appointment change has already been saved; a reminder problem must not hide that.
            System.err.println("[Reminders] Could not update reminders: " + e.getMessage());
        }
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

    /**
     * Cancels one of this user's upcoming appointments.
     *
     * @return false if the appointment is not this user's, has already started, or was already cancelled
     */
    public boolean cancelAppointment(int appointmentId) {
        if (!appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) {
            return false;
        }
        boolean cancelled = appointmentDAO.cancelAppointment(appointmentId);
        if (cancelled) {
            syncReminders(() -> reminderSync.cancelled(appointmentId));
        }
        return cancelled;
    }

    public boolean rescheduleAppointment(int appointmentId, LocalDate newDate) {
        if (!appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) {
            return false;
        }
        if (newDate == null || newDate.isBefore(LocalDate.now())) {
            return false;
        }
        appointmentDAO.rescheduleAppointment(appointmentId, newDate);
        syncReminders(() -> reminderSync.moved(appointmentId));
        return true;
    }

    public Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId) {
        return appointmentDAO.getClinicAndServiceIdsForAppointment(appointmentId);
    }

    /** Slots the appointment could move to on {@code date}, leaving out the slot it is already in. */
    public List<AvailableTimeslot> getAvailableTimeslotsForReschedule(int appointmentId, LocalDate date) {
        if (date == null || !appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) return List.of();
        Optional<int[]> ids = appointmentDAO.getClinicAndServiceIdsForAppointment(appointmentId);
        if (ids.isEmpty()) return List.of();
        int[] clinicService = ids.get();
        Optional<Integer> current = appointmentDAO.getTimeslotIdByAppointmentId(appointmentId);
        return appointmentDAO.getAvailableTimeslots(clinicService[0], clinicService[1], date).stream()
                .filter(s -> current.isEmpty() || s.timeslotId() != current.get())
                .toList();
    }

    public boolean rescheduleToSlot(int appointmentId, AvailableTimeslot slot) {
        if (!appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) {
            return false;
        }
        boolean moved = appointmentDAO.rescheduleToSlot(appointmentId, slot);
        if (moved) {
            syncReminders(() -> reminderSync.moved(appointmentId));
        }
        return moved;
    }

    public boolean isUpcomingAppointmentForUser(int appointmentId) {
        return appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId);
    }
    public List<LocalDate> getAvailableDatesForReschedule(int appointmentId) {
        if (!appointmentDAO.isUpcomingAppointmentForUser(appointmentId, userId)) return List.of();
        Optional<int[]> ids = appointmentDAO.getClinicAndServiceIdsForAppointment(appointmentId);

        if (ids.isEmpty()) return List.of();

        int[] clinicService = ids.get();
        int clinicId = clinicService[0];
        int serviceId = clinicService[1];

        return appointmentDAO.getAvailableDates(clinicId, serviceId);
    }
}
