package view.notification;

import app.Data;
import javafx.application.Platform;
import model.Appointment;
import model.ReminderTemplateOverrides;
import service.NotificationScheduler;
import service.ReminderAlert;

import java.util.function.Supplier;

/**
 * Shows each scheduled reminder as an in-app popup too, when the app is open and the reminder's
 * user is signed in. Uses the saved wording (title / preview line / body and the discreet choice),
 * the same popups as the reminder screens' preview buttons.
 */
public final class ReminderPopups {

    private ReminderPopups() {}

    /** Call once at start-up. */
    public static void install(NotificationScheduler scheduler) {
        scheduler.setAlertListener(alert -> showIfSignedIn(alert, Data::getCurrentUserEmail, Platform::runLater));
    }

    /**
     * Checks who is signed in both when the reminder fires and again on the FX thread, so a popup
     * never appears for someone who has logged out or for a different user in the meantime.
     */
    static void showIfSignedIn(ReminderAlert alert, Supplier<String> signedInEmail, java.util.function.Consumer<Runnable> fxThread) {
        showIfSignedIn(alert, signedInEmail, fxThread, PopupQueue.shared());
    }

    /**
     * Like {@link #showIfSignedIn(ReminderAlert, Supplier, java.util.function.Consumer)}, through the given
     * popup queue, so a reminder waits for any other popup (another reminder, new results) to close
     * first. Who is signed in is checked once more when its turn comes.
     */
    static void showIfSignedIn(ReminderAlert alert, Supplier<String> signedInEmail,
                               java.util.function.Consumer<Runnable> fxThread, PopupQueue popups) {
        if (!alert.isFor(signedInEmail.get())) {
            return;
        }
        fxThread.accept(() -> {
            if (alert.isFor(signedInEmail.get())) {
                popups.enqueue(notificationFor(alert), () -> alert.isFor(signedInEmail.get()));
            }
        });
    }

    /** The popup for a reminder, built from its saved notification text. */
    static Notification notificationFor(ReminderAlert alert) {
        ReminderTemplateOverrides o = alert.overrides();
        String name = alert.recipientName() != null ? alert.recipientName() : "";
        return switch (alert.kind()) {
            case APPOINTMENT -> {
                Appointment a = alert.appointment();
                yield new AppointmentReminderNotification(name, a.getServiceName(), a.getAppointmentDateTime(),
                        a.getClinicName(), a.getDoctorName(),
                        o.getNotificationTitle(), o.getNotificationPreview(), o.getNotificationBody(),
                        o.getDiscreetOverride());
            }
            case TREATMENT -> new TreatmentReminderNotification(name,
                    alert.prescription().getMedicineName(), alert.prescription().getDosage(), alert.dueDate(),
                    o.getNotificationTitle(), o.getNotificationPreview(), o.getNotificationBody(),
                    o.getDiscreetOverride());
            case BOOKING -> new MakeAppointmentReminderNotification(name, alert.reason(),
                    NotificationScheduler.BOOKING_APPOINTMENT_TYPE,
                    o.getNotificationTitle(), o.getNotificationPreview(), o.getNotificationBody(),
                    o.getDiscreetOverride());
        };
    }
}
