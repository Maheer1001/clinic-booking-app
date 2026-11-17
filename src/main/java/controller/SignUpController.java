package controller;

import app.Data;
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
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private CheckBox termsCheck;
    @FXML private CheckBox privacyCheck;
    @FXML private Label messageLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        // Error label takes no space until there is a message to show.
        messageLabel.managedProperty().bind(messageLabel.textProperty().isNotEmpty());
        messageLabel.visibleProperty().bind(messageLabel.textProperty().isNotEmpty());

        var draft = Data.getSignUpDraft();
        if (draft.email != null) emailField.setText(draft.email);
        if (draft.password != null) passwordField.setText(draft.password);
        if (draft.confirmPassword != null) confirmPasswordField.setText(draft.confirmPassword);
        termsCheck.setDisable(!draft.termsReadToEnd);
        privacyCheck.setDisable(!draft.privacyReadToEnd);
        termsCheck.setSelected(draft.termsAccepted && draft.termsReadToEnd);
        privacyCheck.setSelected(draft.privacyAccepted && draft.privacyReadToEnd);

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

        emailField.textProperty().addListener((obs, o, n) -> messageLabel.setText(""));
        passwordField.textProperty().addListener((obs, o, n) -> messageLabel.setText(""));
        confirmPasswordField.textProperty().addListener((obs, o, n) -> messageLabel.setText(""));
    }

    @FXML
    public void onSignUp() {
        String email = emailField.getText();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        boolean accepted = termsCheck.isSelected() && privacyCheck.isSelected();

        if (password == null || !password.equals(confirm)) {
            messageLabel.setText("Passwords do not match.");
            return;
        }

        AuthService.SignUpResult result = authService.signUp(email, password, accepted);

        switch (result) {
            case SUCCESS -> {
                messageLabel.setText("Account created. Please log in.");
                // Clear draft on success (avoid carrying sensitive password fields).
                var d = Data.getSignUpDraft();
                d.email = "";
                d.password = "";
                d.confirmPassword = "";
                d.termsAccepted = false;
                d.privacyAccepted = false;
                d.termsReadToEnd = false;
                d.privacyReadToEnd = false;
                goToLogin();
            }
            case TERMS_NOT_ACCEPTED -> messageLabel.setText("Please accept Terms and Privacy.");
            case INVALID_EMAIL -> messageLabel.setText("Invalid email.");
            case WEAK_PASSWORD -> messageLabel.setText("Password must be 8+ with letters and digits.");
            case EMAIL_ALREADY_USED -> messageLabel.setText("Email already used.");
            case DB_ERROR -> messageLabel.setText("Database error.");
        }
    }

    @FXML
    public void goToLogin() {
        goTo("/fxml/login.fxml");
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
            messageLabel.setText("ERROR");
        }
    }
}
