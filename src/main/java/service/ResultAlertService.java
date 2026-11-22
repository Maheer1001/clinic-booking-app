package service;

import dao.TestDAO;
import dao.UserDAO;
import model.NewResults;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * The patient's "new result" alert, from the database instead of an in-memory count, so results
 * released while logged out, and PENDING -> POSITIVE/NEGATIVE changes, are announced too.
 *
 * <p>Use (off the JavaFX thread):
 * <pre>
 *   NewResults n = alerts.newResults(userId);     // released since the patient last looked
 *   if (!n.isEmpty()) { ...show the in-app alert (never the result itself)... }
 *   // once the patient has seen the Tracking list:
 *   alerts.markSeen(userId, n.watermark());
 * </pre>
 * Pass the watermark that came with the results shown, not "now": a result released in between is
 * then still new next time.
 */
public class ResultAlertService {

    /** Reads for {@link ResultAlertService}; TestDAO and UserDAO in production. */
    public interface Repository {
        NewResults findResultsSinceLastSeen(int userId) throws SQLException;

        boolean setLastResultsSeenAt(int userId, LocalDateTime seenUpTo) throws SQLException;
    }

    private final Repository repository;

    public ResultAlertService() {
        this(new Repository() {
            private final TestDAO tests = new TestDAO();
            private final UserDAO users = new UserDAO();

            @Override
            public NewResults findResultsSinceLastSeen(int userId) throws SQLException {
                return tests.findResultsSinceLastSeen(userId);
            }

            @Override
            public boolean setLastResultsSeenAt(int userId, LocalDateTime seenUpTo) throws SQLException {
                return users.setLastResultsSeenAt(userId, seenUpTo);
            }
        });
    }

    public ResultAlertService(Repository repository) {
        this.repository = repository;
    }

    /** New results for the patient; empty (never an exception) if the database can't be read. */
    public NewResults newResults(int userId) {
        try {
            return repository.findResultsSinceLastSeen(userId);
        } catch (SQLException e) {
            System.err.println("[ResultAlert] Could not check for new results (SQLState " + e.getSQLState() + ")");
            return new NewResults(java.util.List.of(), null);
        }
    }

    /** Records that results up to {@code watermark} have been seen. Null (nothing new) does nothing. */
    public boolean markSeen(int userId, LocalDateTime watermark) {
        if (watermark == null) return false;
        try {
            return repository.setLastResultsSeenAt(userId, watermark);
        } catch (SQLException e) {
            System.err.println("[ResultAlert] Could not record results as seen (SQLState " + e.getSQLState() + ")");
            return false;
        }
    }
}
