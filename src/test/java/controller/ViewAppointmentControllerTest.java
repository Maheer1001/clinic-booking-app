package controller;

import dao.AppointmentRepository;
import model.Appointment;
import model.AppointmentDetails;
import model.AvailableTimeslot;
import model.Book;
import model.UpcomingAppointmentDetails;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ViewAppointmentControllerTest {

    private static final int OWNER = 5;
    private static final int APP_ID = 40;
    private static final int CURRENT_SLOT = 12;
    private static final LocalDate DAY = LocalDate.now().plusDays(1);

    /** One upcoming appointment (APP_ID, owned by OWNER, in slot CURRENT_SLOT). */
    private static class FakeRepo implements AppointmentRepository {
        final List<Integer> cancelled = new ArrayList<>();

        @Override public boolean isUpcomingAppointmentForUser(int appointmentId, int userId) {
            return appointmentId == APP_ID && userId == OWNER;
        }
        @Override public boolean cancelAppointment(int appointmentId) { cancelled.add(appointmentId); return true; }
        @Override public Optional<Integer> getTimeslotIdByAppointmentId(int appointmentId) {
            return appointmentId == APP_ID ? Optional.of(CURRENT_SLOT) : Optional.empty();
        }
        @Override public Optional<int[]> getClinicAndServiceIdsForAppointment(int appointmentId) {
            return Optional.of(new int[] {1, 2});
        }
        @Override public List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date) {
            return List.of(
                    new AvailableTimeslot(CURRENT_SLOT, date, LocalTime.of(9, 0), LocalTime.of(9, 30)),
                    new AvailableTimeslot(0, date, LocalTime.of(9, 30), LocalTime.of(10, 0)));
        }
        @Override public List<LocalDate> getAvailableDates(int clinicId, int serviceId) { return List.of(DAY); }

        @Override public Optional<Integer> save(Book booking, int userId, int timeslotId) { return Optional.empty(); }
        @Override public void rescheduleAppointment(int appointmentId, LocalDate newDate) {}
        @Override public boolean rescheduleToNewTimeslot(int appointmentId, int newTimeslotId) { return false; }
        @Override public boolean rescheduleToSlot(int appointmentId, AvailableTimeslot slot) { return true; }
        @Override public List<AppointmentDetails> getPastAppointmentDetailsByUserId(int userId) { return List.of(); }
        @Override public List<UpcomingAppointmentDetails> getUpcomingAppointmentDetailsByUserId(int userId) { return List.of(); }
        @Override public List<Appointment> getUpcomingAppointmentsForUser(int userId) { return List.of(); }
        @Override public Optional<LocalDate> getLastAppointmentDateByUserId(int userId) { return Optional.empty(); }
    }

    @Test
    void cancel_otherUsersAppointment_isRefused() {
        FakeRepo repo = new FakeRepo();
        ViewAppointmentController controller = new ViewAppointmentController(OWNER + 1, repo);

        assertFalse(controller.cancelAppointment(APP_ID));
        assertTrue(repo.cancelled.isEmpty());
    }

    @Test
    void cancel_ownAppointment_isCancelled() {
        FakeRepo repo = new FakeRepo();
        ViewAppointmentController controller = new ViewAppointmentController(OWNER, repo);

        assertTrue(controller.cancelAppointment(APP_ID));
        assertEquals(List.of(APP_ID), repo.cancelled);
    }

    @Test
    void rescheduleSlots_leaveOutTheCurrentSlot() {
        ViewAppointmentController controller = new ViewAppointmentController(OWNER, new FakeRepo());

        List<AvailableTimeslot> slots = controller.getAvailableTimeslotsForReschedule(APP_ID, DAY);

        assertEquals(1, slots.size());
        assertEquals(LocalTime.of(9, 30), slots.get(0).startTime());
    }

    @Test
    void rescheduleOptions_forOtherUsersAppointment_areEmpty() {
        ViewAppointmentController controller = new ViewAppointmentController(OWNER + 1, new FakeRepo());

        assertTrue(controller.getAvailableDatesForReschedule(APP_ID).isEmpty());
        assertTrue(controller.getAvailableTimeslotsForReschedule(APP_ID, DAY).isEmpty());
        assertFalse(controller.rescheduleToSlot(APP_ID,
                new AvailableTimeslot(0, DAY, LocalTime.of(9, 30), LocalTime.of(10, 0))));
    }
}
