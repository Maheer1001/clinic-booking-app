package view;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountViewTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    private static TextField field(int max) {
        TextField f = new TextField();
        f.setTextFormatter(AccountView.maxLength(max));
        return f;
    }

    @Test
    void overLongPasteIsCutToFitInsteadOfDropped() {
        TextField f = field(5);
        f.replaceSelection("abcdefgh");
        assertEquals("abcde", f.getText());
    }

    @Test
    void pasteIntoPartlyFilledFieldKeepsWhatFits() {
        TextField f = field(5);
        f.setText("ab");
        f.positionCaret(2);
        f.replaceSelection("cdefg");
        assertEquals("abcde", f.getText());
    }

    @Test
    void typingPastTheLimitIsIgnored() {
        TextField f = field(3);
        f.setText("abc");
        f.positionCaret(3);
        f.replaceSelection("d");
        assertEquals("abc", f.getText());
    }

    @Test
    void textWithinTheLimitIsUnchanged() {
        TextField f = field(10);
        f.setText("Qa");
        assertEquals("Qa", f.getText());
    }

    private static TextField phoneField() {
        TextField f = new TextField();
        f.setTextFormatter(AccountView.phoneDigits());
        return f;
    }

    @Test
    void pastedPhoneNumberWithSpacesKeepsItsDigits() {
        TextField f = phoneField();
        f.replaceSelection("07700 900-123");
        assertEquals("07700900123", f.getText());
    }

    @Test
    void pastedPhoneNumberReplacesTheSelection() {
        TextField f = phoneField();
        f.replaceSelection("0770");
        f.selectAll();
        f.replaceSelection("(01632) 960 001");
        assertEquals("01632960001", f.getText());
    }

    @Test
    void phoneRejectsLettersAndMoreThanElevenDigits() {
        TextField f = phoneField();
        f.replaceSelection("abc");
        assertEquals("", f.getText());
        f.replaceSelection("+44 7700 900123");
        assertEquals("", f.getText(), "12 digits do not fit, so nothing is pasted");
        f.replaceSelection("07700900123");
        f.replaceSelection("4");
        assertEquals("07700900123", f.getText());
    }
}
