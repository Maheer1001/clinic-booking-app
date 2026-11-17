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

    /** A clinic's opening hours and the length of one service's appointments. */
    private record Schedule(SlotSchedule.Hours hours, int duration) {}

    @Override
    public List<AvailableTimeslot> findAvailable(int clinicId, int serviceId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        if (date == null || date.isBefore(now.toLocalDate())) return new ArrayList<>();

        try (Connection c = DB.connect()) {
            Schedule schedule = schedule(c, clinicId, serviceId);
            if (schedule == null) return new ArrayList<>();
            return available(schedule, date, bookedRows(c, clinicId, serviceId, date, date)
                    .getOrDefault(date, new HashMap<>()), now);
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public List<LocalDate> findAvailableDates(int clinicId, int serviceId) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate last = today.plusDays(SlotSchedule.BOOKING_WINDOW_DAYS - 1);

        // One connection and two queries for the whole window, instead of a round trip per day.
        try (Connection c = DB.connect()) {
            Schedule schedule = schedule(c, clinicId, serviceId);
            if (schedule == null) return dates;
            Map<LocalDate, Map<LocalTime, SlotRow>> booked = bookedRows(c, clinicId, serviceId, today, last);
            for (LocalDate date = today; !date.isAfter(last); date = date.plusDays(1)) {
                if (!available(schedule, date, booked.getOrDefault(date, new HashMap<>()), now).isEmpty()) {
                    dates.add(date);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dates;
    }

    /** The clinic's hours and the service's length, or null if either doesn't exist. */
    private Schedule schedule(Connection c, int clinicId, int serviceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT c.opening_hours, s.duration_minutes
                FROM "Clinic" c, "Service" s
                WHERE c.clinic_id = ? AND s.service_id = ?
                """)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Schedule(SlotSchedule.parseHours(rs.getString(1)), rs.getInt(2));
            }
        }
    }

    /** Bookable slots on one day, given that day's booked rows (which this consumes). */
    private static List<AvailableTimeslot> available(Schedule schedule, LocalDate date,
                                                     Map<LocalTime, SlotRow> booked, LocalDateTime now) {
        List<AvailableTimeslot> list = new ArrayList<>();
        // Generated slots: free unless a booked row for that start time is full.
        for (SlotSchedule.Slot slot : SlotSchedule.slotsOn(schedule.hours(), date, schedule.duration())) {
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
        list.removeIf(s -> !LocalDateTime.of(s.date(), s.startTime()).isAfter(now));
        list.sort(Comparator.comparing(AvailableTimeslot::startTime));
        return list;
    }

    @Override
    public int reserve(int timeslotId, int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end) {
        try (Connection c = DB.connect()) {
            int id = timeslotId;
            if (id <= 0) {
                SlotRow existing = rowAt(c, clinicId, serviceId, date, start);
                if (existing != null) {
                    id = existing.id();
                } else {
                    int inserted = insertRow(c, clinicId, serviceId, date, start, end);
                    // Two people booking the same free slot at once can each insert a row. Everyone
                    // settles on the oldest row, and an unused duplicate is removed, so the
                    // capacity check below still applies to one row.
                    SlotRow canonical = rowAt(c, clinicId, serviceId, date, start);
                    id = canonical != null ? canonical.id() : inserted;
                    if (id != inserted) deleteUnusedRow(c, inserted);
                }
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

    private SlotRow rowAt(Connection c, int clinicId, int serviceId, LocalDate date, LocalTime start)
            throws SQLException {
        return bookedRows(c, clinicId, serviceId, date, date)
                .getOrDefault(date, Map.of())
                .get(start);
    }

    /**
     * Booked rows for every day from {@code from} to {@code to}, keyed by date then start time.
     * If a start time has more than one row, the oldest (lowest id) wins.
     */
    private Map<LocalDate, Map<LocalTime, SlotRow>> bookedRows(Connection c, int clinicId, int serviceId,
                                                              LocalDate from, LocalDate to) throws SQLException {
        Map<LocalDate, Map<LocalTime, SlotRow>> rows = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT timeslot_id, date, start_time, end_time, booked_count, capacity
                FROM "Timeslot"
                WHERE clinic_id = ? AND service_id = ? AND date BETWEEN ? AND ?
                ORDER BY timeslot_id
                """)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            ps.setObject(3, from);
            ps.setObject(4, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cap = rs.getInt("capacity");
                    Integer capacity = rs.wasNull() ? null : cap;
                    LocalTime start = rs.getObject("start_time", LocalTime.class);
                    if (start == null) continue;
                    rows.computeIfAbsent(rs.getObject("date", LocalDate.class), d -> new HashMap<>())
                            .putIfAbsent(start, new SlotRow(
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
                if (!keys.next()) throw new SQLException("No id returned for new timeslot row");
                return keys.getInt(1);
            }
        }
    }

    private void deleteUnusedRow(Connection c, int timeslotId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                DELETE FROM "Timeslot" WHERE timeslot_id = ? AND COALESCE(booked_count, 0) = 0
                """)) {
            ps.setInt(1, timeslotId);
            ps.executeUpdate();
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
