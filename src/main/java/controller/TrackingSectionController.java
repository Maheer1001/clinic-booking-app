package controller;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import model.TrackingData;
import service.TrackingService;
import view.Navigator;
import view.TrackingView;
import view.ui.UI;

/**
 * Abstract class defining functions for each controller section
 * Includes abstract search text, menu page and details page for
 * Also includes helper function for bullet pointing text
 */
public abstract class TrackingSectionController {
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
}
