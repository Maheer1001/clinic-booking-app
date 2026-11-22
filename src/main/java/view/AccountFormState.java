package view;

import service.AccountService;

import java.time.LocalDate;

/**
 * The editable account details on the Settings page (no JavaFX, so the rules can be unit tested):
 * what counts as a change worth asking about before leaving, and what blocks Save.
 * The email is not part of this: it is read-only on Settings and changed through "Change email".
 */
public record AccountFormState(String firstName, String lastName, String phone, LocalDate dob) {

    public static final String PHONE_HINT = "Phone number must be 11 digits, or leave it empty.";
    public static final String DOB_HINT = "Choose a date of birth that is not in the future, or clear it.";

    /** Trims and treats blank as empty, so typing a space or "Alex " vs "Alex" is not a change. */
    public AccountFormState {
        firstName = clean(firstName);
        lastName = clean(lastName);
        phone = clean(phone);
    }

    /** True when these values differ from {@code saved} (what was loaded or last saved). */
    public boolean differsFrom(AccountFormState saved) {
        return saved == null || !equals(saved);
    }

    public boolean phoneOk() {
        return phone.isEmpty() || phone.matches("\\d{11}");
    }

    /** Date of birth is optional: empty is fine, a chosen date must be a real one. */
    public boolean dobOk() {
        return dob == null || AccountService.isValidDob(dob);
    }

    public boolean canSave() {
        return phoneOk() && dobOk();
    }

    /** Why Save is disabled, or "" when it is not. */
    public String hint() {
        if (!phoneOk() && !dobOk()) return PHONE_HINT + " " + DOB_HINT;
        if (!phoneOk()) return PHONE_HINT;
        if (!dobOk()) return DOB_HINT;
        return "";
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
