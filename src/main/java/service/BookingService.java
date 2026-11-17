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
import java.util.ArrayList;
import java.util.List;

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
                if (item.getTimeslotId() >= 0) {
                    appointmentDAO.save(book, userId, item.getTimeslotId()).ifPresent(appIds::add);
                }
            }
        } else if (book.getTimeslotId() >= 0) {
            appointmentDAO.save(book, userId, book.getTimeslotId()).ifPresent(appIds::add);
        }
        return appIds;
    }
}
