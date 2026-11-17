package model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.time.LocalDate;
import java.util.List;

public class Service {

    private int id;
    private String name;
    private String description;
    private int durationMinutes;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public Service(int id, String name, String description, int durationMinutes) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
    }
    public List<LocalDate> getAvailableDates(int clinicId, int serviceId) {
        return new dao.TimeslotDAO().findAvailableDates(clinicId, serviceId);
    }

    public List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date) {
        return new dao.TimeslotDAO().findAvailable(clinicId, serviceId, date);
    }
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    @Override
    public String toString() {
        return name;
    }
}
