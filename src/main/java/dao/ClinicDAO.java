package dao;

import app.DB;
import model.Clinic;
import model.Coordinate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClinicDAO implements ClinicRepository {

    private static final String SELECT = """
            SELECT clinic_id, name, address, postcode, phone, latitude, longitude, disability_accessibility
            FROM "Clinic"
            """;

    @Override
    public List<Clinic> findAll() {
        List<Clinic> clinics = new ArrayList<>();

        try (Connection c = DB.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(SELECT + " ORDER BY name, clinic_id")) {

            while (rs.next()) {
                clinics.add(map(rs));
            }

        } catch (SQLException e) {
            System.err.println("[ClinicDAO] Could not load clinics: " + e.getMessage());
        }

        return clinics;
    }

    @Override
    public Clinic findById(int clinicId) {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE clinic_id = ?")) {
            ps.setInt(1, clinicId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("[ClinicDAO] Could not load clinic " + clinicId + ": " + e.getMessage());
        }
        return null;
    }

    private static Clinic map(ResultSet rs) throws SQLException {
        return new Clinic(
                rs.getInt("clinic_id"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("postcode"),
                rs.getString("phone"),
                new ArrayList<>(),
                readCoordinate(rs),
                parseAccessibility(rs.getString("disability_accessibility"))
        );
    }

    /** Returns null when either coordinate is missing, rather than a bogus (0, 0) point. */
    private static Coordinate readCoordinate(ResultSet rs) throws SQLException {
        double lat = rs.getDouble("latitude");
        if (rs.wasNull()) return null;
        double lon = rs.getDouble("longitude");
        if (rs.wasNull()) return null;
        return new Coordinate(lat, lon);
    }

    /**
     * Parses the accessibility column, which is either a Postgres array literal
     * ({@code {"Step-free access",Hearing loop}}) or a plain comma-separated list.
     */
    static List<String> parseAccessibility(String raw) {
        List<String> features = new ArrayList<>();
        if (raw == null || raw.isBlank()) return features;
        String trimmed = raw.strip().replaceAll("^\\{|\\}$", "");
        for (String part : trimmed.split(",")) {
            String feature = part.strip().replaceAll("^\"|\"$", "");
            if (!feature.isBlank()) features.add(feature);
        }
        return features;
    }
}
