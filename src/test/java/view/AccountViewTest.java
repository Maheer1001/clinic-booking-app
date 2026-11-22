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
}
