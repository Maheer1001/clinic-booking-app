package dao;

import app.DB;
import model.AppointmentReminder;
import model.BookingReminder;
import model.PrescriptionReminder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for reminder settings (appointment, prescription, booking).
 */
public class ReminderDAO {

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    /**
     * Runs the delete + insert of a reminder save atomically, so a failed insert cannot leave the
     * user's reminder deleted.
     */
    private static boolean inTransaction(SqlWork work) {
        try (Connection c = DB.connect()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                work.run(c);
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<AppointmentReminder> getAppointmentRemindersByUserId(int userId) {
        String sql = "SELECT ar_rem_id, user_id, app_id, notify, custom_days, custom_message, notification_method FROM \"AppointmentReminder\" WHERE user_id = ?";
        List<AppointmentReminder> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AppointmentReminder(
                            rs.getInt("ar_rem_id"),
                            rs.getInt("user_id"),
                            rs.getInt("app_id"),
                            rs.getBoolean("notify"),
                            rs.getString("custom_days"),
                            rs.getString("custom_message"),
                            rs.getString("notification_method")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** @return true if saved, false if the database update failed */
    public boolean saveAppointmentReminder(int userId, int appId, boolean notify, String customDays, String customMessage) {
        return inTransaction(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM \"AppointmentReminder\" WHERE user_id = ? AND app_id = ?")) {
                del.setInt(1, userId);
                del.setInt(2, appId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO \"AppointmentReminder\" (user_id, app_id, notify, custom_days, custom_message, notification_method) VALUES (?, ?, ?, ?, ?, 'email')")) {
                ins.setInt(1, userId);
                ins.setInt(2, appId);
                ins.setBoolean(3, notify);
                ins.setString(4, customDays != null ? customDays : "");
                ins.setString(5, customMessage != null ? customMessage : "");
                ins.executeUpdate();
            }
        });
    }

    public List<PrescriptionReminder> getPrescriptionRemindersByUserId(int userId) {
        String sql = "SELECT pr_rem_id, user_id, prescription_id, notify, interval_between_doses, custom_message, notification_method, schedule_anchor FROM \"PrescriptionReminder\" WHERE user_id = ?";
        List<PrescriptionReminder> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PrescriptionReminder(
                            rs.getInt("pr_rem_id"),
                            rs.getInt("user_id"),
                            rs.getInt("prescription_id"),
                            rs.getBoolean("notify"),
                            rs.getInt("interval_between_doses"),
                            rs.getString("custom_message"),
                            rs.getString("notification_method"),
                            rs.getObject("schedule_anchor", LocalDateTime.class)));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Saves with the dose rhythm starting now. */
    public boolean savePrescriptionReminder(int userId, int prescriptionId, boolean notify, int intervalHours, String customMessage) {
        return savePrescriptionReminder(userId, prescriptionId, notify, intervalHours, customMessage, LocalDateTime.now());
    }

    /**
     * @param scheduleAnchor when the dose rhythm started (doses are due every intervalHours from it)
     * @return true if saved, false if the database update failed
     */
    public boolean savePrescriptionReminder(int userId, int prescriptionId, boolean notify, int intervalHours,
                                            String customMessage, LocalDateTime scheduleAnchor) {
        return inTransaction(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM \"PrescriptionReminder\" WHERE user_id = ? AND prescription_id = ?")) {
                del.setInt(1, userId);
                del.setInt(2, prescriptionId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO \"PrescriptionReminder\" (user_id, prescription_id, notify, interval_between_doses, custom_message, notification_method, schedule_anchor) VALUES (?, ?, ?, ?, ?, 'email', ?)")) {
                ins.setInt(1, userId);
                ins.setInt(2, prescriptionId);
                ins.setBoolean(3, notify);
                ins.setInt(4, intervalHours);
                ins.setString(5, customMessage != null ? customMessage : "");
                ins.setObject(6, scheduleAnchor);
                ins.executeUpdate();
            }
        });
    }

    /** Records when the dose rhythm started, for reminders saved before the anchor was stored. */
    public void setPrescriptionReminderAnchor(int userId, int prescriptionId, LocalDateTime scheduleAnchor) {
        String sql = "UPDATE \"PrescriptionReminder\" SET schedule_anchor = ? WHERE user_id = ? AND prescription_id = ? AND schedule_anchor IS NULL";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, scheduleAnchor);
            ps.setInt(2, userId);
            ps.setInt(3, prescriptionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<BookingReminder> getBookingRemindersByUserId(int userId) {
        String sql = "SELECT br_rem_id, user_id, notify, days_since_last, custom_message, notification_method FROM \"BookingReminder\" WHERE user_id = ?";
        List<BookingReminder> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BookingReminder(
                            rs.getInt("br_rem_id"),
                            rs.getInt("user_id"),
                            rs.getBoolean("notify"),
                            rs.getInt("days_since_last"),
                            rs.getString("custom_message"),
                            rs.getString("notification_method")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** @return true if saved, false if the database update failed */
    public boolean saveBookingReminder(int userId, boolean notify, int daysSinceLast, String customMessage) {
        return inTransaction(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM \"BookingReminder\" WHERE user_id = ?")) {
                del.setInt(1, userId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO \"BookingReminder\" (user_id, notify, days_since_last, custom_message, notification_method) VALUES (?, ?, ?, ?, 'email')")) {
                ins.setInt(1, userId);
                ins.setBoolean(2, notify);
                ins.setInt(3, daysSinceLast);
                ins.setString(4, customMessage != null ? customMessage : "");
                ins.executeUpdate();
            }
        });
    }
}
