package service;

import dao.AppointmentRepository;
import dao.ClinicRepository;
import dao.ServiceRepository;
import dao.TimeslotRepository;
import model.AvailableTimeslot;
import model.Book;
import model.Clinic;
import model.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for the booking flow.
 */
public class BookingService {

    private final ClinicRepository clinicDAO;
    private final ServiceRepository serviceDAO;
    private final TimeslotRepository timeslotDAO;
    private final AppointmentRepository appointmentDAO;
    private final Clock clock;

    public BookingService() {
        this(new dao.ClinicDAO(), new dao.ServiceDAO(), new dao.TimeslotDAO(), new dao.AppointmentDAO());
    }

    public BookingService(ClinicRepository clinicDAO, ServiceRepository serviceDAO,
                          TimeslotRepository timeslotDAO, AppointmentRepository appointmentDAO) {
        this(clinicDAO, serviceDAO, timeslotDAO, appointmentDAO, Clock.systemDefaultZone());
    }

    public BookingService(ClinicRepository clinicDAO, ServiceRepository serviceDAO,
                          TimeslotRepository timeslotDAO, AppointmentRepository appointmentDAO, Clock clock) {
        this.clinicDAO = clinicDAO;
        this.serviceDAO = serviceDAO;
        this.timeslotDAO = timeslotDAO;
        this.appointmentDAO = appointmentDAO;
        this.clock = clock;
    }

    public List<Clinic> findAllClinics() {
        return clinicDAO.findAll();
    }

    public String findClinicNameById(int id) {
        Clinic clinic = clinicDAO.findById(id);
        return clinic != null ? clinic.getName() : "";
    }

    public Clinic findClinicById(int id) {return clinicDAO.findById(id);}

    public List<Service> findServicesByClinicId(int clinicId) {
        return serviceDAO.findByClinicId(clinicId);
    }

    public List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date) {
        return timeslotDAO.findAvailable(clinicId, serviceId, date);
    }

    /** An appointment that was saved. {@code service}, {@code date} and {@code time} may be null on the legacy path. */
    public record BookedAppointment(int appId, Service service, LocalDate date, LocalTime time) {}

    /**
     * What happened when a booking was confirmed.
     *
     * @param booked      appointments saved, in booking order
     * @param unavailable names of the services whose chosen slot could not be booked (full, in the past, or a save error)
     */
    public record BookingResult(List<BookedAppointment> booked, List<String> unavailable) {
        public boolean anyBooked() {
            return !booked.isEmpty();
        }
    }

    /**
     * Confirms booking(s) for the given user.
     * - If the Book has items (multi-service flow), creates one appointment per item with a selected timeslot.
     * - Otherwise, falls back to the legacy single-service behaviour using book.timeslotId.
     *
     * @return new {@code app_id} values for each row successfully inserted (order matches booking order)
     */
    public List<Integer> confirmBooking(Book book, int userId) {
        return placeBooking(book, userId).booked().stream().map(BookedAppointment::appId).toList();
    }

    /**
     * Like {@link #confirmBooking} but says which appointments were saved and which could not be.
     * A slot that has already started by the time the user confirms is not booked.
     */
    public BookingResult placeBooking(Book book, int userId) {
        List<BookedAppointment> booked = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        if (book.getItems() != null && !book.getItems().isEmpty()) {
            for (model.Item item : book.getItems()) {
                boolean picked = item.getTimeslotId() > 0
                        || (item.getTimeslotId() == 0 && item.getDate() != null && item.getTime() != null);
                if (!picked) continue;
                Service service = item.getService();
                String name = service != null ? service.getName() : "Appointment";
                boolean inPast = item.getDate() != null && item.getTime() != null
                        && !LocalDateTime.of(item.getDate(), item.getTime()).isAfter(now);
                boolean noClinic = item.getTimeslotId() == 0 && book.getClinic() == null;
                if (service == null || inPast || noClinic) {
                    unavailable.add(name);
                    continue;
                }
                LocalTime end = item.getTime() == null ? null
                        : item.getTime().plusMinutes(service.getDurationMinutes());
                int clinicId = book.getClinic() == null ? 0 : book.getClinic().getId();
                int slotId = timeslotDAO.reserve(item.getTimeslotId(), clinicId,
                        service.getId(), item.getDate(), item.getTime(), end);
                Optional<Integer> appId = save(book, userId, slotId);
                if (appId.isPresent()) {
                    booked.add(new BookedAppointment(appId.get(), service, item.getDate(), item.getTime()));
                } else {
                    unavailable.add(name);
                }
            }
        } else if (book.getTimeslotId() > 0) {
            Optional<Integer> appId = save(book, userId,
                    timeslotDAO.reserve(book.getTimeslotId(), 0, 0, null, null, null));
            if (appId.isPresent()) {
                booked.add(new BookedAppointment(appId.get(), book.getService(), book.getDate(), book.getTime()));
            } else {
                unavailable.add(book.getService() != null ? book.getService().getName() : "Appointment");
            }
        }
        return new BookingResult(List.copyOf(booked), List.copyOf(unavailable));
    }

    /** Saves an appointment in a reserved slot, releasing the place if the save fails. */
    private Optional<Integer> save(Book book, int userId, int timeslotId) {
        if (timeslotId < 0) return Optional.empty(); // slot became full before confirmation
        Optional<Integer> appId = appointmentDAO.save(book, userId, timeslotId);
        if (appId.isEmpty()) {
            timeslotDAO.decrementBookedCount(timeslotId);
        }
        return appId;
    }
}
