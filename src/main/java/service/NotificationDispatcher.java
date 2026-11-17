package service;

import app.Data;
import javafx.application.Platform;
import service.email.Email;
import service.email.EmailSender;
import view.notification.Notification;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Helper to send paired email + in-app notifications for a specific user.
 * Ensures we only notify when the current session user matches the target user.
 */
public class NotificationDispatcher {

    /** Single background thread for SMTP so a slow mail server never freezes the UI. */
    private static final ExecutorService EMAIL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "email-sender");
        t.setDaemon(true);
        return t;
    });

    private final EmailSender emailSender;

    public NotificationDispatcher() {
        this(new EmailSender());
    }

    public NotificationDispatcher(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * Sends an email and/or in-app notification for the given target user.
     * Both are only delivered if the current logged-in user matches the targetUserId.
     * The email is sent on the calling thread.
     */
    public void notifyUser(int targetUserId, Email email, Notification notification) {
        if (!isCurrentUser(targetUserId)) {
            return;
        }

        if (email != null) {
            emailSender.send(email);
        }
        if (notification != null) {
            notification.show();
        }
    }

    /**
     * Like {@link #notifyUser} but sends the email on a background thread, then reports whether it
     * was sent through {@code onEmailResult} on the JavaFX thread (if given). The notification is
     * shown straight away. Nothing happens, and the callback is not called, if the target user is
     * not the one logged in.
     */
    public void notifyUserAsync(int targetUserId, Email email, Notification notification,
                                Consumer<Boolean> onEmailResult) {
        if (!isCurrentUser(targetUserId)) {
            return;
        }
        if (notification != null) {
            notification.show();
        }
        if (email == null) {
            if (onEmailResult != null) {
                runOnFxThread(() -> onEmailResult.accept(true));
            }
            return;
        }
        EMAIL_EXECUTOR.execute(() -> {
            boolean sent;
            try {
                sent = emailSender.send(email);
            } catch (RuntimeException e) {
                System.err.println("[Email] Failed to send: " + e.getMessage());
                sent = false;
            }
            if (onEmailResult != null) {
                boolean result = sent;
                runOnFxThread(() -> onEmailResult.accept(result));
            }
        });
    }

    private static boolean isCurrentUser(int targetUserId) {
        int currentUserId = Data.getCurrentUserId();
        return currentUserId >= 0 && currentUserId == targetUserId;
    }

    private static void runOnFxThread(Runnable r) {
        try {
            if (Platform.isFxApplicationThread()) {
                r.run();
            } else {
                Platform.runLater(r);
            }
        } catch (IllegalStateException toolkitNotRunning) {
            // e.g. unit tests without a JavaFX toolkit
            r.run();
        }
    }
}
