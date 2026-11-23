package service;

import dao.BookingDetailsDAO;
import dao.BookingDetailsRepository;
import model.BookingDetails;
import model.ClinicBookingDetails;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reads the personal details stored with appointments. Saving happens with the appointment itself
 * (see {@link dao.AppointmentDAO#save}). Errors are reported without the details.
 */
public class BookingDetailsService {

    private final BookingDetailsRepository repository;

    public BookingDetailsService() {
        this(new BookingDetailsDAO());
    }

    public BookingDetailsService(BookingDetailsRepository repository) {
        this.repository = repository;
    }

    /** Details for one appointment; empty if none were given or the database could not be read. */
    public Optional<BookingDetails> forAppointment(int appId) {
        try {
            return repository.findByAppointmentId(appId);
        } catch (SQLException e) {
            System.err.println("[BookingDetails] Could not read details (SQLState " + e.getSQLState() + ")");
            return Optional.empty();
        }
    }

    /** Every appointment at the clinic with stored details, soonest first. */
    public List<ClinicBookingDetails> forClinic(int clinicId) throws SQLException {
        return repository.findByClinic(clinicId, null, null);
    }

    /** Appointments at the clinic between two dates (inclusive; either may be null), soonest first. */
    public List<ClinicBookingDetails> forClinic(int clinicId, LocalDate from, LocalDate toInclusive)
            throws SQLException {
        return repository.findByClinic(clinicId, from, toInclusive);
    }
}
