package view;

import app.Data;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import model.AccessibilitySettings;
import org.controlsfx.control.ToggleSwitch;
import view.ui.UI;

public class AccessibilitySettingsView {

    public static Node getPage() {
        ToggleSwitch contrastSwitch = new ToggleSwitch();
        contrastSwitch.selectedProperty().bindBidirectional(Data.getSettings().highContrastProperty());

        ToggleSwitch boldTextSwitch = new ToggleSwitch();
        boldTextSwitch.selectedProperty().bindBidirectional(Data.getSettings().boldTextProperty());

        Slider textSizeSlider = new Slider(0, 2, 0);
        textSizeSlider.setMajorTickUnit(1);
        textSizeSlider.setMinorTickCount(0);
        textSizeSlider.setSnapToTicks(true);
        textSizeSlider.setShowTickMarks(true);
        switch (Data.getSettings().getTextSize()) {
            case SMALL -> textSizeSlider.setValue(0);
            case LARGE -> textSizeSlider.setValue(2);
            default -> textSizeSlider.setValue(1);
        }

        Data.getSettings().textSizeProperty().addListener((obs, o, n) -> {
            switch (n) {
                case SMALL -> textSizeSlider.setValue(0);
                case LARGE -> textSizeSlider.setValue(2);
                default -> textSizeSlider.setValue(1);
            }
        });

        textSizeSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            Data.getSettings().setTextSize(
                    switch (v) {
                        case 0 -> AccessibilitySettings.TextSize.SMALL;
                        case 2 -> AccessibilitySettings.TextSize.LARGE;
                        default -> AccessibilitySettings.TextSize.MEDIUM;
                    }
            );
        });

        return UI.page(new HBox(20, new Label("High Contrast: "), contrastSwitch),
                       new HBox(20, new Label("Bold Text: "), boldTextSwitch),
                       new HBox(20, new Label("Text Size: "), textSizeSlider));
    }
}
