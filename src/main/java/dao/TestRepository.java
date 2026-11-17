package dao;

import model.Test;
import model.TestResult;

import java.util.List;

/**
 * Contract for test result data access.
 */
public interface TestRepository {
    List<Test> getTestsByUserId(int userId);
    List<TestResult> getTestResultsByTestId(int testId);
}
