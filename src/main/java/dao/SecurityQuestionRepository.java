package dao;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Storage for a user's security question, hashed answer and failed-answer lockout ("User" columns). */
public interface SecurityQuestionRepository {

    /**
     * What is stored for the user. Empty only when the user does not exist; a user without a question
     * has null {@code questionKey}/{@code answerHash}.
     */
    Optional<Stored> find(int userId) throws SQLException;

    /** Sets the question and answer hash and clears any failed attempts / lock. */
    boolean save(int userId, String questionKey, String answerHash) throws SQLException;

    /** Records the failed-attempt count and lock time (null = not locked). */
    void recordAttempts(int userId, int failedAttempts, LocalDateTime lockedUntil) throws SQLException;

    record Stored(String questionKey, String answerHash, int failedAttempts, LocalDateTime lockedUntil) {
        @Override
        public String toString() {
            return "Stored[questionKey=" + questionKey + ", failedAttempts=" + failedAttempts
                    + ", lockedUntil=" + lockedUntil + "]";
        }
    }
}
