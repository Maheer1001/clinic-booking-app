package controller;

import app.Data;
import dao.UserDAO;
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

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    @FXML private Button continueBtn;

    private final AuthService authService = new AuthService();
    private final UserDAO userDAO = new UserDAO();

    @FXML
    private void initialize() {
        // Error label takes no space until there is a message to show.
        messageLabel.managedProperty().bind(messageLabel.textProperty().isNotEmpty());
        messageLabel.visibleProperty().bind(messageLabel.textProperty().isNotEmpty());

        continueBtn.disableProperty().bind(
                emailField.textProperty().isEmpty()
                        .or(passwordField.textProperty().isEmpty())
        );

        emailField.textProperty().addListener((obs, o, n) -> messageLabel.setText(""));
        passwordField.textProperty().addListener((obs, o, n) -> messageLabel.setText(""));
    }

    @FXML
    public void onLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        AuthService.SignInResult result = authService.signIn(email, password);

        switch (result) {
            case SUCCESS -> {
                try {
                    Integer userId = userDAO.getUserIdByEmail(email);
                    String name = userId != null ? userDAO.getDisplayNameByUserId(userId) : null;
                    Data.setCurrentUser(userId != null ? userId : -1, email, name);
                    Stage stage = (Stage) emailField.getScene().getWindow();
                    createNavigator(stage, userId != null ? userId : 0);
                } catch (SQLException ex) {
                    messageLabel.setText("Database error.");
                }
            }
            case INVALID_INPUT -> messageLabel.setText("Invalid email or password.");
            case USER_NOT_FOUND -> messageLabel.setText("User not found.");
            case WRONG_PASSWORD -> messageLabel.setText("Wrong password.");
            case DB_ERROR -> messageLabel.setText("Database error.");
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
            messageLabel.setText("ERROR");
        }
    }

    public void createNavigator(Stage stage, int userID) {
        MainView mainView = new MainView();
        Navigator nav = new Navigator(mainView);

        javafx.scene.Scene scene = new javafx.scene.Scene(nav.getRoot(), 420, 720);
        scene.getStylesheets().add(UI.STYLE);
        Data.setScene(scene);
        try {
            Data.setSettings(UserDAO.getAccessibilitySettingsByUserId(userID));
        } catch (SQLException ignored) {}

        MainController mainController = new MainController(nav, mainView, userID);
        stage.setScene(scene);
    }

}
