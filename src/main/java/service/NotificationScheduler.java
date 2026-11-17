package service;

import model.Appointment;
import model.Prescription;
import model.ReminderSchedule;
import model.ReminderTemplateOverrides;
import service.email.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * Schedules email notifications to be sent at the user's chosen time, or after a default delay (e.g. 30s) when the chosen time is in the past or not applicable.
 */
public class NotificationScheduler {

    private final ScheduledExecutorService scheduler;
    private final EmailSender emailSender;
    private final long defaultDelaySeconds;

    /**
     * Production constructor — uses real EmailSender and default 30s delay (used when user's chosen time is in the past).
     */
    public NotificationScheduler() {
        this(new EmailSender(), 30);
    }

    /**
     * Testable constructor.
     */
    public NotificationScheduler(EmailSender emailSender, long defaultDelaySeconds) {
        this.emailSender = emailSender;
        this.defaultDelaySeconds = defaultDelaySeconds;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

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
        LocalDateTime sendAt = appt.getAppointmentDateTime().minusDays(daysBefore);
        long delaySeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), sendAt);
        if (delaySeconds <= 0) {
            delaySeconds = defaultDelaySeconds;
            log("Appointment reminder for '" + appt.getServiceName() + "' would have been in the past; using default delay " + defaultDelaySeconds + "s");
        }
        scheduleAppointmentReminder(appt, recipientEmail, recipientName, delaySeconds, customMessage);
    }

    public void scheduleAppointmentReminder(Appointment appt, String recipientEmail,
                                            String recipientName, long delaySeconds, String customMessage) {
        log("Appointment reminder for '" + appt.getServiceName() + "' scheduled in " + delaySeconds + "s to " + recipientEmail);

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);

        scheduler.schedule(() -> {
            boolean success = emailSender.send(new AppointmentReminderEmail(
                    recipientEmail, recipientName,
                    appt.getServiceName(), appt.getAppointmentDateTime(),
                    appt.getClinicName(), appt.getDoctorName(),
                    overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName()
            ));
            log("Appointment reminder " + (success ? "sent" : "failed") + " for '" + appt.getServiceName() + "'");
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Schedules using default delay (30s). Use {@link #scheduleTreatmentReminder(Prescription, String, String, int, String)} for user-chosen interval in hours.
     */
    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail, String recipientName) {
        scheduleTreatmentReminder(rx, recipientEmail, recipientName, defaultDelaySeconds, null);
    }

    /**
     * Schedules reminder for (intervalHours) from now. If intervalHours &lt;= 0, uses default delay (30s).
     */
    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail, String recipientName, int intervalHours) {
        scheduleTreatmentReminder(rx, recipientEmail, recipientName, intervalHours, null);
    }

    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail, String recipientName,
                                          int intervalHours, String customMessage) {
        long delaySeconds = intervalHours > 0 ? (long) intervalHours * 3600 : defaultDelaySeconds;
        scheduleTreatmentReminder(rx, recipientEmail, recipientName, delaySeconds, customMessage);
    }

    public void scheduleTreatmentReminder(Prescription rx, String recipientEmail,
                                          String recipientName, long delaySeconds, String customMessage) {
        log("Treatment reminder for '" + rx.getMedicineName() + "' scheduled in " + delaySeconds + "s to " + recipientEmail);

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);

        scheduler.schedule(() -> {
            boolean success = emailSender.send(new TreatmentReminderEmail(
                    recipientEmail, recipientName,
                    rx.getMedicineName(), rx.getDosage(), rx.getEndDate(),
                    overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName()
            ));
            log("Treatment reminder " + (success ? "sent" : "failed") + " for '" + rx.getMedicineName() + "'");
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Schedules using default delay (30s). Use {@link #scheduleBookingReminder(String, String, int, LocalDate, String)} for user-chosen "days since last visit".
     */
    public void scheduleBookingReminder(String recipientEmail, String recipientName, int daysSinceLast) {
        scheduleBookingReminder(recipientEmail, recipientName, daysSinceLast, null, null);
    }

    /**
     * Schedules "book an appointment" reminder for (lastAppointmentDate + daysSinceLast). If that date is in the past or lastAppointmentDate is null, uses default delay (30s).
     */
    public void scheduleBookingReminder(String recipientEmail, String recipientName, int daysSinceLast, LocalDate lastAppointmentDate) {
        scheduleBookingReminder(recipientEmail, recipientName, daysSinceLast, lastAppointmentDate, null);
    }

    public void scheduleBookingReminder(String recipientEmail, String recipientName,
                                        int daysSinceLast, LocalDate lastAppointmentDate, String customMessage) {
        long delaySeconds = defaultDelaySeconds;
        if (lastAppointmentDate != null) {
            LocalDateTime sendAt = lastAppointmentDate.plusDays(daysSinceLast).atStartOfDay();
            long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), sendAt);
            if (secs > 0) {
                delaySeconds = secs;
            }
        }
        scheduleBookingReminder(recipientEmail, recipientName, daysSinceLast, delaySeconds, customMessage);
    }

    public void scheduleBookingReminder(String recipientEmail, String recipientName,
                                        int daysSinceLast, long delaySeconds, String customMessage) {
        log("Booking reminder scheduled in " + delaySeconds + "s to " + recipientEmail);

        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);

        scheduler.schedule(() -> {
            boolean success = emailSender.send(new MakeAppointmentReminderEmail(
                    recipientEmail, recipientName,
                    "It has been " + daysSinceLast + " days since your last visit",
                    "Routine Sexual Health Check-up",
                    overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName()
            ));
            log("Booking reminder " + (success ? "sent" : "failed"));
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Schedule appointment reminders based on a JSON schedule stored in custom_days.
     * Supports multiple reminders, repeats, and quiet hours.
     */
    public void scheduleAppointmentReminderWithSchedule(Appointment appt, String recipientEmail, String recipientName,
                                                        String scheduleRaw, String customMessage) {
        ReminderSchedule schedule = ReminderSchedule.fromString(scheduleRaw, 2);
        ReminderTemplateOverrides overrides = ReminderTemplateOverrides.fromString(customMessage);

        for (ReminderSchedule.ReminderRule rule : schedule.getReminders()) {
            LocalDateTime sendAt = computeInitialSendAt(appt.getAppointmentDateTime(), rule);
            sendAt = schedule.applyQuietHours(sendAt);

            long initialDelay = ChronoUnit.SECONDS.between(LocalDateTime.now(), sendAt);
            if (initialDelay <= 0) {
                initialDelay = defaultDelaySeconds;
            }

            Runnable task = () -> {
                boolean success = emailSender.send(new AppointmentReminderEmail(
                        recipientEmail, recipientName,
                        appt.getServiceName(), appt.getAppointmentDateTime(),
                        appt.getClinicName(), appt.getDoctorName(),
                        overrides.getEmailSubject(), overrides.getEmailBodyIntro(), overrides.getEmailFromName()
                ));
                log("Appointment reminder (schedule-based) " + (success ? "sent" : "failed") + " for '" + appt.getServiceName() + "'");
            };

            if (rule.repeatEvery() != null && rule.repeatEvery().amount() > 0) {
                long periodSeconds = toSeconds(rule.repeatEvery().amount(), rule.repeatEvery().unit());
                if (periodSeconds <= 0) {
                    periodSeconds = defaultDelaySeconds;
                }
                scheduleRepeating(task, initialDelay, periodSeconds, appt.getAppointmentDateTime());
            } else {
                scheduler.schedule(task, initialDelay, TimeUnit.SECONDS);
            }
        }
    }

    private void scheduleRepeating(Runnable task, long initialDelaySeconds, long periodSeconds, LocalDateTime stopAt) {
        final ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        Runnable wrapped = () -> {
            if (stopAt != null && LocalDateTime.now().isAfter(stopAt)) {
                holder[0].cancel(false);
                return;
            }
            task.run();
        };
        holder[0] = scheduler.scheduleAtFixedRate(wrapped, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    private static long toSeconds(int amount, String unit) {
        if (unit == null) return 0;
        switch (unit) {
            case "second" -> {
                return amount;
            }
            case "minute" -> {
                return (long) amount * 60;
            }
            case "hour" -> {
                return (long) amount * 3600;
            }
            case "day" -> {
                return (long) amount * 86400;
            }
            case "week" -> {
                return (long) amount * 7 * 86400;
            }
            default -> {
                return 0;
            }
        }
    }

    private LocalDateTime computeInitialSendAt(LocalDateTime appointmentDateTime, ReminderSchedule.ReminderRule rule) {
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

        if (rule.timeOfDay() != null && !rule.timeOfDay().isBlank()) {
            try {
                java.time.LocalTime t = java.time.LocalTime.parse(rule.timeOfDay().trim(),
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                base = base.toLocalDate().atTime(t);
            } catch (Exception ignored) {
                // leave base as-is on parse errors
            }
        }
        return base;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        System.out.println("[Scheduler] Shut down.");
    }

    private static void log(String msg) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[Scheduler] " + ts + " | " + msg);
    }
}
