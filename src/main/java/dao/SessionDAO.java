package dao;

import app.DB;
import model.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Data access for user sessions.
 */
public class SessionDAO {

    public void save(Session session) {
        String sql = """
            INSERT INTO "Session" (session_id, user_id, created_at, expires_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (session_id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, session.getSessionId());
            ps.setInt(2, session.getUserId());
            ps.setObject(3, session.getCreatedAt() != null ? java.sql.Timestamp.from(session.getCreatedAt()) : null);
            ps.setObject(4, session.getExpiresAt() != null ? java.sql.Timestamp.from(session.getExpiresAt()) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(String sessionId) {
        String sql = "DELETE FROM \"Session\" WHERE session_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Optional<Session> findById(String sessionId) {
        String sql = """
            SELECT session_id, user_id, created_at, expires_at
            FROM "Session"
            WHERE session_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                int userId = rs.getInt("user_id");
                Instant createdAt = rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toInstant() : null;
                Instant expiresAt = rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toInstant() : null;
                // User would need to be loaded from UserDAO - for now we pass null; caller should set user
                var userDAO = new UserDAO();
                var user = userDAO.findById(userId).orElse(null);
                return Optional.of(new Session(sessionId, userId, user, createdAt, expiresAt));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
