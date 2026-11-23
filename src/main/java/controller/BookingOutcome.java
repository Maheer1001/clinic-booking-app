package controller;

import model.Item;
import service.BookingService;

import java.util.ArrayList;
import java.util.List;

/**
 * What the booking screens say and offer after "Confirm booking", kept free of JavaFX so it can be
 * unit tested (like {@link StaffForms}).
 */
final class BookingOutcome {

    private BookingOutcome() {}

    /** Shown under the booking result when the name typed at booking could not be saved to the account. */
    static final String NAME_NOT_SAVED =
            "We couldn't save your name to your account. You can add it in Settings.";

    /**
     * The items of a multi-service booking that were not booked, in booking order, each matched against
     * the appointments that were saved (by service, date and time), with the chosen time cleared and the
     * date kept, so "Choose a different time" reopens the time picker on the same day.
     * Empty when everything was booked.
     */
    static List<Item> itemsToRebook(List<Item> items, BookingService.BookingResult outcome) {
        List<Item> left = new ArrayList<>();
        if (items == null) return left;
        List<BookingService.BookedAppointment> unmatched = new ArrayList<>(outcome.booked());
        for (Item item : items) {
            BookingService.BookedAppointment match = null;
            for (BookingService.BookedAppointment appt : unmatched) {
                if (sameService(item, appt) && java.util.Objects.equals(item.getDate(), appt.date())
                        && java.util.Objects.equals(item.getTime(), appt.time())) {
                    match = appt;
                    break;
                }
            }
            if (match != null) {
                unmatched.remove(match);
                continue;
            }
            Item again = new Item(item.getService());
            again.setDate(item.getDate());
            left.add(again);
        }
        return left;
    }

    private static boolean sameService(Item item, BookingService.BookedAppointment appt) {
        if (item.getService() == appt.service()) return true;
        return item.getService() != null && appt.service() != null
                && item.getService().getId() == appt.service().getId();
    }

    /**
     * When only some services of a booking were saved: which were booked and which were not, and what to
     * do next, e.g. "Booked: STI Test. Not booked, the time was no longer available: Blood test. Choose a
     * different time to book it." Empty when nothing was left unbooked.
     */
    static String partialMessage(List<String> booked, List<String> notBooked) {
        if (notBooked == null || notBooked.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (booked != null && !booked.isEmpty()) {
            sb.append("Booked: ").append(String.join(", ", booked)).append(". ");
        }
        sb.append("Not booked, the time was no longer available: ").append(String.join(", ", notBooked))
                .append(". Choose a different time to book ").append(notBooked.size() == 1 ? "it" : "them").append('.');
        return sb.toString();
    }

    /** The names of the services booked, in order ("Appointment" when one has no service). */
    static List<String> bookedNames(BookingService.BookingResult outcome) {
        return outcome.booked().stream()
                .map(a -> a.service() != null ? a.service().getName() : "Appointment")
                .toList();
    }
}
