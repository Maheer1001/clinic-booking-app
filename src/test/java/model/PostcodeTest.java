package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostcodeTest {

    @Test
    void testExactMatchReturnsFullLength() {
        assertEquals(6, Postcode.score("AB12CD", "AB12CD"));
    }

    @Test
    void testCaseInsensitive() {
        assertEquals(6, Postcode.score("ab12cd", "AB12CD"));
    }

    @Test
    void testPartialMatchReturnsLess() {
        int score = Postcode.score("AB12CD", "AB34CD");
        assertEquals(2, score); // "AB" matches, then diverges at position 2
        assertTrue(score < 6);
    }

    @Test
    void testNoMatchReturnsZero() {
        assertEquals(0, Postcode.score("AB12CD", "XY98ZZ"));
    }

    @Test
    void testNullInputs() {
        assertEquals(0, Postcode.score(null, "AB12CD"));
        assertEquals(0, Postcode.score("AB12CD", null));
    }

    @Test
    void testWithSpaces() {
        assertEquals(6, Postcode.score("AB1 2CD", "AB12CD"));
    }
}
