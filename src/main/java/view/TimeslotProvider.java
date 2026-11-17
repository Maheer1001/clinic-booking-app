package view;

import model.AvailableTimeslot;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface TimeslotProvider {

    Set<LocalDate> getAvailableDates();

    List<AvailableTimeslot> getTimeslots(LocalDate date);
}
