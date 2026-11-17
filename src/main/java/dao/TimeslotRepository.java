package dao;

import model.AvailableTimeslot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Contract for timeslot data access.
 *
 * Possible slots come from each clinic's opening hours; a {@code Timeslot} row only exists once a slot
 * has been booked. An {@link AvailableTimeslot} with {@code timeslotId == 0} is a free slot with no row yet.
 */
public interface TimeslotRepository {
    /** Bookable slots for a clinic, service and day (future times only, not full). */
    List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date);

    /** Days within the booking window that have at least one bookable slot. */
    List<LocalDate> findAvailableDates(int clinicId, int serviceId);

    /**
     * Takes one place in a slot, creating its row if this is the first booking.
     * Never exceeds capacity, even when two people book at once.
     *
     * @param timeslotId existing row id, or 0 for a slot that has no row yet
     * @return the slot's row id, or -1 if the slot is already full
     */
    int reserve(int timeslotId, int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end);

    void incrementBookedCount(int timeslotId);
    void decrementBookedCount(int timeslotId);
    void updateDate(int timeslotId, LocalDate newDate);
}
