package controller;

import app.Data;
import model.AppointmentDetails;
import model.NewResults;
import model.PrescriptionSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.NotificationDispatcher;
import service.ResultAlertService;
import service.TrackingService;
import service.email.Email;
import view.MainView;
import view.Navigator;
import view.notification.Notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The patient's new-result alert in Tracking, fed by ResultAlertService (the database in production). */
class TrackingControllerNotificationTest {

    private static class FakeTrackingService extends TrackingService {
        private final List<model.Test> tests;

        FakeTrackingService(int userId, List<model.Test> tests) {
            super(userId, null, null, null);
            this.tests = tests;
        }

        @Override public List<model.Test> getTestResults() { return tests; }
        @Override public List<AppointmentDetails> getPastAppointments() { return List.of(); }
        @Override public List<PrescriptionSummary> getPrescriptions() { return List.of(); }
    }

    /** Hands out the given results once, then nothing; records what was marked as seen. */
    private static class FakeRepository implements ResultAlertService.Repository {
        volatile NewResults next;
        final AtomicInteger reads = new AtomicInteger();
        final List<LocalDateTime> markedSeen = new CopyOnWriteArrayList<>();
        final CountDownLatch seen = new CountDownLatch(1);
        final CountDownLatch read = new CountDownLatch(1);

        FakeRepository(NewResults next) { this.next = next; }

        @Override
        public NewResults findResultsSinceLastSeen(int userId) {
            reads.incrementAndGet();
            NewResults n = next;
            next = new NewResults(List.of(), null);
            read.countDown();
            return n;
        }

        @Override
        public boolean setLastResultsSeenAt(int userId, LocalDateTime seenUpTo) {
            markedSeen.add(seenUpTo);
            seen.countDown();
            return true;
        }
    }

    private static class CapturingDispatcher extends NotificationDispatcher {
        record Call(int userId, Email email, Notification notification) {}
        final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public void notifyUser(int targetUserId, Email email, Notification notification) {
            calls.add(new Call(targetUserId, email, notification));
        }

        @Override
        public void notifyUserAsync(int targetUserId, Email email, Notification notification,
                                    java.util.function.Consumer<Boolean> onEmailResult) {
            calls.add(new Call(targetUserId, email, notification));
        }
    }

    private static final LocalDateTime WATERMARK = LocalDateTime.of(2026, 3, 2, 9, 30);

    @BeforeAll
    static void initJavaFX() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    @BeforeEach
    void setUp() {
        Data.clearCurrentUser();
        Data.setCurrentUser(1, "patient@example.com", "Test User");
    }

    @AfterEach
    void tearDown() {
        Data.clearCurrentUser();
    }

    private static NewResults.Entry entry(int id, String type, boolean updated) {
        return new NewResults.Entry(id, 3, type, LocalDate.of(2026, 3, 1), WATERMARK.minusMinutes(id), updated);
    }

    private static TrackingController open(FakeRepository repo, CapturingDispatcher dispatcher) {
        return new TrackingController(new Navigator(new MainView()), new FakeTrackingService(1, List.of()),
                new ResultAlertService(repo), dispatcher);
    }

