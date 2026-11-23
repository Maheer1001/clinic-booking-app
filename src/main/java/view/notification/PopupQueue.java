package view.notification;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * Shows the popups that open by themselves (the security-question prompt after login, new test results,
 * scheduled reminders) one at a time: the next opens only after the previous one has closed. Before,
 * a new account could get the security-question dialog and the new-result popup on top of each other.
 *
 * <p>JavaFX-free so the ordering can be unit tested. Use from the JavaFX application thread only.
 */
public final class PopupQueue {

    /** A popup that can be opened and says when it has closed. */
    @FunctionalInterface
    public interface Popup {
        /**
         * Opens the popup and calls {@code onClosed} once it has closed. A popup that decides not to
         * open (e.g. the user has logged out meanwhile) calls {@code onClosed} straight away.
         */
        void open(Runnable onClosed);
    }

    private static final PopupQueue SHARED = new PopupQueue();

    /** The queue the app's popups share. */
    public static PopupQueue shared() {
        return SHARED;
    }

    private final Deque<Popup> waiting = new ArrayDeque<>();
    private boolean showing;

    /** Opens the popup now if nothing is showing, otherwise after the ones ahead of it have closed. */
    public void enqueue(Popup popup) {
        if (popup == null) return;
        waiting.addLast(popup);
        if (!showing) showNext();
    }

    /**
     * Queues a notification popup. {@code stillWanted} is asked when its turn comes, so a popup queued
     * for someone who has logged out since is skipped.
     */
    public void enqueue(Notification notification, BooleanSupplier stillWanted) {
        if (notification == null) return;
        enqueue(onClosed -> {
            if (stillWanted == null || stillWanted.getAsBoolean()) {
                notification.show(onClosed);
            } else {
                onClosed.run();
            }
        });
    }

    /**
     * A stand-in for {@code notification} whose {@link Notification#show()} queues it here instead of
     * opening it straight away; its title and text are the original's. For code that hands the
     * notification on to be shown, e.g. {@code NotificationDispatcher}.
     */
    public Notification queued(Notification notification, BooleanSupplier stillWanted) {
        PopupQueue queue = this;
        return new Notification() {
            @Override public void show() { queue.enqueue(notification, stillWanted); }
            @Override public void show(Runnable onClosed) { notification.show(onClosed); }
            @Override public void dismiss() { notification.dismiss(); }
            @Override public String getTitle() { return notification.getTitle(); }
            @Override public String getMessage() { return notification.getMessage(); }
            @Override public String getType() { return notification.getType(); }
        };
    }

    /** True while a queued popup is open. */
    public boolean isShowing() {
        return showing;
    }

    /** Popups waiting for the open one to close. */
    public int waitingCount() {
        return waiting.size();
    }

    private void showNext() {
        // a loop rather than recursion, so many popups that decide not to open can't overflow the stack
        while (!showing) {
            Popup next = waiting.pollFirst();
            if (next == null) return;
            showing = true;
            boolean[] closed = {false};
            boolean[] openReturned = {false};
            boolean[] closedDuringOpen = {false};
            Runnable onClosed = () -> {
                if (closed[0]) return; // closing twice must not skip the next popup
                closed[0] = true;
                showing = false;
                if (openReturned[0]) {
                    showNext();
                } else {
                    closedDuringOpen[0] = true; // the loop below opens the next one
                }
            };
            try {
                next.open(onClosed);
            } catch (RuntimeException e) {
                System.err.println("[Popup] Could not show a popup: " + e.getClass().getSimpleName());
                onClosed.run();
            }
            openReturned[0] = true;
            if (!closedDuringOpen[0]) return; // still open; its onClosed carries on
        }
    }
}
