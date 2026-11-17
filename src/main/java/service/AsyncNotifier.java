package service;

import service.email.Email;
import view.notification.Notification;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends booking emails off the JavaFX thread.
 *
 * {@link NotificationDispatcher#notifyUser} sends email over SMTP, with retries, on the calling thread,
 * which froze the window after booking, cancelling or rescheduling. The in-app notification is still
 * shown straight away on the caller's thread; only the email is queued.
 */
public final class AsyncNotifier {

    private static final ExecutorService EMAIL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "booking-email");
        t.setDaemon(true);
        return t;
    });

    private AsyncNotifier() {}

    public static void notifyUser(NotificationDispatcher dispatcher, int userId, Email email, Notification notification) {
        if (notification != null) {
            dispatcher.notifyUser(userId, null, notification);
        }
        if (email != null) {
            EMAIL.submit(() -> {
                try {
                    dispatcher.notifyUser(userId, email, null);
                } catch (RuntimeException e) {
                    System.err.println("[Email] Failed to send: " + e.getMessage());
                }
            });
        }
    }
}
