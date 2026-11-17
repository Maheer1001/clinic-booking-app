package controller;

import dao.AppointmentDAO;
import dao.PrescriptionDAO;
import dao.ReminderDAO;
import dao.UserDAO;
import dao.UserRepository;
import model.Appointment;
import model.AppointmentReminderDefaults;
import model.BookingCommunicationPreferences;
import model.AppointmentReminder;
import model.BookingReminder;
import model.Prescription;
import model.PrescriptionReminder;
import service.NotificationScheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controller for appointment, prescription, and booking reminders.
 */
public class ReminderController {

    private final int userId;
    private final ReminderDAO reminderDAO;
    private final AppointmentDAO appointmentDAO;
    private final PrescriptionDAO prescriptionDAO;
    private final NotificationScheduler notificationScheduler;
    private final UserRepository userRepository;

    public ReminderController(int userId) {
        this(userId, new ReminderDAO(), new AppointmentDAO(), new PrescriptionDAO(), NotificationScheduler.shared(),
                new UserDAO());
    }

    public ReminderController(int userId, ReminderDAO reminderDAO, AppointmentDAO appointmentDAO,
                              PrescriptionDAO prescriptionDAO, NotificationScheduler notificationScheduler,
                              UserRepository userRepository) {
        this.userId = userId;
        this.reminderDAO = reminderDAO;
        this.appointmentDAO = appointmentDAO;
        this.prescriptionDAO = prescriptionDAO;
        this.notificationScheduler = notificationScheduler;
        this.userRepository = userRepository;
    }

    public int getUserId() {
        return userId;
    }

    public List<Appointment> getUpcomingAppointments() {
        return appointmentDAO.getUpcomingAppointmentsForUser(userId);
    }

    public List<Prescription> getPrescriptionsForReminders() {
        return prescriptionDAO.getPrescriptionsForRemindersByUserId(userId);
    }

    public List<AppointmentReminder> getAppointmentReminders() {
        return reminderDAO.getAppointmentRemindersByUserId(userId);
    }

    public List<PrescriptionReminder> getPrescriptionReminders() {
        return reminderDAO.getPrescriptionRemindersByUserId(userId);
    }

    public List<BookingReminder> getBookingReminders() {
        return reminderDAO.getBookingRemindersByUserId(userId);
    }

    public Optional<LocalDate> getLastAppointmentDate() {
        return appointmentDAO.getLastAppointmentDateByUserId(userId);
    }

    /**
     * Saves the reminder settings. Turning reminders off also cancels any reminder already scheduled
     * for this appointment.
     *
     * @return true if the settings were saved
     */
    public boolean saveAppointmentReminder(int appId, boolean notify, String customDays, String customMessage) {
        boolean saved = reminderDAO.saveAppointmentReminder(userId, appId, notify, customDays, customMessage);
        if (!notify) {
            notificationScheduler.cancelAppointmentReminders(appId);
        }
        return saved;
    }

    public boolean savePrescriptionReminder(int prescriptionId, boolean notify, int intervalHours, String customMessage) {
        boolean saved = reminderDAO.savePrescriptionReminder(userId, prescriptionId, notify, intervalHours, customMessage);
        if (!notify) {
            notificationScheduler.cancelTreatmentReminders(prescriptionId);
        }
        return saved;
    }

    /**
     * @param recipientEmail used to cancel a booking reminder already scheduled when notify is false (may be null)
     */
    public boolean saveBookingReminder(boolean notify, int daysSinceLast, String customMessage, String recipientEmail) {
        boolean saved = reminderDAO.saveBookingReminder(userId, notify, daysSinceLast, customMessage);
        if (!notify && recipientEmail != null) {
            notificationScheduler.cancelBookingReminders(recipientEmail);
        }
        return saved;
    }

    public boolean saveBookingReminder(boolean notify, int daysSinceLast, String customMessage) {
        return saveBookingReminder(notify, daysSinceLast, customMessage, null);
    }

    public void scheduleAppointmentReminder(Appointment appt, String email, String name, int days) {
        scheduleAppointmentReminder(appt, email, name, days, null);
    }

    public void scheduleAppointmentReminder(Appointment appt, String email, String name, int days, String customMessage) {
        notificationScheduler.scheduleAppointmentReminder(appt, email, name, days, customMessage);
    }

    /**
     * Schedule appointment reminders using the JSON schedule stored in custom_days.
     */
    public void scheduleAppointmentReminderWithSchedule(Appointment appt, String email, String name,
                                                        String scheduleRaw, String customMessage) {
        notificationScheduler.scheduleAppointmentReminderWithSchedule(appt, email, name, scheduleRaw, customMessage);
    }

    public void scheduleTreatmentReminder(Prescription rx, String email, String name, int intervalHours) {
        scheduleTreatmentReminder(rx, email, name, intervalHours, null);
    }

    public void scheduleTreatmentReminder(Prescription rx, String email, String name, int intervalHours, String customMessage) {
        notificationScheduler.scheduleTreatmentReminder(rx, email, name, intervalHours, customMessage);
    }

    public void scheduleBookingReminder(String email, String name, int daysSinceLast, LocalDate lastDate) {
        scheduleBookingReminder(email, name, daysSinceLast, lastDate, null);
    }

    public void scheduleBookingReminder(String email, String name, int daysSinceLast, LocalDate lastDate, String customMessage) {
        notificationScheduler.scheduleBookingReminder(email, name, daysSinceLast, lastDate, customMessage);
    }

    public Optional<AppointmentReminderDefaults> getDefaultAppointmentReminderTemplate() {
        try {
            return userRepository.getDefaultAppointmentReminderTemplate(userId);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveDefaultAppointmentReminderTemplate(AppointmentReminderDefaults defaults) {
        try {
            userRepository.saveDefaultAppointmentReminderTemplate(userId, defaults);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public BookingCommunicationPreferences getBookingCommunicationPreferences() {
        try {
            return userRepository.getBookingCommunicationPreferences(userId);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveBookingCommunicationPreferences(BookingCommunicationPreferences prefs) {
        try {
            userRepository.saveBookingCommunicationPreferences(userId, prefs);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
