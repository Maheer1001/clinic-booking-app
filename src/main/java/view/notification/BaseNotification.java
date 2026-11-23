package view.notification;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import view.ui.UI;

/**
 * Abstract base class for all notifications.
 * Renders a styled popup: inner heading (from {@link #getTitle()}) and body, with a short window title
 * so the OS title bar does not duplicate the main headline.
 */
public abstract class BaseNotification implements Notification {

    private Stage stage;
    private VBox root;
    private Node previewContent;
    private Node fullContent;
    private boolean previewMode;
    /** Called once the popup window has closed; set by {@link #show(Runnable)}. */
    private Runnable onClosed;

    @Override
    public void show(Runnable onClosed) {
        this.onClosed = onClosed;
        show();
    }

    @Override
    public void show() {
        if (Platform.isFxApplicationThread()) {
            buildAndShow();
        } else {
            Platform.runLater(this::buildAndShow);
        }
    }

    @Override
    public void dismiss() {
        if (stage != null && stage.isShowing()) {

            if (Platform.isFxApplicationThread()) {
                stage.close();
            } else {
                Platform.runLater(stage::close);
            }
        }
    }

    /**
     * Short string for the window / taskbar (avoids truncating long headlines and duplicating the in-body title).
     */
    protected String getStageTitle() {
        return "Message";
    }

    protected boolean supportsDiscreetToggle() {
        return false;
    }

    protected boolean isDiscreetEnabled() {
        return true;
    }

    protected String getPreviewText() {
        return getMessage();
    }

    protected String getFullText() {
        return getMessage();
    }

    /**
     * Default: title + message text in a card. Subclasses with rich layouts should override.
     */
    protected Node buildPreviewContent() {
        return defaultStackedBody(getPreviewText());
    }

    protected Node buildFullContent() {
        return defaultStackedBody(getFullText());
    }

    private Node defaultStackedBody(String text) {
        Label heading = UI.titleSm(getTitle());
        Label body = new Label(text);
        body.setWrapText(true);
        VBox card = UI.card(10, body);
        return new VBox(10, heading, card);
    }

    private void buildAndShow() {
        stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.setTitle(getStageTitle());
        stage.setResizable(true);
        stage.setMinWidth(360);
        stage.setMinHeight(200);
        stage.getIcons().add(new Image("/icons/safe.png"));

        boolean usePreview = supportsDiscreetToggle() && isDiscreetEnabled();
        previewMode = usePreview;

        this.previewContent = buildPreviewContent();
        this.fullContent = supportsDiscreetToggle() ? buildFullContent() : previewContent;
        Node initialContent = usePreview ? previewContent : fullContent;

        ScrollPane scroll = new ScrollPane(initialContent);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.setMaxHeight(420);

        Button dismissButton = UI.secondaryButton("Dismiss");
        dismissButton.setOnAction(e -> stage.close());

        HBox buttonRow;
        if (usePreview) {
            Button detailsButton = UI.primaryButton("View details");
            detailsButton.setOnAction(e -> {
                if (previewMode) {
                    scroll.setContent(fullContent);
                    detailsButton.setText("Hide details");
                    previewMode = false;
                } else {
                    scroll.setContent(previewContent);
                    detailsButton.setText("View details");
                    previewMode = true;
                }
                if (stage != null) {
                    stage.sizeToScene();
                }
            });
            buttonRow = new HBox(10, detailsButton, dismissButton);
        } else {
            buttonRow = new HBox(10, dismissButton);
        }

        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        UI.equalSpacing(buttonRow);

        root = new VBox(12, scroll, buttonRow);
        root.getStyleClass().add(UI.NOTIFICATION);

        Scene scene = new Scene(root);
        var cssUrl = BaseNotification.class.getResource("/css/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        Runnable closed = onClosed;
        onClosed = null;
        if (closed != null) stage.setOnHidden(e -> closed.run());
        stage.show();
    }
}
