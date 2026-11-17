package dao;

import app.DB;
import model.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceDAO implements ServiceRepository {

    @Override
    public List<Service> findByClinicId(int clinicId) {
        List<Service> services = new ArrayList<>();

        String sql = """
            SELECT s.service_id, s.name, s.description, s.duration_minutes
            FROM "Service" s
            JOIN "ClinicService" cs ON s.service_id = cs.service_id
            WHERE cs.clinic_id = ?
        """;

        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, clinicId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Service s = new Service(
                            rs.getInt("service_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getInt("duration_minutes")
                    );
                    services.add(s);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return services;
    }
}
