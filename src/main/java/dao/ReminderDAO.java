package dao;

import app.DB;
import model.AppointmentReminder;
import model.BookingReminder;
import model.PrescriptionReminder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for reminder settings (appointment, prescription, booking).
 */
public class ReminderDAO {

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

    public void saveAppointmentReminder(int userId, int appId, boolean notify, String customDays, String customMessage) {
        try (Connection c = DB.connect()) {
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<PrescriptionReminder> getPrescriptionRemindersByUserId(int userId) {
        String sql = "SELECT pr_rem_id, user_id, prescription_id, notify, interval_between_doses, custom_message, notification_method FROM \"PrescriptionReminder\" WHERE user_id = ?";
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
                            rs.getString("notification_method")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void savePrescriptionReminder(int userId, int prescriptionId, boolean notify, int intervalHours, String customMessage) {
        try (Connection c = DB.connect()) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM \"PrescriptionReminder\" WHERE user_id = ? AND prescription_id = ?")) {
                del.setInt(1, userId);
                del.setInt(2, prescriptionId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO \"PrescriptionReminder\" (user_id, prescription_id, notify, interval_between_doses, custom_message, notification_method) VALUES (?, ?, ?, ?, ?, 'email')")) {
                ins.setInt(1, userId);
                ins.setInt(2, prescriptionId);
                ins.setBoolean(3, notify);
                ins.setInt(4, intervalHours);
                ins.setString(5, customMessage != null ? customMessage : "");
                ins.executeUpdate();
            }
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

    public void saveBookingReminder(int userId, boolean notify, int daysSinceLast, String customMessage) {
        try (Connection c = DB.connect()) {
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
