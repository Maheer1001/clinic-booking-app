package view;

import app.Data;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import model.AccessibilitySettings;
import org.controlsfx.control.ToggleSwitch;
import view.ui.UI;

public class AccessibilitySettingsView {

    public static Node getPage() {
        if (Data.getSettings() == null) {
            Data.setSettings(new AccessibilitySettings());
        }
        AccessibilitySettings settings = Data.getSettings();

        ToggleSwitch contrastSwitch = new ToggleSwitch();
        contrastSwitch.setAccessibleText("High contrast");
        contrastSwitch.selectedProperty().bindBidirectional(settings.highContrastProperty());

        ToggleSwitch boldTextSwitch = new ToggleSwitch();
        boldTextSwitch.setAccessibleText("Bold text");
        boldTextSwitch.selectedProperty().bindBidirectional(settings.boldTextProperty());

        Slider textSizeSlider = new Slider(0, 2, toSliderValue(settings.getTextSize()));
        textSizeSlider.setMajorTickUnit(1);
        textSizeSlider.setMinorTickCount(0);
        textSizeSlider.setSnapToTicks(true);
        textSizeSlider.setShowTickMarks(true);
        textSizeSlider.setShowTickLabels(true);
        textSizeSlider.setBlockIncrement(1);
        textSizeSlider.setLabelFormatter(new StringConverter<>() {
            @Override public String toString(Double v) { return sizeName(fromSliderValue(v)); }
            @Override public Double fromString(String s) { return 1.0; }
        });
        textSizeSlider.setAccessibleText("Text size");

        settings.textSizeProperty().addListener((obs, o, n) -> textSizeSlider.setValue(toSliderValue(n)));

        textSizeSlider.valueProperty().addListener((obs, o, n) -> {
            // Only commit whole steps, so dragging doesn't flip between sizes mid-drag.
            if (textSizeSlider.isValueChanging()) return;
            settings.setTextSize(fromSliderValue(n.doubleValue()));
        });
        textSizeSlider.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) settings.setTextSize(fromSliderValue(textSizeSlider.getValue()));
        });

        HBox sizeRow = new HBox(20, new Label("Text Size: "), textSizeSlider);
        sizeRow.setAlignment(Pos.CENTER_LEFT);
        HBox contrastRow = new HBox(20, new Label("High Contrast: "), contrastSwitch);
        contrastRow.setAlignment(Pos.CENTER_LEFT);
        HBox boldRow = new HBox(20, new Label("Bold Text: "), boldTextSwitch);
        boldRow.setAlignment(Pos.CENTER_LEFT);

        Label note = UI.muted("Changes apply straight away and are saved to your account.");
        note.setWrapText(true);

        return UI.page(contrastRow, boldRow, sizeRow, note);
    }

    static double toSliderValue(AccessibilitySettings.TextSize size) {
        if (size == null) return 1;
        return switch (size) {
            case SMALL -> 0;
            case LARGE -> 2;
            default -> 1;
        };
    }

    static AccessibilitySettings.TextSize fromSliderValue(double v) {
        long step = Math.round(v);
        if (step <= 0) return AccessibilitySettings.TextSize.SMALL;
        if (step >= 2) return AccessibilitySettings.TextSize.LARGE;
        return AccessibilitySettings.TextSize.MEDIUM;
    }

    static String sizeName(AccessibilitySettings.TextSize size) {
        if (size == null) return "Default";
        return switch (size) {
            case SMALL -> "Small";
            case LARGE -> "Large";
            default -> "Default";
        };
    }
}
