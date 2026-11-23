package view;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AccountFormStateTest {

    private static final LocalDate DOB = LocalDate.of(1990, 5, 1);
    private final AccountFormState saved = new AccountFormState("Alex", "Q", "07700900123", DOB);

    @Test
    void sameValuesAreNotAChange() {
        assertFalse(new AccountFormState("Alex", "Q", "07700900123", DOB).differsFrom(saved));
    }

    @Test
    void surroundingSpacesAreNotAChange() {
        assertFalse(new AccountFormState("  Alex ", "Q ", " 07700900123", DOB).differsFrom(saved));
    }

    @Test
    void nullAndEmptyAreTheSame() {
        assertFalse(new AccountFormState(null, null, null, null).differsFrom(new AccountFormState("", " ", "", null)));
    }

    @Test
    void editingAnyFieldIsAChange() {
        assertTrue(new AccountFormState("Sam", "Q", "07700900123", DOB).differsFrom(saved));
        assertTrue(new AccountFormState("Alex", "R", "07700900123", DOB).differsFrom(saved));
        assertTrue(new AccountFormState("Alex", "Q", "", DOB).differsFrom(saved));
        assertTrue(new AccountFormState("Alex", "Q", "07700900123", DOB.plusDays(1)).differsFrom(saved));
        assertTrue(new AccountFormState("Alex", "Q", "07700900123", null).differsFrom(saved), "clearing the date is a change");
    }

    @Test
    void editingBackToTheSavedValueIsNotAChange() {
        AccountFormState edited = new AccountFormState("Alexa", "Q", "07700900123", DOB);
        assertTrue(edited.differsFrom(saved));
        assertFalse(new AccountFormState("Alex", "Q", "07700900123", DOB).differsFrom(saved));
    }

    @Test
    void dateOfBirthIsOptional() {
        AccountFormState noDob = new AccountFormState("Alex", "Q", "", null);
        assertTrue(noDob.dobOk());
        assertTrue(noDob.canSave());
        assertEquals("", noDob.hint());
    }

    @Test
    void futureDateOfBirthBlocksSave() {
        AccountFormState s = new AccountFormState("Alex", "Q", "", LocalDate.now().plusDays(1));
        assertFalse(s.canSave());
        assertEquals(AccountFormState.DOB_HINT, s.hint());
    }

    @Test
    void phoneMustBeElevenDigitsOrEmpty() {
        assertTrue(new AccountFormState("", "", "", null).phoneOk());
        assertTrue(new AccountFormState("", "", "07700900123", null).phoneOk());
        AccountFormState partial = new AccountFormState("", "", "0770", null);
        assertFalse(partial.canSave());
        assertEquals(AccountFormState.PHONE_HINT, partial.hint());
    }

    @Test
    void bothProblemsAreExplained() {
        AccountFormState s = new AccountFormState("", "", "0770", LocalDate.now().plusYears(1));
        assertTrue(s.hint().contains(AccountFormState.PHONE_HINT));
        assertTrue(s.hint().contains(AccountFormState.DOB_HINT));
    }
}
