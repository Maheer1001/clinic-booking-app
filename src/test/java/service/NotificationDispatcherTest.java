package service;

import app.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.email.Email;
import service.email.EmailSender;
import service.email.MakeAppointmentReminderEmail;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * notifyUserAsync must report the real outcome of the send (so the UI can show a failure)
 * and must not send on the caller's thread.
 */
class NotificationDispatcherTest {

    private static final Email EMAIL = new MakeAppointmentReminderEmail("p@example.com", "Jane", "Reason", "Check-up");

    private static EmailSender senderReturning(boolean result, AtomicReference<Thread> sendThread) {
        return new EmailSender() {
            @Override
            public boolean send(Email email) {
                sendThread.set(Thread.currentThread());
                return result;
            }
        };
    }

    @BeforeEach
    void setUp() {
        Data.clearCurrentUser();
        Data.setCurrentUser(7, "p@example.com", "Jane");
    }

    @AfterEach
    void tearDown() {
        Data.clearCurrentUser();
    }

    @Test
    void failedSend_isReportedAsFalse_offTheCallingThread() throws Exception {
        AtomicReference<Thread> sendThread = new AtomicReference<>();
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        new NotificationDispatcher(senderReturning(false, sendThread)).notifyUserAsync(7, EMAIL, null, result::complete);

        assertFalse(result.get(2, TimeUnit.SECONDS));
        assertNotSame(Thread.currentThread(), sendThread.get());
    }

    @Test
    void successfulSend_isReportedAsTrue() throws Exception {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        new NotificationDispatcher(senderReturning(true, new AtomicReference<>())).notifyUserAsync(7, EMAIL, null, result::complete);
        assertTrue(result.get(2, TimeUnit.SECONDS));
    }

    @Test
    void exceptionInSender_isReportedAsFalse() throws Exception {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        EmailSender throwing = new EmailSender() {
            @Override
            public boolean send(Email email) {
                throw new IllegalStateException("smtp down");
            }
        };
        new NotificationDispatcher(throwing).notifyUserAsync(7, EMAIL, null, result::complete);
        assertFalse(result.get(2, TimeUnit.SECONDS));
    }

    @Test
    void otherUser_isNotEmailed() throws Exception {
        AtomicReference<Thread> sendThread = new AtomicReference<>();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        new NotificationDispatcher(senderReturning(true, sendThread)).notifyUserAsync(8, EMAIL, null, result::complete);
        Thread.sleep(200);
        assertNull(sendThread.get());
        assertFalse(result.isDone());
    }
}
