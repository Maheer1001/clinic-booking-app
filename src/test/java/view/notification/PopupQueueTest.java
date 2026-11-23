package view.notification;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Popups that open by themselves show one at a time, the next after the previous has closed. */
class PopupQueueTest {

    /** A popup that records when it opens and stays open until {@link #close()}. */
    private static class FakePopup implements PopupQueue.Popup {
        final String name;
        final List<String> log;
        Runnable onClosed;

        FakePopup(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void open(Runnable onClosed) {
            log.add("open " + name);
            this.onClosed = onClosed;
        }

        void close() {
            log.add("close " + name);
            onClosed.run();
        }
    }

    /** A notification without a window that stays "open" until closed by the test. */
    private static class FakeNotification implements Notification {
        final String title;
        int shown;
        Runnable onClosed;

        FakeNotification(String title) { this.title = title; }

        @Override public void show() { shown++; }
        @Override public void show(Runnable onClosed) { shown++; this.onClosed = onClosed; }
        @Override public void dismiss() { if (onClosed != null) onClosed.run(); }
        @Override public String getTitle() { return title; }
        @Override public String getMessage() { return "message for " + title; }
        @Override public String getType() { return "FAKE"; }
    }

    @Test
    void secondPopupWaitsUntilTheFirstCloses() {
        List<String> log = new ArrayList<>();
        PopupQueue queue = new PopupQueue();
        FakePopup security = new FakePopup("security", log);
        FakePopup results = new FakePopup("results", log);

        queue.enqueue(security);
        queue.enqueue(results);

        assertEquals(List.of("open security"), log, "only one popup at a time");
        assertTrue(queue.isShowing());
        assertEquals(1, queue.waitingCount());

        security.close();
        assertEquals(List.of("open security", "close security", "open results"), log);

        results.close();
        assertFalse(queue.isShowing());
        assertEquals(0, queue.waitingCount());
    }

    @Test
    void popupQueuedWhenNothingIsShowing_opensStraightAway() {
        List<String> log = new ArrayList<>();
        PopupQueue queue = new PopupQueue();
        FakePopup first = new FakePopup("first", log);
        queue.enqueue(first);
        first.close();

        FakePopup later = new FakePopup("later", log);
        queue.enqueue(later);
        assertEquals("open later", log.getLast());
    }

    @Test
    void closingTwice_doesNotSkipTheNextPopup() {
        List<String> log = new ArrayList<>();
        PopupQueue queue = new PopupQueue();
        FakePopup a = new FakePopup("a", log);
        FakePopup b = new FakePopup("b", log);
        FakePopup c = new FakePopup("c", log);
        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);

        a.close();
        a.onClosed.run(); // e.g. hidden twice
        assertEquals(List.of("open a", "close a", "open b"), log, "c still waits for b");
    }

    @Test
    void popupThatDecidesNotToOpen_letsTheNextOneShow() {
        List<String> log = new ArrayList<>();
        PopupQueue queue = new PopupQueue();
        FakePopup first = new FakePopup("first", log);
        queue.enqueue(first);
        queue.enqueue(Runnable::run); // e.g. the user logged out meanwhile
        FakePopup third = new FakePopup("third", log);
        queue.enqueue(third);

        first.close();
        assertEquals(List.of("open first", "close first", "open third"), log);
    }

    @Test
    void popupThatFailsToOpen_doesNotBlockTheQueue() {
        List<String> log = new ArrayList<>();
        PopupQueue queue = new PopupQueue();
        queue.enqueue(onClosed -> { throw new IllegalStateException("no window"); });
        FakePopup next = new FakePopup("next", log);
        queue.enqueue(next);
        assertEquals(List.of("open next"), log);
    }

    @Test
    void manyPopupsThatCloseAtOnce_doNotOverflowTheStack() {
        PopupQueue queue = new PopupQueue();
        int[] opened = {0};
        for (int i = 0; i < 20_000; i++) {
            queue.enqueue(onClosed -> { opened[0]++; onClosed.run(); });
        }
        assertEquals(20_000, opened[0]);
        assertFalse(queue.isShowing());
    }

    @Test
    void queuedNotification_keepsItsTextAndShowsOnlyWhenItsTurnComes() {
        PopupQueue queue = new PopupQueue();
        FakeNotification first = new FakeNotification("Add a security question");
        FakeNotification second = new FakeNotification("Test Results Ready");
        Notification queuedSecond = queue.queued(second, () -> true);
        assertEquals("Test Results Ready", queuedSecond.getTitle());
        assertEquals("message for Test Results Ready", queuedSecond.getMessage());
        assertEquals("FAKE", queuedSecond.getType());

        queue.enqueue(first, () -> true);
        queuedSecond.show();
        assertEquals(1, first.shown);
        assertEquals(0, second.shown, "waits for the first to close");

        first.dismiss();
        assertEquals(1, second.shown);
    }

    @Test
    void notificationNoLongerWantedWhenItsTurnComes_isSkipped() {
        PopupQueue queue = new PopupQueue();
        FakeNotification first = new FakeNotification("first");
        FakeNotification stale = new FakeNotification("stale");
        FakeNotification after = new FakeNotification("after");
        AtomicBoolean signedIn = new AtomicBoolean(true);

        queue.enqueue(first, () -> true);
        queue.enqueue(stale, signedIn::get);
        queue.enqueue(after, () -> true);
        signedIn.set(false); // logged out while the first was open

        first.dismiss();
        assertEquals(0, stale.shown);
        assertEquals(1, after.shown);
    }

    @Test
    void notificationWithoutAWindow_countsAsClosedOnceShown() {
        PopupQueue queue = new PopupQueue();
        int[] shown = {0};
        Notification plain = new Notification() {
            @Override public void show() { shown[0]++; }
            @Override public void dismiss() {}
            @Override public String getTitle() { return "t"; }
            @Override public String getMessage() { return "m"; }
            @Override public String getType() { return "T"; }
        };
        queue.enqueue(plain, () -> true);
        queue.enqueue(plain, () -> true);
        assertEquals(2, shown[0]);
        assertFalse(queue.isShowing());
    }
}
