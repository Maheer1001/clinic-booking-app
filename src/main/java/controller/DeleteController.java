package controller;

import app.Data;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import service.UserService;
import view.reminders.AppointmentRemindersView;
import view.ui.UI;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller for the deleting user information.
 */

public class DeleteController {

    public DeleteController(int userId) throws SQLException {
        UserService userService = new UserService();


        ButtonType delete = new ButtonType("Delete");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete User");
        alert.setContentText("Are you sure you want to delete all user information? \n*Note: this action cannot be undone*");

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().addAll(Data.getScene().getStylesheets());
        Stage stage = (Stage) dialogPane.getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResource("/icons/safe.png").toExternalForm()));
        dialogPane.setGraphic(null);

        alert.getButtonTypes().setAll(delete, cancel);

        Button defaultBtn = (Button) dialogPane.lookupButton(delete);
        defaultBtn.getStyleClass().add(UI.BTN_DANGER);
        Button ignoreBtn = (Button) dialogPane.lookupButton(cancel);
        ignoreBtn.getStyleClass().add(UI.ALERT_IGNORE);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == delete) {
            userService.deleteUser(userId);
            goToLogin();
            }

        }

    private void goToLogin() {
        try {
            Stage stage = (Stage) Data.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            stage.setScene(scene);
        } catch (IOException e) {
            //if user cannot be logged out, exit app instead
            Platform.exit();
        }
    }

}
