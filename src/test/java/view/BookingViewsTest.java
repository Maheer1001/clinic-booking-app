package view;

import javafx.application.Platform;
import model.AvailableTimeslot;
import model.Item;
import model.Service;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BookingViewsTest {

    private static final LocalDate DAY = LocalDate.of(2030, 3, 6); // a Wednesday

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    private static AvailableTimeslot slot(int h, int m, int minutes) {
        LocalTime start = LocalTime.of(h, m);
        return new AvailableTimeslot(0, DAY, start, start.plusMinutes(minutes));
    }

    @Test
    void overlaps_detectsSharedTimeOnTheSameDay() {
        assertTrue(DateTime.overlaps(slot(10, 0, 30), slot(10, 15, 30)));
        assertTrue(DateTime.overlaps(slot(10, 0, 60), slot(10, 15, 15)));
        assertFalse(DateTime.overlaps(slot(10, 0, 30), slot(10, 30, 30)), "back-to-back is fine");
        AvailableTimeslot otherDay = new AvailableTimeslot(0, DAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertFalse(DateTime.overlaps(slot(10, 0, 30), otherDay));
    }

    @Test
    void describeTime_showsDayDateAndRange() {
        Item item = new Item(new Service(1, "STI test", "", 30));
        assertEquals("Not chosen", Review.describeTime(item));

        item.setDate(DAY);
        item.setTime(LocalTime.of(9, 30));
        assertEquals("Wed 6 Mar 2030, 09:30 – 10:00", Review.describeTime(item));
    }

    @Test
    void selector_withNoDates_disablesPickersAndExplains() {
        DateTimeSelector selector = new DateTimeSelector(provider(Set.of(), List.of()), s -> {});

        assertTrue(selector.getTimeCombo().isDisabled());
        assertNull(selector.getSelectedDate());
    }

    @Test
    void selector_preselectsFirstDateAndLoadsItsTimes() {
        List<AvailableTimeslot> slots = List.of(slot(9, 0, 30), slot(9, 30, 30));
        List<AvailableTimeslot> selected = new ArrayList<>();
        DateTimeSelector selector = new DateTimeSelector(
                provider(Set.of(DAY.plusDays(2), DAY), slots), selected::add);

        assertEquals(DAY, selector.getSelectedDate());
        assertEquals(slots, selector.getTimeCombo().getItems());
        assertFalse(selector.getTimeCombo().isDisabled());

        selector.getTimeCombo().setValue(slots.get(1));
        assertEquals(List.of(slots.get(1)), selected);
    }

    private static TimeslotProvider provider(Set<LocalDate> dates, List<AvailableTimeslot> slots) {
        return new TimeslotProvider() {
            @Override public Set<LocalDate> getAvailableDates() { return dates; }
            @Override public List<AvailableTimeslot> getTimeslots(LocalDate date) {
                return date != null && dates.contains(date) ? slots : List.of();
            }
        };
    }
}
