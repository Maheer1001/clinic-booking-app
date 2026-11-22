package controller;

import app.Data;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import model.AccessibilitySettings;
import service.AuthService;
import view.MainView;
import view.Navigator;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import view.ui.UI;

import java.sql.SQLException;

public class LoginController {

    /** One message for unknown email and wrong password, so login can't be used to discover accounts. */
    static final String BAD_CREDENTIALS_MESSAGE = "Incorrect email or password.";
    static final String DB_ERROR_MESSAGE = "We couldn't reach the server. Please try again.";

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    @FXML private Button continueBtn;

    private final AuthService authService = new AuthService();
    private final UserDAO userDAO = new UserDAO();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    /** What a successful login needs from the database, loaded off the FX thread. */
    private record LoginData(AuthService.SignInResult result, Integer userId, String name,
                             AccessibilitySettings settings) {}

    @FXML
    private void initialize() {
        // Error label takes no space until there is a message to show.
        messageLabel.managedProperty().bind(messageLabel.textProperty().isNotEmpty());
        messageLabel.visibleProperty().bind(messageLabel.textProperty().isNotEmpty());

        continueBtn.disableProperty().bind(
                busy.or(emailField.textProperty().isEmpty())
                        .or(passwordField.textProperty().isEmpty())
        );
        emailField.disableProperty().bind(busy);
        passwordField.disableProperty().bind(busy);

        emailField.textProperty().addListener((obs, o, n) -> clearMessage());
        passwordField.textProperty().addListener((obs, o, n) -> clearMessage());

        Platform.runLater(emailField::requestFocus);
    }

    /** Shows a positive message (e.g. after sign-up or a password reset) on this screen. */
    public void showInfo(String message) {
        messageLabel.getStyleClass().setAll("label", "auth-success");
        messageLabel.setText(message);
    }

    private void showError(String message) {
        messageLabel.getStyleClass().setAll("label", UI.ERROR);
        messageLabel.setText(message);
    }

    private void clearMessage() {
        messageLabel.setText("");
    }

    @FXML
    public void onLogin() {
        if (continueBtn.isDisabled()) return;
        String email = AuthService.normalize(emailField.getText());
        String password = passwordField.getText();
        clearMessage();
        busy.set(true);
        continueBtn.setText("Logging in…");

        Background.run(() -> loadLogin(email, password), data -> {
            busy.set(false);
            continueBtn.setText("Log in");
            switch (data.result()) {
                case SUCCESS -> {
                    if (data.userId() == null) {
                        showError(DB_ERROR_MESSAGE);
                        return;
                    }
                    Data.setCurrentUser(data.userId(), email, data.name());
                    Data.setSettings(data.settings());
                    restoreReminders(data.userId(), email, data.name());
                    Stage stage = (Stage) emailField.getScene().getWindow();
                    createNavigator(stage, data.userId());
                }
                case INVALID_INPUT -> showError("Enter a valid email address and your password.");
                case USER_NOT_FOUND, WRONG_PASSWORD -> showBadCredentials();
                case DB_ERROR -> showError(DB_ERROR_MESSAGE);
            }
        }, ex -> {
            busy.set(false);
            continueBtn.setText("Log in");
            showError(DB_ERROR_MESSAGE);
        });
    }

    /**
     * Reminders are only held in memory while the app runs, so put this user's saved reminders back
     * on the schedule each time they sign in. Runs in the background so login is not slowed down.
     */
    private static void restoreReminders(int userId, String email, String name) {
        Background.run(() -> new ReminderController(userId).restoreScheduledReminders(email, name),
                count -> System.out.println("[Reminders] Restored " + count + " saved reminder(s) for this user"),
                ex -> System.err.println("[Reminders] Could not restore saved reminders: " + ex.getMessage()));
    }

    /** Empties the password box and says the details were wrong (without saying which). */
    void showBadCredentials() {
        // Clear first: editing the password clears the message, so clearing it afterwards
        // used to wipe the error before the user could see it.
        passwordField.clear();
        passwordField.requestFocus();
        showError(BAD_CREDENTIALS_MESSAGE);
    }

    private LoginData loadLogin(String email, String password) {
        AuthService.SignInResult result = authService.signIn(email, password);
        if (result != AuthService.SignInResult.SUCCESS) return new LoginData(result, null, null, null);
        try {
            Integer userId = userDAO.getUserIdByEmail(email);
            if (userId == null) return new LoginData(AuthService.SignInResult.DB_ERROR, null, null, null);
            String name = userDAO.getDisplayNameByUserId(userId);
            AccessibilitySettings settings;
            try {
                settings = UserDAO.getAccessibilitySettingsByUserId(userId);
            } catch (SQLException e) {
                // Unreadable settings must not block login; fall back to defaults.
                settings = new AccessibilitySettings();
            }
            return new LoginData(result, userId, name, settings);
        } catch (SQLException ex) {
            return new LoginData(AuthService.SignInResult.DB_ERROR, null, null, null);
        }
    }

    @FXML
    public void goToSignUp() {
        goTo("/fxml/signup.fxml");
    }

    @FXML
    public void goToPasswordReset() {
        goTo("/fxml/password-reset.fxml");
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

    public void createNavigator(Stage stage, int userID) {
        if (Data.getSettings() == null) {
            Data.setSettings(new AccessibilitySettings());
        }

        MainView mainView = new MainView();
        Navigator nav = new Navigator(mainView);

        javafx.scene.Scene scene = new javafx.scene.Scene(nav.getRoot(), 420, 720);
        scene.getStylesheets().add(UI.STYLE);
        Data.setScene(scene);

        MainController mainController = new MainController(nav, mainView, userID);
        // save a settings change made just before the window is closed
        stage.setOnHidden(e -> mainController.flushSettings());
        stage.setScene(scene);
    }

}
