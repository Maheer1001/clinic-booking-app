package dao;

import app.DB;
import model.Test;
import model.TestResult;
import model.TestResultStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
     */
    @Override
    public List<Test> getTestsByUserId(int userId) {
        String testSql = """
            SELECT test_id, test_type, test_date, clinician_notes, treatment_instructions
            FROM "Test"
            WHERE user_id = ?
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
