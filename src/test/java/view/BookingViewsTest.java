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
    void personalInfo_validate_rejectsMissingNameFutureBirthdayAndBadPhone() {
        LocalDate adult = LocalDate.now().minusYears(30);
        assertEquals("", PersonalInfo.validate("Sam", "Lee", adult, "07700 900123"));
        assertEquals("", PersonalInfo.validate("Sam", "Lee", null, ""), "date of birth and phone are optional");
        assertEquals("Enter your first and last name.", PersonalInfo.validate(" ", null, adult, ""));
        assertEquals("Enter your first name.", PersonalInfo.validate("", "Lee", adult, ""));
        assertEquals("Enter your last name.", PersonalInfo.validate("Sam", "  ", adult, ""));
        assertTrue(PersonalInfo.validate("Sam", "x".repeat(51), adult, "").startsWith("Names can be at most"));
        assertTrue(PersonalInfo.validate("Sam", "Lee", LocalDate.now().plusDays(1), "").contains("date of birth"));
        assertTrue(PersonalInfo.validate("Sam", "Lee", adult, "not-a-phone").startsWith("Phone number"));
        assertTrue(PersonalInfo.validate("Sam", "Lee", adult, "0770090012").startsWith("Phone number"), "10 digits");
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

    @Test
    void selector_reopensOnThePatientsDateWhenItStillHasTimes() {
        List<AvailableTimeslot> slots = List.of(slot(9, 0, 30));
        DateTimeSelector selector = new DateTimeSelector(
                provider(Set.of(DAY, DAY.plusDays(2)), slots), s -> {}, DAY.plusDays(2));

        assertEquals(DAY.plusDays(2), selector.getSelectedDate(), "not the first free date");
    }

    @Test
    void initialDate_fallsBackToTheFirstFreeDate() {
        LocalDate today = DAY.minusDays(1);
        Set<LocalDate> free = Set.of(DAY.plusDays(3), DAY, today.minusDays(2));
        assertEquals(DAY.plusDays(3), DateTimeSelector.initialDate(free, DAY.plusDays(3), today));
        assertEquals(DAY, DateTimeSelector.initialDate(free, DAY.plusDays(1), today), "picked date has no times left");
        assertEquals(DAY, DateTimeSelector.initialDate(free, today.minusDays(2), today), "picked date is in the past");
        assertEquals(DAY, DateTimeSelector.initialDate(free, null, today));
        assertNull(DateTimeSelector.initialDate(Set.of(), DAY, today));
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
