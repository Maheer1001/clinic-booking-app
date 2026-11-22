package model;

import java.time.LocalDate;
import java.time.LocalTime;

/** A bookable slot at a clinic, for the clinic admin's timeslot editor. */
public record StaffTimeslot(int timeslotId, int clinicId, int serviceId, String serviceName, LocalDate date,
                            LocalTime startTime, LocalTime endTime, int capacity, int bookedCount) {}
