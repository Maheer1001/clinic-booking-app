package controller;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import model.TrackingData;
import service.TrackingService;
import view.Navigator;
import view.TrackingView;
import view.ui.UI;

import java.net.URL;

/**
 * Abstract class defining functions for each controller section
 * Includes abstract search text, menu page and details page for
 * Also includes helper function for bullet pointing text
 */
public abstract class TrackingSectionController {
    /** Extra styles for the tracking screens (status pills); app.css stays shared. */
    private static final URL TRACKING_CSS = TrackingSectionController.class.getResource("/css/tracking.css");

    protected final Navigator nav;
    protected final TrackingView view;
    protected final TrackingService model;

    public TrackingSectionController(Navigator nav, TrackingView view, TrackingService model) {
        this.nav = nav;
        this.view = view;
        this.model = model;
    }

    public abstract String getSearchableText(TrackingData td);

    public abstract Node getMenuPage();

    public abstract Node getDetailsPage(TrackingData td);

    protected HBox bullet(String text) {
        Label dot = new Label("•");
        Label point = UI.body(text);
        point.setWrapText(true);
        return new HBox(10, dot, point);
    }

    /** Shown only while search / date filters leave nothing to list, so the page is never just blank. */
    protected Node noMatchesLabel(ObservableList<?> filtered) {
        VBox label = UI.emptyState(UI.ICON_SEARCH, "No matches", "Nothing matches your search or dates. Try clearing the filters.");
        label.visibleProperty().bind(Bindings.isEmpty(filtered));
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    /**
     * The coloured status pill used across Tracking (test results, prescriptions, missed visits).
     * {@code variant} picks the colours: style class {@code tracking-status-<variant>} in tracking.css.
     */
    static Label statusPill(String text, String variant) {
        Label label = new Label(text);
        label.getStyleClass().addAll("tracking-status", "tracking-status-" + variant);
        label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    /** Adds the tracking stylesheet to a page root. */
    protected static <T extends Parent> T withTrackingStyles(T root) {
        if (TRACKING_CSS != null) {
            root.getStylesheets().add(TRACKING_CSS.toExternalForm());
        }
        return root;
    }
}
