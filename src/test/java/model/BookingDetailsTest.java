package model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BookingDetailsTest {

    @Test
    void fields_areTrimmed_andBlankBecomesNull() {
        BookingDetails d = new BookingDetails(3, "  Sam ", "   ", null, " 0712 345-6789 ", " Sam@Example.COM ",
                "", "  queer ", "\t");
        assertEquals("Sam", d.firstName());
        assertNull(d.lastName());
        assertEquals("07123456789", d.phone());
        assertEquals("sam@example.com", d.email());
        assertNull(d.gender());
        assertEquals("queer", d.sexuality());
        assertNull(d.accessibilityNeeds());
        assertEquals("Sam", d.fullName());
    }

    @Test
    void longValues_areCutToColumnSize() {
        String longText = "x".repeat(800);
        BookingDetails d = new BookingDetails(1, longText, longText, null, null, null, longText, longText, longText);
        assertEquals(BookingDetails.MAX_NAME, d.firstName().length());
        assertEquals(BookingDetails.MAX_NAME, d.lastName().length());
        assertEquals(BookingDetails.MAX_GENDER, d.gender().length());
        assertEquals(BookingDetails.MAX_SEXUALITY, d.sexuality().length());
        assertEquals(BookingDetails.MAX_ACCESSIBILITY, d.accessibilityNeeds().length());
    }

    @Test
    void impossibleDateOfBirth_isDropped() {
        assertNull(new BookingDetails(1, "A", "B", LocalDate.now().plusDays(1), null, null, null, null, null).dob());
        assertNull(new BookingDetails(1, "A", "B", LocalDate.of(1800, 1, 1), null, null, null, null, null).dob());
        assertEquals(LocalDate.of(1990, 5, 6),
                new BookingDetails(1, "A", "B", LocalDate.of(1990, 5, 6), null, null, null, null, null).dob());
    }

    @Test
    void fromBook_emptyBooking_hasNoDetails() {
        assertTrue(BookingDetails.fromBook(new Book()).isEmpty());
        assertTrue(BookingDetails.fromBook(null).isEmpty());
    }

    @Test
    void fromBook_copiesTheDetailsStep() {
        Book b = new Book();
        b.setFirstName("Alex");
        b.setLastName("Taylor");
        b.setDob(LocalDate.of(2000, 1, 2));
        b.setPhone("07123456789");
        b.setEmail("alex@example.com");
        b.setGender("Non-binary");
        b.setSexuality("Bi");
        b.setDisabilities("Step-free access");
        b.setReason("goes to Appointment.notes, not here");

        BookingDetails d = BookingDetails.fromBook(b).orElseThrow();
        assertEquals(0, d.appId());
        assertEquals("Alex Taylor", d.fullName());
        assertEquals(LocalDate.of(2000, 1, 2), d.dob());
        assertEquals("Non-binary", d.gender());
        assertEquals("Bi", d.sexuality());
        assertEquals("Step-free access", d.accessibilityNeeds());
        assertEquals(42, d.withAppId(42).appId());
        assertEquals("Alex", d.withAppId(42).firstName());
    }

    @Test
    void toString_hidesPersonalDetails() {
        BookingDetails d = new BookingDetails(9, "Alex", "Taylor", LocalDate.of(2000, 1, 2), "07123456789",
                "alex@example.com", "Woman", "Lesbian", "Wheelchair");
        String s = d.toString();
        for (String secret : new String[]{"Alex", "Taylor", "2000", "07123456789", "alex@", "Woman", "Lesbian", "Wheelchair"}) {
            assertFalse(s.contains(secret), secret);
        }
        ClinicBookingDetails row = new ClinicBookingDetails(9, 1, null, null, null, "STI test", "CONFIRMED", "n", d);
        assertFalse(row.toString().contains("Alex"));
    }
}
