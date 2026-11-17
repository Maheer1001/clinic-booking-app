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

public class TermsController {

    @FXML private ScrollPane scrollPane;
    @FXML private Label contentLabel;
    @FXML private Button returnBtn;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        contentLabel.setText(buildTermsText());
        if (Data.getSignUpDraft().termsReadToEnd) {
            markRead();
        } else {
            ReadToEndTracker.onReadToEnd(scrollPane, () -> {
                Data.getSignUpDraft().termsReadToEnd = true;
                markRead();
            });
        }
        Platform.runLater(scrollPane::requestFocus);
    }

    private void markRead() {
        statusLabel.setText("You can now tick the Terms of Service box on the sign-up screen.");
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
            Scene scene = new Scene(loader.load(), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            stage.setScene(scene);
        } catch (Exception ignored) {
        }
    }

    private static String buildTermsText() {
        return """
                Terms of Service (demo)

                1. Purpose
                This app is a university project and is provided for demonstration purposes.

                2. Appointments and reminders
                Any booking, reminder, or notification features are non-clinical and should not be relied upon for medical decisions.

                3. Acceptable use
                Do not misuse the app, attempt to break security, or upload harmful content.

                4. Availability
                The app may be unavailable, changed, or removed at any time.

                5. Contact
                For questions, contact the development team.
                """;
    }
}

