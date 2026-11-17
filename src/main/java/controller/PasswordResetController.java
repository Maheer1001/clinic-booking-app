package controller;

import app.Main;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import service.PasswordResetService;
import view.ui.UI;

public class PasswordResetController {

    @FXML private TextField emailField;
    @FXML private Button sendOtpBtn;
    @FXML private TextField otpField;
    @FXML private Button verifyOtpBtn;
    @FXML private VBox newPasswordBox;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button confirmResetBtn;
    @FXML private Label messageLabel;

    private final PasswordResetService service = new PasswordResetService();

    @FXML
    private void initialize() {
        emailField.textProperty().addListener((obs, o, n) -> clearMessage());
        otpField.textProperty().addListener((obs, o, n) -> clearMessage());
        newPasswordField.textProperty().addListener((obs, o, n) -> clearMessage());
        confirmPasswordField.textProperty().addListener((obs, o, n) -> clearMessage());
    }

    @FXML
    public void onSendOtp() {
        clearMessage();
        PasswordResetService.SendOtpResult r = service.sendOtp(emailField.getText());
        switch (r) {
            case SENT -> showInfo("Email sent.");
            case EMAIL_NOT_FOUND -> showError("Email does not exist / is not stored.");
            case INVALID_EMAIL -> showError("Invalid email.");
            case EMAIL_SEND_FAILED -> showError("Could not send email. Please try again.");
            case DB_ERROR -> showError("Database error.");
        }
    }

    @FXML
    public void onVerifyOtp() {
        clearMessage();
        PasswordResetService.VerifyOtpResult r = service.verifyOtp(emailField.getText(), otpField.getText());
        switch (r) {
            case VALID -> {
                showInfo("OTP / OTC validated.");
                newPasswordBox.setManaged(true);
                newPasswordBox.setVisible(true);
            }
            case EXPIRED -> showError("OTP / OTC expired. Please request a new code.");
            case INVALID -> showError("Incorrect OTP / OTC.");
        }
    }

    @FXML
    public void onConfirmReset() {
        clearMessage();
        String p1 = newPasswordField.getText();
        String p2 = confirmPasswordField.getText();
        if (p1 == null || p2 == null || !p1.equals(p2)) {
            showError("The password fields don't match.");
            return;
        }

        PasswordResetService.ResetPasswordResult r = service.resetPassword(emailField.getText(), p1);
        switch (r) {
            case SUCCESS -> goToLogin();
            case WEAK_PASSWORD -> showError("Password must be 8+ with letters and digits.");
            case OTP_NOT_VALIDATED -> showError("Please validate your OTP / OTC first.");
            case EMAIL_NOT_FOUND -> showError("Email does not exist / is not stored.");
            case DB_ERROR -> showError("Database error.");
        }
    }

    @FXML
    public void goToLogin() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            stage.setScene(scene);
        } catch (Exception e) {
            showError("Could not load login screen.");
        }
    }

    private void clearMessage() {
        messageLabel.setText("");
        messageLabel.getStyleClass().setAll("error");
    }

    private void showError(String msg) {
        messageLabel.getStyleClass().setAll("error");
        messageLabel.setText(msg);
    }

    private void showInfo(String msg) {
        messageLabel.getStyleClass().setAll(UI.INLINE_SAVE_CONFIRM);
        messageLabel.setText(msg);
    }
}

