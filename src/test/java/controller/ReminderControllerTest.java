package controller;

import dao.AppointmentDAO;
import dao.PrescriptionDAO;
import dao.ReminderDAO;
import dao.UserRepository;
import model.Appointment;
import model.AppointmentReminder;
import model.AppointmentReminderDefaults;
import model.BookingReminder;
import model.Prescription;
import model.PrescriptionReminder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import service.NotificationScheduler;
import service.email.Email;
import service.email.EmailSender;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReminderController keeps saved reminders and the email scheduler in step: new bookings,
 * restoring at login, reschedules, cancellations and the "time to book" nudge. Uses in-memory fakes.
 */
class ReminderControllerTest {

    private static final int USER = 7;
    private static final String EMAIL = "patient@example.com";

    // ------------------------------------------------------------------ fakes

    /** Records what was saved instead of writing to the database. */
    static class FakeReminderDAO extends ReminderDAO {
        final List<AppointmentReminder> appointment = new ArrayList<>();
        final List<PrescriptionReminder> prescription = new ArrayList<>();
        final List<BookingReminder> booking = new ArrayList<>();

        @Override public List<AppointmentReminder> getAppointmentRemindersByUserId(int userId) { return appointment; }
        @Override public List<PrescriptionReminder> getPrescriptionRemindersByUserId(int userId) { return prescription; }
        @Override public List<BookingReminder> getBookingRemindersByUserId(int userId) { return booking; }

        @Override
        public boolean saveAppointmentReminder(int userId, int appId, boolean notify, String days, String message) {
            appointment.removeIf(r -> r.getAppId() == appId);
            appointment.add(new AppointmentReminder(0, userId, appId, notify, days, message, "email"));
            return true;
        }
    }

    static class FakeAppointmentDAO extends AppointmentDAO {
        final List<Appointment> upcoming = new ArrayList<>();
        Optional<LocalDate> lastVisit = Optional.empty();

        @Override public List<Appointment> getUpcomingAppointmentsForUser(int userId) { return upcoming; }
        @Override public Optional<LocalDate> getLastAppointmentDateByUserId(int userId) { return lastVisit; }
    }

    static class FakePrescriptionDAO extends PrescriptionDAO {
        final List<Prescription> prescriptions = new ArrayList<>();

        @Override public List<Prescription> getPrescriptionsForRemindersByUserId(int userId) { return prescriptions; }
    }

    /** Records scheduling calls; nothing is ever sent. */
    static class RecordingScheduler extends NotificationScheduler {
        final List<String> calls = new ArrayList<>();
        BooleanSupplier lastBookingStillWanted;

        RecordingScheduler() {
            super(new EmailSender() {
                @Override public boolean send(Email email) { return true; }
            }, 0);
        }

        @Override
        public Optional<LocalDateTime> scheduleAppointmentReminderWithSchedule(Appointment appt, String email, String name,
                                                                               String schedule, String message, boolean catchUpMissed) {
            calls.add("appointment " + appt.getAppId() + " " + schedule + " " + message + " catchUp=" + catchUpMissed);
            return Optional.of(appt.getAppointmentDateTime().minusDays(1));
        }

        @Override
        public Optional<LocalDateTime> scheduleTreatmentReminder(Prescription rx, String email, String name,
                                                                 int intervalHours, String message) {
            calls.add("prescription " + rx.getPrescriptionId() + " every " + intervalHours);
            return Optional.empty();
        }

        @Override
        public Optional<LocalDateTime> scheduleBookingReminder(String email, String name, int days, LocalDate last,
                                                               String message, BooleanSupplier stillWanted) {
            calls.add("booking " + days + " after " + last);
            lastBookingStillWanted = stillWanted;
            return Optional.of(last.plusDays(days).atTime(9, 0));
        }

        @Override public void cancelAppointmentReminders(int appId) { calls.add("cancel appointment " + appId); }
        @Override public void cancelBookingReminders(String email) { calls.add("cancel booking " + email); }
    }

