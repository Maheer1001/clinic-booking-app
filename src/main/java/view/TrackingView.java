package view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import view.ui.UI;

/**
 * View for the Tracking section: menu (Appointments, Test Results, Prescriptions) and list/detail layouts.
 */
public class TrackingView {
    private final Button appointmentsButton = UI.menuButton("Appointments");
    private final Button testResultsButton = UI.menuButton("Test Results");
    private final Button prescriptionsButton = UI.menuButton("Prescriptions");

    /** Returns the tracking menu (three section buttons). */
    public VBox getTrackingMainScene() {
        return getTrackingMainScene(new Node[0]);
    }

    /** Returns the tracking menu with optional extra shortcut actions. */
    public VBox getTrackingMainScene(Node... extraActions) {
        VBox buttonVbox = new VBox(10, appointmentsButton, testResultsButton, prescriptionsButton);
        if (extraActions != null && extraActions.length > 0) {
            buttonVbox.getChildren().addAll(extraActions);
        }
        VBox.setVgrow(buttonVbox, Priority.ALWAYS);
        buttonVbox.getStyleClass().add(UI.CENTER);

        VBox vbox = new VBox(10, buttonVbox);
        vbox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vbox.getStyleClass().add(UI.PAGE);
        return vbox;
    }

    public Button getTestResultsButton() { return testResultsButton; }
    public Button getPrescriptionsButton() { return prescriptionsButton; }
    public Button getAppointmentsButton() { return appointmentsButton; }

    /** Applies spacing and page style to a VBox of entry buttons. */
    public VBox getEntryListScene(VBox entriesVbox) {
        entriesVbox.setSpacing(10);
        entriesVbox.getStyleClass().add(UI.PAGE);
        return entriesVbox;
    }

    /** Returns a list-style button with left/right labels (e.g. test type and date). */
    public Button getEntryButton(String left, String right) {
        Label leftLabel = new Label(left);
        leftLabel.setAlignment(Pos.CENTER_LEFT);
        leftLabel.getStyleClass().add("entry-title");
        leftLabel.setMinWidth(0);
        Label rightLabel = new Label(right);
        rightLabel.setAlignment(Pos.CENTER_RIGHT);
        rightLabel.getStyleClass().add("entry-meta");
        rightLabel.setMinWidth(Region.USE_PREF_SIZE);

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox hbox = new HBox(10, leftLabel, space, rightLabel);
        hbox.setAlignment(Pos.CENTER);

        Button button = UI.listButton();
        button.setGraphic(hbox);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        hbox.prefWidthProperty().bind(button.widthProperty().subtract(34));
        button.setAccessibleText(left + ", " + right);
        return button;
    }

    /** The header row of a detail card: the entry's name and date, styled larger than in the list. */
    public HBox getEntryHeader(String left, String right) {
        HBox header = (HBox) getEntryButton(left, right).getGraphic();
        header.prefWidthProperty().unbind();
        header.getStyleClass().add("entry-header");
        ((Label) header.getChildren().getFirst()).setWrapText(true);
        return header;
    }

    /** Returns a page-style VBox with the entry header and detail content. */
    public VBox getEntryDetailsScene(HBox graphic, VBox info) {
        VBox root = new VBox(10, graphic, info);
        root.getStyleClass().add(UI.PAGE);
        return root;
    }
}
