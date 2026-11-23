package controller;

import app.Data;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import service.AuthService;
import service.StaffAuthService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.SecurityQuestion;
import view.SecurityQuestionChoice;
import view.ui.UI;

public class SignUpController {

    @FXML private TextField emailField;
    @FXML private Label emailErrorLabel;
    @FXML private PasswordField passwordField;
    @FXML private Label passwordRulesLabel;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label confirmErrorLabel;
    @FXML private ComboBox<SecurityQuestion> questionBox;
    @FXML private Label questionHintLabel;
    @FXML private TextField answerField;
    @FXML private Label answerErrorLabel;
    @FXML private CheckBox staffCheck;
    @FXML private VBox staffBox;
    @FXML private TextField staffCodeField;
    @FXML private CheckBox termsCheck;
    @FXML private CheckBox privacyCheck;
    @FXML private Label termsHintLabel;
    @FXML private Button createBtn;
    @FXML private Label messageLabel;

    private final AuthService authService = new AuthService();
    private final StaffAuthService staffAuthService = new StaffAuthService();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    @FXML
    private void initialize() {
        // Error labels take no space until there is a message to show.
        hideWhenEmpty(messageLabel);
        hideWhenEmpty(emailErrorLabel);
        hideWhenEmpty(confirmErrorLabel);
        hideWhenEmpty(answerErrorLabel);
        passwordRulesLabel.setText(AuthService.PASSWORD_RULES);
        SecurityQuestionChoice.configure(questionBox);
        questionHintLabel.setText(SecurityQuestionChoice.WHY);

        var draft = Data.getSignUpDraft();
        if (draft.email != null) emailField.setText(draft.email);
        if (draft.password != null) passwordField.setText(draft.password);
        if (draft.confirmPassword != null) confirmPasswordField.setText(draft.confirmPassword);
        // kept while the user reads the Terms / Privacy screens, like the other fields
        SecurityQuestion.fromKey(draft.securityQuestionKey).ifPresent(questionBox::setValue);
        if (draft.securityAnswer != null) answerField.setText(draft.securityAnswer);
        // "I'm clinic staff" reveals the staff code field; both are kept while reading Terms / Privacy
        staffBox.visibleProperty().bind(staffCheck.selectedProperty());
        staffBox.managedProperty().bind(staffCheck.selectedProperty());
        staffCheck.setSelected(draft.staff);
        if (draft.staffCode != null) staffCodeField.setText(draft.staffCode);
        staffCheck.selectedProperty().addListener((obs, o, n) -> {
            draft.staff = n;
            messageLabel.setText("");
            if (n) Platform.runLater(staffCodeField::requestFocus);
        });
        staffCodeField.textProperty().addListener((obs, o, n) -> {
            draft.staffCode = n;
            messageLabel.setText("");
        });
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
        questionBox.valueProperty().addListener((obs, o, n) -> {
            draft.securityQuestionKey = n == null ? null : n.key();
            messageLabel.setText("");
        });
        answerField.textProperty().addListener((obs, o, n) -> {
            draft.securityAnswer = n;
            messageLabel.setText("");
            // clear a "too short" note as soon as the answer is long enough
            if (!answerErrorLabel.getText().isEmpty()) updateAnswerError();
        });
        answerField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && !answerField.getText().isEmpty()) updateAnswerError();
        });
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
                .or(questionBox.valueProperty().isNull())
                .or(answerField.textProperty().isEmpty())
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

    private void updateAnswerError() {
        String problem = SecurityQuestionChoice.answerProblem(answerField.getText());
        answerErrorLabel.setText(problem);
        answerField.getStyleClass().remove("input-invalid");
        if (!problem.isEmpty()) answerField.getStyleClass().add("input-invalid");
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
        String questionKey = questionBox.getValue() == null ? null : questionBox.getValue().key();
        String answer = answerField.getText();

        if (password == null || !password.equals(confirm)) {
            showError("Passwords don't match.");
            return;
        }

        if (staffCheck.isSelected()) {
            signUpStaff(email, password, accepted, staffCodeField.getText(), questionKey, answer);
            return;
        }

        busy.set(true);
        Background.run(() -> authService.signUp(email, password, accepted, questionKey, answer), result -> {
            busy.set(false);
            switch (result) {
                case SUCCESS -> {
                    clearDraft();
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
                case INVALID_SECURITY_QUESTION -> {
                    showError("Choose a security question from the list.");
                    questionBox.requestFocus();
                }
                case INVALID_SECURITY_ANSWER -> {
                    updateAnswerError();
                    answerField.requestFocus();
                }
            }
        }, ex -> {
            busy.set(false);
            showError("We couldn't reach the server. Please try again.");
        });
    }

    /**
     * "I'm clinic staff": the clinic staff code decides which clinic the account is tied to. Every
     * outcome's text comes from the result; on success the login screen shows it (new staff may have
     * to wait for their clinic admin to approve them).
     */
    private void signUpStaff(String email, String password, boolean accepted, String code,
                             String questionKey, String answer) {
        busy.set(true);
        Background.run(() -> staffAuthService.signUpStaff(email, password, accepted, code, questionKey, answer), result -> {
            busy.set(false);
            switch (result.status()) {
                case SUCCESS -> {
                    clearDraft();
                    goToLogin(result.message());
                    return;
                }
                case INVALID_EMAIL -> updateEmailError(true);
                case INVALID_SECURITY_QUESTION -> questionBox.requestFocus();
                case INVALID_SECURITY_ANSWER -> {
                    updateAnswerError();
                    answerField.requestFocus();
                }
                case MISSING_STAFF_CODE, INVALID_STAFF_CODE -> staffCodeField.requestFocus();
                default -> {}
            }
            showError(result.message());
        }, ex -> {
            busy.set(false);
            showError("We couldn't reach the server. Please try again.");
        });
    }

    /** Clears the draft after an account is made (it holds the password and the answer). */
    private static void clearDraft() {
        var d = Data.getSignUpDraft();
        d.email = "";
        d.password = "";
        d.confirmPassword = "";
        d.securityQuestionKey = null;
        d.securityAnswer = "";
        d.termsAccepted = false;
        d.privacyAccepted = false;
        d.termsReadToEnd = false;
        d.privacyReadToEnd = false;
        d.staff = false;
        d.staffCode = "";
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
