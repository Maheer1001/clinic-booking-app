package service;

import dao.AppointmentRepository;
import dao.ClinicRepository;
import dao.ServiceRepository;
import dao.TimeslotRepository;
import model.AvailableTimeslot;
import model.Book;
import model.Clinic;
import model.Service;

import java.time.LocalDate;
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

    public BookingService() {
        this(new dao.ClinicDAO(), new dao.ServiceDAO(), new dao.TimeslotDAO(), new dao.AppointmentDAO());
    }

    public BookingService(ClinicRepository clinicDAO, ServiceRepository serviceDAO,
                          TimeslotRepository timeslotDAO, AppointmentRepository appointmentDAO) {
        this.clinicDAO = clinicDAO;
        this.serviceDAO = serviceDAO;
        this.timeslotDAO = timeslotDAO;
        this.appointmentDAO = appointmentDAO;
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

    /**
     * Confirms booking(s) for the given user.
     * - If the Book has items (multi-service flow), creates one appointment per item with a selected timeslot.
     * - Otherwise, falls back to the legacy single-service behaviour using book.timeslotId.
     *
     * @return new {@code app_id} values for each row successfully inserted (order matches booking order)
     */
    public List<Integer> confirmBooking(Book book, int userId) {
        List<Integer> appIds = new ArrayList<>();
        if (book.getItems() != null && !book.getItems().isEmpty()) {
            for (model.Item item : book.getItems()) {
                boolean picked = item.getTimeslotId() > 0
                        || (item.getTimeslotId() == 0 && item.getDate() != null && item.getTime() != null);
                if (!picked) continue;
                LocalTime end = item.getTime() == null ? null
                        : item.getTime().plusMinutes(item.getService().getDurationMinutes());
                int clinicId = book.getClinic() == null ? 0 : book.getClinic().getId();
                book(book, userId, timeslotDAO.reserve(item.getTimeslotId(), clinicId,
                        item.getService().getId(), item.getDate(), item.getTime(), end), appIds);
            }
        } else if (book.getTimeslotId() > 0) {
            book(book, userId, timeslotDAO.reserve(book.getTimeslotId(), 0, 0, null, null, null), appIds);
        }
        return appIds;
    }

    /** Saves an appointment in a reserved slot, releasing the place if the save fails. */
    private void book(Book book, int userId, int timeslotId, List<Integer> appIds) {
        if (timeslotId < 0) return; // slot became full before confirmation
        Optional<Integer> appId = appointmentDAO.save(book, userId, timeslotId);
        if (appId.isPresent()) {
            appIds.add(appId.get());
        } else {
            timeslotDAO.decrementBookedCount(timeslotId);
        }
    }
}
