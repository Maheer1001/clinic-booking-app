package dao;

import app.DB;
import model.Prescription;
import model.PrescriptionSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionDAO implements PrescriptionRepository {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");

    @Override
    public List<PrescriptionSummary> getPrescriptionsByTestId(int testId) {
        String sql = """
            SELECT m.name, p.dosage, p.doses_per_day, p.days_between_doses,
                   p.start_date, p.end_date, p.instructions, p.actively_taking
            FROM "Prescription" p
            JOIN "Medicine" m ON p.medicine_id = m.medicine_id
            WHERE p.test_id = ?
            """;
        return getPrescriptionSummariesFromQuery(sql, testId, true);
    }

    @Override
    public List<PrescriptionSummary> getPrescriptionsByUserId(int userId) {
        String sql = """
            SELECT m.name, p.dosage, p.doses_per_day, p.days_between_doses,
                   p.start_date, p.end_date, p.instructions, p.actively_taking
            FROM "Prescription" p
            JOIN "Medicine" m ON p.medicine_id = m.medicine_id
            WHERE p.user_id = ?
            ORDER BY p.start_date DESC
            """;
        return getPrescriptionSummariesFromQuery(sql, userId, false);
    }

    @Override
    public List<Prescription> getPrescriptionsForRemindersByUserId(int userId) {
        String sql = """
            SELECT p.prescription_id, p.user_id, m.name AS medicine_name, p.dosage, p.start_date, p.end_date, p.doses_per_day
            FROM "Prescription" p
            JOIN "Medicine" m ON p.medicine_id = m.medicine_id
            WHERE p.user_id = ? AND (p.actively_taking = true OR p.actively_taking IS NULL) AND (p.end_date IS NULL OR p.end_date >= CURRENT_DATE)
            ORDER BY p.end_date DESC
            """;
        List<Prescription> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Prescription(
                            rs.getInt("prescription_id"),
                            rs.getInt("user_id"),
                            rs.getString("medicine_name"),
                            rs.getString("dosage"),
                            rs.getObject("start_date", java.time.LocalDate.class),
                            rs.getObject("end_date", java.time.LocalDate.class),
                            (Integer) rs.getObject("doses_per_day")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private List<PrescriptionSummary> getPrescriptionSummariesFromQuery(String sql, int param, boolean isTestId) {
        List<PrescriptionSummary> list = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var startDate = rs.getObject("start_date", java.time.LocalDate.class);
                    var endDate = rs.getObject("end_date", java.time.LocalDate.class);
                    String startStr = startDate != null ? startDate.format(DATE_FMT) : "";
                    String endStr = endDate != null ? endDate.format(DATE_FMT) : "";
                    list.add(new PrescriptionSummary(
                            rs.getString("name"),
                            rs.getString("dosage"),
                            rs.getInt("doses_per_day"),
                            rs.getInt("days_between_doses"),
                            startStr,
                            endStr,
                            rs.getString("instructions") != null ? rs.getString("instructions") : "",
                            endDate == null || endDate.isAfter(LocalDate.now()))); //rs.getBoolean("actively_taking")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public String getMedicineByPrescriptionId(int prescriptionId) {
        String sql = "SELECT m.name FROM \"Medicine\" m JOIN \"Prescription\" p ON p.medicine_id = m.medicine_id WHERE p.prescription_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, prescriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
