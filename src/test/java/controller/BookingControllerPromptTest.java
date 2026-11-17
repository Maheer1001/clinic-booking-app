package controller;

import app.Data;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import model.Book;
import model.Clinic;
import model.Coordinate;
import model.Item;
import model.Service;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.BookingService;
import service.NotificationDispatcher;
import service.email.Email;
import view.MainView;
import view.Navigator;
import view.notification.Notification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that after confirming a booking, the controller shows an in-app
 * "Booking confirmed" prompt via Navigator with options to set up reminders or skip.
 */
class BookingControllerPromptTest {

    private static class CapturingBookingService extends BookingService {
        int lastUserId = -1;
        Book lastBook;

        @Override
        public List<Integer> confirmBooking(Book book, int userId) {
            this.lastBook = book;
            this.lastUserId = userId;
            return List.of();
        }
    }

    private static class CapturingDispatcher extends NotificationDispatcher {
        int callCount = 0;

        @Override
        public void notifyUser(int targetUserId, Email email, Notification notification) {
            callCount++;
        }
    }

    private static class CapturingNavigator extends Navigator {
        Node lastPage;
        String lastTitle;

        public CapturingNavigator(MainView view) {
            super(view);
        }

        @Override
        public void goToPage(Node page, String title, boolean isHome) {
            super.goToPage(page, title, isHome);
            lastPage = page;
            lastTitle = title;
        }
    }

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @BeforeEach
    void resetData() {
        Data.clearCurrentUser();
        Data.reset();
    }

    @Test
    void confirmBooking_showsPromptPageInNavigator() {
        // Arrange booking data
        Data.setCurrentUser(1, "patient@example.com", "Test User");
        Book b = Data.getBooking();
        Service s1 = new Service(1, "Service A", "desc", 30);
        Item item = new Item(s1);
        item.setTimeslotId(10);
        item.setDate(LocalDate.of(2026, 2, 25));
        item.setTime(LocalTime.of(10, 30));
        b.setItems(List.of(item));
        b.setClinic(new Clinic("Clinic 1", "Addr", "PC", "123", List.of(s1), new Coordinate(0, 0)));

        MainView mainView = new MainView();
        CapturingNavigator nav = new CapturingNavigator(mainView);
        CapturingBookingService bookingService = new CapturingBookingService();
        CapturingDispatcher dispatcher = new CapturingDispatcher();

        BookingController controller = new BookingController(nav, bookingService, dispatcher, uid -> Optional.empty(), new UserDAO());

        // Act
        controller.confirmBooking();

        // Assert booking service called
        assertEquals(1, bookingService.lastUserId);
        assertNotNull(bookingService.lastBook);

        // Assert navigator now shows the prompt page
        assertNotNull(nav.lastPage);
        assertEquals("Booking confirmed", nav.lastTitle);
        assertTrue(nav.lastPage instanceof VBox);

        // Root's center should also be the prompt node
        BorderPane root = nav.getRoot();
        assertEquals(nav.lastPage, root.getCenter());
    }
}

