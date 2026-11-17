package view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClinicMapTest {

    @Test
    void jsStringEscapesQuotesBackslashesAndLineBreaks() {
        assertEquals("'St John\\'s \\\\ Clinic'", ClinicMap.jsString("St John's \\ Clinic"));
        assertEquals("'a\\nb\\rc'", ClinicMap.jsString("a\nb\rc"));
        assertEquals("'x\\u2028y'", ClinicMap.jsString("x y"));
        assertEquals("''", ClinicMap.jsString(null));
    }

    @Test
    void jsStringLeavesHtmlForThePageToEscape() {
        // Names are HTML-escaped in the page; escaping here as well would show "&amp;" to users.
        assertEquals("'A & B <Clinic>'", ClinicMap.jsString("A & B <Clinic>"));
    }
}