    private final FakeReminderDAO reminders = new FakeReminderDAO();
    private final FakeAppointmentDAO appointments = new FakeAppointmentDAO();
    private final FakePrescriptionDAO prescriptions = new FakePrescriptionDAO();
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private AppointmentReminderDefaults savedDefaults;

    private final UserRepository users = (UserRepository) Proxy.newProxyInstance(
            UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
            (proxy, method, args) -> method.getName().equals("getDefaultAppointmentReminderTemplate")
                    ? Optional.ofNullable(savedDefaults) : null);

    private final ReminderController controller =
            new ReminderController(USER, reminders, appointments, prescriptions, scheduler, users);

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    private static Appointment appointment(int appId, int daysAhead) {
        return new Appointment(appId, USER, "STI Test", "Clinic 5", "Clinician",
                LocalDateTime.now().plusDays(daysAhead).withSecond(0).withNano(0));
    }

    // ------------------------------------------------------------------ new bookings

    @Test
    void newBookingWithoutDefaults_getsDiscreetReminderOneDayBefore() {
        appointments.upcoming.add(appointment(11, 3));

        assertTrue(controller.applyReminderForNewBookings(List.of(11), EMAIL, "Pat"));

        AppointmentReminder saved = reminders.appointment.getFirst();
        assertTrue(saved.isNotify(), "the Reminders tab must show the new booking as Enabled");
        assertEquals(ReminderController.NEW_BOOKING_SCHEDULE, saved.getCustomDays());
        assertEquals(ReminderController.NEW_BOOKING_MESSAGE, saved.getCustomMessage());
        assertEquals(List.of("appointment 11 " + ReminderController.NEW_BOOKING_SCHEDULE + " "
                + ReminderController.NEW_BOOKING_MESSAGE + " catchUp=true"), scheduler.calls);
    }

    @Test
    void newBooking_usesSavedDefaultSchedule() {
        appointments.upcoming.add(appointment(11, 3));
        String twoHours = "{\"reminders\":[{\"amount\":2,\"unit\":\"hour\"}]}";
        savedDefaults = new AppointmentReminderDefaults(true, twoHours, "{\"discreet\":\"false\"}");

        controller.applyReminderForNewBookings(List.of(11), EMAIL, "Pat");

        assertEquals(twoHours, reminders.appointment.getFirst().getCustomDays());
        assertTrue(scheduler.calls.getFirst().startsWith("appointment 11 " + twoHours));
    }

    @Test
    void newBooking_respectsDefaultOfOff() {
        appointments.upcoming.add(appointment(11, 3));
        savedDefaults = new AppointmentReminderDefaults(false, "{\"reminders\":[{\"amount\":1,\"unit\":\"day\"}]}", "");

        assertFalse(controller.applyReminderForNewBookings(List.of(11), EMAIL, "Pat"));

        assertFalse(reminders.appointment.getFirst().isNotify());
        assertTrue(scheduler.calls.isEmpty(), "nothing should be scheduled when the default is off");
    }

    // ------------------------------------------------------------------ restoring at login

    @Test
    void restore_schedulesOnlySavedRemindersThatAreOn_withoutCatchUp() {
        appointments.upcoming.add(appointment(11, 3));
        appointments.upcoming.add(appointment(12, 5));
        reminders.appointment.add(new AppointmentReminder(1, USER, 11, true, "1", "", "email"));
        reminders.appointment.add(new AppointmentReminder(2, USER, 12, false, "1", "", "email"));
        reminders.appointment.add(new AppointmentReminder(3, USER, 99, true, "1", "", "email")); // cancelled / past
        prescriptions.prescriptions.add(new Prescription(5, USER, "Doxycycline", "100mg", LocalDate.now(), null));
        reminders.prescription.add(new PrescriptionReminder(1, USER, 5, true, 12, "", "email"));

        assertEquals(2, controller.restoreScheduledReminders(EMAIL, "Pat"));
        assertEquals(List.of("appointment 11 1  catchUp=false", "prescription 5 every 12"), scheduler.calls);
    }

