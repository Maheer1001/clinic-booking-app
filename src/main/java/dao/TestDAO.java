package dao;

import app.DB;
import model.NewResults;
import model.Test;
import model.TestResult;
import model.TestResultStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TestDAO implements TestRepository {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");

    /**
     * The user's tests, most recently added first. The new-result notification treats the first
     * entries as the new ones, so this is by id rather than test date: ordering by date put a test
     * with no date (NULLs sort first) or a back-dated one ahead of the one just added, and the wrong
     * test, or none, was announced. The tracking list sorts by date itself.
     *
     * Only released tests: a draft (released_at NULL) is the clinic's work in progress and must not
     * reach the patient until a clinician releases it.
     */
    @Override
    public List<Test> getTestsByUserId(int userId) {
        String testSql = """
            SELECT test_id, test_type, test_date, clinician_notes, treatment_instructions
            FROM "Test"
            WHERE user_id = ? AND released_at IS NOT NULL
            ORDER BY test_id DESC
            """;
        List<Test> tests = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(testSql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int testId = rs.getInt("test_id");
                    String testDate = formatTestDate(rs.getObject("test_date", java.time.LocalDate.class));
                    String treatment = rs.getString("treatment_instructions");
                    String clinicianNotes = rs.getString("clinician_notes");
                    List<TestResult> results = getTestResultsByTestId(c, testId);
                    tests.add(new Test(
                            rs.getString("test_type"),
                            testDate,
                            treatment,
                            clinicianNotes,
                            results));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tests;
    }

    @Override
    public List<TestResult> getTestResultsByTestId(int testId) {
        try (Connection c = DB.connect()) {
            return getTestResultsByTestId(c, testId);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private List<TestResult> getTestResultsByTestId(Connection c, int testId) throws SQLException {
        String sql = "SELECT condition, result FROM \"TestResult\" WHERE test_id = ? ORDER BY result_id";
        List<TestResult> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TestResultStatus status = parseResult(rs.getString("result"));
                    list.add(new TestResult(rs.getString("condition"), status));
                }
            }
        }
        return list;
    }

    /**
     * Released tests the patient has not seen: released after "User".last_results_seen_at, or released
     * earlier with a result changed to a final POSITIVE/NEGATIVE after both the release and the last
     * view (e.g. PENDING -> POSITIVE). Never-seen (NULL) counts every released test. Newest first.
     */
    public NewResults findResultsSinceLastSeen(int userId) throws SQLException {
        String sql = """
            SELECT t.test_id, t.clinic_id, t.test_type, t.test_date, t.released_at, u.last_results_seen_at,
                   (SELECT MAX(r.updated_at) FROM "TestResult" r
                     WHERE r.test_id = t.test_id AND r.updated_at > t.released_at
                       AND UPPER(r.result) IN ('POSITIVE', 'NEGATIVE')) AS final_changed_at
            FROM "Test" t
            JOIN "User" u ON u.user_id = t.user_id
            WHERE t.user_id = ? AND t.released_at IS NOT NULL
            ORDER BY t.released_at DESC, t.test_id DESC
            """;
        List<NewResults.Entry> entries = new ArrayList<>();
        LocalDateTime watermark = null;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime released = rs.getTimestamp("released_at").toLocalDateTime();
                    Timestamp seenTs = rs.getTimestamp("last_results_seen_at");
                    Timestamp changedTs = rs.getTimestamp("final_changed_at");
                    LocalDateTime seen = seenTs == null ? null : seenTs.toLocalDateTime();
                    LocalDateTime finalChanged = changedTs == null ? null : changedTs.toLocalDateTime();
                    LocalDateTime latest = finalChanged != null && finalChanged.isAfter(released) ? finalChanged : released;

                    boolean newRelease = seen == null || released.isAfter(seen);
                    boolean newResult = !newRelease && finalChanged != null && finalChanged.isAfter(seen);
                    if (!newRelease && !newResult) continue;

                    Date testDate = rs.getDate("test_date");
                    int clinicId = rs.getInt("clinic_id");
                    entries.add(new NewResults.Entry(rs.getInt("test_id"), rs.wasNull() ? 0 : clinicId,
                            rs.getString("test_type"), testDate == null ? null : testDate.toLocalDate(),
                            latest, newResult));
                    if (watermark == null || latest.isAfter(watermark)) watermark = latest;
                }
            }
        }
        return new NewResults(entries, watermark);
    }

    private static String formatTestDate(java.time.LocalDate d) {
        if (d == null) return "";
        return d.format(DATE_FMT);
    }

    private static TestResultStatus parseResult(String result) {
        if (result == null) return TestResultStatus.PENDING;
        return switch (result.toUpperCase()) {
            case "POSITIVE" -> TestResultStatus.POSITIVE;
            case "NEGATIVE" -> TestResultStatus.NEGATIVE;
            default -> TestResultStatus.PENDING;
        };
    }
}
