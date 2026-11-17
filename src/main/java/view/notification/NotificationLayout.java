package view.notification;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import view.ui.UI;

/**
 * Structured rows for in-app notification popups (aligned with {@link view.ui.UI} styles).
 */
public final class NotificationLayout {

    private NotificationLayout() {}

    public static HBox detailRow(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add(UI.MUTED);
        l.setMinWidth(Region.USE_PREF_SIZE);
        Label v = new Label(value);
        v.getStyleClass().add(UI.BODY);
        v.setWrapText(true);
        v.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(v, Priority.ALWAYS);
        HBox row = new HBox(12, l, v);
        row.setAlignment(Pos.TOP_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
}
