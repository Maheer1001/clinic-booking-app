package model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A timeslot that can be booked (has capacity and is in the future).
 */
public record AvailableTimeslot(int timeslotId, LocalDate date, LocalTime startTime, LocalTime endTime) {}
