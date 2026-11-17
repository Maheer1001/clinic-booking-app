package dao;

import model.AvailableTimeslot;

import java.time.LocalDate;
import java.util.List;

/**
 * Contract for timeslot data access.
 */
public interface TimeslotRepository {
    List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date);
    void incrementBookedCount(int timeslotId);
    void decrementBookedCount(int timeslotId);
    void updateDate(int timeslotId, LocalDate newDate);
}
