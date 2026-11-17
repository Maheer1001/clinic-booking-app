package model;

import app.DB;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
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
        List<LocalDate> dates = new ArrayList<>();
        String sql = """
            SELECT DISTINCT date
            FROM "Timeslot"
            WHERE clinic_id = ?
              AND service_id = ?
              AND booked_count < capacity
              AND date >= CURRENT_DATE
        """;

        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                dates.add(rs.getDate("date").toLocalDate());
            }

        } catch (Exception ignored) {}

        return dates;
    }

    public List<AvailableTimeslot> getAvailableTimeslots(int clinicId, int serviceId, LocalDate date) {
        List<AvailableTimeslot> slots = new ArrayList<>();
        String sql = """
            SELECT timeslot_id, date, start_time, end_time
            FROM "Timeslot"
            WHERE clinic_id = ?
              AND service_id = ?
              AND date = ?
              AND booked_count < capacity
              AND (date > CURRENT_DATE OR (date = CURRENT_DATE AND start_time > CURRENT_TIME))
            ORDER BY start_time
        """;

        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            ps.setDate(3, java.sql.Date.valueOf(date));

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                slots.add(new AvailableTimeslot(
                        rs.getInt("timeslot_id"),
                        rs.getDate("date").toLocalDate(),
                        rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time").toLocalTime()
                ));
            }

        } catch (Exception ignored) {}

        return slots;
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
