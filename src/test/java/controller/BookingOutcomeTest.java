package controller;

import model.Item;
import model.Service;
import org.junit.jupiter.api.Test;
import service.BookingService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What a partly booked multi-service booking says and keeps for "Choose a different time". */
class BookingOutcomeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 3);
    private final Service sti = new Service(1, "STI Test", "desc", 30);
    private final Service blood = new Service(2, "Blood test", "desc", 15);
    private final Service pill = new Service(3, "Contraception", "desc", 20);

    private static Item item(Service s, LocalTime time) {
        Item i = new Item(s);
        i.setTimeslotId(9);
        i.setDate(DAY);
        i.setTime(time);
        return i;
    }

    @Test
    void itemsToRebook_keepsOnlyTheUnbookedServices_onTheSameDateWithNoTime() {
        Item a = item(sti, LocalTime.of(10, 0));
        Item b = item(blood, LocalTime.of(11, 0));
        Item c = item(pill, LocalTime.of(12, 0));
        BookingService.BookingResult outcome = new BookingService.BookingResult(
                List.of(new BookingService.BookedAppointment(7, sti, DAY, LocalTime.of(10, 0)),
                        new BookingService.BookedAppointment(8, pill, DAY, LocalTime.of(12, 0))),
                List.of("Blood test"));

        List<Item> left = BookingOutcome.itemsToRebook(List.of(a, b, c), outcome);

        assertEquals(1, left.size());
        Item again = left.getFirst();
        assertSame(blood, again.getService());
        assertEquals(DAY, again.getDate(), "the time picker reopens on the chosen date");
        assertNull(again.getTime(), "a new time has to be picked");
        assertEquals(0, again.getTimeslotId());
    }

    @Test
    void itemsToRebook_emptyWhenEverythingWasBooked() {
        Item a = item(sti, LocalTime.of(10, 0));
        BookingService.BookingResult outcome = new BookingService.BookingResult(
                List.of(new BookingService.BookedAppointment(7, sti, DAY, LocalTime.of(10, 0))), List.of());
        assertTrue(BookingOutcome.itemsToRebook(List.of(a), outcome).isEmpty());
        assertTrue(BookingOutcome.itemsToRebook(null, outcome).isEmpty());
    }

    @Test
    void itemsToRebook_matchesTheSameServiceAtDifferentTimesSeparately() {
        Item early = item(sti, LocalTime.of(9, 0));
        Item late = item(sti, LocalTime.of(15, 0));
        BookingService.BookingResult outcome = new BookingService.BookingResult(
                List.of(new BookingService.BookedAppointment(7, sti, DAY, LocalTime.of(15, 0))), List.of("STI Test"));

        List<Item> left = BookingOutcome.itemsToRebook(List.of(early, late), outcome);

        assertEquals(1, left.size(), "only the 9:00 one is left");
    }

    @Test
    void partialMessage_saysWhatWasBookedAndWhatWasNot() {
        assertEquals("Booked: STI Test, Contraception. Not booked, the time was no longer available: Blood test. "
                        + "Choose a different time to book it.",
                BookingOutcome.partialMessage(List.of("STI Test", "Contraception"), List.of("Blood test")));
        assertEquals("Booked: STI Test. Not booked, the time was no longer available: Blood test, Contraception. "
                        + "Choose a different time to book them.",
                BookingOutcome.partialMessage(List.of("STI Test"), List.of("Blood test", "Contraception")));
        assertEquals("", BookingOutcome.partialMessage(List.of("STI Test"), List.of()));
    }

    @Test
    void bookedNames_inBookingOrder() {
        BookingService.BookingResult outcome = new BookingService.BookingResult(
                List.of(new BookingService.BookedAppointment(7, sti, DAY, LocalTime.of(10, 0)),
                        new BookingService.BookedAppointment(8, null, null, null)),
                List.of());
        assertEquals(List.of("STI Test", "Appointment"), BookingOutcome.bookedNames(outcome));
    }
}
