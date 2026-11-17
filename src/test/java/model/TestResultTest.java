package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestResultTest {

    @Test
    void isPositive_returnsTrue_whenPositive() {
        TestResult result = new TestResult("Chlamydia", TestResultStatus.POSITIVE);
        assertTrue(result.isPositive());
        assertFalse(result.isNegative());
        assertFalse(result.isPending());
    }

    @Test
    void isNegative_returnsTrue_whenNegative() {
        TestResult result = new TestResult("HIV", TestResultStatus.NEGATIVE);
        assertFalse(result.isPositive());
        assertTrue(result.isNegative());
        assertFalse(result.isPending());
    }

    @Test
    void isPending_returnsTrue_whenPending() {
        TestResult result = new TestResult("Syphilis", TestResultStatus.PENDING);
        assertFalse(result.isPositive());
        assertFalse(result.isNegative());
        assertTrue(result.isPending());
    }

    @Test
    void recordAccessors_returnCorrectValues() {
        TestResult result = new TestResult("Gonorrhoea", TestResultStatus.NEGATIVE);
        assertEquals("Gonorrhoea", result.condition());
        assertEquals(TestResultStatus.NEGATIVE, result.result());
    }
}