    @Test
    void restore_withoutEmail_doesNothing() {
        appointments.upcoming.add(appointment(11, 3));
        reminders.appointment.add(new AppointmentReminder(1, USER, 11, true, "1", "", "email"));

        assertEquals(0, controller.restoreScheduledReminders(" ", "Pat"));
        assertTrue(scheduler.calls.isEmpty());
    }

    @Test
    void restore_bookingNudge_onlyWhenStillDue() {
        reminders.booking.add(new BookingReminder(1, USER, true, 30, "", "email"));

        appointments.lastVisit = Optional.of(LocalDate.now().minusDays(40)); // already overdue
        assertEquals(0, controller.restoreScheduledReminders(EMAIL, "Pat"));

        appointments.lastVisit = Optional.of(LocalDate.now().minusDays(10));
        assertEquals(1, controller.restoreScheduledReminders(EMAIL, "Pat"));
        assertEquals("booking 30 after " + LocalDate.now().minusDays(10), scheduler.calls.getLast());
    }

    // ------------------------------------------------------------------ appointment changes

    @Test
    void reschedule_cancelsThenSchedulesForTheNewTime() {
        appointments.upcoming.add(appointment(11, 4));
        reminders.appointment.add(new AppointmentReminder(1, USER, 11, true, "2", "", "email"));

        controller.rescheduleAppointmentReminder(11, EMAIL, "Pat");

        assertEquals(List.of("cancel appointment 11", "appointment 11 2  catchUp=true"), scheduler.calls);
    }

    @Test
    void reschedule_withRemindersOff_onlyCancels() {
        appointments.upcoming.add(appointment(11, 4));
        reminders.appointment.add(new AppointmentReminder(1, USER, 11, false, "2", "", "email"));

        controller.rescheduleAppointmentReminder(11, EMAIL, "Pat");

        assertEquals(List.of("cancel appointment 11"), scheduler.calls);
    }

    @Test
    void turningReminderOff_cancelsWhatWasScheduled() {
        assertTrue(controller.saveAppointmentReminder(11, false, "1", ""));
        assertEquals(List.of("cancel appointment 11"), scheduler.calls);
    }

    // ------------------------------------------------------------------ "time to book" nudge

    @Test
    void bookingNudge_beforeFirstVisit_isNotSent() {
        Optional<LocalDateTime> when = controller.scheduleBookingReminder(EMAIL, "Pat", 180, "");

        assertTrue(when.isEmpty());
        assertEquals(List.of("cancel booking " + EMAIL), scheduler.calls);
    }

    @Test
    void bookingNudge_countsFromLastVisit_andStopsOnceBookedAgain() {
        LocalDate last = LocalDate.now().minusDays(20);
        appointments.lastVisit = Optional.of(last);

        Optional<LocalDateTime> when = controller.scheduleBookingReminder(EMAIL, "Pat", 30, "");

        assertEquals(Optional.of(last.plusDays(30).atTime(9, 0)), when);
        assertTrue(scheduler.lastBookingStillWanted.getAsBoolean());
        appointments.upcoming.add(appointment(11, 2));
        assertFalse(scheduler.lastBookingStillWanted.getAsBoolean(), "no nudge to book while an appointment is booked");
    }

    @Test
    void bookingNudge_savedWhileBooked_isNotReportedAsComing() {
        appointments.lastVisit = Optional.of(LocalDate.now().minusDays(60));
        appointments.upcoming.add(appointment(11, 2));

        Optional<LocalDateTime> when = controller.scheduleBookingReminder(EMAIL, "Pat", 30, "");

        assertTrue(when.isEmpty(), "it would only be skipped at send time, so the screen must not promise it");
        assertEquals(List.of("cancel booking " + EMAIL), scheduler.calls);
        assertTrue(controller.hasUpcomingAppointment());
    }
}
