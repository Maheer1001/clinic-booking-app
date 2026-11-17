package dao;

import app.DB;
import model.Clinic;
import model.Coordinate;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClinicDAO implements ClinicRepository {

    @Override
    public List<Clinic> findAll() {
        List<Clinic> clinics = new ArrayList<>();

        String sql = """
            SELECT clinic_id, name, address, postcode, phone, latitude, longitude, disability_accessibility
            FROM "Clinic"
        """;

        try (Connection c = DB.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {

            while (rs.next()) {
                String raw = rs.getString("disability_accessibility");
                List<String> disabilities = new ArrayList<>();
                if (raw != null && !raw.isBlank()) {
                    String trimmed = raw.replaceAll("^\\{|\\}$", "");
                    if (!trimmed.isBlank()) {
                        for (String part : trimmed.split(",")) {
                            disabilities.add(part.strip().replaceAll("^\"|\"$", ""));
                        }
                    }
                }
                Clinic clinic = new Clinic(
                        rs.getInt("clinic_id"),
                        rs.getString("name"),
                        rs.getString("address"),
                        rs.getString("postcode"),
                        rs.getString("phone"),
                        new ArrayList<>(),
                        new Coordinate(rs.getDouble("latitude"), rs.getDouble("longitude")),
                        disabilities
                );
                clinics.add(clinic);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return clinics;
    }

    @Override
    public Clinic findById(int clinicId) {
        String sql = """
        SELECT clinic_id, name, address, postcode, phone, latitude, longitude, disability_accessibility
        FROM "Clinic"
        WHERE clinic_id = ?
    """;

        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Clinic(
                        rs.getInt("clinic_id"),
                        rs.getString("name"),
                        rs.getString("address"),
                        rs.getString("postcode"),
                        rs.getString("phone"),
                        new ArrayList<>(),
                        new Coordinate(rs.getDouble("latitude"), rs.getDouble("longitude")),
                        new ArrayList<>()
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
