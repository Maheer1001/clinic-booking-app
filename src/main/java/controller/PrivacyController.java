package controller;

import app.Data;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import view.ui.UI;

public class PrivacyController {

    @FXML private ScrollPane scrollPane;
    @FXML private Label contentLabel;
    @FXML private Button returnBtn;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        contentLabel.setText(buildPrivacyText());
        if (Data.getSignUpDraft().privacyReadToEnd) {
            markRead();
        } else {
            ReadToEndTracker.onReadToEnd(scrollPane, () -> {
                Data.getSignUpDraft().privacyReadToEnd = true;
                markRead();
            });
        }
        Platform.runLater(scrollPane::requestFocus);
    }

    private void markRead() {
        statusLabel.setText("You can now tick the Privacy Policy box on the sign-up screen.");
        statusLabel.getStyleClass().setAll("label", "auth-success");
    }

    @FXML
    public void returnToSignup() {
        goTo("/fxml/signup.fxml");
    }

    private void goTo(String fxmlPath) {
        try {
            Stage stage = (Stage) scrollPane.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(UI.authPage(loader.load()), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            stage.setScene(scene);
        } catch (Exception ignored) {
        }
    }

    private static String buildPrivacyText() {
        return """
                Privacy Policy (demo)

                1. What we store
                This app stores account details (e.g., email, password hash) and any data you enter into the app.

                2. Passwords
                Passwords are stored as a hash (not plain text).

                3. Emails
                The app may send emails (e.g., reminders, password reset OTP) if email is configured.

                4. Access control
                Data is associated with your account and should not be shared.

                5. Retention
                Data may be removed at any time as this is a demo system.
                """;
    }
}

