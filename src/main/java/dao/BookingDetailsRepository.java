package dao;

import model.BookingDetails;
import model.ClinicBookingDetails;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read access to the personal details stored with each appointment. */
public interface BookingDetailsRepository {

    /** The details stored with an appointment, or empty if none were given. */
    Optional<BookingDetails> findByAppointmentId(int appId) throws SQLException;

    /**
     * Appointments at a clinic that have stored details, soonest first.
     *
     * @param from          first date to include, or null for no lower bound
     * @param toInclusive   last date to include, or null for no upper bound
     */
    List<ClinicBookingDetails> findByClinic(int clinicId, LocalDate from, LocalDate toInclusive) throws SQLException;
}
