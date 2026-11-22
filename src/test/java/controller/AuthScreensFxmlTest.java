package controller;

import app.Data;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import model.SecurityQuestion;
import service.AuthService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads each authentication screen and checks the wiring that makes the forms usable:
 * the FXML parses, required buttons stay disabled until the form can be submitted,
 * and the password rules are shown up front.
 */
class AuthScreensFxmlTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(work.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private static Parent load(String path) throws Exception {
        return new FXMLLoader(AuthScreensFxmlTest.class.getResource(path)).load();
    }

    @Test
    void allAuthScreensLoad() throws Exception {
        for (String path : new String[]{"/fxml/login.fxml", "/fxml/signup.fxml", "/fxml/password-reset.fxml",
                "/fxml/terms.fxml", "/fxml/privacy.fxml"}) {
            assertNotNull(onFx(() -> load(path)), path);
        }
    }

    @Test
    void login_buttonEnabledOnlyWithEmailAndPassword() throws Exception {
        onFx(() -> {
            Parent root = load("/fxml/login.fxml");
            Button btn = (Button) root.lookup("#continueBtn");
            TextField email = (TextField) root.lookup("#emailField");
            TextField password = (TextField) root.lookup("#passwordField");
            assertTrue(btn.isDisabled());
            assertTrue(btn.isDefaultButton(), "Enter should submit the login form");
            email.setText("a@b.com");
            assertTrue(btn.isDisabled());
            password.setText("x");
            assertFalse(btn.isDisabled());
            return null;
        });
    }

    @Test
    void login_wrongPasswordMessageStaysVisible() throws Exception {
        onFx(() -> {
            FXMLLoader loader = new FXMLLoader(AuthScreensFxmlTest.class.getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            TextField email = (TextField) root.lookup("#emailField");
            TextField password = (TextField) root.lookup("#passwordField");
            Label message = (Label) root.lookup("#messageLabel");
            email.setText("a@b.com");
            password.setText("wrong-password1");

            controller.showBadCredentials();

            assertEquals("", password.getText(), "the wrong password is cleared");
            assertEquals(LoginController.BAD_CREDENTIALS_MESSAGE, message.getText(),
                    "clearing the password must not also clear the error");
            assertTrue(message.isVisible());
            return null;
        });
    }

    @Test
    void signup_showsPasswordRules_andNeedsTermsBeforeSubmit() throws Exception {
        Data.SignUpDraft d = Data.getSignUpDraft();
        d.email = ""; d.password = ""; d.confirmPassword = "";
        d.termsAccepted = false; d.privacyAccepted = false;
        d.termsReadToEnd = true; d.privacyReadToEnd = true;
        try {
            onFx(() -> {
                Parent root = load("/fxml/signup.fxml");
                Label rules = (Label) root.lookup("#passwordRulesLabel");
                assertEquals(AuthService.PASSWORD_RULES, rules.getText());

                Button btn = (Button) root.lookup("#createBtn");
                ((TextField) root.lookup("#emailField")).setText("new@example.com");
                ((TextField) root.lookup("#passwordField")).setText("Password1");
                TextField confirm = (TextField) root.lookup("#confirmPasswordField");
                confirm.setText("Password2");
                Label confirmError = (Label) root.lookup("#confirmErrorLabel");
                assertFalse(confirmError.getText().isEmpty(), "mismatch should be flagged inline");
                confirm.setText("Password1");
                assertTrue(confirmError.getText().isEmpty());

                assertTrue(btn.isDisabled(), "terms not ticked yet");
                ((CheckBox) root.lookup("#termsCheck")).setSelected(true);
                ((CheckBox) root.lookup("#privacyCheck")).setSelected(true);
                assertTrue(btn.isDisabled(), "no security question yet");

                @SuppressWarnings("unchecked")
                ComboBox<SecurityQuestion> question = (ComboBox<SecurityQuestion>) root.lookup("#questionBox");
                assertEquals(SecurityQuestion.all(), question.getItems());
                question.setValue(SecurityQuestion.FIRST_PET);
                assertEquals("FIRST_PET", d.securityQuestionKey, "kept while reading the Terms");
                assertTrue(btn.isDisabled(), "no answer yet");
                TextField answer = (TextField) root.lookup("#answerField");
                answer.setText("x");
                assertFalse(btn.isDisabled());
                return null;
            });
        } finally {
            d.email = ""; d.password = ""; d.confirmPassword = "";
            d.termsAccepted = false; d.privacyAccepted = false;
            d.termsReadToEnd = false; d.privacyReadToEnd = false;
            d.securityQuestionKey = null; d.securityAnswer = "";
        }
    }

    @Test
    void signup_staffOptionRevealsTheCodeAndAMissingCodeIsShownInline() throws Exception {
        Data.SignUpDraft d = Data.getSignUpDraft();
        d.termsReadToEnd = true; d.privacyReadToEnd = true;
        try {
            Parent root = onFx(() -> {
                Parent r = load("/fxml/signup.fxml");
                CheckBox staff = (CheckBox) r.lookup("#staffCheck");
                javafx.scene.Node box = r.lookup("#staffBox");
                assertFalse(staff.isSelected());
                assertFalse(box.isVisible() || box.isManaged(), "the code field is hidden for patients");
                staff.setSelected(true);
                assertTrue(box.isVisible() && box.isManaged());
                assertTrue(d.staff, "kept while reading the Terms");
                ((TextField) r.lookup("#staffCodeField")).setText("clinic9");
                assertEquals("clinic9", d.staffCode);
                return r;
            });
            // coming back from the Terms screen rebuilds the form from the draft
            onFx(() -> {
                Parent again = load("/fxml/signup.fxml");
                assertTrue(((CheckBox) again.lookup("#staffCheck")).isSelected());
                assertEquals("clinic9", ((TextField) again.lookup("#staffCodeField")).getText());
                return null;
            });

            // every other field valid and no code: the service's own message, before any database work
            onFx(() -> {
                ((TextField) root.lookup("#staffCodeField")).setText("  ");
                ((TextField) root.lookup("#emailField")).setText("new@example.com");
                ((TextField) root.lookup("#passwordField")).setText("Password1");
                ((TextField) root.lookup("#confirmPasswordField")).setText("Password1");
                ((CheckBox) root.lookup("#termsCheck")).setSelected(true);
                ((CheckBox) root.lookup("#privacyCheck")).setSelected(true);
                @SuppressWarnings("unchecked")
                ComboBox<SecurityQuestion> question = (ComboBox<SecurityQuestion>) root.lookup("#questionBox");
                question.setValue(SecurityQuestion.FIRST_PET);
                ((TextField) root.lookup("#answerField")).setText("Rex the dog");
                Button create = (Button) root.lookup("#createBtn");
                assertFalse(create.isDisabled());
                create.fire();
                return null;
            });
            Label message = (Label) root.lookup("#messageLabel");
            String expected = service.StaffAuthService.Status.MISSING_STAFF_CODE.message();
            long until = System.currentTimeMillis() + 10_000;
            while (!expected.equals(onFx(message::getText)) && System.currentTimeMillis() < until) {
                Thread.sleep(50);
            }
            assertEquals(expected, onFx(message::getText));
        } finally {
            d.email = ""; d.password = ""; d.confirmPassword = "";
            d.termsAccepted = false; d.privacyAccepted = false;
            d.termsReadToEnd = false; d.privacyReadToEnd = false;
            d.securityQuestionKey = null; d.securityAnswer = "";
            d.staff = false; d.staffCode = "";
        }
    }

    @Test
    void passwordReset_codeFieldAcceptsSixDigitsOnly() throws Exception {
        onFx(() -> {
            Parent root = load("/fxml/password-reset.fxml");
            TextField otp = (TextField) root.lookup("#otpField");
            Button verify = (Button) root.lookup("#verifyOtpBtn");
            otp.setText("12ab");
            assertEquals("", otp.getText(), "letters are rejected");
            otp.setText("12345");
            assertTrue(verify.isDisabled());
            otp.setText("123456");
            assertFalse(verify.isDisabled());
            otp.setText("1234567");
            assertEquals("123456", otp.getText());
            return null;
        });
    }

    @Test
    void terms_returnButtonIsAlwaysUsable() throws Exception {
        onFx(() -> {
            Parent root = load("/fxml/terms.fxml");
            assertFalse(root.lookup("#returnBtn").isDisabled(),
                    "user must never be stuck on the terms screen");
            return null;
        });
    }
}
