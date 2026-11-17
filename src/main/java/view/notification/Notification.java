package view.notification;

/**
 * Contract for all notification types in the sexual health application.
 * Notifications display as closeable JavaFX popup windows.
 * Email dispatch will be added later once the SendGrid API key is available.
 */
public interface Notification {

    /**
     * Displays the notification popup to the user.
     * Must be called on the JavaFX Application Thread.
     */
    void show();

    /**
     * Dismisses the notification popup if it is currently showing.
     */
    void dismiss();

    /**
     * @return the title displayed in the popup window
     */
    String getTitle();

    /**
     * @return the body text displayed in the popup
     */
    String getMessage();

    /**
     * @return the notification type identifier (e.g. "APPOINTMENT_REMINDER")
     */
    String getType();
}