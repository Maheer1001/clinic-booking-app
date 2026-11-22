package dao;

import app.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

/** Security question columns on "User". */
public class SecurityQuestionDAO implements SecurityQuestionRepository {

    @Override
    public Optional<Stored> find(int userId) throws SQLException {
        String sql = """
            SELECT security_question, security_answer_hash, security_failed_attempts, security_locked_until
            FROM "User" WHERE user_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp locked = rs.getTimestamp("security_locked_until");
                return Optional.of(new Stored(
                        rs.getString("security_question"),
                        rs.getString("security_answer_hash"),
                        rs.getInt("security_failed_attempts"),
                        locked == null ? null : locked.toLocalDateTime()));
            }
        }
    }

    @Override
    public boolean save(int userId, String questionKey, String answerHash) throws SQLException {
        String sql = """
            UPDATE "User"
            SET security_question = ?, security_answer_hash = ?, security_failed_attempts = 0,
                security_locked_until = NULL
            WHERE user_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, questionKey);
            ps.setString(2, answerHash);
            ps.setInt(3, userId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public void recordAttempts(int userId, int failedAttempts, LocalDateTime lockedUntil) throws SQLException {
        String sql = """
            UPDATE "User" SET security_failed_attempts = ?, security_locked_until = ? WHERE user_id = ?
            """;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, failedAttempts);
            if (lockedUntil == null) ps.setNull(2, Types.TIMESTAMP);
            else ps.setTimestamp(2, Timestamp.valueOf(lockedUntil));
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }
}
