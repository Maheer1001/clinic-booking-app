package model;

import service.AccountService;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Personal details given on the booking "Your details" step, stored once per appointment
 * (table "BookingDetails"). Every text field is trimmed, blank becomes null, and values are
 * cut to the column size so a long entry can never make the booking itself fail.
 *
 * Sensitive: {@link #toString()} deliberately hides the values so they never end up in a log.
 */
public record BookingDetails(int appId, String firstName, String lastName, LocalDate dob, String phone,
                             String email, String gender, String sexuality, String accessibilityNeeds) {

    public static final int MAX_NAME = 50;
    public static final int MAX_PHONE = 20;
    public static final int MAX_EMAIL = 255;
    public static final int MAX_GENDER = 100;
    public static final int MAX_SEXUALITY = 100;
    public static final int MAX_ACCESSIBILITY = 500;

    public BookingDetails {
        firstName = clean(firstName, MAX_NAME);
        lastName = clean(lastName, MAX_NAME);
        // Keep only a date of birth that could be real (same rule as the Account page).
        dob = dob != null && AccountService.isValidDob(dob) ? dob : null;
        phone = clean(phone == null ? null : phone.replaceAll("[\\s-]", ""), MAX_PHONE);
        email = clean(email == null ? null : email.toLowerCase(), MAX_EMAIL);
        gender = clean(gender, MAX_GENDER);
        sexuality = clean(sexuality, MAX_SEXUALITY);
        accessibilityNeeds = clean(accessibilityNeeds, MAX_ACCESSIBILITY);
    }

    /**
     * The details held on a booking in progress, not yet tied to an appointment (appId 0), or empty when
     * nothing was entered (e.g. the legacy single-slot path).
     */
    public static Optional<BookingDetails> fromBook(Book b) {
        if (b == null) return Optional.empty();
        BookingDetails d = new BookingDetails(0, b.getFirstName(), b.getLastName(), b.getDob(), b.getPhone(),
                b.getEmail(), b.getGender(), b.getSexuality(), b.getDisabilities());
        return d.isEmpty() ? Optional.empty() : Optional.of(d);
    }

    /** The same details for a saved appointment. */
    public BookingDetails withAppId(int newAppId) {
        return new BookingDetails(newAppId, firstName, lastName, dob, phone, email, gender, sexuality,
                accessibilityNeeds);
    }

    public boolean isEmpty() {
        return firstName == null && lastName == null && dob == null && phone == null && email == null
                && gender == null && sexuality == null && accessibilityNeeds == null;
    }

    /** "First Last", or whichever part is present, or "". */
    public String fullName() {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        return lastName != null ? lastName : "";
    }

    @Override
    public String toString() {
        return "BookingDetails[appId=" + appId + ", <personal details hidden>]";
    }

    static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max).strip() : t;
    }
}
