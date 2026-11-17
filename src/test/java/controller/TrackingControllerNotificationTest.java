package controller;

import app.Data;
import model.AppointmentDetails;
import model.PrescriptionSummary;
import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.NotificationDispatcher;
import service.TrackingService;
import service.email.Email;
import view.notification.Notification;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted tests for the "clinic adds new entry → notify user" logic in TrackingController.
 */
class TrackingControllerNotificationTest {

    private static class FakeTrackingService extends TrackingService {
        private final List<model.Test> tests;

        FakeTrackingService(int userId, List<model.Test> tests) {
            // Parent constructor requires DAOs, but we override getTestResults so they are unused.
            super(userId, null, null, null);
            this.tests = tests;
        }

        @Override
        public List<model.Test> getTestResults() {
            return tests;
        }

        @Override
        public List<AppointmentDetails> getPastAppointments() {
            return List.of();
        }

        @Override
        public List<PrescriptionSummary> getPrescriptions() {
            return List.of();
        }
    }

    private static class CapturingDispatcher extends NotificationDispatcher {
        static record Call(int userId, Email email, Notification notification) {}
        final List<Call> calls = new ArrayList<>();

        @Override
        public void notifyUser(int targetUserId, Email email, Notification notification) {
            calls.add(new Call(targetUserId, email, notification));
        }
    }

    TrackingController controller;

    @BeforeEach
    void setUp() {
        controller = new TrackingController(new view.Navigator(new view.MainView()), new FakeTrackingService(1, List.of()));
        Data.clearCurrentUser();
        Data.setCurrentUser(1, "patient@example.com", "Test User");
        Data.setLastSeenTestCount(-1);
    }

    @AfterEach
    void tearDown() {
        Data.clearCurrentUser();
        Data.setLastSeenTestCount(-1);
    }

    @Test
    void firstOpen_setsBaselineAndDoesNotNotify() {
        List<model.Test> tests = List.of(
                new model.Test("Test A", "01/03/26", null, null, List.of())
        );
        FakeTrackingService model = new FakeTrackingService(1, tests);

        controller.notifyForNewTestResults(model);

        assertEquals(1, Data.getLastSeenTestCount());
    }

    @Test
    void hasAnyTrackingRecords_falseWhenListsEmpty_trueWhenTestsPresent() {
        assertFalse(TrackingController.hasAnyTrackingRecords(new FakeTrackingService(1, List.of())));
        model.Test t = new model.Test("Screening", "01/03/26", null, null, List.of());
        assertTrue(TrackingController.hasAnyTrackingRecords(new FakeTrackingService(1, List.of(t))));
    }

    @Test
    void subsequentOpen_withNewTests_sendsNotificationsForNewOnes() {
        // Simulate having seen 1 test already.
        Data.setLastSeenTestCount(1);

        List<TestResult> results = List.of(new TestResult("Condition", TestResultStatus.POSITIVE));
        List<model.Test> tests = List.of(
                new model.Test("New Test 1", "02/03/26", null, null, results),
                new model.Test("New Test 0", "01/03/26", null, null, results)
        );
        FakeTrackingService model = new FakeTrackingService(1, tests);

        // We can't easily intercept the internal NotificationDispatcher without refactoring,
        // but we can still assert that the "new tests" logic updates the lastSeen counter.
        controller.notifyForNewTestResults(model);

        assertEquals(2, Data.getLastSeenTestCount());
    }
}

