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
        // the app stylesheet fixes dropdowns at 44px tall; this one grows when the question wraps
        box.setStyle("-fx-pref-height: -1; -fx-min-height: 44px;");
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

            // The dropdown asks its cell for a height without saying the width, which gives one
            // line; measure at the width the text really gets so a long question wraps.
            @Override
            protected double computePrefHeight(double width) {
                return super.computePrefHeight(width > 0 ? width : textWidth(box));
            }

            @Override
            protected double computeMinHeight(double width) {
                return computePrefHeight(width);
            }
        };
        cell.setWrapText(true);
        cell.setMinHeight(Region.USE_PREF_SIZE);
        box.widthProperty().addListener((o, a, b) -> {
            cell.requestLayout();
            box.requestLayout();
        });
        return cell;
    }

    /** Room for the text inside the dropdown: its width less the arrow button and padding. */
    private static double textWidth(ComboBox<?> box) {
        return box.getWidth() > 0 ? box.getWidth() - 44 : -1;
    }

    /** Client-side answer check, matching the server ("" when fine). */
    public static String answerProblem(String answer) {
        return SecurityQuestionService.isValidAnswer(answer) ? "" : SecurityQuestionService.SetResult.INVALID_ANSWER.message();
    }
}