    /** Waits for the background check to hand its result to the FX thread, then lets that run. */
    private static void afterRead(FakeRepository repo) throws InterruptedException {
        assertTrue(repo.read.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        drainFx();
    }

    /** Lets queued Platform.runLater work (the alert itself) finish. */
    private static void drainFx() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        javafx.application.Platform.runLater(done::countDown);
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    void firstVisitAfterLogin_alertsForResultsReleasedWhileLoggedOut_andMarksTheReturnedWatermarkSeen() throws Exception {
        FakeRepository repo = new FakeRepository(new NewResults(List.of(entry(1, "Blood test", false)), WATERMARK));
        CapturingDispatcher dispatcher = new CapturingDispatcher();

        open(repo, dispatcher);

        assertTrue(repo.seen.await(5, TimeUnit.SECONDS), "results are marked seen after the alert");
        assertEquals(List.of(WATERMARK), repo.markedSeen, "the watermark from the query, not now");
        assertEquals(1, dispatcher.calls.size());
        CapturingDispatcher.Call call = dispatcher.calls.getFirst();
        assertEquals(1, call.userId());
        assertNull(call.email(), "no email from the patient side; release already emailed");
        assertEquals("Test Results Ready", call.notification().getTitle());
        assertTrue(call.notification().getMessage().contains("Blood test"));
    }

    @Test
    void pendingToFinal_saysUpdatedResult_andNeverTheResult() throws Exception {
        FakeRepository repo = new FakeRepository(new NewResults(List.of(entry(2, "Screening", true)), WATERMARK));
        CapturingDispatcher dispatcher = new CapturingDispatcher();

        open(repo, dispatcher);

        assertTrue(repo.seen.await(5, TimeUnit.SECONDS));
        Notification n = dispatcher.calls.getFirst().notification();
        assertEquals("Test Result Updated", n.getTitle());
        String text = n.getMessage().toLowerCase();
        assertTrue(text.contains("updated test result"), text);
        assertFalse(text.contains("positive") || text.contains("negative") || text.contains("pending"), text);
    }

    @Test
    void returningToTheTab_checksAgain() throws Exception {
        FakeRepository repo = new FakeRepository(new NewResults(List.of(), null));
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        TrackingController controller = open(repo, dispatcher);
        afterRead(repo);

        repo.next = new NewResults(List.of(entry(4, "Urine test", false)), WATERMARK);
        controller.checkForNewResults();
        long deadline = System.currentTimeMillis() + 5000;
        while (repo.markedSeen.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(List.of(WATERMARK), repo.markedSeen);
        assertEquals(1, dispatcher.calls.size(), "nothing on the first look, one alert on the return");
    }

    @Test
    void nothingNew_noAlertAndNothingMarked() throws Exception {
        FakeRepository repo = new FakeRepository(new NewResults(List.of(), null));
        CapturingDispatcher dispatcher = new CapturingDispatcher();

        open(repo, dispatcher);

        afterRead(repo);
        assertTrue(dispatcher.calls.isEmpty());
        assertTrue(repo.markedSeen.isEmpty());
    }

    @Test
    void loggedOut_doesNotQuery() throws Exception {
        Data.clearCurrentUser();
        FakeRepository repo = new FakeRepository(new NewResults(List.of(entry(1, "Blood test", false)), WATERMARK));

        open(repo, new CapturingDispatcher());

        drainFx();
        assertEquals(0, repo.reads.get());
    }

    @Test
    void announce_onlyForTheLoggedInPatient() {
        FakeRepository repo = new FakeRepository(new NewResults(List.of(), null));
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        TrackingController controller = open(repo, dispatcher);
        NewResults n = new NewResults(List.of(entry(1, "Blood test", false)), WATERMARK);

        assertFalse(controller.announceNewResults(2, n), "another user's results are not shown or marked");
        assertTrue(dispatcher.calls.isEmpty());
        assertTrue(controller.announceNewResults(1, n));
        assertEquals(1, dispatcher.calls.size());
    }

    @Test
    void severalNewResults_oneCombinedPopupListingThem() throws Exception {
        FakeRepository repo = new FakeRepository(new NewResults(List.of(
                entry(1, "Blood test", false), entry(2, "Urine test", true), entry(3, "Swab", false)), WATERMARK));
        CapturingDispatcher dispatcher = new CapturingDispatcher();

        open(repo, dispatcher);

        assertTrue(repo.seen.await(5, TimeUnit.SECONDS));
        assertEquals(1, dispatcher.calls.size(), "one popup, not one per result");
        Notification n = dispatcher.calls.getFirst().notification();
        assertEquals("You have 3 new results", n.getTitle());
        String text = n.getMessage();
        assertTrue(text.contains("Blood test") && text.contains("Urine test") && text.contains("Swab"), text);
    }

    @Test
    void hasAnyTrackingRecords_falseWhenListsEmpty_trueWhenTestsPresent() {
        assertFalse(TrackingController.hasAnyTrackingRecords(new FakeTrackingService(1, List.of())));
        model.Test t = new model.Test("Screening", "01/03/26", null, null, List.of());
        assertTrue(TrackingController.hasAnyTrackingRecords(new FakeTrackingService(1, List.of(t))));
    }
}
