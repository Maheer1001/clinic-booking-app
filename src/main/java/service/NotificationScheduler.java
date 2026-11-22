package service;

import dao.AppointmentDAO;
import model.Appointment;
import model.Prescription;
import model.ReminderSchedule;
import model.ReminderTemplateOverrides;
import service.email.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Schedules email reminders at the user's chosen time, or after a default delay (e.g. 30s) when the
 * chosen time has already passed.
 *
 * <ul>
 *   <li>Reminders are keyed per appointment / prescription / booking reminder. Scheduling again replaces
 *       the previous reminders for that key instead of adding duplicates, and they can be cancelled.</li>
 *   <li>Appointment reminders are skipped when the appointment is in the past, and re-checked just
 *       before sending so a cancelled or rescheduled appointment does not get a reminder.</li>
 *   <li>Send times are worked out in the local time zone, so a reminder set for 09:00 stays at 09:00
 *       across daylight saving changes. Repeats are rescheduled one at a time, so an exception in one
 *       send is logged and does not silently stop the rest.</li>
 *   <li>Worker threads are daemon threads, so a pending reminder never keeps the app running after
 *       the window is closed.</li>
 * </ul>
 */
public class NotificationScheduler {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** Booking ("time to book") reminders go out in the morning rather than at midnight. */
    private static final LocalTime BOOKING_REMINDER_TIME = LocalTime.of(9, 0);

    private static NotificationScheduler shared;

    private final ScheduledExecutorService scheduler;
    private final EmailSender emailSender;
    private final long defaultDelaySeconds;
    private final Clock clock;
    private final BiPredicate<Integer, Integer> appointmentStillBooked;
    private final Map<String, List<Handle>> pending = new ConcurrentHashMap<>();

    /**
     * App-wide scheduler. Screens should use this instead of creating their own, so that
     * re-saving a reminder replaces the one already scheduled.
     */
    public static synchronized NotificationScheduler shared() {
        if (shared == null) {
            shared = new NotificationScheduler();
        }
        return shared;
    }

    /**
     * Production constructor — uses real EmailSender, default 30s delay (used when user's chosen time is in the past),
     * and checks the database before sending that the appointment is still booked.
     */
    public NotificationScheduler() {
        this(new EmailSender(), 30, Clock.systemDefaultZone(), new AppointmentDAO()::isUpcomingAppointmentForUser);
    }

    /**
     * Testable constructor (no database check before sending).
     */
    public NotificationScheduler(EmailSender emailSender, long defaultDelaySeconds) {
        this(emailSender, defaultDelaySeconds, Clock.systemDefaultZone(), (appId, userId) -> true);
    }

    /**
     * @param appointmentStillBooked (appId, userId) → true while the appointment is still confirmed and upcoming
     */
    public NotificationScheduler(EmailSender emailSender, long defaultDelaySeconds, Clock clock,
                                 BiPredicate<Integer, Integer> appointmentStillBooked) {
        this.emailSender = emailSender;
        this.defaultDelaySeconds = defaultDelaySeconds;
        this.clock = clock;
        this.appointmentStillBooked = appointmentStillBooked;
        AtomicInteger threadCount = new AtomicInteger();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "reminder-scheduler-" + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    // ------------------------------------------------------------------ appointment reminders

    /**
     * Schedules using default delay (30s). Use {@link #scheduleAppointmentReminder(Appointment, String, String, int, String)} for user-chosen "days before".
     */
    public void scheduleAppointmentReminder(Appointment appt, String recipientEmail, String recipientName) {
        scheduleAppointmentReminder(appt, recipientEmail, recipientName, defaultDelaySeconds, null);
    }

    /**
     * Schedules reminder for (appointment date/time minus daysBefore). If that time is in the past, uses default delay (30s).
     */
    public void scheduleAppointmentReminder(Appointment appt, String recipientEmail, String recipientName, int daysBefore) {
        scheduleAppointmentReminder(appt, recipientEmail, recipientName, daysBefore, null);
    }

    public void scheduleAppointmentReminder(Appointment appt, String recipientEmail, String recipientName,
                                            int daysBefore, String customMessage) {
        ZonedDateTime sendAt = zoned(appt.getAppointmentDateTime().minusDays(daysBefore));
        long delaySeconds = Duration.between(now(), sendAt).getSeconds();
        if (delaySeconds <= 0) {
            delaySeconds = defaultDelaySeconds;
            log("Appointment reminder for appointment " + appt.getAppId() + " would have been in the past; using default delay " + defaultDelaySeconds + "s");
        }
        scheduleAppointmentReminder(appt, recipientEmail, recipientName, delaySeconds, customMessage);
    }

    public void scheduleAppointmentReminder(Appointment appt, String recipientEmail,
                                            String recipientName, long delaySeconds, String customMessage) {
        String key = appointmentKey(appt.getAppId());
        cancel(key);
        if (!isAppointmentInFuture(appt)) {
            log("Appointment " + appt.getAppId() + " is in the past; no reminder scheduled");
            return;
        }
        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);
        startChain(key, now().plusSeconds(Math.max(0, delaySeconds)), null, UnaryOperator.identity(), t -> true,
                () -> appointmentStillWanted(appt),
                t -> appointmentEmail(appt, recipientEmail, recipientName, overrides));
    }

