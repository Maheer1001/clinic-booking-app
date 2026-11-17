package service;

import app.Data;
import service.email.Email;
import service.email.EmailSender;
import view.notification.Notification;

/**
 * Helper to send paired email + in-app notifications for a specific user.
 * Ensures we only notify when the current session user matches the target user.
 */
public class NotificationDispatcher {

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
     */
    public void notifyUser(int targetUserId, Email email, Notification notification) {
        int currentUserId = Data.getCurrentUserId();
        if (currentUserId < 0 || currentUserId != targetUserId) {
            return;
        }

        if (email != null) {
            emailSender.send(email);
        }
        if (notification != null) {
            notification.show();
        }
    }
}

