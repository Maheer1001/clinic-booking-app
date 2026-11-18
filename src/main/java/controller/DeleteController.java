package controller;

import app.Data;
import javafx.scene.control.*;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import service.UserService;
import view.ui.UI;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller for the deleting user information.
 */

public class DeleteController {

    /**
     * Asks for confirmation, then deletes the account and all its data.
     *
     * @param onDeleted called on the FX thread after a successful delete, with a message to show
     *                  on the login screen; responsible for clearing the session and leaving the app UI.
     */
    public DeleteController(int userId, Consumer<String> onDeleted) {
        UserService userService = new UserService();

        ButtonType delete = new ButtonType("Delete my account");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete your account?");
        alert.setContentText("This permanently deletes your account, appointments, test results, "
                + "prescriptions and reminders. Upcoming bookings will be cancelled.\n\nThis cannot be undone.");
        styleDialog(alert);
        alert.getButtonTypes().setAll(delete, cancel);

        DialogPane dialogPane = alert.getDialogPane();
        Button deleteBtn = (Button) dialogPane.lookupButton(delete);
        deleteBtn.getStyleClass().add(UI.BTN_DANGER);
        // Enter should never trigger the destructive action.
        deleteBtn.setDefaultButton(false);
        Button cancelBtn = (Button) dialogPane.lookupButton(cancel);
        cancelBtn.getStyleClass().add(UI.ALERT_IGNORE);
        cancelBtn.setDefaultButton(true);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != delete) return;

        Background.run(() -> {
            try {
                userService.deleteUser(userId);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, ignored -> onDeleted.accept("Your account and its data have been deleted."), ex -> {
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Could not delete account");
            error.setHeaderText("Your account was not deleted");
            error.setContentText("Something went wrong and nothing was removed. Please try again later.");
            styleDialog(error);
            error.showAndWait();
        });
    }

    private void styleDialog(Alert alert) {
        UI.styleDialog(alert, Data.getScene());
    }
}