    /**
     * Schedule appointment reminders based on a JSON schedule stored in custom_days.
     * Supports multiple reminders, repeats, and quiet hours. Replaces any reminders already
     * scheduled for this appointment. Reminder times that have already passed are collapsed into a
     * single catch-up reminder after the default delay; nothing is sent for a past appointment.
     */
    public Optional<LocalDateTime> scheduleAppointmentReminderWithSchedule(Appointment appt, String recipientEmail, String recipientName,
                                                                           String scheduleRaw, String customMessage) {
        return scheduleAppointmentReminderWithSchedule(appt, recipientEmail, recipientName, scheduleRaw, customMessage, true);
    }

    /**
     * @param catchUpMissed true when the user has just saved or changed the reminder (send one catch-up
     *                      if a time has already passed); false when restoring saved reminders at login,
     *                      so logging in again never re-sends a reminder whose time has gone
     * @return when the first email will go out, or empty if none was scheduled
     */
    public Optional<LocalDateTime> scheduleAppointmentReminderWithSchedule(Appointment appt, String recipientEmail, String recipientName,
                                                                           String scheduleRaw, String customMessage, boolean catchUpMissed) {
        String key = appointmentKey(appt.getAppId());
        cancel(key);

        ZonedDateTime now = now();
        ZonedDateTime apptAt = zoned(appt.getAppointmentDateTime());
        if (!apptAt.isAfter(now)) {
            log("Appointment " + appt.getAppId() + " is in the past; no reminder scheduled");
            return Optional.empty();
        }

        ReminderSchedule schedule = ReminderSchedule.fromString(scheduleRaw, 2);
        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);
        UnaryOperator<ZonedDateTime> quietHours = t -> zoned(schedule.applyQuietHours(t.toLocalDateTime()));
        Predicate<ZonedDateTime> beforeAppointment = t -> t.isBefore(apptAt);
        BooleanSupplier stillWanted = () -> appointmentStillWanted(appt);
        Function<ZonedDateTime, Email> email = t -> appointmentEmail(appt, recipientEmail, recipientName, overrides);

        boolean missedOne = false;
        ZonedDateTime firstSend = null;
        for (ReminderSchedule.ReminderRule rule : schedule.getReminders()) {
            ZonedDateTime first = zoned(computeInitialSendAt(appt.getAppointmentDateTime(), rule));
            UnaryOperator<ZonedDateTime> next = repeatStep(rule.repeatEvery());

            if (next != null) {
                // Skip occurrences that have already passed rather than firing them all at once.
                while (!quietHours.apply(first).isAfter(now) && first.isBefore(apptAt)) {
                    first = next.apply(first);
                }
                if (!first.isBefore(apptAt)) {
                    missedOne = true;
                    continue;
                }
            } else if (!quietHours.apply(first).isAfter(now)) {
                missedOne = true;
                continue;
            }
            startChain(key, first, next, quietHours, beforeAppointment, stillWanted, email);
            firstSend = earlier(firstSend, quietHours.apply(first));
        }

