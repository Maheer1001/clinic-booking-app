package controller;

import app.Data;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import service.AuthService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import view.ui.UI;

public class SignUpController {

    @FXML private TextField emailField;
    @FXML private Label emailErrorLabel;
    @FXML private PasswordField passwordField;
    @FXML private Label passwordRulesLabel;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label confirmErrorLabel;
    @FXML private CheckBox termsCheck;
    @FXML private CheckBox privacyCheck;
    @FXML private Label termsHintLabel;
    @FXML private Button createBtn;
    @FXML private Label messageLabel;

    private final AuthService authService = new AuthService();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    @FXML
    private void initialize() {
        // Error labels take no space until there is a message to show.
        hideWhenEmpty(messageLabel);
        hideWhenEmpty(emailErrorLabel);
        hideWhenEmpty(confirmErrorLabel);
        passwordRulesLabel.setText(AuthService.PASSWORD_RULES);

        var draft = Data.getSignUpDraft();
        if (draft.email != null) emailField.setText(draft.email);
        if (draft.password != null) passwordField.setText(draft.password);
        if (draft.confirmPassword != null) confirmPasswordField.setText(draft.confirmPassword);
        termsCheck.setDisable(!draft.termsReadToEnd);
        privacyCheck.setDisable(!draft.privacyReadToEnd);
        termsCheck.setSelected(draft.termsAccepted && draft.termsReadToEnd);
        privacyCheck.setSelected(draft.privacyAccepted && draft.privacyReadToEnd);
        boolean bothRead = draft.termsReadToEnd && draft.privacyReadToEnd;
        termsHintLabel.setVisible(!bothRead);
        termsHintLabel.setManaged(!bothRead);

        emailField.textProperty().addListener((obs, o, n) -> draft.email = n);
        passwordField.textProperty().addListener((obs, o, n) -> draft.password = n);
        confirmPasswordField.textProperty().addListener((obs, o, n) -> draft.confirmPassword = n);
        termsCheck.selectedProperty().addListener((obs, o, n) -> {
            if (!draft.termsReadToEnd) {
                termsCheck.setSelected(false);
                draft.termsAccepted = false;
                return;
            }
            draft.termsAccepted = n;
        });
        privacyCheck.selectedProperty().addListener((obs, o, n) -> {
            if (!draft.privacyReadToEnd) {
                privacyCheck.setSelected(false);
                draft.privacyAccepted = false;
                return;
            }
            draft.privacyAccepted = n;
        });

        createBtn.disableProperty().bind(busy
                .or(emailField.textProperty().isEmpty())
                .or(passwordField.textProperty().isEmpty())
                .or(confirmPasswordField.textProperty().isEmpty())
                .or(termsCheck.selectedProperty().not())
                .or(privacyCheck.selectedProperty().not()));

        // Inline validation: check the email once the user leaves the field, and re-check as they fix it.
        emailField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) updateEmailError(true);
        });
        emailField.textProperty().addListener((obs, o, n) -> {
            messageLabel.setText("");
            updateEmailError(false);
        });
        passwordField.textProperty().addListener((obs, o, n) -> {
            messageLabel.setText("");
            updatePasswordHint();
            updateConfirmError();
        });
        confirmPasswordField.textProperty().addListener((obs, o, n) -> {
            messageLabel.setText("");
            updateConfirmError();
        });
        updatePasswordHint();
        updateConfirmError();

        Platform.runLater(emailField::requestFocus);
    }

    private static void hideWhenEmpty(Label label) {
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
    }

    /** @param showIfInvalid when false, only clears an existing error (so we don't nag mid-typing). */
    private void updateEmailError(boolean showIfInvalid) {
        String email = AuthService.normalize(emailField.getText());
        boolean valid = email == null || email.isEmpty() || AuthService.isValidEmail(email);
        if (valid) {
            emailErrorLabel.setText("");
            emailField.getStyleClass().remove("input-invalid");
        } else if (showIfInvalid || !emailErrorLabel.getText().isEmpty()) {
            emailErrorLabel.setText("Enter a valid email address, like name@example.com.");
            if (!emailField.getStyleClass().contains("input-invalid")) {
                emailField.getStyleClass().add("input-invalid");
            }
        }
    }

    private void updatePasswordHint() {
        String p = passwordField.getText();
        boolean ok = p == null || p.isEmpty() || AuthService.isStrongEnoughPassword(p);
        passwordRulesLabel.getStyleClass().setAll("label", ok ? "auth-hint" : "auth-field-error");
    }

    private void updateConfirmError() {
        String p1 = passwordField.getText();
        String p2 = confirmPasswordField.getText();
        boolean mismatch = p2 != null && !p2.isEmpty() && !p2.equals(p1);
        confirmErrorLabel.setText(mismatch ? "Passwords don't match." : "");
    }

    @FXML
    public void onSignUp() {
        if (createBtn.isDisabled()) return;
        String email = emailField.getText();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        boolean accepted = termsCheck.isSelected() && privacyCheck.isSelected();

        if (password == null || !password.equals(confirm)) {
            showError("Passwords don't match.");
            return;
        }

        busy.set(true);
        Background.run(() -> authService.signUp(email, password, accepted), result -> {
            busy.set(false);
            switch (result) {
                case SUCCESS -> {
                    // Clear draft on success (avoid carrying sensitive password fields).
                    var d = Data.getSignUpDraft();
                    d.email = "";
                    d.password = "";
                    d.confirmPassword = "";
                    d.termsAccepted = false;
                    d.privacyAccepted = false;
                    d.termsReadToEnd = false;
                    d.privacyReadToEnd = false;
                    goToLogin("Account created. Please log in.");
                }
                case TERMS_NOT_ACCEPTED -> showError("Please accept the Terms of Service and Privacy Policy.");
                case INVALID_EMAIL -> {
                    updateEmailError(true);
                    showError("Enter a valid email address, like name@example.com.");
                }
                case WEAK_PASSWORD -> showError("Password is too weak. " + AuthService.PASSWORD_RULES);
                case EMAIL_ALREADY_USED -> showError("An account with this email already exists. Try logging in or resetting your password.");
                case DB_ERROR -> showError("We couldn't reach the server. Please try again.");
            }
        }, ex -> {
            busy.set(false);
            showError("We couldn't reach the server. Please try again.");
        });
    }

    private void showError(String msg) {
        messageLabel.getStyleClass().setAll("label", UI.ERROR);
        messageLabel.setText(msg);
    }

    @FXML
    public void goToLogin() {
        goToLogin(null);
    }

    private void goToLogin(String infoMessage) {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(UI.authPage(loader.load()), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            if (infoMessage != null) {
                LoginController login = loader.getController();
                login.showInfo(infoMessage);
            }
            stage.setScene(scene);
        } catch (Exception e) {
            showError("Could not open the login screen.");
        }
    }

    @FXML
    public void goToTerms() {
        goTo("/fxml/terms.fxml");
    }

    @FXML
    public void goToPrivacy() {
        goTo("/fxml/privacy.fxml");
    }

    private void goTo(String fxmlPath) {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(loader.load(), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            stage.setScene(scene);
        } catch (Exception e) {
            showError("Could not open that screen.");
        }
    }
}
