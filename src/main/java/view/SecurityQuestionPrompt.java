package view;

import app.Data;
import controller.AccountController;
import controller.Background;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.SecurityQuestion;
import service.SecurityQuestionService.SetResult;
import view.ui.UI;

/**
 * After login, accounts made before security questions existed are asked once to choose one.
 * "Later" closes it; Settings keeps "Change email" unavailable (and says why) until one is set.
 */
public final class SecurityQuestionPrompt {

    public static final String TITLE = "Add a security question";
    public static final String BODY = "Choose a question only you know the answer to. "
            + "We ask it before your email can be changed, to check it's you.";

    private SecurityQuestionPrompt() {}

    /** Checks in the background and shows the prompt only when the signed-in user has no question. */
    public static void offerIfMissing(int userId) {
        AccountController controller = new AccountController();
        Background.run(() -> controller.hasSecurityQuestion(userId), has -> {
            // still the same user on the main screen (they may have logged out meanwhile)
            if (!has && Data.getCurrentUserId() == userId && Data.getScene() != null) show(controller, userId);
        }, ex -> System.err.println("[Account] Could not check the security question: " + ex.getClass().getSimpleName()));
    }

    static Dialog<ButtonType> show(AccountController controller, int userId) {
        ComboBox<SecurityQuestion> choice = SecurityQuestionChoice.create();
        TextField answer = UI.textField("");
        answer.setPromptText("Your answer");
        answer.setAccessibleText("Security answer");
        answer.setMaxWidth(Double.MAX_VALUE);
        Label body = UI.wrappingLabel(BODY);
        Label error = UI.error("");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setHeaderText(TITLE);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterType = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(saveType, laterType);
        pane.setContent(new VBox(10, body, AccountView.formLabel("Question"), choice,
                AccountView.formLabel("Answer"), answer, error));
        UI.styleDialog(dialog, Data.getScene());
        Button save = (Button) pane.lookupButton(saveType);
        save.getStyleClass().add(UI.ALERT_AGREE);
        pane.lookupButton(laterType).getStyleClass().add(UI.ALERT_IGNORE);
        save.disableProperty().bind(choice.valueProperty().isNull().or(answer.textProperty().isEmpty()));
        answer.textProperty().addListener((o, a, b) -> error.setText(""));

        // Save checks and stores the answer without closing; the dialog closes only once it worked.
        save.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            String problem = SecurityQuestionChoice.answerProblem(answer.getText());
            if (!problem.isEmpty()) {
                error.setText(problem);
                return;
            }
            SecurityQuestion q = choice.getValue();
            String a = answer.getText();
            save.disableProperty().unbind();
            save.setDisable(true);
            Background.run(() -> controller.setSecurityQuestion(userId, q.key(), a), r -> {
                // ALREADY_SET: one was saved meanwhile (e.g. another window); nothing more to ask
                if (r == SetResult.SUCCESS || r == SetResult.ALREADY_SET) {
                    dialog.setResult(saveType);
                    dialog.close();
                } else {
                    error.setText(r.message());
                    save.disableProperty().bind(choice.valueProperty().isNull().or(answer.textProperty().isEmpty()));
                }
            }, ex -> {
                error.setText(SetResult.DB_ERROR.message());
                save.disableProperty().bind(choice.valueProperty().isNull().or(answer.textProperty().isEmpty()));
            });
        });
        dialog.show();
        return dialog;
    }
}
