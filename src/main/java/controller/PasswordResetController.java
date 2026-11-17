package controller;

import app.Main;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import service.AuthService;
import service.PasswordResetService;
import view.ui.UI;

public class PasswordResetController {

    /** Same text whether or not the account exists, so this screen can't be used to discover accounts. */
    static final String CODE_SENT_MESSAGE =
            "If an account exists for that email, we've sent a 6-digit code. It expires in 10 minutes.";

    @FXML private TextField emailField;
    @FXML private Button sendOtpBtn;
    @FXML private TextField otpField;
    @FXML private Button verifyOtpBtn;
    @FXML private VBox newPasswordBox;
    @FXML private PasswordField newPasswordField;
    @FXML private Label passwordRulesLabel;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label confirmErrorLabel;
    @FXML private Button confirmResetBtn;
    @FXML private Label messageLabel;

    private final PasswordResetService service = new PasswordResetService();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty verified = new SimpleBooleanProperty(false);

    @FXML
    private void initialize() {
        hideWhenEmpty(messageLabel);
        hideWhenEmpty(confirmErrorLabel);
        passwordRulesLabel.setText(AuthService.PASSWORD_RULES);

        // Digits only, at most 6.
        otpField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,6}") ? change : null));

        emailField.disableProperty().bind(busy.or(verified));
        sendOtpBtn.disableProperty().bind(busy.or(verified).or(emailField.textProperty().isEmpty()));
        otpField.disableProperty().bind(busy.or(verified));
        verifyOtpBtn.disableProperty().bind(busy.or(verified).or(otpField.textProperty().length().lessThan(6)));
        confirmResetBtn.disableProperty().bind(busy
                .or(newPasswordField.textProperty().isEmpty())
                .or(confirmPasswordField.textProperty().isEmpty()));

        emailField.textProperty().addListener((obs, o, n) -> clearMessage());
        otpField.textProperty().addListener((obs, o, n) -> clearMessage());
        newPasswordField.textProperty().addListener((obs, o, n) -> { clearMessage(); updateConfirmError(); });
        confirmPasswordField.textProperty().addListener((obs, o, n) -> { clearMessage(); updateConfirmError(); });

        Platform.runLater(emailField::requestFocus);
    }

    @FXML
    public void onSendOtp() {
        if (sendOtpBtn.isDisabled()) return;
        clearMessage();
        String email = emailField.getText();
        busy.set(true);
        Background.run(() -> service.sendOtp(email), r -> {
            busy.set(false);
            switch (r) {
                case SENT, EMAIL_NOT_FOUND -> {
                    showInfo(CODE_SENT_MESSAGE);
                    otpField.requestFocus();
                }
                case INVALID_EMAIL -> showError("Enter a valid email address, like name@example.com.");
                case TOO_SOON -> showError("A code was sent less than a minute ago. Check your inbox, or try again shortly.");
                case EMAIL_SEND_FAILED -> showError("We couldn't send the email. Please try again.");
                case DB_ERROR -> showError("Something went wrong on our side. Please try again.");
            }
        }, ex -> {
            busy.set(false);
            showError("Something went wrong on our side. Please try again.");
        });
    }

    @FXML
    public void onVerifyOtp() {
        if (verifyOtpBtn.isDisabled()) return;
        clearMessage();
        String email = emailField.getText();
        String code = otpField.getText();
        busy.set(true);
        Background.run(() -> service.verifyOtp(email, code), r -> {
            busy.set(false);
            switch (r) {
                case VALID -> {
                    verified.set(true);
                    newPasswordBox.setManaged(true);
                    newPasswordBox.setVisible(true);
                    newPasswordField.requestFocus();
                }
                case EXPIRED -> showError("That code has expired. Request a new one.");
                case INVALID -> showError("That code isn't right. Check the latest email and try again.");
                case TOO_MANY_ATTEMPTS -> {
                    otpField.clear();
                    showError("Too many incorrect attempts. Request a new code.");
                }
            }
        }, ex -> {
            busy.set(false);
            showError("Something went wrong on our side. Please try again.");
        });
    }

    @FXML
    public void onConfirmReset() {
        if (confirmResetBtn.isDisabled()) return;
        clearMessage();
        String p1 = newPasswordField.getText();
        String p2 = confirmPasswordField.getText();
        if (p1 == null || !p1.equals(p2)) {
            showError("The passwords don't match.");
            return;
        }

        String email = emailField.getText();
        busy.set(true);
        Background.run(() -> service.resetPassword(email, p1), r -> {
            busy.set(false);
            switch (r) {
                case SUCCESS -> goToLogin("Password updated. Log in with your new password.");
                case WEAK_PASSWORD -> showError("Password is too weak. " + AuthService.PASSWORD_RULES);
                case OTP_NOT_VALIDATED, EMAIL_NOT_FOUND -> showError("Your code is no longer valid. Request a new one.");
                case DB_ERROR -> showError("Something went wrong on our side. Please try again.");
            }
        }, ex -> {
            busy.set(false);
            showError("Something went wrong on our side. Please try again.");
        });
    }

    @FXML
    public void goToLogin() {
        goToLogin(null);
    }

    private void goToLogin(String infoMessage) {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            if (infoMessage != null) {
                LoginController login = loader.getController();
                login.showInfo(infoMessage);
            }
            stage.setScene(scene);
        } catch (Exception e) {
            showError("Could not load login screen.");
        }
    }

    private void updateConfirmError() {
        String p1 = newPasswordField.getText();
        String p2 = confirmPasswordField.getText();
        boolean mismatch = p2 != null && !p2.isEmpty() && !p2.equals(p1);
        confirmErrorLabel.setText(mismatch ? "Passwords don't match." : "");
    }

    private static void hideWhenEmpty(Label label) {
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
    }

    private void clearMessage() {
        messageLabel.setText("");
        messageLabel.getStyleClass().setAll("label", UI.ERROR);
    }

    private void showError(String msg) {
        messageLabel.getStyleClass().setAll("label", UI.ERROR);
        messageLabel.setText(msg);
    }

    private void showInfo(String msg) {
        messageLabel.getStyleClass().setAll("label", "auth-success");
        messageLabel.setText(msg);
    }
}
