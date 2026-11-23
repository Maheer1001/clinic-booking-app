package controller;

import app.Data;
import service.FakeUserRepository;
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

    /** Reminder setup is stubbed so confirming a booking never reaches a database. */
    private static final BookingController.NewBookingReminders NO_REMINDERS = (userId, appIds, email, name) -> true;

    private static class CapturingBookingService extends BookingService {
        int lastUserId = -1;
        Book lastBook;
        boolean slotTaken;

        @Override
        public BookingResult placeBooking(Book book, int userId) {
            this.lastBook = book;
            this.lastUserId = userId;
            if (slotTaken) {
                return new BookingResult(List.of(), List.of("Service A"));
            }
            Item item = book.getItems().get(0);
            return new BookingResult(
                    List.of(new BookedAppointment(55, item.getService(), item.getDate(), item.getTime())),
                    List.of());
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
    void confirmBooking_whenNothingCouldBeBooked_staysOnPageAndKeepsTheBooking() {
        Data.setCurrentUser(1, "patient@example.com", "Test User");
        Book b = Data.getBooking();
        Service s1 = new Service(1, "Service A", "desc", 30);
        Item item = new Item(s1);
        item.setTimeslotId(10);
        item.setDate(LocalDate.now().plusDays(1));
        item.setTime(LocalTime.of(10, 30));
        b.setItems(List.of(item));

        CapturingNavigator nav = new CapturingNavigator(new MainView());
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.slotTaken = true;
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        BookingController controller = new BookingController(nav, bookingService, dispatcher, uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);

        BookingService.BookingResult result = controller.confirmBooking();

        assertFalse(result.anyBooked());
        assertNull(nav.lastPage, "should not show the confirmation page");
        assertSame(b, Data.getBooking(), "booking should be kept so another time can be chosen");
        assertEquals(0, dispatcher.callCount, "no confirmation should be sent");
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
        b.setClinic(new Clinic("Clinic 1", "Addr", "PC", "123", List.of(s1), new Coordinate(0, 0), List.of()));

        MainView mainView = new MainView();
        CapturingNavigator nav = new CapturingNavigator(mainView);
        CapturingBookingService bookingService = new CapturingBookingService();
        CapturingDispatcher dispatcher = new CapturingDispatcher();

        BookingController controller = new BookingController(nav, bookingService, dispatcher, uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);

        // Act
        controller.confirmBooking();

        // Assert booking service called
        assertEquals(1, bookingService.lastUserId);
        assertNotNull(bookingService.lastBook);

        // Assert navigator now shows the prompt page
        assertNotNull(nav.lastPage);
        assertEquals("Booking confirmed", nav.lastTitle);
        assertTrue(nav.lastPage instanceof javafx.scene.control.ScrollPane sp && sp.getContent() instanceof VBox,
                "the confirmation scrolls, so a long result message is never cut short");

        // Root's center should also be the prompt node
        BorderPane root = nav.getRoot();
        assertEquals(nav.lastPage, root.getCenter());
    }

    /** Records the name offer instead of reading or writing the account. */
    private static class NameOfferController extends BookingController {
        final List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        final boolean nameMissing;

        NameOfferController(Navigator nav, CapturingBookingService bookingService, boolean nameMissing) {
            super(nav, bookingService, new CapturingDispatcher(), uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);
            this.nameMissing = nameMissing;
        }

        @Override
        public boolean shouldOfferSaveName(String first, String last) {
            events.add("check " + first + " " + last);
            return nameMissing;
        }

        @Override
        public service.AccountSecurityService.SaveNameResult saveNameToAccount(String first, String last) {
            events.add("save " + first + " " + last);
            return service.AccountSecurityService.SaveNameResult.SUCCESS;
        }
    }

    private static BookingService.BookingResult bookOfferingName(NameOfferController controller) throws Exception {
        java.util.concurrent.CompletableFuture<BookingService.BookingResult> done = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> controller.confirmBookingOfferingName(done::complete));
        return done.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void bookingWithTypedName() {
        Data.setCurrentUser(1, "patient@example.com", "patient@example.com");
        Book b = Data.getBooking();
        Service s1 = new Service(1, "Service A", "desc", 30);
        Item item = new Item(s1);
        item.setTimeslotId(10);
        item.setDate(LocalDate.now().plusDays(1));
        item.setTime(LocalTime.of(10, 30));
        b.setItems(List.of(item));
        b.setFirstName("Sam");
        b.setLastName("Lee");
    }

    @Test
    void confirmBookingOfferingName_savesTheTypedNameBeforeBooking() throws Exception {
        bookingWithTypedName();
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.slotTaken = true; // keeps the test off the reminder and email paths
        NameOfferController controller = new NameOfferController(new CapturingNavigator(new MainView()), bookingService, true);
        List<String> asked = new java.util.ArrayList<>();
        controller.saveNamePrompt = (f, l) -> { asked.add(f + " " + l); controller.events.add("asked"); return true; };

        BookingService.BookingResult result = bookOfferingName(controller);

        assertFalse(result.anyBooked());
        assertEquals(List.of("Sam Lee"), asked);
        assertEquals(List.of("check Sam Lee", "asked", "save Sam Lee"), controller.events);
        assertNotNull(bookingService.lastBook, "the booking still goes ahead after saving the name");
    }

    @Test
    void confirmBookingOfferingName_notNowBooksWithoutSaving() throws Exception {
        bookingWithTypedName();
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.slotTaken = true;
        NameOfferController controller = new NameOfferController(new CapturingNavigator(new MainView()), bookingService, true);
        controller.saveNamePrompt = (f, l) -> false;

        bookOfferingName(controller);

        assertEquals(List.of("check Sam Lee"), controller.events, "nothing saved after Not now");
        assertNotNull(bookingService.lastBook);
    }

    @Test
    void confirmBookingOfferingName_accountWithANameIsNotAsked() throws Exception {
        bookingWithTypedName();
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.slotTaken = true;
        NameOfferController controller = new NameOfferController(new CapturingNavigator(new MainView()), bookingService, false);
        controller.saveNamePrompt = (f, l) -> fail("should not ask when the account already has a name");

        bookOfferingName(controller);

        assertEquals(List.of("check Sam Lee"), controller.events);
        assertNotNull(bookingService.lastBook);
    }

    @Test
    void sortClinicsByPostcode_withEmptyBox_asksForAPostcodeAndKeepsTheOrder() {
        Clinic a = new Clinic("Clinic A", "Addr", "SO17 2FW", "1", List.of(), new Coordinate(0, 0), List.of());
        Clinic b = new Clinic("Clinic B", "Addr", "SO15 3HL", "2", List.of(), new Coordinate(0, 0), List.of());
        List<Clinic> clinics = new java.util.ArrayList<>(List.of(a, b));
        BookingController controller = new BookingController(new CapturingNavigator(new MainView()),
                new CapturingBookingService(), new CapturingDispatcher(), uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);

        assertTrue(controller.sortClinicsByPostcode(clinics, "   ").isEmpty(), "no distances without a postcode");
        assertEquals(List.of(a, b), clinics);
    }

    @Test
    void recipientName_prefersTheNameTypedForThisBooking() {
        Book b = new Book();
        b.setFirstName(" Sam ");
        b.setLastName("Lee");
        assertEquals("Sam Lee", BookingController.recipientName(b, "someone@example.com"),
                "an account without a name has its email as display name; the typed name should win");
        Book blank = new Book();
        assertEquals("Alex Morgan", BookingController.recipientName(blank, "Alex Morgan"));
        assertEquals("Patient", BookingController.recipientName(blank, " "));
        assertEquals("Patient", BookingController.recipientName(blank, null));
    }

    @Test
    void clinicMeta_showsDistanceOnlyAfterASearch() {
        Clinic clinic = new Clinic("Clinic 1", "Addr", "SO17 2FW", "123", List.of(), new Coordinate(0, 0), List.of());
        assertEquals("SO17 2FW", BookingController.clinicMeta(clinic, null));
        assertEquals("1.3 km away  ·  SO17 2FW", BookingController.clinicMeta(clinic, 1.27));
    }
}