        if (missedOne && catchUpMissed) {
            log("Some reminder times for appointment " + appt.getAppId() + " have passed; sending one reminder in " + defaultDelaySeconds + "s");
            ZonedDateTime catchUp = now.plusSeconds(defaultDelaySeconds);
            startChain(key, catchUp, null, UnaryOperator.identity(), t -> true, stillWanted, email);
            firstSend = earlier(firstSend, catchUp);
        }
        return Optional.ofNullable(firstSend).map(ZonedDateTime::toLocalDateTime);
    }

    private static ZonedDateTime earlier(ZonedDateTime a, ZonedDateTime b) {
        return a == null || b.isBefore(a) ? b : a;
    }

    /** Cancels every pending reminder for this appointment (e.g. reminders turned off). */
    public void cancelAppointmentReminders(int appId) {
        cancel(appointmentKey(appId));
    }

    // ------------------------------------------------------------------ treatment reminders

    /**
     * Schedules using default delay (30s). Use {@link #scheduleTreatmentReminder(Prescription, String, String, int, String)} for user-chosen interval in hours.
     */
    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail, String recipientName) {
        scheduleTreatmentReminder(rx, recipientEmail, recipientName, defaultDelaySeconds, null);
    }

    /**
     * Schedules a reminder every intervalHours, starting intervalHours from now. If intervalHours &lt;= 0, sends once after the default delay (30s).
     */
    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail, String recipientName, int intervalHours) {
        scheduleTreatmentReminder(rx, recipientEmail, recipientName, intervalHours, null);
    }

    /**
     * Schedules a reminder every intervalHours until the prescription's end date (inclusive), replacing any
     * treatment reminders already scheduled for this prescription.
     *
     * @return when the first reminder will go out, or empty if none was scheduled
     */
    public Optional<LocalDateTime> scheduleTreatmentReminder(Prescription rx, String recipientEmail, String recipientName,
                                                             int intervalHours, String customMessage) {
        if (intervalHours <= 0) {
            scheduleTreatmentReminder(rx, recipientEmail, recipientName, defaultDelaySeconds, customMessage);
            return Optional.of(now().plusSeconds(defaultDelaySeconds).toLocalDateTime());
        }
        String key = prescriptionKey(rx.getPrescriptionId());
        cancel(key);
        Predicate<ZonedDateTime> withinCourse = treatmentCourse(rx);
        ZonedDateTime first = now().plusHours(intervalHours);
        if (!withinCourse.test(first)) {
            log("Prescription " + rx.getPrescriptionId() + " ends before the next dose; no reminder scheduled");
            return Optional.empty();
        }
        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);
        startChain(key, first, t -> t.plusHours(intervalHours), UnaryOperator.identity(), withinCourse, () -> true,
                t -> treatmentEmail(rx, recipientEmail, recipientName, overrides, t.toLocalDate()));
        return Optional.of(first.toLocalDateTime());
    }

    /** Sends a single treatment reminder after delaySeconds, replacing any already scheduled for this prescription. */
    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail,
                                          String recipientName, long delaySeconds, String customMessage) {
        String key = prescriptionKey(rx.getPrescriptionId());
        cancel(key);
        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);
        startChain(key, now().plusSeconds(Math.max(0, delaySeconds)), null, UnaryOperator.identity(), treatmentCourse(rx), () -> true,
                t -> treatmentEmail(rx, recipientEmail, recipientName, overrides, t.toLocalDate()));
    }

    public void cancelTreatmentReminders(int prescriptionId) {
        cancel(prescriptionKey(prescriptionId));
    }

    // ------------------------------------------------------------------ booking reminders

    /**
     * Schedules using default delay (30s). Use {@link #scheduleBookingReminder(String, String, int, LocalDate, String)} for user-chosen "days since last visit".
     */
    public void scheduleBookingReminder(String recipientEmail, String recipientName, int daysSinceLast) {
        scheduleBookingReminder(recipientEmail, recipientName, daysSinceLast, null, null);
    }

    /**
     * Schedules "book an appointment" reminder for 09:00 on (lastAppointmentDate + daysSinceLast). If that date is in the past or lastAppointmentDate is null, uses default delay (30s).
     */
    public void scheduleBookingReminder(String recipientEmail, String recipientName, int daysSinceLast, LocalDate lastAppointmentDate) {
        scheduleBookingReminder(recipientEmail, recipientName, daysSinceLast, lastAppointmentDate, null);
    }

    public void scheduleBookingReminder(String recipientEmail, String recipientName,
                                        int daysSinceLast, LocalDate lastAppointmentDate, String customMessage) {
        scheduleBookingReminder(recipientEmail, recipientName, daysSinceLast, lastAppointmentDate, customMessage, () -> true);
    }

    /**
     * @param stillWanted checked just before sending, e.g. false once the user has booked again
     * @return when the reminder will go out
     */
    public Optional<LocalDateTime> scheduleBookingReminder(String recipientEmail, String recipientName, int daysSinceLast,
                                                           LocalDate lastAppointmentDate, String customMessage,
                                                           BooleanSupplier stillWanted) {
        ZonedDateTime sendAt = now().plusSeconds(defaultDelaySeconds);
        if (lastAppointmentDate != null) {
            // Exactly 09:00 (working in whole seconds from "now" used to log and send at 08:59).
            ZonedDateTime due = zoned(lastAppointmentDate.plusDays(daysSinceLast).atTime(BOOKING_REMINDER_TIME));
            if (due.isAfter(now())) {
                sendAt = due;
            }
        }
        startBookingReminder(recipientEmail, recipientName, daysSinceLast, sendAt, customMessage, stillWanted);
        return Optional.of(sendAt.toLocalDateTime());
    }

    public void scheduleBookingReminder(String recipientEmail, String recipientName,
                                        int daysSinceLast, long delaySeconds, String customMessage) {
        startBookingReminder(recipientEmail, recipientName, daysSinceLast,
                now().plusSeconds(Math.max(0, delaySeconds)), customMessage, () -> true);
    }

    private void startBookingReminder(String recipientEmail, String recipientName, int daysSinceLast,
                                      ZonedDateTime sendAt, String customMessage, BooleanSupplier stillWanted) {
        String key = bookingKey(recipientEmail);
        cancel(key);
        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);
        boolean discreet = isDiscreet(overrides);
        startChain(key, sendAt, null, UnaryOperator.identity(), t -> true, stillWanted,
                t -> new MakeAppointmentReminderEmail(
                        recipientEmail, recipientName,
                        "It has been " + daysSinceLast + " days since your last visit",
                        "Routine Sexual Health Check-up",
                        overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName(),
                        discreet));
    }

    public void cancelBookingReminders(String recipientEmail) {
        cancel(bookingKey(recipientEmail));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Number of reminder chains still waiting to send for all keys (for tests and diagnostics). */
    public int pendingCount() {
        return pending.values().stream().mapToInt(List::size).sum();
    }

    int pendingCount(String key) {
        List<Handle> handles = pending.get(key);
        return handles == null ? 0 : handles.size();
    }

    public void shutdown() {
        pending.keySet().forEach(this::cancel);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log("Shut down.");
    }

    // ------------------------------------------------------------------ internals

    /** One reminder (or chain of repeats). Each run schedules the next one itself. */
    private final class Handle {
        final String key;
        volatile boolean cancelled;
        volatile ScheduledFuture<?> future;

        Handle(String key) {
            this.key = key;
        }

        void cancel() {
            cancelled = true;
            ScheduledFuture<?> f = future;
            if (f != null) {
                f.cancel(false);
            }
        }
    }

    /**
     * @param first        nominal time of the first send
     * @param next         nominal time of the following send, or null for a one-off
     * @param adjust       maps a nominal time to the real send time (quiet hours)
     * @param keepGoing    nominal times failing this are not sent and end the chain
     * @param stillWanted  checked right before each send; false ends the chain without sending
     * @param emailFor     builds the email for a given send time
     */
    private void startChain(String key, ZonedDateTime first, UnaryOperator<ZonedDateTime> next,
                            UnaryOperator<ZonedDateTime> adjust, Predicate<ZonedDateTime> keepGoing,
                            BooleanSupplier stillWanted, Function<ZonedDateTime, Email> emailFor) {
        Handle handle = new Handle(key);
        pending.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(handle);
        scheduleRun(handle, first, next, adjust, keepGoing, stillWanted, emailFor);
    }

    private void scheduleRun(Handle handle, ZonedDateTime nominal, UnaryOperator<ZonedDateTime> next,
                             UnaryOperator<ZonedDateTime> adjust, Predicate<ZonedDateTime> keepGoing,
                             BooleanSupplier stillWanted, Function<ZonedDateTime, Email> emailFor) {
        if (handle.cancelled) {
            return;
        }
        ZonedDateTime fireAt = adjust.apply(nominal);
        long delayMillis = Math.max(0, Duration.between(now(), fireAt).toMillis());
        try {
            handle.future = scheduler.schedule(() -> {
                boolean continueChain = true;
                try {
                    if (handle.cancelled) {
                        return;
                    }
                    if (!stillWanted.getAsBoolean()) {
                        log("Reminder " + handle.key + " no longer needed; not sending");
                        continueChain = false;
                        return;
                    }
                    boolean success = emailSender.send(emailFor.apply(fireAt));
                    log("Reminder " + handle.key + " " + (success ? "sent" : "failed"));
                } catch (Throwable t) {
                    // Never let one failure end the chain silently.
                    logError("Reminder " + handle.key + " threw " + t);
                } finally {
                    ZonedDateTime following = continueChain && next != null ? next.apply(nominal) : null;
                    // After e.g. the computer sleeping, skip runs that are already overdue instead of bursting.
                    while (following != null && !adjust.apply(following).isAfter(now()) && keepGoing.test(following)) {
                        following = next.apply(following);
                    }
                    if (following != null && keepGoing.test(following) && !handle.cancelled) {
                        scheduleRun(handle, following, next, adjust, keepGoing, stillWanted, emailFor);
                    } else {
                        remove(handle);
                    }
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
            log("Reminder " + handle.key + " scheduled for " + fireAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
        } catch (RejectedExecutionException e) {
            logError("Reminder " + handle.key + " not scheduled: scheduler is shut down");
            remove(handle);
        }
    }

    private void cancel(String key) {
        List<Handle> handles = pending.remove(key);
        if (handles != null) {
            handles.forEach(Handle::cancel);
        }
    }

    private void remove(Handle handle) {
        pending.computeIfPresent(handle.key, (k, list) -> {
            list.remove(handle);
            return list.isEmpty() ? null : list;
        });
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }

    private ZonedDateTime zoned(LocalDateTime local) {
        return zoned(local, clock.getZone());
    }

    /**
     * Local wall-clock time to an instant in the given zone. Times inside a spring-forward gap move
     * forward; times in an autumn overlap use the earlier offset.
     */
    static ZonedDateTime zoned(LocalDateTime local, ZoneId zone) {
        return local.atZone(zone);
    }

    private boolean isAppointmentInFuture(Appointment appt) {
        return zoned(appt.getAppointmentDateTime()).isAfter(now());
    }

    private boolean appointmentStillWanted(Appointment appt) {
        if (!isAppointmentInFuture(appt)) {
            return false;
        }
        try {
            return appointmentStillBooked.test(appt.getAppId(), appt.getUserId());
        } catch (RuntimeException e) {
            logError("Could not check appointment " + appt.getAppId() + ": " + e.getMessage());
            return false;
        }
    }

    /** Nominal times up to the end of the prescription's last day (no end date = ongoing). */
    private Predicate<ZonedDateTime> treatmentCourse(Prescription rx) {
        LocalDate end = rx.getEndDate();
        if (end == null) {
            return t -> true;
        }
        ZonedDateTime stopAt = zoned(end.plusDays(1).atStartOfDay());
        return t -> t.isBefore(stopAt);
    }

    /** Discreet unless the user explicitly chose full details (matches the reminder form's default). */
    static boolean isDiscreet(ReminderTemplateOverrides overrides) {
        return !Boolean.FALSE.equals(overrides.getDiscreetOverride());
    }

    private static Email appointmentEmail(Appointment appt, String recipientEmail, String recipientName,
                                          ReminderTemplateOverrides overrides) {
        return new AppointmentReminderEmail(
                recipientEmail, recipientName,
                appt.getServiceName(), appt.getAppointmentDateTime(),
                appt.getClinicName(), appt.getDoctorName(),
                overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName(),
                isDiscreet(overrides));
    }

    private static Email treatmentEmail(Prescription rx, String recipientEmail, String recipientName,
                                        ReminderTemplateOverrides overrides, LocalDate dueDate) {
        return new TreatmentReminderEmail(
                recipientEmail, recipientName,
                rx.getMedicineName(), rx.getDosage(), dueDate,
                overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName(),
                isDiscreet(overrides));
    }

    private static String appointmentKey(int appId) {
        return "appointment:" + appId;
    }

    private static String prescriptionKey(int prescriptionId) {
        return "prescription:" + prescriptionId;
    }

    private static String bookingKey(String recipientEmail) {
        return "booking:" + (recipientEmail == null ? "" : recipientEmail.trim().toLowerCase(Locale.ROOT));
    }

    /** Next nominal time for a repeat rule, or null if the rule does not repeat (or is invalid). */
    static UnaryOperator<ZonedDateTime> repeatStep(ReminderSchedule.RepeatEvery repeat) {
        if (repeat == null || repeat.amount() <= 0 || repeat.unit() == null) {
            return null;
        }
        int amount = repeat.amount();
        return switch (repeat.unit()) {
            case "second" -> t -> t.plusSeconds(amount);
            case "minute" -> t -> t.plusMinutes(amount);
            case "hour" -> t -> t.plusHours(amount);
            // Calendar units keep the same wall-clock time across daylight saving changes.
            case "day" -> t -> t.plusDays(amount);
            case "week" -> t -> t.plusWeeks(amount);
            default -> null;
        };
    }

    static LocalDateTime computeInitialSendAt(LocalDateTime appointmentDateTime, ReminderSchedule.ReminderRule rule) {
        LocalDateTime base = appointmentDateTime;
        String unit = rule.unit() != null ? rule.unit() : "day";
        int amount = rule.amount() > 0 ? rule.amount() : 1;
        switch (unit) {
            case "second" -> base = base.minusSeconds(amount);
            case "minute" -> base = base.minusMinutes(amount);
            case "hour" -> base = base.minusHours(amount);
            case "day" -> base = base.minusDays(amount);
            case "week" -> base = base.minusWeeks(amount);
            default -> base = base.minusDays(amount);
        }

        // Accepts single-digit hours such as "9:00"; an unreadable time leaves the lead time as-is.
        LocalTime t = ReminderSchedule.parseTime(rule.timeOfDay());
        if (t != null) {
            base = base.toLocalDate().atTime(t);
        }
        return base;
    }

    private static void log(String msg) {
        String ts = LocalDateTime.now().format(HH_MM);
        System.out.println("[Scheduler] " + ts + " | " + msg);
    }

    private static void logError(String msg) {
        String ts = LocalDateTime.now().format(HH_MM);
        System.err.println("[Scheduler] " + ts + " | " + msg);
    }
}
