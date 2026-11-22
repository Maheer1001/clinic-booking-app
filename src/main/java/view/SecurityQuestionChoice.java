package view;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import model.SecurityQuestion;
import service.SecurityQuestionService;

/**
 * The security question dropdown used on sign-up, after login and in Settings. The prompts are long
 * ("What was the name of your first secondary school teacher?"), so the shown value and the list
 * rows wrap instead of being cut off with "..." in the 420px window.
 */
public final class SecurityQuestionChoice {

    public static final String PROMPT = "Choose a security question";
    public static final String WHY = "We ask this if you change your email, to check it's you.";

    private SecurityQuestionChoice() {}

    public static ComboBox<SecurityQuestion> create() {
        ComboBox<SecurityQuestion> box = new ComboBox<>();
        configure(box);
        return box;
    }

    /** Fills and styles a dropdown (also one declared in FXML). */
    public static void configure(ComboBox<SecurityQuestion> box) {
        box.getItems().setAll(SecurityQuestion.all());
        box.setPromptText(PROMPT);
        box.setAccessibleText("Security question");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setVisibleRowCount(SecurityQuestion.all().size());
        box.setConverter(new StringConverter<>() {
            @Override public String toString(SecurityQuestion q) { return q == null ? "" : q.prompt(); }
            @Override public SecurityQuestion fromString(String s) { return null; }
        });
        box.setCellFactory(lv -> wrappingCell(box));
        box.setButtonCell(wrappingCell(box));
    }

    private static ListCell<SecurityQuestion> wrappingCell(ComboBox<SecurityQuestion> box) {
        ListCell<SecurityQuestion> cell = new ListCell<>() {
            @Override
            protected void updateItem(SecurityQuestion q, boolean empty) {
                super.updateItem(q, empty);
                // The button cell shows the prompt text while nothing is chosen.
                setText(empty || q == null ? box.getPromptText() : q.prompt());
            }
        };
        cell.setWrapText(true);
        cell.setMinHeight(Region.USE_PREF_SIZE);
        // Wrap at the dropdown's width (less the arrow), not the text's natural one-line width.
        cell.prefWidthProperty().bind(box.widthProperty().subtract(40));
        return cell;
    }

    /** Client-side answer check, matching the server ("" when fine). */
    public static String answerProblem(String answer) {
        return SecurityQuestionService.isValidAnswer(answer) ? "" : SecurityQuestionService.SetResult.INVALID_ANSWER.message();
    }
}
