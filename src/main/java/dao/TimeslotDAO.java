package dao;

import app.DB;
import model.AvailableTimeslot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TimeslotDAO implements TimeslotRepository {

    @Override
    public List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date) {
        String sql = """
            SELECT t.timeslot_id, t.date, t.start_time, t.end_time
            FROM "Timeslot" t
            WHERE t.clinic_id = ? AND t.service_id = ? AND t.date = ?
              AND (t.capacity IS NULL OR COALESCE(t.booked_count, 0) < t.capacity)
              AND (t.date > CURRENT_DATE OR (t.date = CURRENT_DATE AND t.start_time >= CURRENT_TIME))
            ORDER BY t.start_time
            """;
        List<AvailableTimeslot> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            ps.setObject(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AvailableTimeslot(
                            rs.getInt("timeslot_id"),
                            rs.getObject("date", LocalDate.class),
                            rs.getObject("start_time", LocalTime.class),
                            rs.getObject("end_time", LocalTime.class)));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void incrementBookedCount(int timeslotId) {
        String sql = """
            UPDATE "Timeslot"
            SET booked_count = COALESCE(booked_count, 0) + 1
            WHERE timeslot_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, timeslotId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void decrementBookedCount(int timeslotId) {
        String sql = """
            UPDATE "Timeslot"
            SET booked_count = GREATEST(COALESCE(booked_count, 0) - 1, 0)
            WHERE timeslot_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, timeslotId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateDate(int timeslotId, LocalDate newDate) {
        String sql = """
            UPDATE "Timeslot"
            SET date = ?
            WHERE timeslot_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(newDate));
            ps.setInt(2, timeslotId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
