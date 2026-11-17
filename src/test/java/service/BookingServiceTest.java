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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        @Override public void cancelAppointment(int appointmentId) {}
        @Override public void rescheduleAppointment(int appointmentId, LocalDate newDate) {}
        @Override public void rescheduleToNewTimeslot(int appointmentId, int newTimeslotId) {}
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

    private static class NoopTimeslotRepo implements TimeslotRepository {
        @Override public List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date) { return List.of(); }
        @Override public void incrementBookedCount(int timeslotId) {}
        @Override public void decrementBookedCount(int timeslotId) {}
        @Override public void updateDate(int timeslotId, LocalDate newDate) {}
    }

    @Test
    void confirmBooking_multiService_savesOneAppointmentPerItemWithTimeslot() {
        CapturingAppointmentRepo repo = new CapturingAppointmentRepo();
        BookingService service = new BookingService(
                new NoopClinicRepo(),
                new NoopServiceRepo(),
                new NoopTimeslotRepo(),
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
                new NoopTimeslotRepo(),
                repo
        );

        Book book = new Book();
        book.setTimeslotId(7);

        List<Integer> ids = service.confirmBooking(book, 99);

        assertEquals(1, ids.size());
        assertEquals(1, repo.saves.size());
        assertEquals(7, repo.saves.get(0).timeslotId());
    }
}

