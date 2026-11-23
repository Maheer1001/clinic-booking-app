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
        /** Books the first item only; every other item's slot has been taken. */
        boolean onlyFirstFree;
        /** Books every item. */
        boolean allFree;

        @Override
        public BookingResult placeBooking(Book book, int userId) {
            this.lastBook = book;
            this.lastUserId = userId;
            if (slotTaken) {
                return new BookingResult(List.of(), List.of("Service A"));
            }
            if (allFree) {
                return new BookingResult(book.getItems().stream()
                        .map(i -> new BookedAppointment(55 + book.getItems().indexOf(i), i.getService(), i.getDate(), i.getTime()))
                        .toList(), List.of());
            }
            if (onlyFirstFree) {
                Item first = book.getItems().getFirst();
                List<String> taken = book.getItems().stream().skip(1).map(i -> i.getService().getName()).toList();
                return new BookingResult(
                        List.of(new BookedAppointment(55, first.getService(), first.getDate(), first.getTime())), taken);
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

        @Override
        public void notifyUserAsync(int targetUserId, Email email, Notification notification,
                                    java.util.function.Consumer<Boolean> onEmailResult) {
            callCount++; // no popup window or email from tests
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
            if (saveFails) throw new IllegalStateException("database unreachable");
            return saveResult;
        }

        service.AccountSecurityService.SaveNameResult saveResult = service.AccountSecurityService.SaveNameResult.SUCCESS;
        boolean saveFails;
    }

    /** Every piece of text (labels and buttons) on a page, in order. */
    private static List<String> texts(Node node) {
        List<String> out = new java.util.ArrayList<>();
        collectTexts(node, out);
        return out;
    }

    private static void collectTexts(Node node, List<String> out) {
        if (node instanceof javafx.scene.control.Labeled l && l.getText() != null) out.add(l.getText());
        if (node instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) collectTexts(sp.getContent(), out);
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) collectTexts(child, out);
        }
    }

    private static javafx.scene.control.Button button(Node page, String text) {
        List<javafx.scene.control.Button> found = new java.util.ArrayList<>();
        findButtons(page, text, found);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static void findButtons(Node node, String text, List<javafx.scene.control.Button> out) {
        if (node instanceof javafx.scene.control.Button b && text.equals(b.getText())) out.add(b);
        if (node instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) findButtons(sp.getContent(), text, out);
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) findButtons(child, text, out);
        }
    }

    /** Runs on the FX thread and waits for it. */
    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
        java.util.concurrent.CompletableFuture<T> done = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> {
            try { done.complete(work.call()); } catch (Throwable t) { done.completeExceptionally(t); }
        });
        return done.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static Clinic clinicOffering(Service... services) {
        return new Clinic("Clinic 1", "Addr", "PC", "123", List.of(services), new Coordinate(0, 0), List.of());
    }

    @Test
    void confirmBooking_whenOnlySomeServicesAreBooked_saysWhichAndOffersAnotherTimeForTheRest() throws Exception {
        Data.setCurrentUser(1, "patient@example.com", "Test User");
        Book b = Data.getBooking();
        Service s1 = new Service(1, "Service A", "desc", 30);
        Service s2 = new Service(2, "Service B", "desc", 15);
        LocalDate day = LocalDate.now().plusDays(2);
        Item a = new Item(s1);
        a.setTimeslotId(10);
        a.setDate(day);
        a.setTime(LocalTime.of(10, 30));
        Item second = new Item(s2);
        second.setTimeslotId(11);
        second.setDate(day);
        second.setTime(LocalTime.of(11, 30));
        b.setItems(List.of(a, second));
        b.setClinic(clinicOffering(s1, s2));
        b.setFirstName("Sam");

        CapturingNavigator nav = new CapturingNavigator(new MainView());
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.onlyFirstFree = true;
        BookingController controller = new BookingController(nav, bookingService, new CapturingDispatcher(), uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);

        BookingService.BookingResult result = onFx(controller::confirmBooking);

        assertTrue(result.anyBooked());
        assertEquals("Booking confirmed", nav.lastTitle);
        List<String> text = texts(nav.lastPage);
        assertTrue(text.contains("Booked: Service A. Not booked, the time was no longer available: Service B. "
                + "Choose a different time to book it."), text.toString());
        assertNotNull(button(nav.lastPage, "Choose a different time"), "same recovery as the single-service path");

        assertSame(b, Data.getBooking(), "the unbooked service is kept to pick another time for");
        assertEquals(1, b.getItems().size());
        Item left = b.getItems().getFirst();
        assertSame(s2, left.getService());
        assertEquals(day, left.getDate(), "the time picker reopens on the chosen date");
        assertNull(left.getTime());
        assertEquals("Sam", b.getFirstName(), "details typed are kept for the retry");

        onFx(() -> { button(nav.lastPage, "Done").fire(); return null; });
        assertNotSame(b, Data.getBooking(), "Done ends the half-finished booking");
    }

    @Test
    void confirmBooking_severalServices_confirmationPopupsWaitTheirTurn() throws Exception {
        Data.setCurrentUser(1, "patient@example.com", "Test User");
        Book b = Data.getBooking();
        Service s1 = new Service(1, "Service A", "desc", 30);
        Service s2 = new Service(2, "Service B", "desc", 15);
        LocalDate day = LocalDate.now().plusDays(2);
        Item a = new Item(s1);
        a.setDate(day);
        a.setTime(LocalTime.of(10, 30));
        Item second = new Item(s2);
        second.setDate(day);
        second.setTime(LocalTime.of(11, 30));
        b.setItems(List.of(a, second));
        b.setClinic(clinicOffering(s1, s2));
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.allFree = true;
        // like the real dispatcher: shows the popup it is handed
        List<String> shown = new java.util.ArrayList<>();
        NotificationDispatcher showing = new NotificationDispatcher() {
            @Override
            public void notifyUserAsync(int targetUserId, Email email, Notification notification,
                                        java.util.function.Consumer<Boolean> onEmailResult) {
                shown.add(notification.getTitle());
                notification.show();
            }
        };
        BookingController controller = new BookingController(new CapturingNavigator(new MainView()), bookingService,
                showing, uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);
        view.notification.PopupQueue queue = new view.notification.PopupQueue();
        queue.enqueue(onClosed -> {}); // another popup (e.g. a reminder) is open
        controller.popups = queue;

        onFx(controller::confirmBooking);

        assertEquals(2, shown.size(), "one confirmation per booked service");
        assertEquals(2, queue.waitingCount(), "both wait behind the open popup instead of opening on top of it");
    }

    @Test
    void confirmBooking_whenEverythingIsBooked_endsTheBookingWithNoRetry() throws Exception {
        Data.setCurrentUser(1, "patient@example.com", "Test User");
        Book b = Data.getBooking();
        Service s1 = new Service(1, "Service A", "desc", 30);
        Item a = new Item(s1);
        a.setTimeslotId(10);
        a.setDate(LocalDate.now().plusDays(2));
        a.setTime(LocalTime.of(10, 30));
        b.setItems(List.of(a));
        b.setClinic(clinicOffering(s1));
        CapturingNavigator nav = new CapturingNavigator(new MainView());
        BookingController controller = new BookingController(nav, new CapturingBookingService(),
                new CapturingDispatcher(), uid -> Optional.empty(), new FakeUserRepository(), NO_REMINDERS);

        onFx(controller::confirmBooking);

        assertNotSame(b, Data.getBooking());
        assertNull(button(nav.lastPage, "Choose a different time"));
    }

    @Test
    void confirmBookingOfferingName_nameNotSaved_stillBooksAndSaysSo() throws Exception {
        bookingWithTypedName();
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.slotTaken = true;
        NameOfferController controller = new NameOfferController(new CapturingNavigator(new MainView()), bookingService, true);
        controller.saveNamePrompt = (f, l) -> true;
        controller.saveResult = service.AccountSecurityService.SaveNameResult.DB_ERROR;

        bookOfferingName(controller);

        assertNotNull(bookingService.lastBook, "the booking still goes ahead");
        assertEquals(BookingOutcome.NAME_NOT_SAVED, controller.bookingNotice());
    }

    @Test
    void confirmBookingOfferingName_saveThrows_stillBooksAndSaysSo() throws Exception {
        bookingWithTypedName();
        Data.getBooking().setClinic(clinicOffering(Data.getBooking().getItems().getFirst().getService()));
        CapturingBookingService bookingService = new CapturingBookingService();
        CapturingNavigator nav = new CapturingNavigator(new MainView());
        NameOfferController controller = new NameOfferController(nav, bookingService, true);
        controller.saveNamePrompt = (f, l) -> true;
        controller.saveFails = true;

        BookingService.BookingResult result = bookOfferingName(controller);

        assertTrue(result.anyBooked());
        assertEquals(BookingOutcome.NAME_NOT_SAVED, controller.bookingNotice());
        assertTrue(texts(nav.lastPage).contains(BookingOutcome.NAME_NOT_SAVED), "shown on the Booking confirmed page");
    }

    @Test
    void confirmBookingOfferingName_nameSaved_noNotice() throws Exception {
        bookingWithTypedName();
        CapturingBookingService bookingService = new CapturingBookingService();
        bookingService.slotTaken = true;
        NameOfferController controller = new NameOfferController(new CapturingNavigator(new MainView()), bookingService, true);
        controller.saveNamePrompt = (f, l) -> true;

        bookOfferingName(controller);

        assertNull(controller.bookingNotice());
    }

    /** A UserRepository whose default clinic is the given id (or none for a negative id); nothing else is used. */
    private static dao.UserRepository defaultClinicRepo(int clinicId) {
        return (dao.UserRepository) java.lang.reflect.Proxy.newProxyInstance(
                dao.UserRepository.class.getClassLoader(), new Class<?>[]{dao.UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "defaultClinicExists" -> clinicId >= 0;
                    case "getDefaultClinic" -> clinicId;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void findDefaultClinic_givesTheSavedClinicsIdAndName() throws Exception {
        CapturingBookingService bookingService = new CapturingBookingService() {
            @Override public String findClinicNameById(int id) { return id == 4 ? "Riverside Clinic" : null; }
        };
        BookingController controller = new BookingController(new CapturingNavigator(new MainView()),
                bookingService, new CapturingDispatcher(), uid -> Optional.empty(), defaultClinicRepo(4));

        assertEquals(Optional.of(new BookingController.DefaultClinic(4, "Riverside Clinic")), controller.findDefaultClinic(1));
    }

    @Test
    void findDefaultClinic_noneSavedOrLoggedOut_isEmpty() throws Exception {
        BookingController none = new BookingController(new CapturingNavigator(new MainView()),
                new CapturingBookingService(), new CapturingDispatcher(), uid -> Optional.empty(), defaultClinicRepo(-1));
        assertTrue(none.findDefaultClinic(1).isEmpty());
        BookingController saved = new BookingController(new CapturingNavigator(new MainView()),
                new CapturingBookingService(), new CapturingDispatcher(), uid -> Optional.empty(), defaultClinicRepo(4));
        assertTrue(saved.findDefaultClinic(-1).isEmpty(), "logged out: no database read");
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
