package dao;

import model.AuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Appends to "AuditLog". Only ever inserts: there is no update or delete (and on PostgreSQL a
 * trigger rejects them). Runs on the caller's connection so a write and its audit row share a
 * transaction.
 */
public final class AuditLogDAO {

    private static final String INSERT = """
        INSERT INTO "AuditLog" (staff_user_id, clinic_id, action, entity, entity_id, patient_user_id)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private AuditLogDAO() {}

    public static void insert(Connection c, AuditEntry e) throws SQLException {
        insertAll(c, List.of(e));
    }

    public static void insertAll(Connection c, List<AuditEntry> entries) throws SQLException {
        if (entries.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(INSERT)) {
            for (AuditEntry e : entries) {
                ps.setInt(1, e.staffUserId());
                ps.setInt(2, e.clinicId());
                ps.setString(3, e.action().name());
                ps.setString(4, e.entity());
                setInt(ps, 5, e.entityId());
                setInt(ps, 6, e.patientUserId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER);
        else ps.setInt(i, v);
    }
}
