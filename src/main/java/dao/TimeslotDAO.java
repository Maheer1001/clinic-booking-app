package dao;

import app.DB;
import model.AvailableTimeslot;
import service.SlotSchedule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimeslotDAO implements TimeslotRepository {

    /** A booked slot's row. */
    private record SlotRow(int id, LocalTime end, int booked, Integer capacity) {
        boolean hasSpace() {
            return capacity == null || booked < capacity;
        }
    }

    @Override
    public List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date) {
        List<AvailableTimeslot> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        if (date == null || date.isBefore(now.toLocalDate())) return list;

        try (Connection c = DB.connect()) {
            SlotSchedule.Hours hours = SlotSchedule.DEFAULT_HOURS;
            int duration = 0;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT c.opening_hours, s.duration_minutes
                    FROM "Clinic" c, "Service" s
                    WHERE c.clinic_id = ? AND s.service_id = ?
                    """)) {
                ps.setInt(1, clinicId);
                ps.setInt(2, serviceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return list;
                    hours = SlotSchedule.parseHours(rs.getString(1));
                    duration = rs.getInt(2);
                }
            }

            Map<LocalTime, SlotRow> booked = bookedRows(c, clinicId, serviceId, date);

            // Generated slots: free unless a booked row for that start time is full.
            for (SlotSchedule.Slot slot : SlotSchedule.slotsOn(hours, date, duration)) {
                SlotRow row = booked.remove(slot.start());
                if (row == null) {
                    list.add(new AvailableTimeslot(0, date, slot.start(), slot.end()));
                } else if (row.hasSpace()) {
                    list.add(new AvailableTimeslot(row.id(), date, slot.start(), row.end()));
                }
            }
            // Rows outside the generated grid (e.g. created before opening hours were used) still count.
            for (Map.Entry<LocalTime, SlotRow> e : booked.entrySet()) {
                if (e.getValue().hasSpace()) {
                    list.add(new AvailableTimeslot(e.getValue().id(), date, e.getKey(), e.getValue().end()));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        list.removeIf(s -> !LocalDateTime.of(s.date(), s.startTime()).isAfter(now));
        list.sort(Comparator.comparing(AvailableTimeslot::startTime));
        return list;
    }

    @Override
    public List<LocalDate> findAvailableDates(int clinicId, int serviceId) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < SlotSchedule.BOOKING_WINDOW_DAYS; i++) {
            LocalDate date = today.plusDays(i);
            if (!findAvailable(clinicId, serviceId, date).isEmpty()) {
                dates.add(date);
            }
        }
        return dates;
    }

    @Override
    public int reserve(int timeslotId, int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end) {
        try (Connection c = DB.connect()) {
            int id = timeslotId;
            if (id <= 0) {
                SlotRow existing = bookedRows(c, clinicId, serviceId, date).get(start);
                id = existing != null ? existing.id() : insertRow(c, clinicId, serviceId, date, start, end);
            }
            // Single conditional update, so concurrent bookings can never push a slot over capacity.
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE "Timeslot"
                    SET booked_count = COALESCE(booked_count, 0) + 1
                    WHERE timeslot_id = ? AND (capacity IS NULL OR COALESCE(booked_count, 0) < capacity)
                    """)) {
                ps.setInt(1, id);
                return ps.executeUpdate() == 1 ? id : -1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private Map<LocalTime, SlotRow> bookedRows(Connection c, int clinicId, int serviceId, LocalDate date)
            throws SQLException {
        Map<LocalTime, SlotRow> rows = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT timeslot_id, start_time, end_time, booked_count, capacity
                FROM "Timeslot"
                WHERE clinic_id = ? AND service_id = ? AND date = ?
                """)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            ps.setObject(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cap = rs.getInt("capacity");
                    Integer capacity = rs.wasNull() ? null : cap;
                    rows.putIfAbsent(rs.getObject("start_time", LocalTime.class), new SlotRow(
                            rs.getInt("timeslot_id"),
                            rs.getObject("end_time", LocalTime.class),
                            rs.getInt("booked_count"),
                            capacity));
                }
            }
        }
        return rows;
    }

    private int insertRow(Connection c, int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO "Timeslot" (date, start_time, end_time, service_id, clinic_id, capacity, booked_count)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, date);
            ps.setObject(2, start);
            ps.setObject(3, end);
            ps.setInt(4, serviceId);
            ps.setInt(5, clinicId);
            ps.setInt(6, SlotSchedule.DEFAULT_CAPACITY);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
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
