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
import java.time.LocalDateTime;
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
    /** @return when the first reminder email will be sent, or empty if none could be scheduled */
    public Optional<LocalDateTime> scheduleAppointmentReminderWithSchedule(Appointment appt, String email, String name,
                                                                           String scheduleRaw, String customMessage) {
        return notificationScheduler.scheduleAppointmentReminderWithSchedule(appt, email, name, scheduleRaw, customMessage);
    }

    public void scheduleTreatmentReminder(Prescription rx, String email, String name, int intervalHours) {
        scheduleTreatmentReminder(rx, email, name, intervalHours, null);
    }

    /** @return when the first dose reminder will be sent, or empty if the course ends first */
    public Optional<LocalDateTime> scheduleTreatmentReminder(Prescription rx, String email, String name, int intervalHours, String customMessage) {
        return notificationScheduler.scheduleTreatmentReminder(rx, email, name, intervalHours, customMessage);
    }

    public void scheduleBookingReminder(String email, String name, int daysSinceLast, LocalDate lastDate) {
        scheduleBookingReminder(email, name, daysSinceLast, lastDate, null);
    }

    public void scheduleBookingReminder(String email, String name, int daysSinceLast, LocalDate lastDate, String customMessage) {
        notificationScheduler.scheduleBookingReminder(email, name, daysSinceLast, lastDate, customMessage);
    }

    /**
     * Schedules the "time to book" nudge for {@code daysSinceLast} days after the last visit. Nothing
     * is scheduled before a first visit (it used to send "It has been 180 days since your last visit"
     * 30 seconds later to people who had never been), and it is not sent while an appointment is
     * already booked.
     *
     * With an appointment already booked nothing is scheduled now: the count restarts from that
     * visit (picked up at the next sign-in). Before, the screen said a nudge would be emailed in 30
     * seconds and then it was silently skipped.
     *
     * @return when it will be sent, or empty if there is no previous visit to count from or an
     *         appointment is already booked (see {@link #hasUpcomingAppointment()})
     */
    public Optional<LocalDateTime> scheduleBookingReminder(String email, String name, int daysSinceLast, String customMessage) {
        Optional<LocalDate> last = getLastAppointmentDate();
        if (last.isEmpty() || hasUpcomingAppointment()) {
            notificationScheduler.cancelBookingReminders(email);
            return Optional.empty();
        }
        return notificationScheduler.scheduleBookingReminder(email, name, daysSinceLast, last.get(), customMessage,
                this::hasNoUpcomingAppointment);
    }

    public boolean hasUpcomingAppointment() {
        return !getUpcomingAppointments().isEmpty();
    }

    private boolean hasNoUpcomingAppointment() {
        return getUpcomingAppointments().isEmpty();
    }

    // ------------------------------------------------------------------ keeping the scheduler in step

    /** Used for a new booking when the user has not saved their own default reminder. */
    public static final String NEW_BOOKING_SCHEDULE = "{\"reminders\":[{\"amount\":1,\"unit\":\"day\"}]}";
    /** Discreet by default, matching the reminder form. */
    public static final String NEW_BOOKING_MESSAGE = "{\"discreet\":\"true\"}";

    /**
     * Gives each newly booked appointment a reminder straight away: the user's saved default if they
     * have one (respecting a default of "off"), otherwise a discreet reminder one day before.
     *
     * @return true if reminders are now on for these appointments
     */
    public boolean applyReminderForNewBookings(List<Integer> appIds, String email, String name) {
        Optional<AppointmentReminderDefaults> saved;
        try {
            saved = getDefaultAppointmentReminderTemplate().filter(AppointmentReminderDefaults::hasSavedTemplate);
        } catch (RuntimeException e) {
            saved = Optional.empty();
        }
        boolean notify = saved.map(AppointmentReminderDefaults::notificationsEnabled).orElse(true);
        String days = saved.map(AppointmentReminderDefaults::customDays).filter(s -> !s.isBlank()).orElse(NEW_BOOKING_SCHEDULE);
        String message = saved.map(AppointmentReminderDefaults::customMessage).filter(s -> !s.isBlank()).orElse(NEW_BOOKING_MESSAGE);

        List<Appointment> upcoming = notify ? getUpcomingAppointments() : List.of();
        for (int appId : appIds) {
            reminderDAO.saveAppointmentReminder(userId, appId, notify, days, message);
            if (notify && email != null && !email.isBlank()) {
                upcoming.stream().filter(a -> a.getAppId() == appId).findFirst()
                        .ifPresent(a -> notificationScheduler.scheduleAppointmentReminderWithSchedule(
                                a, email, name, days, message));
            }
        }
        return notify;
    }

    /**
     * Re-creates this user's reminders from the database. The scheduler only lives while the app is
     * open, so without this every saved reminder was forgotten the next time the app started.
     * Times that have already passed are skipped rather than sent late, so logging in twice never
     * re-sends anything.
     *
     * @return number of reminder entries scheduled
     */
    public int restoreScheduledReminders(String email, String name) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        String displayName = name == null || name.isBlank() ? email : name;
        int restored = 0;

        List<Appointment> upcoming = getUpcomingAppointments();
        for (AppointmentReminder r : getAppointmentReminders()) {
            if (!r.isNotify()) continue;
            for (Appointment a : upcoming) {
                if (a.getAppId() == r.getAppId()) {
                    notificationScheduler.scheduleAppointmentReminderWithSchedule(
                            a, email, displayName, r.getCustomDays(), r.getCustomMessage(), false);
                    restored++;
                }
            }
        }

        List<Prescription> prescriptions = getPrescriptionsForReminders();
        for (PrescriptionReminder r : getPrescriptionReminders()) {
            if (!r.isNotify() || r.getIntervalBetweenDoses() <= 0) continue;
            for (Prescription rx : prescriptions) {
                if (rx.getPrescriptionId() == r.getPrescriptionId()) {
                    notificationScheduler.scheduleTreatmentReminder(
                            rx, email, displayName, r.getIntervalBetweenDoses(), r.getCustomMessage());
                    restored++;
                }
            }
        }

        for (BookingReminder r : getBookingReminders()) {
            if (!r.isNotify()) continue;
            Optional<LocalDate> last = getLastAppointmentDate();
            // Only a nudge that is still in the future; an overdue one would otherwise fire on every login.
            if (last.isPresent() && last.get().plusDays(r.getDaysSinceLast()).isAfter(LocalDate.now())) {
                notificationScheduler.scheduleBookingReminder(email, displayName, r.getDaysSinceLast(), last.get(),
                        r.getCustomMessage(), this::hasNoUpcomingAppointment);
                restored++;
            }
        }
        return restored;
    }

    /** After an appointment moves, re-schedules its saved reminder for the new time. */
    public void rescheduleAppointmentReminder(int appId, String email, String name) {
        notificationScheduler.cancelAppointmentReminders(appId);
        if (email == null || email.isBlank()) {
            return;
        }
        String displayName = name == null || name.isBlank() ? email : name;
        getAppointmentReminders().stream()
                .filter(r -> r.getAppId() == appId && r.isNotify())
                .findFirst()
                .ifPresent(r -> getUpcomingAppointments().stream()
                        .filter(a -> a.getAppId() == appId)
                        .findFirst()
                        .ifPresent(a -> notificationScheduler.scheduleAppointmentReminderWithSchedule(
                                a, email, displayName, r.getCustomDays(), r.getCustomMessage())));
    }

    /** After a cancellation, drops any reminder still waiting for that appointment. */
    public void cancelAppointmentReminders(int appId) {
        notificationScheduler.cancelAppointmentReminders(appId);
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
