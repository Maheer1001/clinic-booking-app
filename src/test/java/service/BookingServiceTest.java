package service;

import dao.AppointmentRepository;
import dao.ClinicRepository;
import dao.ServiceRepository;
import dao.TimeslotRepository;
import model.Appointment;
import model.AppointmentDetails;
import model.AvailableTimeslot;
import model.Book;
import model.Clinic;
import model.Item;
import model.Service;
import model.UpcomingAppointmentDetails;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BookingService.confirmBooking, focusing on multi-service behaviour.
 */
class BookingServiceTest {

    private static class CapturingAppointmentRepo implements AppointmentRepository {
        static record SaveCall(Book book, int userId, int timeslotId) {}
        final List<SaveCall> saves = new ArrayList<>();

        private int nextAppId = 1;

        @Override
        public Optional<Integer> save(Book booking, int userId, int timeslotId) {
            saves.add(new SaveCall(booking, userId, timeslotId));
            return Optional.of(nextAppId++);
        }

        // Unused methods in these tests; provide no-op or empty implementations.
        @Override public boolean cancelAppointment(int appointmentId) { return false; }
        @Override public void rescheduleAppointment(int appointmentId, LocalDate newDate) {}
        @Override public boolean rescheduleToNewTimeslot(int appointmentId, int newTimeslotId) { return false; }
        @Override public Optional<Integer> getTimeslotIdByAppointmentId(int appointmentId) { return Optional.empty(); }
        @Override public boolean rescheduleToSlot(int appointmentId, AvailableTimeslot slot) { return false; }
        @Override public Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId) { return Optional.empty(); }
        @Override public boolean isUpcomingAppointmentForUser(int appointmentId, int userId) { return false; }
        @Override public List<AppointmentDetails> getPastAppointmentDetailsByUserId(int userId) { return List.of(); }
        @Override public List<UpcomingAppointmentDetails> getUpcomingAppointmentDetailsByUserId(int userId) { return List.of(); }
        @Override public List<Appointment> getUpcomingAppointmentsForUser(int userId) { return List.of(); }
        @Override public List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date) { return List.of(); }
        @Override public Optional<LocalDate> getLastAppointmentDateByUserId(int userId) { return Optional.empty(); }

        @Override
        public List<LocalDate> getAvailableDates(int clinicId, int serviceId) {
            return List.of();
        }
    }

    private static class NoopClinicRepo implements ClinicRepository {
        @Override public List<Clinic> findAll() { return List.of(); }
        @Override public Clinic findById(int clinicId) { return null; }
    }

    private static class NoopServiceRepo implements ServiceRepository {
        @Override public List<Service> findByClinicId(int clinicId) { return List.of(); }
    }

    /**
     * In-memory slots: id 0 creates a row with capacity 1; unknown positive ids behave like
     * legacy rows with no capacity limit.
     */
    private static class FakeTimeslotRepo implements TimeslotRepository {
        final Map<Integer, int[]> rows = new HashMap<>(); // id -> {booked, capacity}
        private int nextId = 1000;

        @Override public List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date) { return List.of(); }
        @Override public List<LocalDate> findAvailableDates(int clinicId, int serviceId) { return List.of(); }

        @Override
        public int reserve(int timeslotId, int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end) {
            int id = timeslotId > 0 ? timeslotId : nextId++;
            int[] row = rows.computeIfAbsent(id, k -> new int[] {0, timeslotId > 0 ? Integer.MAX_VALUE : 1});
            if (row[0] >= row[1]) return -1;
            row[0]++;
            return id;
        }

        @Override public void incrementBookedCount(int timeslotId) {}
        @Override public void decrementBookedCount(int timeslotId) { rows.computeIfPresent(timeslotId, (k, r) -> new int[] {r[0] - 1, r[1]}); }
        @Override public void updateDate(int timeslotId, LocalDate newDate) {}
    }

    @Test
    void confirmBooking_multiService_savesOneAppointmentPerItemWithTimeslot() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        BookingService service = new BookingService(
                new NoopClinicRepo(),
                new NoopServiceRepo(),
                new FakeTimeslotRepo(),
                repo
        );

        Book book = new Book();
        List<Item> items = new ArrayList<>();
        Item i1 = new Item(new Service(1, "Test A", "desc", 30));
        i1.setTimeslotId(10);
        Item i2 = new Item(new Service(2, "Test B", "desc", 30));
        i2.setTimeslotId(11);
        Item i3 = new Item(new Service(3, "Test C", "desc", 30));
        i3.setTimeslotId(-1); // should be ignored
        items.add(i1);
        items.add(i2);
        items.add(i3);
        book.setItems(items);

        List<Integer> ids = service.confirmBooking(book, 42);

        assertEquals(2, ids.size());
        assertEquals(2, repo.saves.size());
        assertEquals(10, repo.saves.get(0).timeslotId());
        assertEquals(11, repo.saves.get(1).timeslotId());
    }

    @Test
    void confirmBooking_singleService_fallsBackToBookTimeslotId() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        BookingService service = new BookingService(
                new NoopClinicRepo(),
                new NoopServiceRepo(),
                new FakeTimeslotRepo(),
                repo
        );

        Book book = new Book();
        book.setTimeslotId(7);

        List<Integer> ids = service.confirmBooking(book, 99);

        assertEquals(1, ids.size());
        assertEquals(1, repo.saves.size());
        assertEquals(7, repo.saves.get(0).timeslotId());
    }

    private static Book bookAtClinic(Item... items) {
        Book book = new Book();
        book.setClinic(new Clinic(3, "Clinic 3", "Addr", "SO18 2RD", "0123", new ArrayList<>(), null, new ArrayList<>()));
        book.setItems(new ArrayList<>(List.of(items)));
        return book;
    }

    private static Item newSlotItem(LocalTime time) {
        Item item = new Item(new Service(3, "STI Test", "desc", 30));
        item.setTimeslotId(0); // no row yet: the slot only exists in the clinic's schedule
        item.setDate(LocalDate.now().plusDays(3));
        item.setTime(time);
        return item;
    }

    @Test
    void confirmBooking_newSlot_createsItsRowAndBooksIt() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        FakeTimeslotRepo slots = new FakeTimeslotRepo();
        BookingService service = new BookingService(new NoopClinicRepo(), new NoopServiceRepo(), slots, repo);

        List<Integer> ids = service.confirmBooking(bookAtClinic(newSlotItem(LocalTime.of(10, 0))), 42);

        assertEquals(1, ids.size());
        int slotId = repo.saves.get(0).timeslotId();
        assertTrue(slotId > 0, "appointment should point at the newly created slot row");
        assertEquals(1, slots.rows.get(slotId)[0]);
    }

    @Test
    void confirmBooking_fullSlot_isNotBooked() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        FakeTimeslotRepo slots = new FakeTimeslotRepo();
        BookingService service = new BookingService(new NoopClinicRepo(), new NoopServiceRepo(), slots, repo);

        service.confirmBooking(bookAtClinic(newSlotItem(LocalTime.of(10, 0))), 1);
        int fullSlot = repo.saves.get(0).timeslotId();

        Item second = newSlotItem(LocalTime.of(10, 0));
        second.setTimeslotId(fullSlot); // capacity 1, already taken
        List<Integer> ids = service.confirmBooking(bookAtClinic(second), 2);

        assertTrue(ids.isEmpty());
        assertEquals(1, repo.saves.size());
        assertEquals(1, slots.rows.get(fullSlot)[0]);
    }

    @Test
    void placeBooking_slotThatHasAlreadyStarted_isNotBooked() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        FakeTimeslotRepo slots = new FakeTimeslotRepo();
        BookingService service = new BookingService(new NoopClinicRepo(), new NoopServiceRepo(), slots, repo);

        Item stale = newSlotItem(LocalTime.of(10, 0));
        stale.setDate(LocalDate.now().minusDays(1)); // page left open overnight

        BookingService.BookingResult result = service.placeBooking(bookAtClinic(stale), 42);

        assertFalse(result.anyBooked());
        assertEquals(List.of("STI Test"), result.unavailable());
        assertTrue(repo.saves.isEmpty());
        assertTrue(slots.rows.isEmpty(), "no slot row should be reserved");
    }

    @Test
    void placeBooking_reportsWhichItemsCouldNotBeBooked() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        FakeTimeslotRepo slots = new FakeTimeslotRepo();
        BookingService service = new BookingService(new NoopClinicRepo(), new NoopServiceRepo(), slots, repo);

        service.confirmBooking(bookAtClinic(newSlotItem(LocalTime.of(10, 0))), 1);
        int fullSlot = repo.saves.get(0).timeslotId();

        Item full = new Item(new Service(4, "HIV Test", "desc", 30));
        full.setTimeslotId(fullSlot);
        full.setDate(LocalDate.now().plusDays(3));
        full.setTime(LocalTime.of(10, 0));
        Item free = newSlotItem(LocalTime.of(11, 0));

        BookingService.BookingResult result = service.placeBooking(bookAtClinic(full, free), 2);

        assertEquals(1, result.booked().size());
        assertEquals(LocalTime.of(11, 0), result.booked().get(0).time());
        assertEquals("STI Test", result.booked().get(0).service().getName());
        assertEquals(List.of("HIV Test"), result.unavailable());
    }

    @Test
    void placeBooking_newSlotWithoutClinic_isNotBooked() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        BookingService service = new BookingService(new NoopClinicRepo(), new NoopServiceRepo(), new FakeTimeslotRepo(), repo);
        Book book = new Book();
        book.setItems(List.of(newSlotItem(LocalTime.of(10, 0))));

        BookingService.BookingResult result = service.placeBooking(book, 42);

        assertFalse(result.anyBooked());
        assertTrue(repo.saves.isEmpty());
    }

    @Test
    void confirmBooking_itemWithoutAPickedSlot_isIgnored() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        BookingService service = new BookingService(new NoopClinicRepo(), new NoopServiceRepo(), new FakeTimeslotRepo(), repo);

        Item notPicked = new Item(new Service(3, "STI Test", "desc", 30)); // id 0, but no date or time chosen

        assertTrue(service.confirmBooking(bookAtClinic(notPicked), 42).isEmpty());
        assertTrue(repo.saves.isEmpty());
    }
}
